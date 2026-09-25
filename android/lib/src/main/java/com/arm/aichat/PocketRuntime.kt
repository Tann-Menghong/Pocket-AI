package com.arm.aichat

import android.content.Context
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.concurrent.Executors

data class TextOptions(val context:Int=4096,val threads:Int=4,val temperature:Float=.7f,val topP:Float=.9f,val topK:Int=40,val repetition:Float=1.1f,val seed:Long=-1)
data class RuntimeMessage(val role:String,val text:String)
class PocketRuntime(context:Context) {
 private val dispatcher=Executors.newSingleThreadExecutor{r->Thread(r,"PocketInference")}.asCoroutineDispatcher()
 private val directory=context.applicationInfo.nativeLibraryDir
 private var started=false
 @Volatile private var loadedPath=""
 @Volatile private var loadedContext=0
 @Volatile private var linked=false
 @Volatile var lastPromptTokens=0;private set
 @Volatile var generatedTokens=0;private set
 private external fun nativeInit(path:String)
 private external fun nativeLoad(path:String,context:Int,threads:Int)
 private external fun nativePrepare(roles:Array<String>,messages:Array<String>,maximum:Int,temperature:Float,topP:Float,topK:Int,repetition:Float,seed:Long,threads:Int):Int
 private external fun nativeNext():ByteArray?
 private external fun nativeCancel()
 private external fun nativeUnload()
 private external fun nativeCount():Int
 private external fun nativeLoadProgress():Int
 fun loadProgress()=if(linked)nativeLoadProgress()else 0
 suspend fun load(path:String,options:TextOptions)=withContext(dispatcher){
  if(!started){System.loadLibrary("ai-chat");linked=true;nativeInit(directory);started=true}
  if(loadedPath!=path||loadedContext!=options.context){nativeUnload();loadedPath="";nativeLoad(path,options.context,options.threads);loadedPath=path;loadedContext=options.context}
 }
 fun generate(messages:List<RuntimeMessage>,maximum:Int,options:TextOptions):Flow<String> = flow {
  check(loadedPath.isNotEmpty()){"Load a model before chatting."}
  lastPromptTokens=nativePrepare(messages.map{it.role}.toTypedArray(),messages.map{it.text}.toTypedArray(),maximum,options.temperature,options.topP,options.topK,options.repetition,options.seed,options.threads)
  generatedTokens=0
  val output=java.io.ByteArrayOutputStream()
  var emitted=0
  while(currentCoroutineContext().isActive){
   val token=nativeNext()?:break
   output.write(token);generatedTokens=nativeCount()
   // Decode only complete UTF-8 prefixes, preserving split emoji and multilingual tokens.
   val raw=output.toByteArray()
   var end=raw.size
   var start=end-1
   while(start>=0 && raw[start].toInt() and 0xC0==0x80)start--
   if(start>=0){val lead=raw[start].toInt() and 255;val expected=when{lead<128->1;lead and 0xE0==0xC0->2;lead and 0xF0==0xE0->3;lead and 0xF8==0xF0->4;else->1};if(end-start<expected)end=start}
   if(end>emitted){emit(String(raw,emitted,end-emitted,Charsets.UTF_8));emitted=end}
  }
 }.flowOn(dispatcher)
 fun isLoaded(path:String,context:Int)=loadedPath==path&&loadedContext==context
 fun cancel(){if(linked)nativeCancel()}
 suspend fun unload()=withContext(dispatcher){if(started)nativeUnload();loadedPath="";loadedContext=0}
 suspend fun close(){unload();dispatcher.close()}
}
