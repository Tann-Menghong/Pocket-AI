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
 @Test fun searchReturnsNewestMatchesBeyondItsLimit()=runBlocking{
  g.ready.await()
  val prefix="Search order "+java.util.UUID.randomUUID()
  val chats=(0 until 24).map{index->Conversation(id="${prefix}_%03d".format(index),title=prefix,updated=1_000L+index,messages=listOf(ChatMessage(role="user",text="entry $index")))}
  try{
   chats.forEach{g.db.put("chat",it.id,it.json())}
   assertEquals((23 downTo 19).map{chats[it].id},g.db.searchChats(prefix,5).map{it.id})
  }finally{chats.forEach{g.db.remove("chat",it.id)}}
 }

 @Test fun searchFindsOlderUnicodeMessagesWithoutExpandingSummaries()=runBlocking{
  g.ready.await()
  val c=Conversation(title="Search QA",messages=listOf(ChatMessage(role="user",text="Unique old needle ខ្មែរ 100%_"))+(1..8).map{ChatMessage(role="assistant",text="Later "+it)})
  try{
   g.db.put("chat",c.id,c.json())
   assertTrue(g.db.searchChats("Unique old needle").any{it.id==c.id})
   assertTrue(g.db.searchChats("ខ្មែរ 100%_").any{it.id==c.id})
   assertFalse(g.db.searchChats("absent needle").any{it.id==c.id})
   val result=g.db.searchChats("Unique old needle").first{it.id==c.id}
   assertTrue(result.summary);assertEquals(9,result.totalMessages);assertEquals(2,result.messages.size)
   g.db.remove("chat",c.id);assertFalse(g.db.searchChats("Unique old needle").any{it.id==c.id})
  }finally{g.db.remove("chat",c.id)}
 }
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
