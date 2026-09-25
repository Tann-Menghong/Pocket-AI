package com.example.llama
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.llama.data.*
import com.example.llama.core.*
import kotlinx.coroutines.runBlocking
import org.json.*
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.io.*
@RunWith(AndroidJUnit4::class)
class LibraryUpgradeTest{
 private val g get()=(InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as PocketApplication).graph
 @Test fun summariesPreserveFullMessagesAndBackupMerges()=runBlocking{
  g.ready.await()
  val c=Conversation(title="QA backup",messages=(1..20).map{ChatMessage(role=if(it%2==0)"assistant" else "user",text="Message "+it+" "+"x".repeat(1000))})
  val oldIds=g.conversations().map{it.id}.toSet()
  try{
   g.db.put("chat",c.id,c.json())
   val summary=g.conversations().first{it.id==c.id};assertEquals(20,summary.totalMessages);assertEquals(2,summary.messages.size);assertTrue(summary.messages.all{it.text.length<=500})
   assertEquals(20,g.fullChat(c.id).messages.size)
   val out=ByteArrayOutputStream();g.library.export(out);val parsed=g.library.inspect(ByteArrayInputStream(out.toByteArray()))
   val minimal=JSONObject().put("format","pocket-ai-backup").put("version",1).put("chat",JSONArray().put(c.json())).put("preset",JSONArray()).put("prompt",JSONArray()).put("settings",JSONObject().put("offline",false).put("fontPercent",110))
   val currentOffline=g.settings.offline;assertEquals(1,g.library.restore(g.library.inspect(ByteArrayInputStream(minimal.toString().toByteArray()))))
   assertEquals(currentOffline,g.settings.offline);assertEquals(20,g.fullChat(c.id).messages.size)
   assertTrue(parsed.getJSONArray("chat").length()>0)
  }finally{g.conversations().filter{it.id !in oldIds}.forEach{g.db.remove("chat",it.id)};g.settings.remove("fontPercent")}
 }
 @Test fun rejectsBadBackupAndWrongPreferenceType()=runBlocking{
  g.ready.await()
  assertThrows(Exception::class.java){g.library.inspect(ByteArrayInputStream("{\"format\":\"other\"}".toByteArray()))}
  assertThrows(IllegalArgumentException::class.java){g.settings.validateRestore(JSONObject().put("fontPercent","huge"))}
  assertThrows(IllegalArgumentException::class.java){g.settings.validateRestore(JSONObject().put("fontPercent",99999))};Unit
 }
 @Test fun archiveAndAssistantMetadataRoundTrip()=runBlocking{
  g.ready.await();val p=Preset(name="QA assistant",options=GenerationOptions(),icon="✦",description="QA",modelId="qwen-small")
  assertEquals(p,Preset.from(p.json()))
  val c=Conversation(title="Archived",archived=true)
  try{g.db.put("chat",c.id,c.json());assertTrue(g.fullChat(c.id).archived);assertTrue(g.conversations().first{it.id==c.id}.archived)}finally{g.db.remove("chat",c.id)}
 }
}
