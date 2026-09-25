package com.example.llama.data

import com.example.llama.core.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.InputStream
import java.io.OutputStream

data class PromptTemplate(val id:String=newId(),val name:String,val category:String="Custom",val text:String,val favorite:Boolean=false){
 fun json()=JSONObject().put("id",id).put("name",name).put("category",category).put("text",text).put("favorite",favorite)
 companion object{fun from(o:JSONObject)=PromptTemplate(o.getString("id"),o.getString("name"),o.optString("category","Custom"),o.getString("text"),o.optBoolean("favorite"))}
}
object TemplateFields {
 private val pattern=Regex("\\{([A-Za-z][A-Za-z0-9_]{0,39})\\}")
 fun names(text:String)=pattern.findAll(text).map{it.groupValues[1]}.distinct().toList()
 fun fill(text:String,values:Map<String,String>):String=pattern.replace(text){values[it.groupValues[1]]?:error("Fill every template field.")}
}
class LibraryTools(private val g:AppGraph){
 fun prompts()=g.db.all("prompt").map{PromptTemplate.from(it)}.sortedWith(compareByDescending<PromptTemplate>{it.favorite}.thenBy{it.name})
 fun initialize(){
  if(g.db.get("meta","library21")!=null)return
  g.db.transaction{
   listOf(
    PromptTemplate(name="Summarize",category="Productivity",text="Summarize the key points of this text:\n{text}"),
    PromptTemplate(name="Professional email",category="Writing",text="Write a professional email about: {topic}"),
    PromptTemplate(name="Translate",category="Translation",text="Translate this text into {language}, preserving meaning:\n{text}"),
    PromptTemplate(name="Debug code",category="Coding",text="Explain the bug and suggest a correction:\n{code}"),
    PromptTemplate(name="Study",category="Education",text="Teach me {topic} step by step, then ask a practice question."),
    PromptTemplate(name="Brainstorm",category="Creativity",text="Suggest ten distinct ideas for {topic}."),
    PromptTemplate(name="Image prompt",category="Image Generation",text="Create a detailed visual prompt for an image of {subject}.")
   ).forEach{g.db.put("prompt",it.id,it.json())}
   listOf("Khmer Assistant" to "Help in Khmer when possible. Be honest about language limitations and ask for clarification instead of inventing translations.",
    "English Tutor" to "Teach English with short explanations, examples and gentle corrections. Adapt to the learner.").forEach{(name,prompt)->
     val p=Preset(name=name,options=GenerationOptions(system=prompt),description="Language practice; accuracy depends on the selected model.")
     g.db.put("preset",p.id,p.json())
    }
   g.db.put("meta","library21",JSONObject().put("done",true))
  }
 }
 // Deliberately excludes model weights, media and network/download state.
 fun export(output:OutputStream){
  val out=java.io.ByteArrayOutputStream()
  fun append(value:String){val bytes=value.toByteArray(Charsets.UTF_8);require(out.size()+bytes.size<=32*1024*1024){"Backup exceeds the 32 MB limit. Export large chats separately."};out.write(bytes)}
  append("{\"format\":\"pocket-ai-backup\",\"version\":1")
  for(category in listOf("chat","preset","prompt")){
   append(",\""+category+"\":[");var first=true
   g.db.readableDatabase.query("records",arrayOf("payload"),"category=?",arrayOf(category),null,null,null).use{cursor->while(cursor.moveToNext()){if(!first)append(",");first=false;append(cursor.getString(0))}}
   append("]")
  }
  append(",\"settings\":"+g.settings.snapshot().toString()+"}")
  out.writeTo(output);output.flush()
 }
 fun inspect(input:InputStream):JSONObject{
  val bytes=input.readNBytes(32*1024*1024+1)
  require(bytes.size<=32*1024*1024){"Backup is too large (maximum 32 MB)."}
  val root=JSONObject(bytes.toString(Charsets.UTF_8))
  require(root.optString("format")=="pocket-ai-backup"&&root.optInt("version")==1){"Unsupported Pocket AI backup."}
  var totalMessages=0
  for(category in listOf("chat","preset","prompt")){
   val a=root.getJSONArray(category);require(a.length()<=5000){"Too many backup entries."}
   for(i in 0 until a.length()){
    val o=a.getJSONObject(i);require(o.getString("id").length in 1..100)
    when(category){
     "chat"->{val c=Conversation.from(o);totalMessages+=c.messages.size;require(totalMessages<=50000);require(c.messages.all{it.role in setOf("user","assistant","system")&&it.text.length<=200000})}
     "preset"->{val p=Preset.from(o);p.options.validate();require(p.name.length<=100&&p.description.length<=2000)}
     "prompt"->{val p=PromptTemplate.from(o);require(p.name.length<=100&&p.text.length<=16000)}
    }
   }
  }
  root.optJSONObject("settings")?.let{require(it.length()<=200);require(it.toString().length<=100000);g.settings.validateRestore(it)}
  return root
 }
 fun restore(root:JSONObject):Int{
  root.optJSONObject("settings")?.let{g.settings.validateRestore(it)}
  var count=0
  // Merge as new records, never overwrite existing conversations or assistants.
  g.db.transaction{
   for(category in listOf("chat","preset","prompt")){
    val a=root.getJSONArray(category)
    for(i in 0 until a.length()){
     val o=JSONObject(a.getJSONObject(i).toString()).put("id",newId())
     if(category=="chat"&&g.models.value.none{it.id==o.optString("modelId")})o.put("modelId","qwen-small")
     g.db.put(category,o.getString("id"),o);count++
    }
   }
  }
  // Settings restore is explicit, limited to supported primitive preferences.
  root.optJSONObject("settings")?.let{g.settings.restore(it)}
  if(g.models.value.none{it.id==g.settings.string("defaultModel","qwen-small")&&it.kind=="text"})g.settings.set("defaultModel","qwen-small")
  if(g.models.value.none{it.id==g.settings.string("defaultImage","sd15")&&it.kind=="image"})g.settings.set("defaultImage","sd15")
  return count
 }
}
