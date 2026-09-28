package com.example.llama
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.llama.core.*
import org.json.JSONArray
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
@RunWith(AndroidJUnit4::class)
class CatalogTest {
 @Test fun curatedModelsHavePinnedIntegrityAndUniqueFiles(){
  val app=InstrumentationRegistry.getInstrumentation().targetContext
  val json=JSONArray(app.assets.open("catalog.json").bufferedReader().use{it.readText()})
  val models=(0 until json.length()).map{ModelSpec.from(json.getJSONObject(it))}
  assertEquals(15,models.size)
  assertEquals(models.size,models.map{it.id}.distinct().size)
  assertEquals(models.size,models.map{it.file}.distinct().size)
  assertEquals(models.size,models.map{it.sha256}.distinct().size)
  models.forEach{m->
   assertTrue(Regex("[a-f0-9]{64}").matches(m.sha256))
   assertTrue(Regex("[a-f0-9]{40}").matches(m.revision))
   assertTrue(m.bytes>0 && m.memory>m.bytes)
   assertTrue(m.url.startsWith("https://huggingface.co/"))
   if(m.kind=="text")assertTrue(m.architecture in GgufInspector.supported)
  }
 }
}
