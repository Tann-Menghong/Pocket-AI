package com.example.llama.updates

import android.app.*
import android.app.job.*
import android.content.*
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Environment
import android.provider.Settings
import androidx.core.content.FileProvider
import androidx.core.app.NotificationCompat
import com.example.llama.MainActivity
import com.example.llama.R
import com.example.llama.data.*
import com.example.llama.models.RemoteJson
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.*
import java.io.File
import java.security.MessageDigest

object UpdatePolicy{
 const val REPOSITORY="Tann-Menghong/Pocket-AI"
 fun version(value:String):List<Int>?=Regex("^v?(\\d+)\\.(\\d+)(?:\\.(\\d+))?(?:[-+].*)?$").matchEntire(value)?.let{m->listOf(m.groupValues[1].toIntOrNull()?:return null,m.groupValues[2].toIntOrNull()?:return null,m.groupValues[3].toIntOrNull()?:0)}
 fun compare(candidate:String,current:String):Int{
  val a=version(candidate)?:return 0;val b=version(current)?:return 0
  for(i in 0..2){if(a[i]!=b[i])return a[i].compareTo(b[i])}
  fun suffix(s:String)=s.substringBefore('+').substringAfter('-', "")
  val x=suffix(candidate);val y=suffix(current)
  if(x==y)return 0
  if(x.isEmpty())return 1
  if(y.isEmpty())return -1
  val left=x.split('.');val right=y.split('.')
  for(i in 0 until minOf(left.size,right.size)){
   val l=left[i];val r=right[i];if(l==r)continue
   val ln=l.toLongOrNull();val rn=r.toLongOrNull()
   return when{ln!=null&&rn!=null->ln.compareTo(rn);ln!=null->-1;rn!=null->1;else->l.compareTo(r)}
  }
  return left.size.compareTo(right.size)
 }
 fun newer(candidate:String,current:String):Boolean=version(candidate)!=null&&version(current)!=null&&compare(candidate,current)>0
 fun trusted(url:String):Boolean=runCatching{val u=java.net.URI(url);u.scheme=="https"&&u.host=="github.com"&&u.userInfo==null&&u.port in setOf(-1,443)&&u.rawPath.startsWith("/"+REPOSITORY+"/releases/download/")&&!u.rawPath.contains("..")}.getOrDefault(false)
 fun eligiblePackage(expected:String,actual:String,currentCode:Long,newCode:Long,currentSigners:Set<String>,newSigners:Set<String>)=
  expected==actual&&newCode>currentCode&&currentSigners.isNotEmpty()&&currentSigners==newSigners
}
data class UpdateRelease(val tag:String,val title:String,val notes:String,val url:String,val bytes:Long,val sha:String,val prerelease:Boolean){
 fun json()=JSONObject().put("tag",tag).put("title",title).put("notes",notes).put("url",url).put("bytes",bytes).put("sha",sha).put("prerelease",prerelease)
 companion object{fun from(o:JSONObject)=UpdateRelease(o.getString("tag"),o.getString("title"),o.getString("notes"),o.getString("url"),o.getLong("bytes"),o.getString("sha"),o.optBoolean("prerelease"))}
}
class UpdateManager(private val g:AppGraph){
 private val app=g.app
 private val pm=app.packageManager
 private val dm=app.getSystemService(DownloadManager::class.java)
 private val gate=Mutex()
 val file:File get()=File(app.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS),"updates/PocketAI-update.apk")
 val current get()=pm.getPackageInfo(app.packageName,0)
 fun candidate()=runCatching{UpdateRelease.from(JSONObject(g.settings.string("updateCandidate")))}.getOrNull()
 private fun status(text:String){g.settings.set("updateStatus",text)}
 private fun storeDistributed()=pm.getInstallSourceInfo(app.packageName).installingPackageName=="com.android.vending"
 suspend fun check(automatic:Boolean=false):UpdateRelease?=gate.withLock{
  if(storeDistributed()){status("Use Google Play to update this installation.");return@withLock null}
  kotlin.check(!g.settings.offline){"Update checks require internet. Offline-only mode is enabled."}
  val a=JSONArray(RemoteJson.get("https://api.github.com/repos/"+UpdatePolicy.REPOSITORY+"/releases?per_page=20",setOf("api.github.com")){g.settings.offline})
  val releases=mutableListOf<UpdateRelease>()
  for(i in 0 until a.length()){
   val o=a.getJSONObject(i)
   if(o.optBoolean("draft")||o.optBoolean("prerelease")&&!g.settings.bool("updateBeta",true))continue
   val tag=o.getString("tag_name")
   if(!UpdatePolicy.newer(tag,current.versionName.orEmpty()))continue
   val assets=o.getJSONArray("assets")
   for(j in 0 until assets.length()){
    val asset=assets.getJSONObject(j);val digest=asset.optString("digest").removePrefix("sha256:");val url=asset.optString("browser_download_url");val bytes=asset.optLong("size")
    if(!asset.optString("name").endsWith(".apk")||!Regex("[a-f0-9]{64}").matches(digest)||!UpdatePolicy.trusted(url)||bytes !in 1_000_000..300_000_000L)continue
    releases+=UpdateRelease(tag,o.optString("name",tag),o.optString("body").take(20000),url,bytes,digest,o.optBoolean("prerelease"));break
   }
  }
  val latest=releases.maxWithOrNull(Comparator{a,b->UpdatePolicy.compare(a.tag,b.tag)})
  g.settings.set("updateChecked",System.currentTimeMillis().toString())
  if(latest==null){candidate()?.let{if(!UpdatePolicy.newer(it.tag,current.versionName.orEmpty())){cancelDownload();g.settings.remove("updateCandidate")}};status("No newer eligible release was found.");return@withLock null}
  val previous=candidate()
  if(previous?.sha!=latest.sha){cancelDownload();g.settings.set("updateCandidate",latest.json().toString())}
  status("Update available: "+latest.title)
  if(automatic&&g.settings.bool("updateNotify",true))notifyUpdate(latest.title)
  if(automatic&&g.settings.bool("updateAutoDownload",false))enqueue(latest,true)
  latest
 }
 fun enqueue(release:UpdateRelease,wifiOnly:Boolean=g.settings.bool("wifiOnly",true)){
  kotlin.check(!g.settings.offline){"Offline-only mode is enabled."}
  kotlin.check(UpdatePolicy.trusted(release.url)&&Regex("[a-f0-9]{64}").matches(release.sha))
  val oldId=g.settings.string("updateDownloadId").toLongOrNull()
  if(oldId!=null){dm.query(DownloadManager.Query().setFilterById(oldId)).use{c->if(c.moveToFirst()&&c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)) in setOf(DownloadManager.STATUS_RUNNING,DownloadManager.STATUS_PENDING,DownloadManager.STATUS_PAUSED))return}}
  kotlin.check(app.noBackupFilesDir.usableSpace>release.bytes*2+64_000_000){"Not enough storage for the APK and installation. Free space first."}
  cancelDownload();file.parentFile?.mkdirs();g.settings.set("updateCandidate",release.json().toString())
  val request=DownloadManager.Request(Uri.parse(release.url)).setTitle("Pocket AI update").setDescription(release.title)
   .setMimeType("application/vnd.android.package-archive").setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
   .setDestinationInExternalFilesDir(app,Environment.DIRECTORY_DOWNLOADS,"updates/PocketAI-update.apk")
   .setAllowedNetworkTypes(if(wifiOnly)DownloadManager.Request.NETWORK_WIFI else DownloadManager.Request.NETWORK_WIFI or DownloadManager.Request.NETWORK_MOBILE)
  g.settings.set("updateDownloadId",dm.enqueue(request).toString());status("Downloading update through Android. Installation always requires your confirmation.")
 }
 fun cancelDownload(){g.settings.string("updateDownloadId").toLongOrNull()?.let{dm.remove(it)};g.settings.remove("updateDownloadId");g.settings.remove("updateVerified");if(file.exists())file.delete()}
 fun progress():String{
  val id=g.settings.string("updateDownloadId").toLongOrNull()?:return g.settings.string("updateStatus","No update check yet.")
  return dm.query(DownloadManager.Query().setFilterById(id)).use{c->
   if(!c.moveToFirst())return@use "Download no longer exists. Download again."
   val state=c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS));val done=c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR));val total=c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
   when(state){DownloadManager.STATUS_SUCCESSFUL->"Downloaded. Verify before installing.";DownloadManager.STATUS_FAILED->"Update download failed. Retry the download.";DownloadManager.STATUS_PAUSED->"Paused by Android; waiting for an allowed connection.";else->"Downloading: "+com.example.llama.core.bytesLabel(done)+" / "+com.example.llama.core.bytesLabel(total.coerceAtLeast(0))}
  }
 }
 suspend fun verify():File=withContext(Dispatchers.IO){
  val release=candidate()?:error("Check for an update first.")
  kotlin.check(file.isFile&&file.length()==release.bytes){"Update download is incomplete."}
  kotlin.check(g.hashFile(file)==release.sha){"Update checksum mismatch. Delete the update and download it again."}
  val archive=pm.getPackageArchiveInfo(file.path,PackageManager.GET_SIGNING_CERTIFICATES)?:error("The update is not a valid Android package.")
  val installed=pm.getPackageInfo(app.packageName,PackageManager.GET_SIGNING_CERTIFICATES)
  fun fingerprints(info:android.content.pm.PackageInfo)=info.signingInfo?.apkContentsSigners?.map{MessageDigest.getInstance("SHA-256").digest(it.toByteArray()).joinToString(""){b->"%02x".format(b.toInt() and 255)}}?.toSet().orEmpty()
  kotlin.check(UpdatePolicy.eligiblePackage(app.packageName,archive.packageName,installed.longVersionCode,archive.longVersionCode,fingerprints(installed),fingerprints(archive))){"Update rejected: package, signing certificate, or version does not match a safe upgrade."}
  g.settings.set("updateVerified",release.sha);status("Integrity and signing certificate verified. Ready for Android installation.");file
 }
 suspend fun install(activity:Activity){
  if(storeDistributed()){activity.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse("market://details?id="+app.packageName)));return}
  val apk=verify()
  if(!pm.canRequestPackageInstalls()){
   activity.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,Uri.parse("package:"+app.packageName)));return
  }
  val uri=FileProvider.getUriForFile(app,app.packageName+".files",apk)
  activity.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri,"application/vnd.android.package-archive").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
 }
 private fun notifyUpdate(title:String){
  if(app.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)return
  val manager=app.getSystemService(NotificationManager::class.java)
  manager.createNotificationChannel(NotificationChannel("updates","App updates",NotificationManager.IMPORTANCE_DEFAULT))
  val pending=PendingIntent.getActivity(app,91,Intent(app,MainActivity::class.java).putExtra("screen","Updates"),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
  manager.notify(91,NotificationCompat.Builder(app,"updates").setSmallIcon(R.drawable.pocket_icon).setContentTitle(title).setContentText("A Pocket AI update is available. Review before installing.").setContentIntent(pending).setAutoCancel(true).build())
 }
 companion object{
  fun schedule(g:AppGraph){
   val scheduler=g.app.getSystemService(JobScheduler::class.java)
   if(g.settings.offline||!g.settings.bool("updateAuto",false)&&!g.settings.bool("modelAuto",false)){scheduler.cancel(210);return}
   scheduler.schedule(JobInfo.Builder(210,ComponentName(g.app,UpdateJob::class.java)).setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY).setPeriodic(24*60*60*1000L).setRequiresBatteryNotLow(true).build())
  }
 }
}
class UpdateJob:JobService(){
 private var job:Job?=null
 override fun onStartJob(params:JobParameters):Boolean{
  val g=(application as PocketApplication).graph
  job=g.scope.launch{try{g.ready.await();if(g.settings.bool("updateAuto")&&!g.settings.offline)g.updates.check(true);if(g.settings.bool("modelAuto")&&!g.settings.offline)com.example.llama.models.ModelHub(g).checkUpdates()}catch(e:Exception){g.settings.set("updateStatus",e.message?:"Automatic check could not finish.")}finally{jobFinished(params,false)}};return true
 }
 override fun onStopJob(params:JobParameters):Boolean{job?.cancel();return true}
}
