package com.example.llama

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import android.os.PowerManager
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.arm.aichat.*
import com.example.llama.core.*
import com.example.llama.data.*
import com.example.llama.download.DownloadService
import com.example.pocketdiffusion.ImageRuntime
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.concurrent.Executors

data class AppState(
 val ready:Boolean=false,val screen:String="Home",val chat:Conversation=Conversation(),val conversations:List<Conversation> = emptyList(),
 val media:List<MediaItem> = emptyList(),val presets:List<Preset> = emptyList(),val busy:Boolean=false,val status:String="Opening your library...",
 val progress:Int=-1,val error:String="",val loaded:String="",val revision:Int=0
)
class PlatformViewModel(app:Application):AndroidViewModel(app) {
 val graph=(app as PocketApplication).graph
 private val mutable=MutableStateFlow(AppState())
 val state=mutable.asStateFlow()
 private val runtime=graph.runtime
 private val imageDispatcher=Executors.newSingleThreadExecutor{r->Thread(r,"PocketImage")}.asCoroutineDispatcher()
 private var image:ImageRuntime?=null
 private var generation:Job?=null
 private var loading=false
 private var partialText:String?=null
 var draft=""
 var createPrompt=""
 var negativePrompt=""
 var createMode="Text"
 private var currentScreen="Home"
 init{
  viewModelScope.launch{
   try{
    graph.ready.await()
    withContext(Dispatchers.IO){
     val all=graph.conversations()
     val selected=all.firstOrNull{it.id==graph.settings.string("activeChat")}?:all.firstOrNull()
     val active=selected?.let{graph.fullChat(it.id)}?:Conversation(modelId=graph.settings.string("defaultModel","qwen-small"))
     mutable.update{it.copy(ready=true,chat=active,conversations=all,presets=presets(),media=graph.media(),status="Private by default. Ready when you are.")}
    }
   }catch(e:Exception){error("Your library could not be opened. Existing data was preserved. "+e.message)}
  }
  viewModelScope.launch{graph.error.collect{if(it.isNotEmpty())error(it)}}
  viewModelScope.launch{graph.memoryPressure.collect{if(it){if(state.value.busy)stop()else unload();graph.memoryPressure.value=false;error("Android reports memory pressure. Try a smaller model or context.")}}}
 }
 private fun presets()=graph.db.all("preset").map{Preset.from(it)}.sortedBy{it.name}
 fun error(message:String){mutable.update{it.copy(error=message)}}
 fun dismissError(){mutable.update{it.copy(error="")};graph.error.value=""}
 fun navigate(screen:String){currentScreen=screen;mutable.update{it.copy(screen=screen)}}
 fun refresh(){viewModelScope.launch(Dispatchers.IO){runCatching{mutable.update{it.copy(conversations=graph.conversations(),media=graph.media(),presets=presets(),revision=it.revision+1)}}.onFailure{error("Could not read local library: "+it.message)}}}
 private fun work(block:suspend()->Unit){viewModelScope.launch(Dispatchers.IO){try{graph.ready.await();block();refresh()}catch(e:Exception){error(e.message?:"The operation could not be completed.")}}}
 private suspend fun save(chat:Conversation){withContext(Dispatchers.IO){graph.db.put("chat",chat.id,chat.json())};graph.settings.set("activeChat",chat.id)}
 fun newChat(){if(state.value.busy)return;draft="";val c=Conversation(modelId=graph.settings.string("defaultModel","qwen-small"));mutable.update{it.copy(chat=c,screen="Chat",status="New conversation")};work{save(c)}}
 fun openChat(c:Conversation){if(state.value.busy){error("Stop generation before opening another conversation.");return};draft="";work{val full=graph.fullChat(c.id);mutable.update{it.copy(chat=full,screen="Chat")};graph.settings.set("activeChat",c.id)}}
 fun rename(c:Conversation,title:String){if(state.value.busy)return;work{val updated=graph.fullChat(c.id).copy(title=title.trim().take(100).ifBlank{"Untitled"});if(c.id==state.value.chat.id)mutable.update{it.copy(chat=updated)};save(updated)}}
 fun archive(c:Conversation){if(state.value.busy)return;work{val full=graph.fullChat(c.id);val updated=full.copy(archived=!full.archived);if(c.id==state.value.chat.id)mutable.update{it.copy(chat=updated)};graph.db.put("chat",c.id,updated.json())}}
 fun pin(c:Conversation){if(state.value.busy)return;work{val full=graph.fullChat(c.id);val updated=full.copy(pinned=!full.pinned);if(c.id==state.value.chat.id)mutable.update{it.copy(chat=updated)};graph.db.put("chat",c.id,updated.json())}}
 fun deleteChat(c:Conversation){if(state.value.busy)return;work{graph.db.remove("chat",c.id);if(state.value.chat.id==c.id)mutable.update{it.copy(chat=Conversation(modelId=graph.settings.string("defaultModel","qwen-small")))}}}
 fun clearChat(){if(state.value.busy)return;val c=state.value.chat.copy(messages=emptyList());mutable.update{it.copy(chat=c)};work{save(c)}}
 fun clearHistory(){if(state.value.busy)return;work{graph.db.transaction{graph.conversations().forEach{graph.db.remove("chat",it.id)}};mutable.update{it.copy(chat=Conversation())}}}
 fun selectModel(id:String){if(state.value.busy)return;val m=graph.model(id);if(m.kind!="text")return;val c=state.value.chat.copy(modelId=id);mutable.update{it.copy(chat=c)};work{save(c)}}
 private fun options():TextOptions {val o=graph.settings.options;val h=Hardware.detect(getApplication());return TextOptions(o.context,ResourcePolicy.threads(graph.settings.profile,h.cores),o.temperature,o.topP,o.topK,o.repetition,o.seed)}
 private fun guard(m:ModelSpec){
  val h=Hardware.detect(getApplication());val compatibility=ResourcePolicy.compatibility(m,h);check(compatibility.allowed){compatibility.reason}
  check(h.thermal<PowerManager.THERMAL_STATUS_SEVERE){"Your phone is too warm. Let it cool before generating."}
  check(ResourcePolicy.canLoadNow(m,h,graph.settings.options.context)){"Not enough available memory to load this model while leaving room for Android. Close other apps, reduce context length, or select a smaller model."}
  check(graph.installed(m)){"Download or import this model first, from Models."}
 }
 fun unload(){if(state.value.busy)return;work{graph.inferenceLock.withLock{runtime.unload()};mutable.update{it.copy(loaded="",status="Model unloaded. Memory released.")}}}
 fun loadModel(){if(state.value.busy)return;begin("Loading model..."){
  val m=graph.model(state.value.chat.modelId);loadText(m)
  mutable.update{it.copy(status="Ready · local inference")}
 }}
 private suspend fun loadText(m:ModelSpec){
  if(!runtime.isLoaded(graph.file(m).path,graph.settings.options.context)){runtime.unload();mutable.update{it.copy(loaded="")}}
  if(!runtime.isLoaded(graph.file(m).path,graph.settings.options.context))guard(m) else check(Hardware.detect(getApplication()).thermal<PowerManager.THERMAL_STATUS_SEVERE){"Let your phone cool before generating."}
  if(!graph.verified(m)){mutable.update{it.copy(status="Verifying installed model...")};graph.fileLock.withLock{graph.verify(m)}}
  loading=true
  val monitor=viewModelScope.launch{while(isActive){mutable.update{it.copy(progress=runtime.loadProgress(),status="Loading model · "+runtime.loadProgress()+"%")};delay(250)}}
  try{runtime.load(graph.file(m).path,options())}finally{monitor.cancel();loading=false;mutable.update{it.copy(progress=-1)}}
  mutable.update{it.copy(loaded=m.id)}
 }
 private fun begin(status:String,block:suspend()->Unit){
  if(state.value.busy)return
  partialText=null
  mutable.update{it.copy(busy=true,status=status,progress=-1,error="")}
  generation=viewModelScope.launch {
   try{graph.inferenceLock.withLock{block()}}
   catch(e:CancellationException){mutable.update{it.copy(status="Stopped. Your partial result was saved.")};throw e}
   catch(e:OutOfMemoryError){runtime.cancel();mutable.update{it.copy(error="Not enough memory. Close other apps or choose a smaller model.",loaded="")};withContext(NonCancellable){runtime.unload()}}
   catch(e:LinkageError){error("The native runtime could not start on this device. "+e.message)}
   catch(e:Exception){if(isActive)error(e.message?:"Generation failed. Try a smaller model.")}
   finally {
    withContext(NonCancellable){
     val c=state.value.chat
     if(c.messages.any{it.state=="generating"}){
      val revised=c.copy(messages=c.messages.map{if(it.state=="generating")it.copy(state="interrupted",text=partialText?:it.text)else it})
      mutable.update{it.copy(chat=revised)}
      runCatching{save(revised)}.onFailure{error("Could not save the partial response.")}
     }
    }
    mutable.update{it.copy(busy=false,progress=-1)};refresh()
   }
  }
 }
 fun send(text:String,replaceFrom:Int?=null){
  if(text.isBlank()||state.value.busy)return
  val clean=text.trim().take(16000)
  val original=state.value.chat
  val preceding=if(replaceFrom!=null)original.messages.take(replaceFrom)else original.messages
  val c=original.copy(title=if(original.messages.isEmpty())clean.take(60)else original.title,updated=System.currentTimeMillis(),messages=preceding+ChatMessage(role="user",text=clean)+ChatMessage(role="assistant",text="",state="generating"))
  mutable.update{it.copy(chat=c)};draft=""
  begin("Preparing your conversation..."){
   save(c)
   val m=graph.model(c.modelId);loadText(m)
   val o=graph.settings.options
   val history=c.messages.dropLast(1).takeLast(100).dropWhile{it.role!="user"}.map{RuntimeMessage(it.role,it.text)}
   val suffix=if(m.architecture=="qwen3")" /no_think" else ""
   val messages=listOf(RuntimeMessage("system",o.system+suffix))+history.dropLast(1)+history.last().copy(text=history.last().text+suffix)
   val out=StringBuilder();val started=SystemClock.elapsedRealtime();var lastSave=started;var lastUi=started;var stoppedBySequence=false
   mutable.update{it.copy(status="Generating on your phone...")}
   runtime.generate(messages,o.maximum,options()).collect{piece->
    if(stoppedBySequence)return@collect
    out.append(piece)
    val cutoff=o.stops.lines().filter{it.isNotEmpty()}.map{out.indexOf(it)}.filter{it>=0}.minOrNull()
    if(cutoff!=null){out.setLength(cutoff);stoppedBySequence=true;runtime.cancel()}
    partialText=out.toString()
    val now=SystemClock.elapsedRealtime()
    if(graph.settings.bool("streaming",true)&&now-lastUi>60||stoppedBySequence){updateReply(out.toString(),"generating");lastUi=now}
    if(now-lastSave>1000){val snapshot=state.value.chat;save(snapshot.copy(messages=snapshot.messages.map{if(it.state=="generating")it.copy(text=out.toString())else it}));lastSave=now}
   }
   val elapsed=(SystemClock.elapsedRealtime()-started).coerceAtLeast(1)
   val stats="${runtime.generatedTokens} tokens · %.1f tok/s · %.1f s".format(runtime.generatedTokens*1000.0/elapsed,elapsed/1000.0)
   updateReply(out.toString(),"complete",stats)
   save(state.value.chat)
   mutable.update{it.copy(status="Ready · "+stats)}
  }
 }
 private fun updateReply(text:String,status:String,stats:String=""){mutable.update{it.copy(chat=it.chat.copy(updated=System.currentTimeMillis(),messages=it.chat.messages.dropLast(1)+it.chat.messages.last().copy(text=text,state=status,stats=stats)))}}
 fun regenerate(){val index=state.value.chat.messages.indexOfLast{it.role=="user"};if(index>=0)send(state.value.chat.messages[index].text,index)}
 fun continueReply(){send("Continue your previous response from where it stopped.")}
 fun stop(){runtime.cancel();image?.cancel();generation?.cancel();mutable.update{it.copy(status=if(loading)"Stop requested. Finishing model load..." else "Stopping...")}}
 fun background(){if(state.value.busy)stop()}
 fun download(action:String,id:String?=null){try{DownloadService.command(getApplication(),action,id)}catch(e:Exception){error("Android could not start the download. Reopen the app and try again.")}}
 fun deleteModel(m:ModelSpec){if(state.value.busy){error("Stop generation before deleting a model.");return};work{
  check(graph.record(m.id)?.state !in setOf("downloading","verifying","queued")){"Pause or cancel this download before deleting."}
  graph.inferenceLock.withLock{runtime.unload();mutable.update{it.copy(loaded="")}
  graph.fileLock.withLock{for(f in listOf(graph.file(m),graph.partial(m),File(graph.file(m).path+".verified")))check(!f.exists()||f.delete()){"Could not delete the selected file."}}}
  graph.db.remove("download",m.id)
  if(m.imported){graph.db.remove("model",m.id);graph.models.value=graph.models.value.filter{it.id!=m.id};if(state.value.chat.modelId==m.id)selectModel("qwen-small")}
  graph.refreshDownloads()
 }}
 fun verify(m:ModelSpec){if(state.value.busy)return;begin("Verifying model..."){graph.fileLock.withLock{graph.verify(m)};mutable.update{it.copy(status="SHA-256 verification passed.")}}}
 fun importModel(uri:Uri){if(state.value.busy)return;begin("Importing and inspecting GGUF..."){
  withContext(Dispatchers.IO){graph.fileLock.withLock{
   val id="import-"+newId();val temp=File(graph.modelsDir,"$id.part")
   try{
    getApplication<Application>().contentResolver.openInputStream(uri)?.use{input->FileOutputStream(temp).use{out->
     val buffer=ByteArray(256*1024);var total=0L
     while(true){currentCoroutineContext().ensureActive();val n=input.read(buffer);if(n<0)break;total+=n;check(total<=4_500_000_000L){"This model exceeds the 4.5 GB import limit."};check(graph.modelsDir.usableSpace>n+256_000_000L){"Not enough storage to import the model."};out.write(buffer,0,n)}
     out.fd.sync()
    }}?:error("The selected file could not be read.")
    val arch=GgufInspector.architecture(temp);check(arch in GgufInspector.supported){"This architecture ($arch) is not enabled for import."}
    val sha=graph.hashFile(temp);val size=temp.length()
    val m=ModelSpec(id,"Imported $arch", "$id.gguf",size,sha,architecture=arch,quantization="From GGUF",license="User-provided; check original license",creator="Local import",memory=size+1_200_000_000L,recommended=size+3_000_000_000L,experimental=true,imported=true)
    check(ResourcePolicy.compatibility(m,Hardware.detect(getApplication())).allowed){"This imported model is too large for this device."}
    Files.move(temp.toPath(),graph.file(m).toPath(),StandardCopyOption.ATOMIC_MOVE)
    graph.db.put("model",id,m.json());graph.markVerified(m);graph.models.value=graph.models.value+m
    mutable.update{it.copy(status="Imported $arch. Compatibility remains experimental.")}
   }finally{temp.delete()}
  }}
 }}
 fun applyPreset(p:Preset){if(state.value.busy)return;graph.settings.options=p.options;if(p.modelId.isNotBlank()&&graph.models.value.any{it.id==p.modelId&&it.kind=="text"})selectModel(p.modelId);mutable.update{it.copy(status="Preset: "+p.name,revision=it.revision+1)}}
 fun savePreset(p:Preset)=work{p.options.validate();graph.db.put("preset",p.id,p.json())}
 fun deletePreset(p:Preset)=work{graph.db.remove("preset",p.id)}
 fun favoriteMedia(m:MediaItem)=work{graph.db.put("media",m.id,m.copy(favorite=!m.favorite).json())}
 fun deleteMedia(m:MediaItem)=work{val f=File(graph.mediaDir,m.file);check(f.canonicalFile.parentFile==graph.mediaDir.canonicalFile);check(!f.exists()||f.delete()){"Could not delete this image."};graph.db.remove("media",m.id)}
 fun clearMedia()=work{graph.media().forEach{m->val f=File(graph.mediaDir,m.file);if(!f.exists()||f.delete())graph.db.remove("media",m.id)}}
 fun clearCache()=work{val dir=File(getApplication<Application>().cacheDir,"exports");dir.listFiles().orEmpty().filter{it.isFile}.forEach{it.delete()}}
 fun imageGenerate(prompt:String,negative:String,width:Int,height:Int,steps:Int,cfg:Float,seed:Long,count:Int){
  if(prompt.isBlank()||state.value.busy)return
  begin("Preparing image runtime. First load can take time..."){
   runtime.unload();mutable.update{it.copy(loaded="")}
   val m=graph.model(graph.settings.string("defaultImage","sd15"));check(m.kind=="image");guard(m);if(!graph.verified(m))graph.fileLock.withLock{graph.verify(m)}
   require(width in 256..512&&height in 256..512&&width%64==0&&height%64==0&&steps in 1..30&&count in 1..3&&cfg in 1f..12f)
   withContext(imageDispatcher){
    val runner=image?:ImageRuntime().also{image=it}
    repeat(count){index->
     currentCoroutineContext().ensureActive();guard(m)
     val actualSeed=if(seed<0)java.security.SecureRandom().nextLong().ushr(1)else seed+index
     val imageStarted=SystemClock.elapsedRealtime()
     val monitor=viewModelScope.launch {while(isActive){mutable.update{it.copy(progress=runner.progress(),status="Image ${index+1}/$count · CPU generation · ${runner.progress()}% · "+((SystemClock.elapsedRealtime()-imageStarted)/1000)+" s") };delay(500)}}
     try{
      val pixels=runner.generate(graph.file(m).path,prompt,negative,width,height,steps,cfg,actualSeed,ResourcePolicy.threads(graph.settings.profile,Runtime.getRuntime().availableProcessors()))
      currentCoroutineContext().ensureActive()
      val colors=IntArray(width*height){i->0xff000000.toInt() or ((pixels[i*3].toInt() and 255) shl 16) or ((pixels[i*3+1].toInt() and 255) shl 8) or (pixels[i*3+2].toInt() and 255)}
      val bitmap=Bitmap.createBitmap(colors,width,height,Bitmap.Config.ARGB_8888)
      val name=newId()+".png";val target=File(graph.mediaDir,name);val temp=File(graph.mediaDir,name+".part")
      try{FileOutputStream(temp).use{check(bitmap.compress(Bitmap.CompressFormat.PNG,100,it));it.fd.sync()};Files.move(temp.toPath(),target.toPath(),StandardCopyOption.ATOMIC_MOVE)}finally{bitmap.recycle();temp.delete()}
      val media=MediaItem(file=name,prompt=prompt,negative=negative,model=m.name,seed=actualSeed,width=width,height=height,steps=steps,cfg=cfg)
      graph.db.put("media",media.id,media.json());refresh()
     }finally{monitor.cancel()}
    }
   }
   mutable.update{it.copy(status="Images saved to your local gallery.",screen="History")}
  }
 }
 fun exportText(c:Conversation)=buildString{append("# "+c.title+"\n\n");c.messages.forEach{append("## "+if(it.role=="user")"You" else "Pocket AI");append("\n\n"+it.text+"\n\n")}}
 override fun onCleared(){runtime.cancel();image?.cancel();val job=generation;job?.cancel();graph.scope.launch{job?.join();graph.inferenceLock.withLock{runtime.unload()};imageDispatcher.close()};super.onCleared()}
}
