package com.example.llama

import android.widget.*
import android.os.Build
import com.example.llama.core.*
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.*

fun MainActivity.buildSettings(body:LinearLayout){
 val settings=vm.graph.settings
 kit.section(body,"Make it yours","Appearance, generation, privacy and storage.")
 val categories=listOf("Appearance","Chat","Generation","Downloads & privacy","Storage","Device & runtime")
 val selected=settings.string("settingsCategory","Overview")
 if(selected=="Overview"){
  categories.forEach{title->body.addView(kit.button(title,true){settings.set("settingsCategory",title);buildPage("Settings")})}
  body.addView(kit.button("App updates",true){vm.navigate("Updates")});body.addView(kit.button("AI model updates",true){vm.navigate("Model updates")})
  body.addView(kit.button("Back up local library",true){exportBackup()});body.addView(kit.button("Restore local backup",true){restoreBackup()})
  body.addView(kit.button("Assistants",true){vm.navigate("Assistants")});body.addView(kit.button("Prompt library",true){vm.navigate("Prompts")});return
 }
 body.addView(kit.button("All settings",true){settings.set("settingsCategory","Overview");buildPage("Settings")})
 fun group(title:String):LinearLayout=kit.card().also{it.addView(kit.label(title,18,true));it.visibility=if(title==selected)android.view.View.VISIBLE else android.view.View.GONE;body.addView(it)}
 fun choice(parent:LinearLayout,label:String,key:String,default:String,items:List<String>,appearance:Boolean=false){
  parent.addView(kit.button(label+": "+settings.string(key,default),true){choose(label,items){settings.set(key,it);if(appearance)refreshAppearance()else{vm.refresh();toast("Saved")}}})
 }
 fun toggle(parent:LinearLayout,label:String,key:String,default:Boolean=false,appearance:Boolean=false,after:(Boolean)->Unit={}){
  parent.addView(MaterialSwitch(this).apply{text=label;setTextColor(kit.p.text);minHeight=kit.dp(48);isChecked=settings.bool(key,default);setOnCheckedChangeListener{_,checked->settings.set(key,checked);after(checked);if(appearance)refreshAppearance()}})
 }
 val appearance=group("Appearance")
 choice(appearance,"Theme","theme","System",listOf("System","Light","Dark","AMOLED"),true)
 choice(appearance,"Accent","accent","Mint",listOf("Mint","Blue","Amber","Rose"),true)
 toggle(appearance,"Use Android dynamic color","dynamic",false,true)
 appearance.addView(kit.button("Interface font: ${settings.int("fontPercent",100)}%",true){choose("Font scale",listOf("90","100","110","120")){settings.set("fontPercent",it.toInt());refreshAppearance()}})
 appearance.addView(kit.button("Chat text: ${settings.int("chatSize",16)} sp",true){choose("Chat text size",listOf("14","16","18","20","22")){settings.set("chatSize",it.toInt());refreshAppearance()}})
 appearance.addView(kit.button("Message spacing: ${settings.int("spacing",10)} dp",true){choose("Spacing",listOf("6","10","16","22")){settings.set("spacing",it.toInt());refreshAppearance()}})
 toggle(appearance,"Rounded corners","rounded",true,true)
 choice(appearance,"Animation level","animations","Reduced",listOf("Off","Reduced","Full"),true)
 appearance.addView(kit.button("Bubble radius",true){choose("Radius",listOf("4","12","20","28")){settings.set("bubbleRadius",it.toInt());refreshAppearance()}})
 choice(appearance,"Model cards","modelDensity","Comfortable",listOf("Compact","Comfortable"),true)
 appearance.addView(kit.button("Reset appearance",true){confirm("Reset appearance?","Your chats and models are kept."){listOf("theme","accent","dynamic","fontPercent","chatSize","spacing","rounded","animations","bubbleRadius","modelDensity").forEach{settings.remove(it)};refreshAppearance()}})
 val chat=group("Chat")
 toggle(chat,"Show timestamps","timestamps",true)
 toggle(chat,"Format Markdown emphasis and code","markdown",true)
 toggle(chat,"Highlight code keywords","highlight",true)
 toggle(chat,"Haptic feedback","haptic",false)
 toggle(chat,"Stream replies as they arrive","streaming",true)
 toggle(chat,"Follow new replies automatically","autoScroll",true)
 toggle(chat,"Show response statistics","stats",true)
 toggle(chat,"Live resource monitor","resourceMonitor",false)
 val generation=group("Generation")
 generation.addView(kit.label("Profiles change CPU thread count: Battery Saver 2, Balanced 4, Performance up to 6. Thermal checks may block new work while the phone is hot.",13,secondary=true))
 choice(generation,"Performance profile","profile","Balanced",listOf("Battery Saver","Balanced","Performance"))
 generation.addView(kit.button("Default text model",true){val models=vm.graph.models.value.filter{it.kind=="text"};choose("Default text model",models.map{it.name}){name->settings.set("defaultModel",models.first{it.name==name}.id);toast("Saved")}})
 generation.addView(kit.button("Default image model",true){val models=vm.graph.models.value.filter{it.kind=="image"};choose("Default image model",models.map{it.name}){name->settings.set("defaultImage",models.first{it.name==name}.id);toast("Saved")}})
 generation.addView(kit.button("Text generation parameters",true){generationEditor(null)})
 generation.addView(kit.button("Manage assistant presets",true){presetManager()})
 val downloads=group("Downloads & privacy")
 toggle(downloads,"Wi-Fi only for model downloads","wifiOnly",true)
 toggle(downloads,"Offline-only mode","offline",false,after={if(it){vm.download("pauseAll");vm.graph.updates.cancelDownload()};com.example.llama.updates.UpdateManager.schedule(vm.graph)})
 downloads.addView(kit.label("Offline-only mode blocks app-initiated downloads and external model websites. Core inference never sends prompts to a server. Sharing is always an explicit action. No analytics or crash-reporting SDK is installed.",13,secondary=true))
 downloads.addView(kit.button("Clear all conversations",true){confirm("Delete all conversations?","Your model files and generated images will remain."){vm.clearHistory()}})
 downloads.addView(kit.button("Clear generated images",true){confirm("Delete all generated images?","This cannot be undone."){vm.clearMedia()}})
 val storage=group("Storage")
 storage.addView(kit.label("Models and private data use app-private storage. Android removes these when the app is uninstalled. Model relocation to shared storage is not enabled; use import and explicit exports.",13,secondary=true))
 val sizes=kit.label("Calculating storage...",14);storage.addView(sizes)
 lifecycleScope.launch{try{val usage=vm.graph.storage();sizes.text=usage.entries.joinToString("\n"){it.key+" — "+bytesLabel(it.value)}+"\nAvailable — "+bytesLabel(Hardware.detect(this@buildSettings).storage)}catch(e:Exception){sizes.text="Storage information unavailable."}}
 storage.addView(kit.button("Find large model files",true){vm.navigate("Models")})
 storage.addView(kit.button("Manage generated media",true){vm.navigate("History")})
 storage.addView(kit.button("Clear share/export cache",true){confirm("Clear cache?","Only temporary export copies are removed. Models and history are kept."){vm.clearCache();toast("Cache cleanup requested")}})
 storage.addView(kit.button("Unload text model from RAM",true){vm.unload()})
 val advanced=group("Device & runtime")
 val h=Hardware.detect(this)
 advanced.addView(kit.label("${h.device}\nAndroid ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})\nCPU: ${h.abi} · ${h.cores} logical cores\nRAM: ${bytesLabel(h.availableRam)} available / ${bytesLabel(h.totalRam)} total\nThermal status: ${h.thermal} (0 = normal)\nText: llama.cpp CPU with ARM optimizations\nImage: stable-diffusion.cpp CPU (experimental)\nGPU/NPU acceleration: not enabled\nPocket AI 2.1",13,secondary=true))
 advanced.addView(kit.button("Privacy & diagnostic information",true){MaterialAlertDialogBuilder(this).setTitle("Diagnostics").setMessage("Native prompt/output logging is disabled. Errors appear locally; they are not uploaded.\n\nText backend: bd4f514\nImage backend: 88411ef\n\nThis build needs testing on the physical iQOO phone. CPU image generation is experimental; no video performance claims are made.").setPositiveButton("Close",null).show()})
 advanced.addView(kit.button("Open-source licenses",true){choose("Licenses",listOf("Markwon","llama.cpp","Stable Diffusion model","stable-diffusion.cpp","KleidiAI","Android NDK")){title->val file=when(title){"Markwon"->"MARKWON-LICENSE.txt";"llama.cpp"->"LLAMA-LICENSE.txt";"Stable Diffusion model"->"SD-MODEL-LICENSE.txt";"stable-diffusion.cpp"->"SD-RUNTIME-LICENSE.txt";"KleidiAI"->"KLEIDIAI-LICENSES.txt";else->"NDK-NOTICE.txt"};lifecycleScope.launch{val text=withContext(Dispatchers.IO){runCatching{assets.open(file).bufferedReader().use{it.readText()}}.getOrDefault("License file unavailable.")};MaterialAlertDialogBuilder(this@buildSettings).setTitle(title).setMessage(text).setPositiveButton("Close",null).show()}}})
}
fun MainActivity.presetManager(){
 val entries=vm.state.value.presets
 choose("Assistant presets",listOf("Create new preset")+entries.map{it.name}){name->
  if(name=="Create new preset")generationEditor(Preset(name="My preset",options=vm.graph.settings.options))
  else entries.firstOrNull{it.name==name}?.let{p->choose(p.name,listOf("Apply","Edit","Duplicate","Delete")){when(it){"Apply"->vm.applyPreset(p);"Edit"->generationEditor(p);"Duplicate"->generationEditor(p.copy(id=newId(),name=p.name+" copy"));"Delete"->confirm("Delete preset?","Existing conversations are kept."){vm.deletePreset(p)}}}}
 }
}
fun MainActivity.generationEditor(preset:Preset?){
 val options=preset?.options?:vm.graph.settings.options
 val content=kit.column().apply{setPadding(kit.dp(20),0,kit.dp(20),0)}
 val name=if(preset!=null)kit.input("Preset name",preset.name).also{content.addView(it)}else null
 val icon=if(preset!=null)kit.input("Icon",preset.icon).also{content.addView(it)}else null
 val description=if(preset!=null)kit.input("Description",preset.description,true).also{content.addView(it)}else null
 var preferred=preset?.modelId.orEmpty()
 if(preset!=null)content.addView(kit.button("Preferred model",true){val models=vm.graph.models.value.filter{it.kind=="text"};choose("Preferred model",listOf("Use current model")+models.map{it.name}){name->preferred=models.firstOrNull{it.name==name}?.id.orEmpty()}})
 val temp=number(content,"Temperature (0–2)",options.temperature.toString())
 val topP=number(content,"Top P (0.01–1)",options.topP.toString())
 val topK=number(content,"Top K (1–200)",options.topK.toString())
 val max=number(content,"Maximum output tokens (16–2048)",options.maximum.toString())
 val context=number(content,"Context tokens (1024–8192)",options.context.toString())
 val repetition=number(content,"Repetition penalty (1–2)",options.repetition.toString())
 val seed=number(content,"Seed (-1 = random)",options.seed.toString())
 content.addView(kit.label("System prompt",13));val system=kit.input("Assistant instructions",options.system,true);content.addView(system)
 content.addView(kit.label("Stop sequences (one per line)",13));val stops=kit.input("Optional stop sequences",options.stops,true);content.addView(stops)
 val dialog=MaterialAlertDialogBuilder(this).setTitle(if(preset==null)"Generation parameters" else "Assistant preset").setView(kit.scroll(content)).setNegativeButton("Cancel",null).setPositiveButton("Save",null).create()
 dialog.setOnShowListener{dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener{
  try{
   val o=GenerationOptions(temp.text.toString().toFloat(),topP.text.toString().toFloat(),topK.text.toString().toInt(),max.text.toString().toInt(),context.text.toString().toInt(),repetition.text.toString().toFloat(),seed.text.toString().toLong(),system.text.toString(),stops.text.toString()).validate()
   if(preset==null)vm.graph.settings.options=o else{check(!name?.text.isNullOrBlank()){"Give this preset a name."};vm.savePreset(preset.copy(name=name!!.text.toString().take(60),options=o,icon=icon?.text.toString().take(8),description=description?.text.toString().take(2000),modelId=preferred))}
   dialog.dismiss();toast("Saved")
  }catch(e:Exception){toast(e.message?:"Check the values and try again.")}
 }}
 dialog.show()
}
