package com.example.llama.core
import org.junit.Assert.*
import org.junit.Test
import java.io.File
class SafetyPolicyTest {
 @Test fun resumesOnlyMatchingRange(){assertTrue(RangePolicy.append(206,100,"bytes 100-999/1000",1000));assertFalse(RangePolicy.append(200,100,null,1000))}
 @Test fun rejectsWrongRange(){assertThrows(IllegalArgumentException::class.java){RangePolicy.append(206,100,"bytes 0-999/1000",1000)}}
 @Test fun rejectsChangedTotal(){assertThrows(IllegalArgumentException::class.java){RangePolicy.append(206,100,"bytes 100-999/2000",1000)}}
 @Test fun rejectsHttpFailure(){assertThrows(IllegalArgumentException::class.java){RangePolicy.append(403,0,null,1000)}}
 private val phone=Hardware(12_000_000_000,7_000_000_000,20_000_000_000,"arm64-v8a",8,0,"QA")
 private val model=ModelSpec("test","Test","test.gguf",600_000_000,"hash",memory=1_500_000_000)
 @Test fun blocksImpossibleMemory(){assertFalse(ResourcePolicy.compatibility(model.copy(memory=11_000_000_000),phone).allowed)}
 @Test fun blocksVideo(){assertFalse(ResourcePolicy.compatibility(model.copy(kind="video"),phone).allowed)}
 @Test fun blocks32Bit(){assertFalse(ResourcePolicy.compatibility(model,phone.copy(abi="armeabi-v7a")).allowed)}
 @Test fun contextAddsMemory(){assertTrue(ResourcePolicy.required(model,8192)>ResourcePolicy.required(model,2048))}
 @Test fun profilesRespectCoreCount(){assertEquals(2,ResourcePolicy.threads("Performance",2));assertEquals(2,ResourcePolicy.threads("Battery Saver",8))}
 @Test fun rejectsImpossibleTokenBudget(){assertThrows(IllegalArgumentException::class.java){GenerationOptions(context=1024,maximum=1024).validate()}}
 @Test fun rejectsNotGguf(){val f=File.createTempFile("invalid",".gguf");try{f.writeText("garbage data");assertThrows(IllegalArgumentException::class.java){GgufInspector.architecture(f)}}finally{f.delete()}}
}
