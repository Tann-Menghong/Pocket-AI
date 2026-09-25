package com.example.llama.models
import com.example.llama.core.*
import com.example.llama.data.AppGraph
import kotlinx.coroutines.*
import org.json.*
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

object RemoteJson {
 suspend fun get(url:String,allowedHosts:Set<String>,offline:()->Boolean):String=withContext(Dispatchers.IO){
  check(!offline()){"Offline-only mode is enabled. Installed models remain available."}
  val target=URL(url);require(target.protocol=="https"&&target.host in allowedHosts&&target.userInfo==null)
  val c=target.openConnection() as HttpURLConnection
  c.connectTimeout=15000;c.readTimeout=20000;c.instanceFollowRedirects=false
  c.setRequestProperty("User-Agent","PocketAI/2.1")
  c.setRequestProperty("Accept","application/json")
  try{
   check(c.responseCode==200){when(c.responseCode){403,429->"Repository rate limit reached. Try later.";404->"Repository or release was not found.";else->"Repository unavailable (HTTP "+c.responseCode+")."}}
   c.inputStream.use{input->val out=java.io.ByteArrayOutputStream();val b=ByteArray(16384);while(true){currentCoroutineContext().ensureActive();check(!offline()){"Offline-only mode enabled."};val n=input.read(b);if(n<0)break;check(out.size()+n<=4_000_000){"Repository metadata exceeds the safe size limit."};out.write(b,0,n)};out.toString("UTF-8")}
  }finally{c.disconnect()}
 }
}
class ModelHub(private val g:AppGraph){
 suspend fun search(query:String):List<String>{
  require(query.trim().length in 2..100)
  val a=JSONArray(RemoteJson.get("https://huggingface.co/api/models?search="+URLEncoder.encode(query,"UTF-8")+"&filter=gguf&limit=20",setOf("huggingface.co")){g.settings.offline})
  return (0 until a.length()).map{a.getJSONObject(it).getString("id")}
 }
 fun repository(value:String):String{
  val clean=value.trim().removePrefix("https://huggingface.co/").split('/').take(2).joinToString("/")
  require(Regex("[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+").matches(clean)){"Use a Hugging Face repository URL or owner/repository."}
  return clean
 }
 suspend fun inspect(value:String):List<ModelSpec>{
  val repo=repository(value)
  val o=JSONObject(RemoteJson.get("https://huggingface.co/api/models/"+repo+"?blobs=true",setOf("huggingface.co")){g.settings.offline})
  val arch=o.optJSONObject("gguf")?.optString("architecture").orEmpty()
  require(arch in GgufInspector.supported){"This repository does not report a supported text architecture. Image models require the curated catalog; arbitrary checkpoints are not enabled."}
  val rev=o.getString("sha");require(Regex("[a-f0-9]{40}").matches(rev))
  val files=o.getJSONArray("siblings");val results=mutableListOf<ModelSpec>()
  for(i in 0 until files.length()){
   val f=files.getJSONObject(i);val name=f.getString("rfilename");val lfs=f.optJSONObject("lfs")?:continue
   if(!Regex("[A-Za-z0-9_.-]+\\.gguf",RegexOption.IGNORE_CASE).matches(name)||Regex("-\\d{5}-of-\\d{5}").containsMatchIn(name)||name.contains("mmproj",true))continue
   val sha=lfs.optString("sha256");val bytes=lfs.optLong("size")
   if(!Regex("[a-f0-9]{64}").matches(sha)||bytes !in 50_000_000..6_000_000_000L)continue
   val q=Regex("(Q[2-8]_[A-Z0-9_]+|F16|BF16)",RegexOption.IGNORE_CASE).find(name)?.value?:"Unknown"
   if(q=="Unknown")continue
   val id="hub-"+sha.take(16)
   results+=ModelSpec(id,repo.substringAfter("/")+" · "+q,id+"-"+name,bytes,sha,repo,rev,
    architecture=arch,quantization=q,license=o.optJSONObject("cardData")?.optString("license","Check source")?:"Check source",
    creator=repo.substringBefore("/"),purposes="Chat, Experimental",memory=bytes+1_600_000_000L,recommended=bytes+3_500_000_000L,experimental=true,remoteFile=name)
  }
  require(results.isNotEmpty()){"No supported single-file GGUF models under 6 GB with integrity metadata were found."}
  return results.sortedBy{it.bytes}
 }
 suspend fun add(m:ModelSpec)=withContext(Dispatchers.IO){
  require(ResourcePolicy.compatibility(m,Hardware.detect(g.app)).allowed)
  if(g.models.value.any{it.sha256==m.sha256})return@withContext
  g.db.put("model",m.id,m.json());g.models.value=g.models.value+m
 }
 suspend fun checkUpdates():List<ModelSpec>{
  val newer=mutableListOf<ModelSpec>()
  for(m in g.models.value.filter{it.kind=="text"&&!it.imported&&it.repo.isNotBlank()}.distinctBy{it.repo}){
   val candidates=inspect(m.repo)
   for(old in g.models.value.filter{it.repo==m.repo}){
    candidates.firstOrNull{it.remoteFile==old.remoteFile.ifBlank{old.file}&&it.sha256!=old.sha256}?.let{newer+=it}
   }
  }
  withContext(Dispatchers.IO){g.db.put("meta","modelUpdates",JSONObject().put("checked",System.currentTimeMillis()).put("models",JSONArray(newer.map{it.json()})))}
  return newer
 }
}
object Recommendations{
 fun select(models:List<ModelSpec>,h:Hardware,context:Int):List<Pair<String,ModelSpec>>{
  val eligible=models.filter{it.kind=="text"&&ResourcePolicy.compatibility(it,h).allowed&&ResourcePolicy.required(it,context)<h.availableRam}.sortedBy{it.bytes}
  if(eligible.isEmpty())return emptyList()
  val results=mutableListOf("Fast" to eligible.first())
  eligible.filter{it.memory<h.totalRam/2}.let{if(it.isNotEmpty())results+="Balanced" to it[it.size/2]}
  results+="Quality" to eligible.last()
  eligible.firstOrNull{it.purposes.contains("Coding")}?.let{results+="Coding" to it}
  eligible.firstOrNull{it.purposes.contains("Multilingual")}?.let{results+="Multilingual" to it}
  return results
 }
}
