package com.example.llama.data

import android.app.Application
import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.os.PowerManager
import com.example.llama.core.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

class PocketDatabase(c:Context):SQLiteOpenHelper(c,File(c.noBackupFilesDir,"pocket.db").path,null,1) {
 init{setWriteAheadLoggingEnabled(true)}
 override fun onCreate(db:SQLiteDatabase){db.execSQL("CREATE TABLE records (category TEXT NOT NULL,id TEXT NOT NULL,payload TEXT NOT NULL,PRIMARY KEY(category,id))")}
 override fun onUpgrade(db:SQLiteDatabase,old:Int,new:Int){error("A database migration is required. Your data has not been deleted.")}
 fun all(category:String):List<JSONObject> = readableDatabase.query("records",arrayOf("payload"),"category=?",arrayOf(category),null,null,null).use{c->buildList{while(c.moveToNext())add(JSONObject(c.getString(0)))}}
 fun get(category:String,id:String):JSONObject? = readableDatabase.query("records",arrayOf("payload"),"category=? AND id=?",arrayOf(category,id),null,null,null).use{if(it.moveToFirst())JSONObject(it.getString(0))else null}
 fun put(category:String,id:String,o:JSONObject){val v=ContentValues().apply{put("category",category);put("id",id);put("payload",o.toString())};check(writableDatabase.insertWithOnConflict("records",null,v,SQLiteDatabase.CONFLICT_REPLACE)!=-1L){"Local data could not be saved."}}
 fun remove(category:String,id:String){writableDatabase.delete("records","category=? AND id=?",arrayOf(category,id))}
 fun transaction(block:()->Unit){val db=writableDatabase;db.beginTransaction();try{block();db.setTransactionSuccessful()}finally{db.endTransaction()}}
}
class Settings(c:Context) {
 private val p=c.getSharedPreferences("pocket-v2",0)
 fun string(key:String,default:String="")=p.getString(key,default)?:default
 fun bool(key:String,default:Boolean=false)=p.getBoolean(key,default)
 fun int(key:String,default:Int)=p.getInt(key,default)
 fun set(key:String,value:String){p.edit().putString(key,value).apply()}
 fun set(key:String,value:Boolean){p.edit().putBoolean(key,value).apply()}
 fun set(key:String,value:Int){p.edit().putInt(key,value).apply()}
 var options:GenerationOptions
  get()=runCatching{GenerationOptions.from(JSONObject(string("options","{}"))).validate()}.getOrDefault(GenerationOptions())
  set(v){set("options",v.validate().json().toString())}
 val profile get()=string("profile","Balanced")
 val offline get()=bool("offline")
}
class PocketApplication:Application(){
 val graph by lazy { AppGraph(this) }
 override fun onCreate(){super.onCreate();graph}
 override fun onTrimMemory(level:Int){super.onTrimMemory(level);if(level>=TRIM_MEMORY_RUNNING_LOW)graph.memoryPressure.value=true}
}
class AppGraph(val app:Application) {
 val scope=CoroutineScope(SupervisorJob()+Dispatchers.IO)
 val db=PocketDatabase(app)
 val settings=Settings(app)
 val models=MutableStateFlow<List<ModelSpec>>(emptyList())
 val downloads=MutableStateFlow<List<DownloadRecord>>(emptyList())
 val memoryPressure=MutableStateFlow(false)
 val error=MutableStateFlow("")
 val ready=CompletableDeferred<Unit>()
 val modelsDir=File(app.noBackupFilesDir,"models").apply{mkdirs()}
 val mediaDir=File(app.noBackupFilesDir,"images").apply{mkdirs()}
 val runtime by lazy { com.arm.aichat.PocketRuntime(app) }
 val inferenceLock=kotlinx.coroutines.sync.Mutex()
 val downloadLock=kotlinx.coroutines.sync.Mutex()
 val fileLock=kotlinx.coroutines.sync.Mutex()
 init {scope.launch {
  try {
   val a=JSONArray(app.assets.open("catalog.json").bufferedReader().use{it.readText()})
   models.value=(0 until a.length()).map{ModelSpec.from(a.getJSONObject(it))}+db.all("model").map{ModelSpec.from(it)}
   db.transaction {
    if(db.get("meta","migration")==null){
     val old=File(app.noBackupFilesDir,"chat.json")
     if(old.exists()){
      try {
      val messages=JSONArray(old.readText()).let{j->(0 until j.length()).map{val o=j.getJSONObject(it);ChatMessage(role=if(o.getString("role")=="You")"user" else "assistant",text=o.getString("text"))}}
      if(messages.isNotEmpty()){val chat=Conversation(title="Imported conversation",messages=messages);db.put("chat",chat.id,chat.json())}
      }catch(e:org.json.JSONException){error.value="The previous conversation could not be imported. Its original file was preserved for recovery."}
     }
     db.put("meta","migration",JSONObject().put("version",1))
    }
    if(db.get("meta","presets")==null) {
     val prompts=mapOf("General Assistant" to "Be a helpful, honest and concise assistant.","Coding" to "Help write and explain code. State assumptions and flag untested code.","Writing" to "Help draft and edit clear, natural writing.","Study" to "Teach step by step and check understanding.","Translation" to "Translate accurately, preserving tone and meaning.","Summarization" to "Summarize the supplied text faithfully without inventing facts.","Creative" to "Help brainstorm original ideas and creative writing.")
     prompts.forEach{(name,prompt)->val preset=Preset(name=name,options=GenerationOptions(system=prompt));db.put("preset",preset.id,preset.json())}
     db.put("meta","presets",JSONObject().put("version",1))
    }
    db.all("download").map{DownloadRecord.from(it)}.filter{it.state in setOf("downloading","verifying","queued")}.forEach{putDownload(it.copy(state="paused",speed=0,error="Interrupted by an app or device restart. Resume when ready."))}
    db.all("chat").map{Conversation.from(it)}.filter{c->c.messages.any{it.state=="generating"}}.forEach{c->db.put("chat",c.id,c.copy(messages=c.messages.map{if(it.state=="generating")it.copy(state="interrupted")else it}).json())}
   }
   refreshDownloads();ready.complete(Unit)
  }catch(t:Exception){error.value="Local data could not be opened: "+t.message;ready.completeExceptionally(t)}
 }}
 fun model(id:String)=models.value.firstOrNull{it.id==id}?:error("The selected model is no longer available.")
 fun file(m:ModelSpec)=File(modelsDir,m.file).also{require(it.canonicalFile.parentFile==modelsDir.canonicalFile){"Invalid model file name."}}
 fun partial(m:ModelSpec)=File(file(m).path+".part")
 fun installed(m:ModelSpec)=file(m).let{it.isFile && it.length()==m.bytes}
 fun verified(m:ModelSpec):Boolean {val f=file(m);return installed(m)&&runCatching{File(f.path+".verified").readText()=="${m.sha256}:${f.length()}:${f.lastModified()}"}.getOrDefault(false)}
 fun markVerified(m:ModelSpec){val f=file(m);File(f.path+".verified").writeText("${m.sha256}:${f.length()}:${f.lastModified()}")}
 suspend fun verify(m:ModelSpec){withContext(Dispatchers.IO){check(installed(m)){"The model file is missing or incomplete."};check(hashFile(file(m))==m.sha256){"Model verification failed. Delete the corrupt model and download it again."};markVerified(m)}}
 fun putDownload(d:DownloadRecord){db.put("download",d.id,d.json())}
 fun refreshDownloads(){downloads.value=db.all("download").map{DownloadRecord.from(it)}}
 fun record(id:String)=db.get("download",id)?.let{DownloadRecord.from(it)}
 fun conversations()=db.all("chat").map{Conversation.from(it)}.sortedWith(compareByDescending<Conversation>{it.pinned}.thenByDescending{it.updated})
 fun media()=db.all("media").map{MediaItem.from(it)}.sortedByDescending{it.created}
 suspend fun hashFile(f:File):String {val digest=MessageDigest.getInstance("SHA-256");f.inputStream().use{input->val buffer=ByteArray(256*1024);while(true){currentCoroutineContext().ensureActive();val n=input.read(buffer);if(n<0)break;digest.update(buffer,0,n)}};return digest.digest().joinToString(""){"%02x".format(it.toInt() and 255)}}
 suspend fun storage():Map<String,Long> = withContext(Dispatchers.IO){mapOf("AI models" to modelsDir.walkTopDown().filter{it.isFile}.sumOf{it.length()},"Generated images" to mediaDir.walkTopDown().filter{it.isFile}.sumOf{it.length()},"Generated videos" to 0L,"Conversations & metadata" to app.noBackupFilesDir.listFiles().orEmpty().filter{it.name.startsWith("pocket.db")||it.name=="chat.json"}.sumOf{it.length()},"Cache" to app.cacheDir.walkTopDown().filter{it.isFile}.sumOf{it.length()})}
}
