package com.example.llama.models
import com.example.llama.core.*

object ModelBrowse {
 fun ready(m:ModelSpec,h:Hardware,context:Int)=ResourcePolicy.canLoadNow(m,h,context)
 fun search(m:ModelSpec,query:String)=query.trim().split(Regex("\\s+")).filter{it.isNotBlank()}.all { word ->
  listOf(m.name,m.creator,m.purposes,m.quantization,m.architecture,m.license).any{it.contains(word,true)}
 }
 fun sort(models:List<ModelSpec>,order:String,installed:(ModelSpec)->Boolean):List<ModelSpec> = when(order){
  "Smallest download"->models.sortedBy{it.bytes}
  "Lowest RAM"->models.sortedBy{it.memory}
  "Name"->models.sortedBy{it.name.lowercase()}
  "Installed first"->models.sortedWith(compareByDescending<ModelSpec>{installed(it)}.thenBy{it.bytes})
  else->models
 }
}
