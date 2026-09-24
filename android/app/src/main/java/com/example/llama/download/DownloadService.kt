package com.example.llama.download

import android.app.*
import android.content.*
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.*
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.example.llama.MainActivity
import com.example.llama.R
import com.example.llama.core.*
import com.example.llama.data.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.withLock
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.concurrent.atomic.AtomicReference

class DownloadService:Service() {
 private val scope=CoroutineScope(SupervisorJob()+Dispatchers.IO)
 private val connection=AtomicReference<HttpURLConnection?>()
 private lateinit var graph:AppGraph
 private var worker:Job?=null
 @Volatile private var active:String?=null
 private val stateLock=Any()
 private var wake:PowerManager.WakeLock?=null
 override fun onCreate(){super.onCreate();graph=(application as PocketApplication).graph
  getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel("models","Model downloads",NotificationManager.IMPORTANCE_LOW))
 }
 override fun onBind(intent:Intent?)=null
 override fun onStartCommand(intent:Intent?,flags:Int,startId:Int):Int {
  ServiceCompat.startForeground(this,72,notification("Preparing model downloads"),ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
  scope.launch {
   try{
    graph.ready.await()
    synchronized(stateLock){
    val id=intent?.getStringExtra("id")
    when(intent?.action){
     "pause","cancel"->if(id!=null){
      val old=graph.record(id)
      if(old!=null){graph.putDownload(old.copy(state=if(intent.action=="cancel")"cancelled" else "paused",speed=0,error=""));graph.refreshDownloads()}
      if(active==id)connection.get()?.disconnect()
      if(intent.action=="cancel" && active!=id)graph.partial(graph.model(id)).delete()
     }
     "pauseAll"->{graph.downloads.value.filter{it.state in setOf("queued","downloading","verifying")}.forEach{graph.putDownload(it.copy(state="paused",speed=0,error="Paused by offline-only mode or Android."))};connection.get()?.disconnect();graph.refreshDownloads()}
     "enqueue"->if(id!=null){
      check(!graph.settings.offline){"Offline-only mode is enabled. Disable it in Settings before downloading."}
      val m=graph.model(id);val h=Hardware.detect(this@DownloadService)
      check(ResourcePolicy.compatibility(m,h).allowed){ResourcePolicy.compatibility(m,h).reason}
      check(m.url.startsWith("https://")){"This imported model has no download source."}
      if(graph.record(id)?.state !in setOf("downloading","verifying","queued")){
       graph.putDownload(DownloadRecord(id,total=m.bytes,downloaded=graph.partial(m).length()))
       graph.refreshDownloads()
      }
     }
    }
    drain()
    }
   }catch(e:Exception){graph.error.value=e.message?:"Download could not start.";stopIfIdle()}
  }
  return START_NOT_STICKY
 }
 @Synchronized private fun drain(){
  if(worker?.isActive==true)return
  worker=scope.launch {
   graph.downloadLock.withLock {
    wake=(getSystemService(POWER_SERVICE) as PowerManager).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"PocketAI:download").apply{acquire(30*60*1000L)}
    try{
     while(isActive) {
      val next=graph.downloads.value.firstOrNull{it.state=="queued"}?:break
      active=next.id
      try{
       check(!graph.settings.offline){"Offline-only mode is enabled."}
       val network=getSystemService(ConnectivityManager::class.java)
       if(graph.settings.bool("wifiOnly",true)) check(network.getNetworkCapabilities(network.activeNetwork)?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)==true){"Wi-Fi is required. Connect to Wi-Fi or change Download settings."}
       transfer(graph.model(next.id))
      }catch(e:CancellationException){throw e}
       catch(e:Exception){
        var retryDelay=0L
        synchronized(stateLock){
        val current=graph.record(next.id)?:next
        if(current.state !in setOf("paused","cancelled")){
         val retry=current.attempt+1
         graph.putDownload(current.copy(state=if(retry<3&&!graph.settings.offline)"queued" else "failed",speed=0,error=e.message?:"Download interrupted. Resume to continue.",attempt=retry));graph.refreshDownloads()
         if(retry<3)retryDelay=3000L*retry
        }
        }
        if(retryDelay>0)delay(retryDelay)
       }finally{
        connection.getAndSet(null)?.disconnect()
        if(graph.record(next.id)?.state=="cancelled")graph.partial(graph.model(next.id)).delete()
        active=null
       }
     }
    }finally{wake?.let{if(it.isHeld)it.release()};wake=null}
   }
   synchronized(stateLock){worker=null;if(graph.downloads.value.any{it.state=="queued"})drain() else {stopForeground(STOP_FOREGROUND_REMOVE);stopSelf()}}
  }
 }
 private suspend fun transfer(m:ModelSpec)=graph.fileLock.withLock {
  val final=graph.file(m);val part=graph.partial(m)
  if(graph.verified(m)){graph.putDownload(DownloadRecord(m.id,"complete",m.bytes,m.bytes));graph.refreshDownloads();return@withLock}
  if(part.length()>m.bytes)check(part.delete()){"Cannot reset a damaged partial file."}
  check(graph.modelsDir.usableSpace>m.bytes-part.length()+256_000_000L){"Not enough storage. Free space before resuming."}
  var d=(graph.record(m.id)?:DownloadRecord(m.id)).copy(state="downloading",total=m.bytes,error="")
  synchronized(stateLock){check(graph.record(m.id)?.state=="queued"){"Download paused."};graph.putDownload(d);graph.refreshDownloads()}
  fun checkActive(){check(!graph.settings.offline){"Offline-only mode is enabled."};check(graph.record(m.id)?.state in setOf("downloading","verifying")){"Download paused."}}
  if(part.length()<m.bytes){
   val offset=part.length()
   var url=URL(m.url);var conn:HttpURLConnection?=null
   for(hop in 0..5){
    check(url.protocol=="https"){"An insecure download redirect was blocked."}
    conn=url.openConnection() as HttpURLConnection
    connection.set(conn);conn.connectTimeout=20_000;conn.readTimeout=20_000;conn.instanceFollowRedirects=false
    conn.setRequestProperty("Accept-Encoding","identity")
    if(offset>0)conn.setRequestProperty("Range","bytes=$offset-")
    val code=conn.responseCode
    if(code in setOf(301,302,303,307,308)){
     val next=conn.getHeaderField("Location")?:error("The model host sent an invalid redirect.")
     val nextUrl=URL(url,next);conn.disconnect();url=nextUrl
     if(hop==5)error("Too many model-host redirects.")
    }else break
   }
   val c=checkNotNull(conn)
   try{
    val append=RangePolicy.append(c.responseCode,offset,c.getHeaderField("Content-Range"),m.bytes)
    if(!append)check(graph.modelsDir.usableSpace+part.length()>m.bytes+256_000_000L){"Not enough space to restart this transfer."}
    var total=if(append)offset else 0L
    var checkpoint=SystemClock.elapsedRealtime();var lastBytes=total
    c.inputStream.buffered().use{input->FileOutputStream(part,append).use{output->
     val buffer=ByteArray(256*1024)
     while(true){
      currentCoroutineContext().ensureActive();checkActive()
      val n=input.read(buffer);if(n<0)break
      require(total+n<=m.bytes){"The model host returned an unexpected file size."}
      output.write(buffer,0,n);total+=n
      val now=SystemClock.elapsedRealtime()
      if(now-checkpoint>=750){
       d=d.copy(downloaded=total,speed=(total-lastBytes)*1000/(now-checkpoint))
       synchronized(stateLock){checkActive();graph.putDownload(d);graph.refreshDownloads();notifyProgress(m,d)}
       checkpoint=now;lastBytes=total
      }
     };output.fd.sync()
    }}
   }finally{connection.getAndSet(null)?.disconnect()}
  }
  checkActive()
  check(part.length()==m.bytes){"The connection ended early. Resume to continue."}
  synchronized(stateLock){checkActive();graph.putDownload(d.copy(state="verifying",downloaded=m.bytes,speed=0));graph.refreshDownloads();notifyProgress(m,d.copy(state="verifying"))}
  val hash=graph.hashFile(part)
  checkActive()
  if(hash!=m.sha256){part.delete();error("File verification failed. The damaged download was removed; retry to fetch it again.")}
  synchronized(stateLock){checkActive()
  try{Files.move(part.toPath(),final.toPath(),StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING)}
  catch(e:java.nio.file.AtomicMoveNotSupportedException){Files.move(part.toPath(),final.toPath(),StandardCopyOption.REPLACE_EXISTING)}
  graph.markVerified(m)
  graph.putDownload(DownloadRecord(m.id,"complete",m.bytes,m.bytes));graph.refreshDownloads()
  }
 }
 private fun notification(message:String,progress:Int=-1):Notification{
  val intent=PendingIntent.getActivity(this,0,Intent(this,MainActivity::class.java).putExtra("screen","Downloads"),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
  val b=NotificationCompat.Builder(this,"models").setSmallIcon(R.drawable.pocket_icon).setContentTitle("Pocket AI downloads").setContentText(message).setContentIntent(intent).setOnlyAlertOnce(true).setOngoing(true)
  b.setProgress(100,progress.coerceAtLeast(0),progress<0)
  active?.let{id->val pause=PendingIntent.getService(this,1,Intent(this,DownloadService::class.java).setAction("pause").putExtra("id",id),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE);b.addAction(0,"Pause",pause)}
  return b.build()
 }
 private fun notifyProgress(m:ModelSpec,d:DownloadRecord){getSystemService(NotificationManager::class.java).notify(72,notification(m.name+" · "+d.state+" · "+d.percent+"%",if(d.state=="verifying")-1 else d.percent))}
 private fun stopIfIdle(){if(worker?.isActive!=true){stopForeground(STOP_FOREGROUND_REMOVE);stopSelf()}}
 override fun onTimeout(startId:Int,fgsType:Int){
  scope.launch{synchronized(stateLock){graph.downloads.value.filter{it.state in setOf("queued","downloading","verifying")}.forEach{graph.putDownload(it.copy(state="paused",speed=0,error="Android paused this long transfer. Resume from Downloads."))};graph.refreshDownloads()};connection.get()?.disconnect();worker?.cancel();stopForeground(STOP_FOREGROUND_REMOVE);stopSelf()}
 }
 override fun onDestroy(){connection.get()?.disconnect();scope.cancel();wake?.let{if(it.isHeld)it.release()};super.onDestroy()}
 companion object {
  fun command(c:Context,action:String,id:String?=null){
   androidx.core.content.ContextCompat.startForegroundService(c,Intent(c,DownloadService::class.java).setAction(action).putExtra("id",id))
  }
 }
}
