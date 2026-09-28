package com.example.llama.core
import com.example.llama.updates.UpdatePolicy
import com.example.llama.data.TemplateFields
import org.junit.Assert.*
import org.junit.Test
class PlatformPolicyTest{
 @Test fun browsingSearchesAllWordsAndSortsInstalledFirst(){
  val a=ModelSpec("a","Small coder","a.gguf",100,"a",creator="Qwen",purposes="Coding",quantization="Q4_K_M")
  val b=a.copy(id="b",name="B",bytes=200)
  val browse=com.example.llama.models.ModelBrowse
  assertTrue(browse.search(a,"qwen   coding Q4"))
  assertFalse(browse.search(a,"coding image"))
  assertEquals(listOf(b,a),browse.sort(listOf(a,b),"Installed first"){it.id=="b"})
  assertEquals(listOf(a,b),browse.sort(listOf(b,a),"Smallest download"){false})
 }
 @Test fun availableMemoryFilterIncludesContextAndReserve(){
  val m=ModelSpec("a","A","a.gguf",100,"a",memory=1_000_000_000)
  val h=Hardware(12_000_000_000,1_600_000_000,20_000_000_000,"arm64-v8a",8,0,"test")
  assertTrue(com.example.llama.models.ModelBrowse.ready(m,h,2048))
  assertFalse(com.example.llama.models.ModelBrowse.ready(m,h,8192))
  assertFalse(ResourcePolicy.compatibility(m.copy(architecture="unknown"),h).allowed)
  assertFalse(ResourcePolicy.compatibility(m.copy(kind="image",architecture="flux"),h).allowed)
 }

 @Test fun modelRevisionDoesNotRepeatKnownHash(){
  val old=ModelSpec(id="old",name="Old",file="model.gguf",bytes=100,sha256="a",repo="owner/repo",revision="1")
  val next=old.copy(id="new",file="new-local.gguf",remoteFile="model.gguf",sha256="b",revision="2")
  val policy=com.example.llama.models.ModelRevisionPolicy
  assertEquals(listOf(next),policy.candidates(listOf(old),listOf(next,next)))
  assertTrue(policy.candidates(listOf(old,next),listOf(next)).isEmpty())
  assertTrue(policy.candidates(listOf(old),listOf(next.copy(architecture="wrong"))).isEmpty())
 }
 @Test fun versionComparisonIsNumeric(){assertTrue(UpdatePolicy.newer("v2.10.0","2.9.9"));assertFalse(UpdatePolicy.newer("v2.1.0-preview","2.1.0"));assertFalse(UpdatePolicy.newer("garbage","2.0"))}
 @Test fun stableAndPrereleaseOrdering(){assertTrue(UpdatePolicy.newer("2.1.0","2.1.0-preview"));assertTrue(UpdatePolicy.newer("2.1.0-preview.10","2.1.0-preview.2"));assertFalse(UpdatePolicy.newer("2.1.0+build2","2.1.0+build1"))}
 @Test fun onlyOurHttpsReleaseAssetsAreTrusted(){assertTrue(UpdatePolicy.trusted("https://github.com/Tann-Menghong/Pocket-AI/releases/download/v2.1.0/PocketAI.apk"));assertFalse(UpdatePolicy.trusted("http://github.com/Tann-Menghong/Pocket-AI/releases/download/v2/app.apk"));assertFalse(UpdatePolicy.trusted("https://github.com/attacker/app/releases/download/v2/app.apk"));assertFalse(UpdatePolicy.trusted("https://github.com.evil.test/Tann-Menghong/Pocket-AI/releases/download/v2/app.apk"))}
 @Test fun rejectWrongSignerPackageAndDowngrade(){val good=setOf("trusted");assertTrue(UpdatePolicy.eligiblePackage("app","app",2,3,good,good));assertFalse(UpdatePolicy.eligiblePackage("app","app",2,3,good,setOf("other")));assertFalse(UpdatePolicy.eligiblePackage("app","other",2,3,good,good));assertFalse(UpdatePolicy.eligiblePackage("app","app",2,2,good,good));assertFalse(UpdatePolicy.eligiblePackage("app","app",2,3,emptySet(),emptySet()))}
 @Test fun templateFieldsAreDistinctAndLiteral(){val t="Explain {topic} and {topic} in {language}";assertEquals(listOf("topic","language"),TemplateFields.names(t));assertEquals("Explain math and math in English",TemplateFields.fill(t,mapOf("topic" to "math","language" to "English")));assertThrows(IllegalStateException::class.java){TemplateFields.fill(t,emptyMap())}}
}
