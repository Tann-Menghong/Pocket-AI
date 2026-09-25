package com.example.llama
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.core.app.ActivityScenario
import com.example.llama.data.PocketApplication
import com.example.llama.core.GenerationOptions
import com.example.llama.download.DownloadService
import kotlinx.coroutines.*
import org.junit.Test
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.runner.RunWith
@RunWith(AndroidJUnit4::class)
class FullTextGenerationTest {
 @Test fun verifiedModelGeneratesOffline()=runBlocking {
  assumeTrue("Opt in with -e fullModels true: downloads 639 MB",InstrumentationRegistry.getArguments().getString("fullModels")=="true")
  val app=InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as PocketApplication
  val g=app.graph;g.ready.await();g.settings.set("onboarded",true)
  val oldOptions=g.settings.options;val oldOffline=g.settings.offline
  ActivityScenario.launch(MainActivity::class.java).use{scenario->
   try{
    g.settings.set("offline",false);val testModel=InstrumentationRegistry.getArguments().getString("modelId")?:"qwen-small";val m=g.model(testModel)
    if(!g.verified(m)){
     DownloadService.command(app,"enqueue",m.id)
     withTimeout(600000){while(g.record(m.id)?.state!="complete"){check(g.record(m.id)?.state!="failed"){g.record(m.id)?.error?:"Download failed"};delay(500)}}
    }
    g.verify(m);assertTrue(g.verified(m))
    g.settings.options=GenerationOptions(context=2048,maximum=32,temperature=0f)
    g.settings.set("offline",true)
    lateinit var vm:PlatformViewModel
    scenario.onActivity{vm=it.vm}
    withTimeout(10000){while(!vm.state.value.ready)delay(100)}
    scenario.onActivity{vm.newChat();vm.selectModel(testModel);vm.send("Say hello in one short sentence.")}
    withTimeout(180000){while(vm.state.value.busy)delay(250)}
    assertEquals("",vm.state.value.error)
    val reply=vm.state.value.chat.messages.last()
    assertEquals("assistant",reply.role);assertEquals("complete",reply.state);assertTrue(reply.text.isNotBlank());assertTrue(reply.stats.contains("tokens"));InstrumentationRegistry.getInstrumentation().sendStatus(2,android.os.Bundle().apply{putString("performance",reply.stats);putString("model",testModel)})
    assertTrue(g.conversations().any{it.id==vm.state.value.chat.id&&it.messages.last().text==reply.text})
   }finally{g.settings.options=oldOptions;g.settings.set("offline",oldOffline);g.runtime.unload()}
  }
 }
}
