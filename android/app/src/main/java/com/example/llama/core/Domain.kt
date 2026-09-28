package com.example.llama.core

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.PowerManager
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.RandomAccessFile
import java.util.UUID

fun newId() = UUID.randomUUID().toString()
fun bytesLabel(n: Long): String = if (n >= 1_000_000_000) "%.2f GB".format(n / 1e9) else "%.0f MB".format(n / 1e6)
data class ModelSpec(
 val id: String, val name: String, val file: String, val bytes: Long, val sha256: String,
 val repo: String = "", val revision: String = "", val kind: String = "text",
 val architecture: String = "qwen3", val quantization: String = "Q8_0",
 val license: String = "Apache-2.0", val creator: String = "Qwen",
 val purposes: String = "Chat, Writing, Translation, Multilingual",
 val memory: Long = bytes + 900_000_000L, val recommended: Long = 4_000_000_000L,
 val experimental: Boolean = false, val imported: Boolean = false, val remoteFile:String=""
) {
 val url get() = if (repo.isBlank()) "" else "https://huggingface.co/$repo/resolve/$revision/${remoteFile.ifBlank{file}}"
 val source get() = if (imported) "Imported from your device" else "https://huggingface.co/$repo"
 fun json() = JSONObject().put("id",id).put("name",name).put("file",file).put("bytes",bytes).put("sha256",sha256)
  .put("repo",repo).put("revision",revision).put("kind",kind).put("architecture",architecture).put("quantization",quantization)
  .put("license",license).put("creator",creator).put("purposes",purposes).put("memory",memory).put("recommended",recommended).put("experimental",experimental).put("imported",imported).put("remoteFile",remoteFile)
 companion object { fun from(o: JSONObject) = ModelSpec(o.getString("id"),o.getString("name"),o.getString("file"),o.getLong("bytes"),o.getString("sha256"),o.optString("repo"),o.optString("revision"),o.optString("kind","text"),o.optString("architecture","qwen3"),o.optString("quantization","Unknown"),o.optString("license","Check source"),o.optString("creator"),o.optString("purposes"),o.optLong("memory"),o.optLong("recommended"),o.optBoolean("experimental"),o.optBoolean("imported"),o.optString("remoteFile")) }
}
data class ChatMessage(val id: String = newId(), val role: String, val text: String, val time: Long = System.currentTimeMillis(), val stats: String = "", val state: String = "complete") {
 fun json()=JSONObject().put("id",id).put("role",role).put("text",text).put("time",time).put("stats",stats).put("state",state)
 companion object { fun from(o:JSONObject)=ChatMessage(o.optString("id",newId()),o.getString("role"),o.getString("text"),o.optLong("time",System.currentTimeMillis()),o.optString("stats"),o.optString("state","complete")) }
}
data class Conversation(val id:String=newId(), val title:String="New conversation", val pinned:Boolean=false, val updated:Long=System.currentTimeMillis(), val modelId:String="qwen-small", val messages:List<ChatMessage> = emptyList(), val archived:Boolean=false,val summary:Boolean=false,val totalMessages:Int=messages.size) {
 fun json()=JSONObject().put("id",id).put("title",title).put("pinned",pinned).put("updated",updated).put("modelId",modelId).put("messages",JSONArray(messages.map{it.json()})).put("archived",archived).put("summary",summary).put("totalMessages",if(summary)totalMessages else messages.size)
 companion object { fun from(o:JSONObject):Conversation {val a=o.optJSONArray("messages")?:JSONArray();return Conversation(o.getString("id"),o.getString("title"),o.optBoolean("pinned"),o.optLong("updated"),o.optString("modelId","qwen-small"),(0 until a.length()).map{ChatMessage.from(a.getJSONObject(it))},o.optBoolean("archived"),o.optBoolean("summary"),o.optInt("totalMessages",a.length()))} }
}
data class DownloadRecord(val id:String,val state:String="queued",val downloaded:Long=0,val total:Long=0,val speed:Long=0,val error:String="",val attempt:Int=0) {
 val percent get()=if(total>0)(downloaded*100/total).coerceIn(0,100).toInt() else 0
 val remainingSeconds get()=if(speed>0)(total-downloaded)/speed else -1
 fun json()=JSONObject().put("id",id).put("state",state).put("downloaded",downloaded).put("total",total).put("speed",speed).put("error",error).put("attempt",attempt)
 companion object { fun from(o:JSONObject)=DownloadRecord(o.getString("id"),o.getString("state"),o.optLong("downloaded"),o.optLong("total"),o.optLong("speed"),o.optString("error"),o.optInt("attempt")) }
}
data class GenerationOptions(val temperature:Float=.7f,val topP:Float=.9f,val topK:Int=40,val maximum:Int=512,val context:Int=4096,val repetition:Float=1.1f,val seed:Long=-1,val system:String="You are a helpful, honest offline assistant. Be clear and concise.",val stops:String="") {
 fun validate():GenerationOptions {require(temperature in 0f..2f && topP in .01f..1f && topK in 1..200 && maximum in 16..2048 && context in 1024..8192 && maximum<context-128 && repetition in 1f..2f && system.length<=8000 && stops.length<=1000){"Check generation settings: output must fit within the context."};return this}
 fun json()=JSONObject().put("temperature",temperature).put("topP",topP).put("topK",topK).put("maximum",maximum).put("context",context).put("repetition",repetition).put("seed",seed).put("system",system).put("stops",stops)
 companion object {fun from(o:JSONObject)=GenerationOptions(o.optDouble("temperature",.7).toFloat(),o.optDouble("topP",.9).toFloat(),o.optInt("topK",40),o.optInt("maximum",512),o.optInt("context",4096),o.optDouble("repetition",1.1).toFloat(),o.optLong("seed",-1),o.optString("system","You are a helpful offline assistant."),o.optString("stops"))}
}
data class Preset(val id:String=newId(),val name:String,val options:GenerationOptions,val icon:String="✦",val description:String="",val modelId:String="") {
 fun json()=JSONObject().put("id",id).put("name",name).put("options",options.json()).put("icon",icon).put("description",description).put("modelId",modelId)
 companion object{fun from(o:JSONObject)=Preset(o.getString("id"),o.getString("name"),GenerationOptions.from(o.getJSONObject("options")),o.optString("icon","✦"),o.optString("description"),o.optString("modelId"))}
}
data class MediaItem(val id:String=newId(),val file:String,val prompt:String,val negative:String,val model:String,val seed:Long,val width:Int,val height:Int,val steps:Int,val cfg:Float,val created:Long=System.currentTimeMillis(),val favorite:Boolean=false) {
 fun json()=JSONObject().put("id",id).put("file",file).put("prompt",prompt).put("negative",negative).put("model",model).put("seed",seed).put("width",width).put("height",height).put("steps",steps).put("cfg",cfg).put("created",created).put("favorite",favorite)
 companion object{fun from(o:JSONObject)=MediaItem(o.getString("id"),o.getString("file"),o.getString("prompt"),o.optString("negative"),o.getString("model"),o.getLong("seed"),o.getInt("width"),o.getInt("height"),o.getInt("steps"),o.getDouble("cfg").toFloat(),o.getLong("created"),o.optBoolean("favorite"))}
}
data class Hardware(val totalRam:Long,val availableRam:Long,val storage:Long,val abi:String,val cores:Int,val thermal:Int,val device:String) {
 companion object {fun detect(c:Context):Hardware{val m=ActivityManager.MemoryInfo();(c.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).getMemoryInfo(m);return Hardware(m.totalMem,m.availMem,c.noBackupFilesDir.usableSpace,Build.SUPPORTED_ABIS.joinToString(),Runtime.getRuntime().availableProcessors(),(c.getSystemService(Context.POWER_SERVICE) as PowerManager).currentThermalStatus,Build.MANUFACTURER+" "+Build.MODEL)}}
}
data class Compatibility(val allowed:Boolean,val label:String,val reason:String)
object ResourcePolicy {
 fun required(model:ModelSpec,context:Int)=model.memory+if(model.kind=="text") (context-2048).coerceAtLeast(0)*160_000L else 0
 fun compatibility(m:ModelSpec,h:Hardware):Compatibility = when {
  !h.abi.contains("arm64-v8a") && !h.abi.contains("x86_64")->Compatibility(false,"Unsupported","A 64-bit ARM or x86 Android device is required.")
  m.kind=="text" && m.architecture !in GgufInspector.supported->Compatibility(false,"Unsupported","This text architecture is not enabled in Pocket AI.")
  m.kind=="image" && m.architecture!="sd1"->Compatibility(false,"Unsupported","Only the curated SD 1 image backend is enabled.")
  m.kind !in setOf("text","image","video")->Compatibility(false,"Unsupported","No runtime is enabled for this model type.")
  m.kind=="video"->Compatibility(false,"Unsupported","This model is not recommended for this device. No validated mobile video backend is enabled.")
  m.kind=="image" && h.totalRam<10_000_000_000L->Compatibility(false,"Too large","Experimental image generation requires at least 10 GB physical RAM.")
  m.memory+1_500_000_000L>h.totalRam->Compatibility(false,"Too large","This model leaves too little memory for Android.")
  m.experimental||m.imported->Compatibility(true,"Experimental","Runtime-compatible format; not validated on this phone. CPU generation may be slow.")
  else->Compatibility(true,"Compatible (estimated)","Supported architecture and format. Actual speed and peak RAM require device testing.")
 }
 fun threads(profile:String,cores:Int)=when(profile){"Battery Saver"->2;"Performance"->6;else->4}.coerceAtMost(cores.coerceAtLeast(1))
}
object RangePolicy {
 fun append(code:Int,offset:Long,contentRange:String?,total:Long):Boolean {
  require(code==200||code==206){"The server returned HTTP $code. Try again later."}
  if(code==206) {val r=Regex("bytes (\\d+)-(\\d+)/(\\d+)").matchEntire(contentRange.orEmpty())?:error("Invalid resume response.")
   require(r.groupValues[1].toLong()==offset && r.groupValues[3].toLong()==total && r.groupValues[2].toLong() in offset until total){"The server returned a different file range. Resume was stopped safely."}}
  return code==206 && offset>0
 }
}
object GgufInspector {
 fun architecture(file:File):String = RandomAccessFile(file,"r").use { f ->
  fun uint()=Integer.reverseBytes(f.readInt()).toLong() and 0xffffffffL
  fun ulong()=java.lang.Long.reverseBytes(f.readLong()).also{require(it>=0)}
  fun string():String {val n=ulong();require(n<=1_048_576 && n<=f.length()-f.filePointer){"Invalid GGUF string."};return ByteArray(n.toInt()).also{f.readFully(it)}.toString(Charsets.UTF_8)}
  require(f.readInt()==0x47475546){"This file is not GGUF."};require(uint() in 2L..3L){"Unsupported GGUF version."}
  require(ulong() in 1..100_000L){"Invalid GGUF tensor count."};val count=ulong();require(count<=100_000)
  fun skip(type:Long,depth:Int=0) {
   require(depth<3 && f.filePointer<64_000_000){"GGUF metadata exceeds the safe parsing limit."}
   val size=when(type){0L,1L,7L->1;2L,3L->2;4L,5L,6L->4;10L,11L,12L->8;else->0}
   if(size>0){require(f.filePointer+size<=f.length());f.seek(f.filePointer+size)}
   else if(type==8L){string()}
   else if(type==9L){val element=uint();val n=ulong();require(n<=1_000_000);repeat(n.toInt()){skip(element,depth+1)}}
   else error("Unknown GGUF metadata type.")
  }
  repeat(count.toInt()){val key=string();val type=uint();if(key=="general.architecture"){require(type==8L);return@use string()};skip(type)}
  error("This GGUF has no supported text architecture metadata.")
 }
 val supported=setOf("qwen3","qwen2","llama","gemma","gemma2","gemma3","phi3")
}
