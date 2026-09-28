package com.example.llama

import android.content.*
import android.widget.*
import android.view.View
import android.os.BatteryManager
import androidx.lifecycle.lifecycleScope
import com.example.llama.core.*
import com.example.llama.data.*
import com.example.llama.models.*
import com.example.llama.updates.UpdateManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch
import kotlinx.coroutines.*
import org.json.JSONObject
import java.util.Calendar

fun <T>MainActivity.io(label:String,block:suspend()->T,done:(T)->Unit={}){
 toast(label)
 lifecycleScope.launch{try{val result=withContext(Dispatchers.IO){block()};done(result)}catch(e:CancellationException){throw e}catch(e:Exception){vm.error(e.message?:"The operation could not finish.")}}
}
fun MainActivity.buildHome(body:LinearLayout){
 val h=Hardware.detect(this);val g=vm.graph
 val greeting=when(Calendar.getInstance().get(Calendar.HOUR_OF_DAY)){in 5..11->"Good morning";in 12..17->"Good afternoon";else->"Good evening"}
 kit.section(body,greeting,"What would you like to do?")
 kit.addButtons(body,kit.button("Chat with AI"){vm.newChat()},kit.button("Create image",true){vm.createMode="Image";vm.navigate("Create")})
 kit.addButtons(body,kit.button("Assistants",true){vm.navigate("Assistants")},kit.button("Prompt library",true){vm.navigate("Prompts")},kit.button("Search",true){globalSearch()})
 body.addView(kit.button("Edit Home",true){editHome()})
 val sections=g.settings.string("homeSections","Recent chats,Quick actions,Recommended models,Storage").split(",")
 for(section in sections){
  val card=kit.card()
  when(section){
   "Recent chats"->{kit.section(card,"Continue chatting");val chats=vm.state.value.conversations.filter{!it.archived}.take(3);if(chats.isEmpty())card.addView(kit.label("Your recent conversations will appear here.",14,secondary=true));chats.forEach{c->card.addView(kit.button(c.title,true){vm.openChat(c)})}}
   "Quick actions"->{kit.section(card,"Quick actions");io("Loading shortcuts",{g.library.prompts().filter{it.favorite}.ifEmpty{g.library.prompts().take(4)}}){list->list.take(6).forEach{p->card.addView(kit.button(p.name,true){usePrompt(p)})}}}
   "Recommended models"->{kit.section(card,"Recommended for available memory","Estimates, not phone benchmarks.");Recommendations.select(g.models.value,h,g.settings.options.context).take(3).forEach{(label,m)->card.addView(kit.button(label+" · "+m.name,true){modelDetails(m)})}}
   "Favorite models"->{kit.section(card,"Your favorite models");g.models.value.filter{g.settings.bool("favorite."+it.id)}.take(5).forEach{m->card.addView(kit.button(m.name,true){modelDetails(m)})}}
   "Storage"->{kit.section(card,"Storage");val text=kit.label("Calculating...",14);card.addView(text);lifecycleScope.launch{val usage=g.storage();text.text=usage.entries.joinToString("\n"){it.key+": "+bytesLabel(it.value)}+"\nAvailable: "+bytesLabel(h.storage)}}
   "Downloads"->{kit.section(card,"Downloads");card.addView(kit.label(g.downloads.value.count{it.state in setOf("queued","downloading","verifying")}.toString()+" active or queued",14));card.addView(kit.button("Manage downloads",true){vm.navigate("Downloads")})}
   "Recent images"->{kit.section(card,"Recent creations");vm.state.value.media.take(3).forEach{m->card.addView(kit.button(m.prompt.take(70),true){showImage(m)})}}
   "Performance"->{kit.section(card,"Device resources");card.addView(kit.label(bytesLabel(h.availableRam)+" / "+bytesLabel(h.totalRam)+" available RAM\nCPU only · "+h.cores+" cores\nThermal status: "+h.thermal,14));card.addView(kit.label("GPU/NPU acceleration is not enabled.",12,secondary=true))}
   else->continue
  }
  body.addView(card)
 }
 val offline=kit.card();kit.section(offline,if(g.settings.offline)"Offline-only mode" else "Private by default")
 offline.addView(kit.label("Installed text models: "+g.models.value.count{it.kind=="text"&&g.installed(it)}+"\nInstalled image models: "+g.models.value.count{it.kind=="image"&&g.installed(it)}+"\nChat and images run locally. Downloads, discovery and update checks require internet.",14));body.addView(offline)
}
fun MainActivity.editHome(){
 val all=listOf("Recent chats","Quick actions","Recommended models","Favorite models","Storage","Downloads","Recent images","Performance")
 val current=vm.graph.settings.string("homeSections","Recent chats,Quick actions,Recommended models,Storage").split(",").filter{it in all}.toMutableList()
 choose("Edit Home",listOf("Show / hide sections","Move section to top","Reset Home")){action->
  when(action){
   "Show / hide sections"->MaterialAlertDialogBuilder(this).setTitle("Home sections").setMultiChoiceItems(all.toTypedArray(),all.map{it in current}.toBooleanArray()){_,i,on->if(on&&!current.contains(all[i]))current.add(all[i])else if(!on)current.remove(all[i])}.setPositiveButton("Save"){_,_->vm.graph.settings.set("homeSections",current.joinToString(","));buildPage("Home")}.setNegativeButton("Cancel",null).show()
   "Move section to top"->choose("Move to top",current){current.remove(it);current.add(0,it);vm.graph.settings.set("homeSections",current.joinToString(","));buildPage("Home")}
   else->{vm.graph.settings.remove("homeSections");buildPage("Home")}
  }
 }
}
fun MainActivity.buildPrompts(body:LinearLayout){
 kit.section(body,"Prompt library","Reusable templates and custom quick actions. Favorite prompts appear on Home.")
 body.addView(kit.button("Create prompt"){promptEditor(null)})
 val search=kit.input("Search prompts or categories");body.addView(search);val rows=kit.column();body.addView(rows)
 io("Opening prompt library",{vm.graph.library.prompts()}){prompts->
  fun render(){rows.removeAllViews();prompts.filter{(it.name+" "+it.category+" "+it.text).contains(search.text.toString(),true)}.take(100).forEach{p->
   val card=kit.card();card.addView(kit.label((if(p.favorite)"★ " else "")+p.name,18,true));card.addView(kit.label(p.category,12,secondary=true));card.addView(kit.label(p.text.take(200),14))
   kit.addButtons(card,kit.button("Use"){usePrompt(p)},kit.button("Actions",true){choose(p.name,listOf("Edit","Favorite / unfavorite","Duplicate","Delete")){action->when(action){
    "Edit"->promptEditor(p)
    "Duplicate"->promptEditor(p.copy(id=newId(),name=p.name+" copy"))
    "Favorite / unfavorite"->io("Saving",{vm.graph.db.put("prompt",p.id,p.copy(favorite=!p.favorite).json())}){buildPage("Prompts")}
    "Delete"->confirm("Delete prompt?","This removes only the selected template."){io("Deleting",{vm.graph.db.remove("prompt",p.id)}){buildPage("Prompts")}}
   }}});rows.addView(card)
  }}
  search.addTextChangedListener(object:android.text.TextWatcher{override fun beforeTextChanged(s:CharSequence?,a:Int,c:Int,f:Int){};override fun onTextChanged(s:CharSequence?,a:Int,b:Int,c:Int){render()};override fun afterTextChanged(e:android.text.Editable?){} });render()
 }
}
fun MainActivity.promptEditor(p:PromptTemplate?){
 val body=kit.column().apply{setPadding(kit.dp(20),0,kit.dp(20),0)}
 val name=kit.input("Name",p?.name.orEmpty());val category=kit.input("Category",p?.category?:"Custom");val text=kit.input("Template, e.g. Explain {topic}",p?.text.orEmpty(),true)
 listOf(name,category,text).forEach{body.addView(it)}
 val dialog=MaterialAlertDialogBuilder(this).setTitle("Prompt template").setView(kit.scroll(body)).setNegativeButton("Cancel",null).setPositiveButton("Save",null).create()
 dialog.setOnShowListener{dialog.getButton(-1).setOnClickListener{
  if(name.text.isBlank()||text.text.isBlank()){toast("Enter a name and prompt.");return@setOnClickListener}
  val item=PromptTemplate(p?.id?:newId(),name.text.toString().take(100),category.text.toString().take(60),text.text.toString().take(16000),p?.favorite?:false)
  io("Saving prompt",{vm.graph.db.put("prompt",item.id,item.json())}){dialog.dismiss();buildPage("Prompts")}
 }};dialog.show()
}
fun MainActivity.usePrompt(p:PromptTemplate){
 val names=TemplateFields.names(p.text);val values=mutableMapOf<String,String>()
 fun collect(index:Int){
  if(index<names.size){inputDialog(names[index],"",true){values[names[index]]=it;collect(index+1)};return}
  if(vm.state.value.busy){vm.error("Stop generation before using a prompt.");return}
  vm.newChat();vm.draft=TemplateFields.fill(p.text,values);vm.navigate("Chat");buildPage("Chat")
 }
 collect(0)
}
fun MainActivity.buildAssistants(body:LinearLayout){
 kit.section(body,"Your assistants","Personal instructions, sampling settings and preferred models.")
 body.addView(kit.button("Create assistant"){generationEditor(Preset(name="My assistant",options=GenerationOptions()))})
 vm.state.value.presets.forEach{p->val card=kit.card();card.addView(kit.label(p.icon+" "+p.name,19,true));card.addView(kit.label(p.description.ifBlank{p.options.system}.take(220),14,secondary=true))
  kit.addButtons(card,kit.button("Chat"){vm.newChat();vm.applyPreset(p);vm.navigate("Chat")},kit.button("Edit",true){generationEditor(p)},kit.button("More",true){choose(p.name,listOf("Duplicate","Delete")){if(it=="Duplicate")generationEditor(p.copy(id=newId(),name=p.name+" copy"))else confirm("Delete assistant?","Conversations are kept."){vm.deletePreset(p);buildPage("Assistants")}}});body.addView(card)}
}
fun MainActivity.globalSearch(){
 inputDialog("Search chats, models and prompts",""){query->
  io("Searching",{val results=mutableListOf<Pair<String,()->Unit>>()
   vm.graph.db.searchChats(query,15).forEach{c->results+=("Chat · "+c.title) to {vm.openChat(c)}}
   vm.graph.models.value.filter{(it.name+" "+it.purposes).contains(query,true)}.forEach{m->results+=("Model · "+m.name) to {modelDetails(m)}}
   vm.graph.library.prompts().filter{(it.name+" "+it.text).contains(query,true)}.take(15).forEach{p->results+=("Prompt · "+p.name) to {usePrompt(p)}}
   vm.state.value.media.filter{it.prompt.contains(query,true)}.take(15).forEach{m->results+=("Image · "+m.prompt.take(60)) to {showImage(m)}}
   listOf("Appearance","Chat","Generation","Downloads & privacy","Storage","Device & runtime").filter{it.contains(query,true)}.forEach{category->results+=("Settings · "+category) to {vm.graph.settings.set("settingsCategory",category);vm.navigate("Settings")}}
   results
  }){results->choose("Search results",results.map{it.first}){name->results.first{it.first==name}.second()}}
 }
}
fun MainActivity.discoverModels(){
 choose("Model discovery · Internet required",listOf("Search Hugging Face","Paste repository URL","Check model updates")){action->
  if(action=="Check model updates"){vm.navigate("Model updates");return@choose}
  inputDialog(if(action=="Search Hugging Face")"Search GGUF models" else "Hugging Face repository",""){value->
   val hub=ModelHub(vm.graph)
   fun inspect(repo:String){io("Inspecting format, architecture, size and integrity metadata",{hub.inspect(repo)}){models->
    choose("Single-file GGUF candidates",models.map{it.name+" · "+bytesLabel(it.bytes)}){name->
     val m=models.first{it.name+" · "+bytesLabel(it.bytes)==name};val compat=ResourcePolicy.compatibility(m,Hardware.detect(this))
     confirm("Add experimental model?",m.name+"\n"+bytesLabel(m.bytes)+" · "+m.quantization+"\nEstimated RAM: "+bytesLabel(ResourcePolicy.required(m,vm.graph.settings.options.context))+"\nLicense: "+m.license+"\n"+compat.reason+"\nRepository metadata is not a phone benchmark. No weights are downloaded yet."){io("Adding model",{hub.add(m)}){vm.navigate("Models");buildPage("Models")}}
    }
   }}
   if(action=="Search Hugging Face")io("Searching Hugging Face",{hub.search(value)}){repos->choose("Repositories",repos){inspect(it)}}else inspect(value)
  }
 }
}
fun MainActivity.buildModelUpdates(body:LinearLayout){
 kit.section(body,"AI model updates","Text model revisions only. Old files are retained; downloads always require confirmation.")
 val hub=ModelHub(vm.graph)
 val automatic=MaterialSwitch(this).apply{text="Check model revisions daily (metadata only)";setTextColor(kit.p.text);isChecked=vm.graph.settings.bool("modelAuto");setOnCheckedChangeListener{_,on->vm.graph.settings.set("modelAuto",on);UpdateManager.schedule(vm.graph)}};body.addView(automatic)
 val rows=kit.column()
 fun render(list:List<ModelSpec>){
  rows.removeAllViews()
  if(list.isEmpty())rows.addView(kit.label("No pending revisions in the last check. Check online to refresh.",15))
  list.forEach{m->rows.addView(kit.button(m.name+" · "+bytesLabel(m.bytes),true){
   confirm("Keep both versions?","New download: "+bytesLabel(m.bytes)+"\nAdditional storage is required; the old model is retained.\nRevision "+m.revision.take(12)+"\nReview the source for changes. Compatibility and available space are checked before download."){
    io("Adding new revision",{hub.add(m)}){registered->downloadDialog(registered);render(list.filter{it.sha256!=registered.sha256})}
   }
  })}
 }
 body.addView(kit.button("Check now · Internet required"){io("Checking model revisions",{hub.checkUpdates()}){render(it)}})
 body.addView(rows)
 io("Reading saved model checks",{hub.cachedUpdates()}){render(it)}
}
fun MainActivity.buildUpdates(body:LinearLayout){
 val g=vm.graph;val u=g.updates
 kit.section(body,"Pocket AI updates","Trusted source: github.com/Tann-Menghong/Pocket-AI")
 body.addView(kit.label("Installed: "+u.current.versionName+" · build "+u.current.longVersionCode,15,true))
 fun toggle(label:String,key:String,default:Boolean=false){body.addView(MaterialSwitch(this).apply{text=label;setTextColor(kit.p.text);isChecked=g.settings.bool(key,default);setOnCheckedChangeListener{_,on->g.settings.set(key,on);UpdateManager.schedule(g)}})}
 toggle("Automatic daily checks (internet required)","updateAuto")
 toggle("Notify when an update is available","updateNotify",true)
 toggle("Automatically download updates on Wi-Fi","updateAutoDownload")
 toggle("Include preview / beta releases","updateBeta",true)
 body.addView(kit.label("Android schedules checks when resources allow. Automatic downloads require automatic checks. Installation is never automatic. Offline-only mode blocks checks and cancels pending update downloads.",13,secondary=true))
 val status=kit.label(u.progress(),14);body.addView(status)
 body.addView(kit.button("Check now"){io("Checking GitHub releases",{u.check()}){buildPage("Updates")}})
 u.candidate()?.let{r->
  val card=kit.card();kit.section(card,r.title,bytesLabel(r.bytes)+" · "+if(r.prerelease)"Preview" else "Stable")
  card.addView(kit.button("What's new",true){MaterialAlertDialogBuilder(this).setTitle(r.title).setMessage(r.notes).setPositiveButton("Close",null).show()})
  kit.addButtons(card,kit.button("Download"){try{u.enqueue(r);status.text=u.progress()}catch(e:Exception){vm.error(e.message.orEmpty())}},kit.button("Verify",true){io("Verifying APK integrity and signer",{u.verify()}){status.text="Verified. Ready for installation."}})
  card.addView(kit.button("Install with Android"){lifecycleScope.launch{try{u.install(this@buildUpdates)}catch(e:Exception){vm.error(e.message?:"Could not open installer.")}}})
  card.addView(kit.button("Cancel / remove download",true){u.cancelDownload();status.text="Update download removed."});body.addView(card)
 }
 body.addView(kit.button("Refresh download status",true){status.text=u.progress()})
 body.addView(kit.button("Current version · What's new",true){MaterialAlertDialogBuilder(this).setTitle("Pocket AI "+u.current.versionName).setMessage("Expanded free-model catalog, size and memory filters, persistent sorting, multi-word search and stronger compatibility checks. Image generation remains experimental; video is unavailable.").setPositiveButton("Close",null).show()})
}
fun MainActivity.showImage(m:MediaItem){
 val image=ImageView(this).apply{adjustViewBounds=true;contentDescription=m.prompt}
 io("Opening image",{android.graphics.BitmapFactory.decodeFile(java.io.File(vm.graph.mediaDir,m.file).path)}){bitmap->
  image.setImageBitmap(bitmap)
  MaterialAlertDialogBuilder(this).setTitle(m.prompt.take(100)).setView(kit.scroll(image)).setMessage(m.width.toString()+" × "+m.height+" · "+m.steps+" steps · CFG "+m.cfg+"\nSeed "+m.seed+"\n"+m.model+"\nNegative: "+m.negative)
   .setPositiveButton("Share"){_,_->shareImage(m)}.setNeutralButton("Copy prompt"){_,_->(getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager).setPrimaryClip(ClipData.newPlainText("Image prompt",m.prompt))}.setNegativeButton("Close",null).show()
 }
}

fun MainActivity.freeModelGuide(){
 val choices=listOf("Qwen · multilingual and coding","SmolLM2 · compact English models","Model selection tips")
 choose("Free models · source links require internet",choices){choice->
  when(choice){
   choices[0]->openSource("https://huggingface.co/Qwen")
   choices[1]->openSource("https://huggingface.co/HuggingFaceTB")
   else->MaterialAlertDialogBuilder(this).setTitle("Choose a model").setMessage("Start small, then compare responses. The new curated text downloads use Apache-2.0 licenses and need no paid API. SmolLM2 focuses on English; Khmer quality is not validated. Q4, Q5, Q6 and Q8 are precision variants, not different assistants. More bits use more storage and RAM and do not guarantee better answers. Use Fits available RAM as an estimate, not a benchmark. All new choices remain experimental until qualified on your phone. Websites offering free cloud chat cannot automatically be used offline.").setPositiveButton("Close",null).show()
  }
 }
}
