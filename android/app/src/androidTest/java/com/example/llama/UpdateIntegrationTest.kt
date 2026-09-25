package com.example.llama
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.llama.data.PocketApplication
import com.example.llama.updates.UpdateRelease
import com.example.llama.models.ModelHub
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.io.File
@RunWith(AndroidJUnit4::class)
class UpdateIntegrationTest{
 private val g get()=(InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as PocketApplication).graph
 @Test fun rejectsTamperedAndSameVersionApk()=runBlocking{
  g.ready.await();val manager=g.updates;val oldCandidate=g.settings.string("updateCandidate")
  try{
   manager.file.parentFile!!.mkdirs();File(g.app.applicationInfo.sourceDir).copyTo(manager.file,true)
   val size=manager.file.length();val sha=g.hashFile(manager.file)
   val candidate=UpdateRelease("v99.0.0","QA fixture","Synthetic test","https://github.com/Tann-Menghong/Pocket-AI/releases/download/qa/app.apk",size,"0".repeat(64),true)
   g.settings.set("updateCandidate",candidate.json().toString())
   try{manager.verify();fail("Wrong checksum accepted")}catch(expected:IllegalStateException){assertTrue(expected.message!!.contains("checksum"))}
   g.settings.set("updateCandidate",candidate.copy(sha=sha).json().toString())
   try{manager.verify();fail("Same-version APK accepted")}catch(expected:IllegalStateException){assertTrue(expected.message!!.contains("version"))}
  }finally{manager.file.delete();g.settings.set("updateCandidate",oldCandidate)}
 }
 @Test fun liveMetadataChecksDoNotDownloadWeights()=runBlocking{
  g.ready.await();val old=g.settings.offline;g.settings.set("offline",false)
  try{
   val before=g.modelsDir.listFiles()?.sumOf{it.length()}?:0
   val choices=ModelHub(g).inspect("https://huggingface.co/Qwen/Qwen2.5-0.5B-Instruct-GGUF")
   assertTrue(choices.any{it.remoteFile=="qwen2.5-0.5b-instruct-q4_k_m.gguf"&&it.sha256=="74a4da8c9fdbcd15bd1f6d01d621410d31c6fc00986f5eb687824e7b93d7a9db"})
   g.updates.check()
   assertEquals(before,g.modelsDir.listFiles()?.sumOf{it.length()}?:0)
   g.settings.set("offline",true)
   try{ModelHub(g).inspect("Qwen/Qwen2.5-0.5B-Instruct-GGUF");fail("Offline network allowed")}catch(expected:IllegalStateException){assertTrue(expected.message!!.contains("Offline"))}
  }finally{g.settings.set("offline",old)}
 }
}
