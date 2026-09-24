package com.example.llama
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.core.app.ActivityScenario
import com.example.llama.data.*
import com.example.llama.core.*
import com.arm.aichat.TextOptions
import com.example.pocketdiffusion.ImageRuntime
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
@RunWith(AndroidJUnit4::class)
class PlatformSmokeTest {
 private val app get()=InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as PocketApplication
 @Test fun libraryAndSettingsPersist()=runBlocking {
  val g=app.graph;g.ready.await();assertEquals(4,g.models.value.size)
  val c=Conversation(title="QA roundtrip",messages=listOf(ChatMessage(role="user",text="Hello 世界 👋")))
  try{g.db.put("chat",c.id,c.json());assertEquals(c,Conversation.from(g.db.get("chat",c.id)!!))}finally{g.db.remove("chat",c.id)}
  val old=g.settings.string("profile","Balanced");try{g.settings.set("profile","Battery Saver");assertEquals("Battery Saver",Settings(app).profile)}finally{g.settings.set("profile",old)}
 }
 @Test fun screensSurviveRecreation(){
  app.graph.settings.set("onboarded",true)
  ActivityScenario.launch(MainActivity::class.java).use{scenario->
   for(screen in listOf("Chat","Models","Downloads","Create","History","Settings")){
    scenario.onActivity{it.vm.navigate(screen)}
    InstrumentationRegistry.getInstrumentation().waitForIdleSync()
    scenario.recreate()
    scenario.onActivity{assertEquals(screen,it.vm.state.value.screen)}
   }
  }
 }
 @Test fun downloadPauseResumeCancel()=runBlocking {
  val g=app.graph;g.ready.await();val id="qwen-small";val m=g.model(id)
  if(g.installed(m))return@runBlocking
  g.settings.set("onboarded",true)
  ActivityScenario.launch(MainActivity::class.java).use{
   com.example.llama.download.DownloadService.command(app,"enqueue",id)
   kotlinx.coroutines.withTimeout(60000){while((g.record(id)?.downloaded?:0)<262144 && g.record(id)?.state!="complete")kotlinx.coroutines.delay(250)}
   com.example.llama.download.DownloadService.command(app,"pause",id)
   kotlinx.coroutines.withTimeout(10000){while(g.record(id)?.state!="paused")kotlinx.coroutines.delay(100)}
   kotlinx.coroutines.delay(1500);assertEquals("paused",g.record(id)?.state)
   val before=g.partial(m).length();assertTrue(before>0)
   com.example.llama.download.DownloadService.command(app,"enqueue",id)
   kotlinx.coroutines.withTimeout(60000){while(g.partial(m).length()<=before && g.record(id)?.state!="complete")kotlinx.coroutines.delay(250)}
   com.example.llama.download.DownloadService.command(app,"cancel",id)
   kotlinx.coroutines.withTimeout(10000){while(g.record(id)?.state!="cancelled"||g.partial(m).exists())kotlinx.coroutines.delay(100)}
   assertFalse(g.partial(m).exists())
  }
 }
 @Test fun nativeBackendsLinkAndRejectMissingModels()=runBlocking{
  val runtime=app.graph.runtime
  try{runtime.load(app.filesDir.path+"/missing.gguf",TextOptions());fail("Missing model was accepted")}catch(expected:java.io.IOException){assertTrue(expected.message!!.contains("model",true))}finally{runtime.unload()}
  val image=ImageRuntime();image.cancel();assertTrue(image.progress()>=0)
  try{image.generate(app.filesDir.path+"/missing.gguf","test","",256,256,1,7f,1,2);fail("Missing image model was accepted")}catch(expected:java.io.IOException){assertNotNull(expected.message)}
 }
}
