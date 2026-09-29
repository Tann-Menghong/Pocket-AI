package com.example.llama

import android.Manifest
import android.content.*
import android.content.pm.PackageManager
import android.os.Bundle
import android.graphics.BitmapFactory
import android.view.*
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.FileProvider
import androidx.core.view.*
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.*
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.llama.core.*
import com.example.llama.models.ModelBrowse
import com.example.llama.data.*
import com.example.llama.ui.*
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.color.DynamicColors
import com.google.android.material.appbar.MaterialToolbar
import kotlinx.coroutines.*
import java.io.File
import java.text.DateFormat

class MainActivity:AppCompatActivity(){
 lateinit var vm:PlatformViewModel
 lateinit var kit:UiKit
 private lateinit var page:android.widget.FrameLayout
 private lateinit var toolbar:MaterialToolbar
 private lateinit var nav:BottomNavigationView
 private val markdown by lazy { io.noties.markwon.Markwon.builder(this).usePlugin(io.noties.markwon.ext.tables.TablePlugin.create(this)).usePlugin(object:io.noties.markwon.AbstractMarkwonPlugin(){override fun configureConfiguration(builder:io.noties.markwon.MarkwonConfiguration.Builder){builder.linkResolver{_,link->if(link.startsWith("https://"))openSource(link)};builder.syntaxHighlight{_,code->val out=android.text.SpannableStringBuilder(code);if(vm.graph.settings.bool("highlight",true))Regex("\\b(fun|val|var|class|return|if|else|for|while|def|import|const|let|public|private|true|false|null)\\b").findAll(code).forEach{m->out.setSpan(android.text.style.ForegroundColorSpan(kit.p.accent),m.range.first,m.range.last+1,android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)};out}}}).build() }
 private var shown=""
 private var ready=false
 private var prompt:EditText?=null
 private var chatTitle:TextView?=null
 private var chatStatus:TextView?=null
 private var chatModel:Button?=null
 private var sendButton:Button?=null
 private var loadButton:Button?=null
 private var messageList:RecyclerView?=null
 private var adapter:ChatAdapter?=null
 private var modelRows:LinearLayout?=null
 private var downloadRows:LinearLayout?=null
 private var historyRows:LinearLayout?=null
 private var createStatus:TextView?=null
 private var createProgress:ProgressBar?=null
 private var createButton:Button?=null
 private var pendingDownload:String?=null
 private var lastError=""
 private var query=""
 private var modelFilter="All"
 private var historyFilter="All"
 private var historyLimit=50
 private var historySearchJob:Job?=null
 private var historySearchResult:Pair<String,Set<String>>?=null
 private var favoritesOnly=false
 private var downloadFilter="All"
 private var renderedLibraryRevision=-1
 private val backupExport=registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")){uri->if(uri!=null)io("Writing local backup",{contentResolver.openOutputStream(uri)?.use{vm.graph.library.export(it)}?:error("Cannot write backup.")}){toast("Backup saved")}}
 private val backupImport=registerForActivityResult(ActivityResultContracts.OpenDocument()){uri->if(uri!=null)io("Inspecting backup",{contentResolver.openInputStream(uri)?.use{vm.graph.library.inspect(it)}?:error("Cannot read backup.")}){root->confirm("Restore local backup?","Conversations, assistants and prompts are added as new records. Supported appearance/AI preferences are restored. Existing data and model files are kept."){io("Restoring backup",{vm.graph.library.restore(root)}){count->vm.refresh();toast("Restored "+count+" entries");recreate()}}}}
 fun exportBackup(){if(vm.state.value.busy){vm.error("Stop generation first.");return};confirm("Export private library?","This is a readable JSON backup of chats, assistants, prompts and settings. Store it somewhere you trust. Model weights and generated media are excluded."){backupExport.launch("Pocket-AI-backup.json")}}
 fun restoreBackup(){if(vm.state.value.busy){vm.error("Stop generation first.");return};backupImport.launch(arrayOf("application/json","text/plain","application/octet-stream"))}
 private val importLauncher=registerForActivityResult(ActivityResultContracts.OpenDocument()){it?.let{uri->vm.importModel(uri)}}
 private val exportLauncher=registerForActivityResult(ActivityResultContracts.CreateDocument("text/markdown")){uri->if(uri!=null){val text=vm.exportText(vm.state.value.chat);lifecycleScope.launch(Dispatchers.IO){try{contentResolver.openOutputStream(uri)?.use{it.write(text.toByteArray())}?:error("No output stream");withContext(Dispatchers.Main){toast("Conversation exported")}}catch(e:Exception){vm.error("Could not export: "+e.message)}}}}
 private val notificationPermission=registerForActivityResult(ActivityResultContracts.RequestPermission()){pendingDownload?.let{vm.download("enqueue",it)};pendingDownload=null}
 override fun onCreate(savedInstanceState:Bundle?){
  val prefs=Settings(this)
  AppCompatDelegate.setDefaultNightMode(when(prefs.string("theme","System")){"Light"->AppCompatDelegate.MODE_NIGHT_NO;"Dark","AMOLED"->AppCompatDelegate.MODE_NIGHT_YES;else->AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM})
  if(prefs.bool("dynamic"))DynamicColors.applyToActivityIfAvailable(this)
  super.onCreate(savedInstanceState)
  vm=ViewModelProvider(this)[PlatformViewModel::class.java];kit=UiKit(this,vm.graph.settings)
  WindowCompat.getInsetsController(window,window.decorView).apply{isAppearanceLightStatusBars=!kit.p.dark;isAppearanceLightNavigationBars=!kit.p.dark}
  val root=kit.column().apply{setBackgroundColor(kit.p.background)}
  toolbar=MaterialToolbar(this).apply{title="Pocket AI";setTitleTextColor(kit.p.text);subtitle="Private AI, on your phone";setSubtitleTextColor(kit.p.secondary)}
  toolbar.menu.add("Downloads").setOnMenuItemClickListener{vm.navigate("Downloads");true}
  toolbar.menu.add("Settings").apply{setIcon(R.drawable.nav_settings);setShowAsAction(android.view.MenuItem.SHOW_AS_ACTION_ALWAYS);setOnMenuItemClickListener{vm.navigate("Settings");true}}
  toolbar.menu.add("Search").setOnMenuItemClickListener{globalSearch();true}
  root.addView(toolbar)
  page=FrameLayout(this);root.addView(page,LinearLayout.LayoutParams(-1,0,1f))
  nav=BottomNavigationView(this).apply{
   setBackgroundColor(kit.p.surface)
   itemIconTintList=android.content.res.ColorStateList(arrayOf(intArrayOf(android.R.attr.state_checked),intArrayOf()),intArrayOf(kit.p.accent,kit.p.secondary))
   itemTextColor=itemIconTintList
   labelVisibilityMode=com.google.android.material.navigation.NavigationBarView.LABEL_VISIBILITY_LABELED
   listOf("Home","Chat","Create","Models","Library").forEachIndexed{i,t->menu.add(0,i+1,i,t).setIcon(when(i){0->R.drawable.nav_home;1->R.drawable.nav_chat;2->R.drawable.nav_create;3->R.drawable.nav_models;else->R.drawable.nav_library})}
   setOnItemSelectedListener{item->vm.navigate(listOf("Home","Chat","Create","Models","History")[item.itemId-1]);true}
  }
  root.addView(nav);setContentView(root)
  ViewCompat.setOnApplyWindowInsetsListener(root){v,i->val b=i.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime());v.setPadding(b.left,b.top,b.right,b.bottom);nav.visibility=if(i.isVisible(WindowInsetsCompat.Type.ime()))View.GONE else View.VISIBLE;i}
  onBackPressedDispatcher.addCallback(this,object:androidx.activity.OnBackPressedCallback(true){override fun handleOnBackPressed(){if(vm.state.value.screen!="Home")vm.navigate("Home")else{isEnabled=false;onBackPressedDispatcher.onBackPressed()}}})
  lifecycleScope.launch{repeatOnLifecycle(Lifecycle.State.STARTED){
   launch{vm.state.collect{render(it)}}
   launch{vm.graph.models.collect{if(shown=="Models")renderModels()}}
   launch{vm.graph.downloads.collect{if(shown=="Downloads")renderDownloads();if(shown=="Models")renderModels()}}
  }}
  intent.getStringExtra("screen")?.let{vm.navigate(it)}
 }
 override fun onNewIntent(intent:Intent){super.onNewIntent(intent);intent.getStringExtra("screen")?.let{vm.navigate(it)}}
 override fun onStop(){super.onStop();if(!isChangingConfigurations)vm.background()}
 private fun render(s:AppState){
  if(!s.ready){if(!ready){page.removeAllViews();page.addView(kit.label(if(s.error.isBlank())"Opening your private library..." else s.error,16))};return}
  if(shown!=s.screen||!ready){shown=s.screen;ready=true;buildPage(shown)}
  val navIndex=listOf("Home","Chat","Create","Models","History").indexOf(shown);if(navIndex>=0)nav.menu.findItem(navIndex+1).isChecked=true
  toolbar.subtitle=if(vm.graph.settings.offline)"Offline-only mode" else when(shown){"Chat"->"Local inference · No cloud chat";"Create"->"Make something, privately";else->"Your AI workspace"}
  chatTitle?.text=s.chat.title
  chatModel?.text=vm.graph.models.value.firstOrNull{it.id==s.chat.modelId}?.name?:"Choose model"
  chatModel?.isEnabled=!s.busy;loadButton?.isEnabled=!s.busy
  loadButton?.text=if(s.loaded==s.chat.modelId)"Unload" else "Load"
  chatStatus?.text=s.status
  sendButton?.text=if(s.busy)"Stop" else "Send"
  prompt?.isEnabled=!s.busy
  if(shown=="Chat"){
   val follow=vm.graph.settings.bool("autoScroll",true)&&messageList?.canScrollVertically(1)!=true
   adapter?.submit(s.chat.messages)
   if(follow&&s.chat.messages.isNotEmpty())messageList?.scrollToPosition(s.chat.messages.lastIndex)
  }
  if(shown=="History"&&renderedLibraryRevision!=s.revision){renderedLibraryRevision=s.revision;renderHistory();if(query.isNotBlank())searchHistory()}
  createStatus?.text=s.status
  createButton?.text=if(s.busy)"Stop" else "Generate image"
  createProgress?.apply{visibility=if(s.busy)View.VISIBLE else View.GONE;isIndeterminate=s.progress<0;progress=s.progress.coerceAtLeast(0)}
  if(s.error.isNotBlank()&&s.error!=lastError){lastError=s.error;MaterialAlertDialogBuilder(this).setTitle("Let's fix that").setMessage(s.error).setPositiveButton("OK"){_,_->lastError="";vm.dismissError()}.setOnCancelListener{lastError="";vm.dismissError()}.show()}
  if(!vm.graph.settings.bool("onboarded"))onboard()
 }
 fun buildPage(name:String){
  prompt?.let{vm.draft=it.text.toString()}
  prompt=null;chatTitle=null;chatModel=null;chatStatus=null;sendButton=null;loadButton=null;messageList=null;adapter=null;modelRows=null;downloadRows=null;historyRows=null;createStatus=null;createProgress=null;createButton=null
  page.removeAllViews()
  val duration=when(vm.graph.settings.string("animations","Reduced")){"Off"->0L;"Full"->160L;else->70L};if(duration>0){page.alpha=0f;page.animate().alpha(1f).setDuration(duration).start()}else page.alpha=1f
  if(name=="Chat"){buildChat();return}
  val body=kit.column().apply{setPadding(kit.dp(20),kit.dp(8),kit.dp(20),kit.dp(20))}
  page.addView(kit.scroll(body))
  when(name){"Home"->buildHome(body);"Prompts"->buildPrompts(body);"Assistants"->buildAssistants(body);"Updates"->buildUpdates(body);"Model updates"->buildModelUpdates(body);"Models"->buildModels(body);"Downloads"->buildDownloads(body);"History"->buildHistory(body);"Create"->buildCreate(body);"Settings"->buildSettings(body)}
 }
 private var onboardingVisible=false
 private fun onboard(){
  if(onboardingVisible)return;onboardingVisible=true
  val interests=mutableListOf("General Chat")
  val model=vm.graph.model("qwen-small")
  fun finish(){vm.graph.settings.set("onboarded",true);onboardingVisible=false}
  fun step(index:Int){
   val dialog=MaterialAlertDialogBuilder(this).setCancelable(false)
   when(index){
    0->dialog.setTitle("Welcome to Pocket AI").setMessage("Private AI on your phone. Choose models, create assistants and keep your conversations local.").setPositiveButton("Next"){_,_->step(1)}.setNegativeButton("Skip setup"){_,_->finish()}
    1->{val labels=arrayOf("General Chat","Coding","Study","Writing","Translation","Image Generation");dialog.setTitle("How will you use AI?").setMultiChoiceItems(labels,labels.map{it in interests}.toBooleanArray()){_,i,on->if(on)interests.add(labels[i])else interests.remove(labels[i])}.setPositiveButton("Next"){_,_->vm.graph.settings.set("interests",interests.distinct().joinToString(","));step(2)}}
    2->dialog.setTitle("Recommended starter").setMessage(model.name+" · "+bytesLabel(model.bytes)+"\nStart small to check speed and memory on your phone.\n"+ResourcePolicy.compatibility(model,Hardware.detect(this)).reason).setPositiveButton("Next"){_,_->step(3)}.setNegativeButton("Choose another"){_,_->finish();vm.navigate("Models")}
    else->dialog.setTitle("Your workspace is ready").setMessage("Download a model to enable local AI. Once installed, chat works without internet. Conversations stay on this device unless you export or share them. Image generation is experimental; video is unavailable.").setPositiveButton("Download starter"){_,_->finish();downloadDialog(model)}.setNegativeButton("Explore Home"){_,_->finish();vm.navigate("Home")}
   };dialog.show()
  };step(0)
 }
 private fun buildChat(){
  val root=kit.column().apply{setPadding(kit.dp(16),0,kit.dp(16),kit.dp(6))}
  val head=kit.row()
  chatTitle=kit.label(vm.state.value.chat.title,20,true).apply{maxLines=1;ellipsize=android.text.TextUtils.TruncateAt.END}
  head.addView(chatTitle,LinearLayout.LayoutParams(0,-2,1f));head.addView(kit.button("+",true){vm.newChat()})
  head.addView(kit.button("⋮",true){chatMenu()}.apply{contentDescription="Conversation actions";minWidth=kit.dp(48)})
  root.addView(head)
  val controls=kit.row()
  chatModel=kit.button("Choose model",true){val choices=vm.graph.models.value.filter{it.kind=="text"};choose("Text model",choices.map{it.name+if(vm.graph.installed(it))" · Installed" else " · Download first"}){label->vm.selectModel(choices.first{label.startsWith(it.name)}.id)}}
  loadButton=kit.button("Load",true){if(vm.state.value.loaded==vm.state.value.chat.modelId)vm.unload()else vm.loadModel()}
  controls.addView(chatModel,LinearLayout.LayoutParams(0,-2,1f));controls.addView(loadButton)
  root.addView(controls)
  chatStatus=kit.label(vm.state.value.status,12,secondary=true).apply{maxLines=3};root.addView(chatStatus)
  messageList=RecyclerView(this).apply{layoutManager=LinearLayoutManager(this@MainActivity).apply{stackFromEnd=true};itemAnimator?.changeDuration=when(vm.graph.settings.string("animations","Reduced")){"Off"->0L;"Full"->180L;else->50L};adapter=ChatAdapter().also{this@MainActivity.adapter=it}}
  root.addView(messageList,LinearLayout.LayoutParams(-1,0,1f))
  val compose=kit.row()
  prompt=kit.input("Message your local AI",vm.draft,true).apply{minLines=1;maxLines=5;filters=arrayOf(android.text.InputFilter.LengthFilter(16000));doAfterTextChanged{vm.draft=it.toString()}}
  compose.addView(prompt,LinearLayout.LayoutParams(0,-2,1f))
  sendButton=kit.button("Send"){if(vm.state.value.busy)vm.stop()else{val text=prompt?.text.toString();if(text.isNotBlank()){vm.send(text);prompt?.setText("")}}}
  compose.addView(sendButton);root.addView(compose)
  if(vm.graph.settings.bool("resourceMonitor")){val monitor=kit.label("",12,secondary=true);root.addView(monitor);lifecycleScope.launch{while(isActive){if(!monitor.isAttachedToWindow){delay(250);if(!monitor.isAttachedToWindow)break};val h=Hardware.detect(this@MainActivity);val battery=getSystemService(android.os.BatteryManager::class.java).getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY);monitor.text="RAM used: "+bytesLabel(h.totalRam-h.availableRam)+" / "+bytesLabel(h.totalRam)+" · Battery "+battery+"% · Thermal "+h.thermal;delay(2000)}}}
  page.addView(root)
 }
 private fun chatMenu(){choose("Conversation",listOf("Choose preset","Prompt library","Rename","Pin / unpin","Archive / restore","Regenerate last reply","Continue reply","Export Markdown","Share conversation","Clear messages","Delete conversation")){action->
  val c=vm.state.value.chat
  when(action){
   "Choose preset"->choose("Assistant preset",vm.state.value.presets.map{it.name}){name->vm.state.value.presets.firstOrNull{it.name==name}?.let{vm.applyPreset(it)}}
   "Prompt library"->vm.navigate("Prompts")
   "Archive / restore"->vm.archive(c)
   "Rename"->inputDialog("Rename conversation",c.title){vm.rename(c,it)}
   "Pin / unpin"->vm.pin(c)
   "Regenerate last reply"->vm.regenerate()
   "Continue reply"->vm.continueReply()
   "Export Markdown"->exportLauncher.launch("Pocket-AI-conversation.md")
   "Share conversation"->shareText(vm.exportText(c))
   "Clear messages"->confirm("Clear this conversation?","This removes its messages from local history."){vm.clearChat()}
   "Delete conversation"->confirm("Delete conversation?","This cannot be undone."){vm.deleteChat(c)}
  }
 }}
 private inner class ChatAdapter:RecyclerView.Adapter<ChatAdapter.Holder>(){
  private var lines:List<ChatMessage> = emptyList()
  inner class Holder(val card:LinearLayout,val role:TextView,val body:TextView,val meta:TextView):RecyclerView.ViewHolder(card)
  fun submit(next:List<ChatMessage>){if(lines==next)return;val old=lines;lines=next;if(old.size==next.size&&next.isNotEmpty()&&old.dropLast(1)==next.dropLast(1))notifyItemChanged(next.lastIndex)else notifyDataSetChanged()}
  override fun getItemCount()=if(lines.isEmpty())1 else lines.size
  override fun onCreateViewHolder(p:ViewGroup,t:Int):Holder{val card=kit.card();card.layoutParams=RecyclerView.LayoutParams(-1,-2).apply{bottomMargin=kit.dp(vm.graph.settings.int("spacing",10))};val role=kit.label("",12,true);val body=kit.label("",vm.graph.settings.int("chatSize",16));val meta=kit.label("",11,secondary=true);card.addView(role);card.addView(body);card.addView(meta);body.setTextIsSelectable(true);return Holder(card,role,body,meta)}
  override fun onBindViewHolder(h:Holder,pos:Int){
   if(lines.isEmpty()){h.role.text="A little space to think.";h.body.text="Ask a question, work through an idea, or write something new.\n\nChoose a model above, or visit Models to get started.";h.meta.text="Everything is processed on your device.";h.card.setOnClickListener{vm.navigate("Models")};h.card.setOnLongClickListener(null);return}
   val m=lines[pos];h.role.text=if(m.role=="user")"YOU" else "POCKET AI"
   if(m.state=="complete"&&vm.graph.settings.bool("markdown",true))markdown.setMarkdown(h.body,m.text)else h.body.text=m.text.ifBlank{if(m.state=="generating")"Thinking..." else "No response. Long press to retry."}
   h.meta.text=listOf(if(vm.graph.settings.bool("timestamps",true))DateFormat.getTimeInstance(DateFormat.SHORT).format(m.time)else "",if(vm.graph.settings.bool("stats",true))m.stats else "",if(m.state!="complete")m.state else "").filter{it.isNotBlank()}.joinToString(" · ")
   h.card.setOnClickListener(null)
   h.card.setOnLongClickListener{choose("Message actions",if(m.role=="user")listOf("Copy","Edit and resend")else listOf("Copy","Retry / regenerate","Continue")){action->
    when(action){"Copy"->{(getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager).setPrimaryClip(ClipData.newPlainText("Pocket AI",m.text));toast("Copied")};"Edit and resend"->inputDialog("Edit prompt",m.text,true){vm.send(it,pos)};"Retry / regenerate"->vm.regenerate();"Continue"->vm.continueReply()}
   };true}
   h.card.contentDescription=(if(m.role=="user")"Your message" else "AI response")+". Long press for actions."
  }
 }
 private fun buildModels(body:LinearLayout){
  kit.section(body,"Your model library","Only compatible formats. Clear sizes. No model weights inside the app.")
  val h=Hardware.detect(this);body.addView(kit.label("${h.device} · ${bytesLabel(h.totalRam)} RAM\n${bytesLabel(h.availableRam)} free RAM · ${bytesLabel(h.storage)} free storage",13,secondary=true))
  val search=kit.input("Search name, task, creator or quantization",query);search.doAfterTextChanged{query=it.toString();renderModels()};body.addView(search)
  kit.addButtons(body,kit.button("Filter",true){choose("Show models",listOf("All","Installed","Favorites","Fits available RAM","Under 1 GB","Compatible","Experimental","Chat","Coding","Reasoning","Writing","Translation","Multilingual","Small & Fast","Image","Video")){modelFilter=it;renderModels()}},kit.button("Import GGUF",true){importLauncher.launch(arrayOf("*/*"))})
  kit.addButtons(body,kit.button("Discover online",true){discoverModels()},kit.button("Model updates",true){vm.navigate("Model updates")})
  kit.addButtons(body,kit.button("Sort",true){choose("Sort models",listOf("Catalog order","Smallest download","Lowest RAM","Name","Installed first")){vm.graph.settings.set("modelSort",it);renderModels()}},kit.button("Free model guide",true){freeModelGuide()})
  body.addView(kit.label("Fast: smallest supported text model. Balanced: mid-size. Quality: larger model with higher estimated RAM use. These are relative recommendations, not phone benchmarks.",12,secondary=true))
  modelRows=kit.column();body.addView(modelRows);renderModels()
 }
 private fun renderModels(){
  val host=modelRows?:return;host.removeAllViews()
  val h=Hardware.detect(this)
  val matching=vm.graph.models.value.filter{m->ModelBrowse.search(m,query)&&when(modelFilter){
   "All"->true
   "Favorites"->vm.graph.settings.bool("favorite."+m.id)
   "Fits available RAM"->ModelBrowse.ready(m,h,vm.graph.settings.options.context)
   "Under 1 GB"->m.bytes<1_000_000_000L
   "Compatible"->ResourcePolicy.compatibility(m,h).allowed
   "Experimental"->m.experimental
   "Chat"->m.kind=="text"
   "Installed"->vm.graph.installed(m)
   "Image"->m.kind=="image"
   "Video"->m.kind=="video"
   else->m.purposes.contains(modelFilter,true)
  }}
  val shownModels=ModelBrowse.sort(matching,vm.graph.settings.string("modelSort","Catalog order")){vm.graph.installed(it)}
  host.addView(kit.label(shownModels.size.toString()+" models · "+modelFilter+" · "+vm.graph.settings.string("modelSort","Catalog order"),13,secondary=true))
  if(shownModels.isEmpty())host.addView(kit.label("No models match. Try a different filter.",16,secondary=true))
  shownModels.forEach{m->
   val card=kit.card();val compat=ResourcePolicy.compatibility(m,h);val record=vm.graph.downloads.value.firstOrNull{it.id==m.id}
   val heading=kit.row();heading.addView(kit.label(m.name,19,true),LinearLayout.LayoutParams(0,-2,1f));heading.addView(kit.button(if(vm.graph.settings.bool("favorite."+m.id))"★" else "☆",true){vm.graph.settings.set("favorite."+m.id,!vm.graph.settings.bool("favorite."+m.id));renderModels()}.apply{contentDescription="Favorite "+m.name});card.addView(heading)
   card.addView(kit.label(m.creator+" · "+m.kind.uppercase(),12,secondary=true))
   card.addView(kit.label("${bytesLabel(m.bytes)} · GGUF ${m.quantization}\n${compat.label} · Estimated runtime RAM ${bytesLabel(ResourcePolicy.required(m,vm.graph.settings.options.context))}",14))
   if(vm.graph.settings.string("modelDensity","Comfortable")!="Compact")card.addView(kit.label(m.purposes,12,secondary=true))
   if(compat.allowed&&!ModelBrowse.ready(m,h,vm.graph.settings.options.context))card.addView(kit.label("Low available RAM for current context. Close other apps or lower context before loading.",12,secondary=true))
   if(record!=null&&record.state!="complete")card.addView(kit.label("${record.state} · ${record.percent}%",12))
   kit.addButtons(card,kit.button(if(vm.graph.installed(m))"Use model" else if(record?.state in setOf("queued","downloading","verifying"))"Downloading" else "Download"){
    if(vm.graph.installed(m)){if(m.kind=="text"){vm.selectModel(m.id);vm.navigate("Chat")}else{vm.graph.settings.set("defaultImage",m.id);vm.createMode="Image";vm.navigate("Create")}}
    else if(record?.state in setOf("queued","downloading","verifying"))vm.navigate("Downloads") else downloadDialog(m)
   }.apply{isEnabled=compat.allowed&&!m.imported || vm.graph.installed(m)},kit.button("Details",true){modelDetails(m)})
   host.addView(card)
  }
 }
 fun modelDetails(m:ModelSpec){
  val h=Hardware.detect(this);val compatibility=ResourcePolicy.compatibility(m,h)
  val actions=if(vm.graph.installed(m))arrayOf("Verify integrity","Delete model","View source")else arrayOf("Download","View source")
  MaterialAlertDialogBuilder(this).setTitle(m.name).setMessage("Format: GGUF · ${m.quantization}\nArchitecture: ${m.architecture}\nRuntime: ${if(m.kind=="text")"llama.cpp / CPU" else "stable-diffusion.cpp / CPU"}\nDownload / installed: ${bytesLabel(m.bytes)}\nEstimated runtime RAM: ${bytesLabel(ResourcePolicy.required(m,vm.graph.settings.options.context))}\nRecommended device RAM: ${bytesLabel(m.recommended)}\nLicense: ${m.license}\nSource: ${m.source}\n\n${compatibility.label}: ${compatibility.reason}\n\nSHA-256: ${m.sha256}")
   .setPositiveButton("Actions"){_,_->choose(m.name,actions.toList()){when(it){"Verify integrity"->vm.verify(m);"Delete model"->confirm("Delete ${m.name}?","Removes its model file and partial download. Conversations are kept."){vm.deleteModel(m)};"Download"->downloadDialog(m);"View source"->openSource(m.source)}}}.setNegativeButton("Close",null).show()
 }
 fun downloadDialog(m:ModelSpec){
  val h=Hardware.detect(this);val compatibility=ResourcePolicy.compatibility(m,h)
  if(!compatibility.allowed){vm.error(compatibility.reason);return}
  if(vm.graph.settings.offline){vm.error("Offline-only mode is enabled. Turn it off in Settings to download.");return}
  if(h.storage<m.bytes-vm.graph.partial(m).length()+256_000_000){vm.error("Free approximately "+bytesLabel(m.bytes-vm.graph.partial(m).length()+256_000_000-h.storage)+" more storage before downloading this model.");return}
  MaterialAlertDialogBuilder(this).setTitle("Download ${m.name}?").setMessage("Download: ${bytesLabel(m.bytes)}\nInstalled size: about ${bytesLabel(m.bytes)}\nAvailable storage: ${bytesLabel(h.storage)}\nEstimated runtime RAM: ${bytesLabel(ResourcePolicy.required(m,vm.graph.settings.options.context))}\n\n${compatibility.label}\n${compatibility.reason}\n\nSource: ${m.repo}\nLicense: ${m.license}\n\n${if(m.experimental)"Experimental models may be slow and use significant memory and battery. " else ""}Downloading uses your internet connection. ${if(vm.graph.settings.bool("wifiOnly",true))"Wi-Fi only is enabled." else "Mobile data is allowed."}")
   .setNegativeButton("Cancel",null).setNeutralButton("License"){_,_->if(m.kind=="image")MaterialAlertDialogBuilder(this).setTitle(m.license).setMessage(assets.open("SD-MODEL-LICENSE.txt").bufferedReader().use{it.readText()}).setPositiveButton("Close",null).show() else openSource("https://huggingface.co/${m.repo}/blob/${m.revision}/LICENSE")}
   .setPositiveButton("Download"){_,_->vm.navigate("Downloads");if(checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED){pendingDownload=m.id;notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)}else vm.download("enqueue",m.id)}.show()
 }
 private fun buildDownloads(body:LinearLayout){kit.section(body,"Downloads","Transfers run in the background. Paused or interrupted files can be resumed without starting over.");body.addView(kit.button("Show: "+downloadFilter,true){choose("Download status",listOf("All","Active","Queue","Completed","Failed")){downloadFilter=it;buildPage("Downloads")}});downloadRows=kit.column();body.addView(downloadRows);renderDownloads()}
 private fun renderDownloads(){
  val host=downloadRows?:return;host.removeAllViews()
  if(vm.graph.downloads.value.isEmpty()){host.addView(kit.label("No downloads yet. Choose a model to get started.",16,secondary=true));host.addView(kit.button("Browse models"){vm.navigate("Models")});return}
  vm.graph.downloads.value.filter{when(downloadFilter){"Active"->it.state in setOf("downloading","verifying");"Queue"->it.state in setOf("queued","paused");"Completed"->it.state=="complete";"Failed"->it.state in setOf("failed","cancelled");else->true}}.forEach{d->val m=vm.graph.models.value.firstOrNull{it.id==d.id}?:return@forEach
   val card=kit.card();card.addView(kit.label(m.name,18,true));card.addView(kit.label(d.state.replaceFirstChar{it.uppercase()}+" · ${d.percent}%",14))
   card.addView(ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal).apply{max=100;progress=d.percent;isIndeterminate=d.state=="verifying"})
   card.addView(kit.label("${bytesLabel(d.downloaded)} / ${bytesLabel(d.total)} · ${bytesLabel(d.speed)}/s"+if(d.remainingSeconds>=0)" · about ${d.remainingSeconds/60} min remaining" else "",12,secondary=true))
   if(d.error.isNotBlank())card.addView(kit.label(d.error,13,secondary=true))
   if(d.state!="complete")kit.addButtons(card,kit.button(if(d.state in setOf("queued","downloading","verifying"))"Pause" else "Resume / retry"){vm.download(if(d.state in setOf("queued","downloading","verifying"))"pause" else "enqueue",d.id)},kit.button("Cancel",true){confirm("Cancel this download?","The incomplete file will be removed."){vm.download("cancel",d.id)}})
   else card.addView(kit.button("Use model",true){if(m.kind=="text"){vm.selectModel(m.id);vm.navigate("Chat")}else{vm.graph.settings.set("defaultImage",m.id);vm.createMode="Image";vm.navigate("Create")}})
   host.addView(card)
  }
 }
 private fun buildHistory(body:LinearLayout){
  kit.section(body,"Your library","Conversations and creations, saved only on this device.")
  body.addView(kit.input("Search history",query).apply{doAfterTextChanged{query=it.toString();historyLimit=50;searchHistory()}})
  kit.addButtons(body,kit.button("Type: $historyFilter",true){choose("History",listOf("All","Text","Images","Videos","Archived")){historyFilter=it;buildPage("History")}},kit.button(if(favoritesOnly)"Favorites only" else "All items",true){favoritesOnly=!favoritesOnly;buildPage("History")})
  if(historyFilter=="Images")body.addView(kit.button(if(vm.graph.settings.bool("galleryGrid"))"List view" else "Grid view",true){vm.graph.settings.set("galleryGrid",!vm.graph.settings.bool("galleryGrid"));renderHistory()})
  historyRows=kit.column();body.addView(historyRows);renderHistory();if(query.isNotBlank())searchHistory()
 }
 private fun searchHistory(){
  historySearchJob?.cancel()
  val term=query
  historySearchResult=null
  renderHistory()
  if(term.isBlank())return
  historySearchJob=lifecycleScope.launch{
   delay(250)
   try{
    val ids=withContext(Dispatchers.IO){vm.graph.db.searchChats(term,100).map{it.id}.toSet()}
    if(query==term&&shown=="History"){historySearchResult=term to ids;renderHistory()}
   }catch(e:CancellationException){throw e}catch(e:Exception){if(query==term)vm.error("Could not search conversations: "+e.message)}
  }
 }
 private fun renderHistory(){
  val host=historyRows?:return;host.removeAllViews();var count=0
  val matches=historySearchResult?.takeIf{it.first==query}?.second
  if(historyFilter in setOf("All","Text","Archived")){
  val chats=vm.state.value.conversations.filter{(if(historyFilter=="Archived")it.archived else !it.archived)&&(!favoritesOnly||it.pinned)&&(query.isBlank()||(matches?.contains(it.id)?:((it.title+" "+it.messages.joinToString(" "){m->m.text}).contains(query,true))))}
  chats.take(historyLimit).forEach{c->
   count++;val card=kit.card();card.addView(kit.label((if(c.pinned)"★ " else "")+c.title,18,true));card.addView(kit.label("${if(c.summary)c.totalMessages else c.messages.size} messages · "+DateFormat.getDateTimeInstance(DateFormat.MEDIUM,DateFormat.SHORT).format(c.updated),12,secondary=true))
   kit.addButtons(card,kit.button("Open"){vm.openChat(c)},kit.button("Actions",true){choose(c.title,listOf("Rename","Pin / unpin","Archive / restore","Share","Delete")){when(it){"Archive / restore"->vm.archive(c);"Rename"->inputDialog("Rename",c.title){vm.rename(c,it)};"Pin / unpin"->vm.pin(c);"Share"->io("Preparing conversation",{vm.exportText(vm.graph.fullChat(c.id))}){shareText(it)};"Delete"->confirm("Delete conversation?","This cannot be undone."){vm.deleteChat(c)}}}})
   host.addView(card)
  }
  if(chats.size>historyLimit)host.addView(kit.button("Show more conversations",true){historyLimit+=50;renderHistory()})
  }
  var imageRow:LinearLayout?=null;var imageIndex=0
  if(historyFilter in setOf("All","Images")){
  val images=vm.state.value.media.filter{(!favoritesOnly||it.favorite)&&(query.isBlank()||it.prompt.contains(query,true))}
  images.take(historyLimit).forEach{m->
   count++;val card=kit.card();card.addView(kit.label((if(m.favorite)"★ " else "")+m.prompt.take(100),17,true))
   val preview=ImageView(this).apply{adjustViewBounds=true;contentDescription=m.prompt;maxHeight=kit.dp(300);setOnClickListener{showImage(m)}};card.addView(preview)
   lifecycleScope.launch{val bitmap=withContext(Dispatchers.IO){BitmapFactory.decodeFile(File(vm.graph.mediaDir,m.file).path,BitmapFactory.Options().apply{inSampleSize=4})};preview.setImageBitmap(bitmap)}
   card.addView(kit.label("${m.width} × ${m.height} · ${m.steps} steps · Seed ${m.seed}\n${m.model}",12,secondary=true))
   kit.addButtons(card,kit.button("Share",true){shareImage(m)},kit.button("Reuse",true){vm.createPrompt=m.prompt;vm.negativePrompt=m.negative;vm.createMode="Image";vm.navigate("Create")},kit.button("More",true){choose("Image",listOf("Favorite / unfavorite","Regenerate","Delete")){when(it){"Favorite / unfavorite"->vm.favoriteMedia(m);"Regenerate"->{vm.graph.models.value.firstOrNull{it.name==m.model}?.let{vm.graph.settings.set("defaultImage",it.id)};vm.imageGenerate(m.prompt,m.negative,m.width,m.height,m.steps,m.cfg,m.seed,1)};"Delete"->confirm("Delete image?","This removes the local image."){vm.deleteMedia(m)}}}})
   if(vm.graph.settings.bool("galleryGrid")&&historyFilter=="Images"){if(imageIndex%2==0){imageRow=kit.row().apply{gravity=android.view.Gravity.TOP};host.addView(imageRow)};imageRow!!.addView(card,LinearLayout.LayoutParams(0,-2,1f).apply{marginEnd=kit.dp(4)});imageIndex++}else host.addView(card)
  }
  if(images.size>historyLimit)host.addView(kit.button("Show more images",true){historyLimit+=50;renderHistory()})
  }
  if(count==0)host.addView(kit.label(if(historyFilter=="Videos")"Local video generation is not enabled. No unsupported video models are offered for download." else "Nothing here yet. Your conversations and creations will appear here.",16,secondary=true))
 }
 private fun buildCreate(body:LinearLayout){
  kit.section(body,"Create","Choose the right tool for your idea.")
  kit.addButtons(body,*listOf("Text","Image","Video").map{mode->kit.button(mode,vm.createMode!=mode){vm.createMode=mode;buildPage("Create")}}.toTypedArray())
  when(vm.createMode){
   "Text"->{body.addView(kit.label("Write, code, translate or study with your local chat model.",16));vm.state.value.presets.forEach{p->body.addView(kit.button(p.name,true){vm.applyPreset(p);vm.navigate("Chat")})}}
   "Video"->{val card=kit.card();kit.section(card,"Video is not available yet","This model is not recommended for this device.");card.addView(kit.label("No video runtime/model combination has been validated for useful local performance within this phone's memory and thermal limits. Desktop video models are blocked from download. A future backend can add text-to-video and image-to-video when tested.",15));body.addView(card)}
   else->{
    val m=vm.graph.models.value.firstOrNull{it.id==vm.graph.settings.string("defaultImage","sd15")}?:vm.graph.model("sd15")
    body.addView(kit.button("Model: "+m.name,true){val images=vm.graph.models.value.filter{it.kind=="image"}.sortedByDescending{vm.graph.settings.bool("favorite."+it.id)};choose("Image model",images.map{it.name}){name->vm.graph.settings.set("defaultImage",images.first{it.name==name}.id);buildPage("Create")}})
    body.addView(kit.label("Stable Diffusion 1.5 · Experimental CPU backend\n256–512 px · One image at a time · Generation may take minutes",13,secondary=true))
    if(!vm.graph.installed(m))body.addView(kit.button("Download image model"){downloadDialog(m)})
    val positive=kit.input("Describe your image",vm.createPrompt,true).apply{doAfterTextChanged{vm.createPrompt=it.toString()}}
    val negative=kit.input("Negative prompt",vm.negativePrompt,true).apply{doAfterTextChanged{vm.negativePrompt=it.toString()}}
    body.addView(positive);body.addView(negative)
    fun imageSetting(label:String,key:String,default:String)=number(body,label,vm.graph.settings.string(key,default)).apply{doAfterTextChanged{vm.graph.settings.set(key,it.toString())}}
    val width=imageSetting("Width (256 / 384 / 512)","imageWidth","384");val height=imageSetting("Height (256 / 384 / 512)","imageHeight","384")
    val steps=imageSetting("Steps (1–30)","imageSteps","15");val seed=imageSetting("Seed (-1 = random)","imageSeed","-1")
    val cfg=imageSetting("Guidance CFG (1–12)","imageCfg","7");val count=imageSetting("Number of images (1–3, sequential)","imageCount","1")
    createProgress=ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);body.addView(createProgress)
    createStatus=kit.label(vm.state.value.status,13,secondary=true);body.addView(createStatus)
    createButton=kit.button("Generate image"){
     if(vm.state.value.busy)vm.stop()else try{vm.imageGenerate(positive.text.toString(),negative.text.toString(),width.text.toString().toInt(),height.text.toString().toInt(),steps.text.toString().toInt(),cfg.text.toString().toFloat(),seed.text.toString().toLong(),count.text.toString().toInt())}catch(e:Exception){vm.error("Check the image settings. Use numbers within the shown ranges.")}
    };body.addView(createButton)
   }
  }
 }
 fun number(parent:LinearLayout,title:String,value:String):EditText{parent.addView(kit.label(title,13,secondary=true));return kit.input(title,value).apply{inputType=android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL or android.text.InputType.TYPE_NUMBER_FLAG_SIGNED;parent.addView(this)}}
 fun inputDialog(title:String,initial:String,multiline:Boolean=false,onSave:(String)->Unit){val field=kit.input(title,initial,multiline);MaterialAlertDialogBuilder(this).setTitle(title).setView(field).setNegativeButton("Cancel",null).setPositiveButton("Save"){_,_->onSave(field.text.toString())}.show()}
 fun choose(title:String,items:List<String>,onChoose:(String)->Unit){if(items.isEmpty()){toast("No items yet");return};MaterialAlertDialogBuilder(this).setTitle(title).setItems(items.toTypedArray()){_,i->onChoose(items[i])}.setNegativeButton("Cancel",null).show()}
 fun confirm(title:String,message:String,action:()->Unit){MaterialAlertDialogBuilder(this).setTitle(title).setMessage(message).setNegativeButton("Cancel",null).setPositiveButton("Confirm"){_,_->action()}.show()}
 fun toast(message:String)=Toast.makeText(this,message,Toast.LENGTH_SHORT).show()
 fun refreshAppearance(){recreate()}
 fun openSource(url:String){if(!url.startsWith("https://"))return;if(vm.graph.settings.offline){vm.error("External websites are disabled in offline-only mode.");return};startActivity(Intent(Intent.ACTION_VIEW,android.net.Uri.parse(url)))}
 fun shareText(text:String){startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT,text),"Share intentionally"))}
 fun shareImage(m:MediaItem){lifecycleScope.launch{try{val file=withContext(Dispatchers.IO){val dir=File(cacheDir,"exports").apply{mkdirs()};File(vm.graph.mediaDir,m.file).copyTo(File(dir,m.file),true)};val uri=FileProvider.getUriForFile(this@MainActivity,packageName+".files",file);startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("image/png").putExtra(Intent.EXTRA_STREAM,uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),"Share image"))}catch(e:Exception){vm.error("Could not share image: "+e.message)}}}
}
