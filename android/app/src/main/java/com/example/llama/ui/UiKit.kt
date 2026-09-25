package com.example.llama.ui

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.*
import android.view.Gravity
import android.view.View
import android.widget.*
import com.example.llama.data.Settings
import com.google.android.material.button.MaterialButton

data class Palette(val background:Int,val surface:Int,val text:Int,val secondary:Int,val accent:Int,val dark:Boolean)
fun palette(context:Context,s:Settings):Palette{
 val dark=when(s.string("theme","System")){"Light"->false;"Dark","AMOLED"->true;else->context.resources.configuration.uiMode and 0x30==0x20}
 val color=when(s.string("accent","Mint")){"Blue"->"#82B1FF";"Amber"->"#F5C970";"Rose"->"#F3A3BD";else->"#79DDB8"}
 val base=if(s.string("theme")=="AMOLED")Color.BLACK else Color.parseColor(if(dark)"#10191F" else "#F5F8F7")
 var accent=Color.parseColor(color)
 if(!dark)accent=when(s.string("accent","Mint")){"Blue"->Color.rgb(36,86,153);"Amber"->Color.rgb(119,78,0);"Rose"->Color.rgb(146,50,90);else->Color.rgb(20,107,79)}
 if(s.bool("dynamic")&&android.os.Build.VERSION.SDK_INT>=31){val v=android.util.TypedValue();context.theme.resolveAttribute(androidx.appcompat.R.attr.colorPrimary,v,true);accent=v.data}
 return Palette(base,Color.parseColor(if(dark)"#1B2931" else "#FFFFFF"),Color.parseColor(if(dark)"#E9F0EF" else "#162920"),Color.parseColor(if(dark)"#A8BABF" else "#53665D"),accent,dark)
}
class UiKit(val context:Context,val settings:Settings){
 val p=palette(context,settings)
 fun dp(n:Int)=(n*context.resources.displayMetrics.density).toInt()
 fun column()=LinearLayout(context).apply{orientation=LinearLayout.VERTICAL}
 fun row()=LinearLayout(context).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL}
 fun label(value:String,size:Int=15,bold:Boolean=false,secondary:Boolean=false)=TextView(context).apply{
  text=value;textSize=size.toFloat()*settings.int("fontPercent",100)/100
  setTextColor(if(secondary)p.secondary else p.text)
  if(bold)setTypeface(null,Typeface.BOLD)
  setPadding(0,dp(4),0,dp(4))
 }
 fun button(value:String,outlined:Boolean=false,click:()->Unit)=MaterialButton(context,null,if(outlined)com.google.android.material.R.attr.materialButtonOutlinedStyle else com.google.android.material.R.attr.materialButtonStyle).apply{
  text=value;isAllCaps=false;minHeight=dp(48);textSize=13f;cornerRadius=dp(if(settings.bool("rounded",true))16 else 4)
  setTextColor(if(outlined)p.accent else if(p.dark)Color.rgb(10,35,26)else Color.WHITE)
  backgroundTintList=android.content.res.ColorStateList.valueOf(if(outlined)Color.TRANSPARENT else p.accent)
  if(outlined)strokeColor=android.content.res.ColorStateList.valueOf(p.accent)
  setOnClickListener{if(settings.bool("haptic",false))performHapticFeedback(android.view.HapticFeedbackConstants.CONFIRM);click()}
 }
 fun input(hintText:String,value:String="",multiline:Boolean=false)=EditText(context).apply{
  hint=hintText;setText(value);setTextColor(p.text);setHintTextColor(p.secondary);textSize=16f;minHeight=dp(48)
  setPadding(dp(12),dp(8),dp(12),dp(8));background=shape(p.surface)
  inputType=android.text.InputType.TYPE_CLASS_TEXT or if(multiline)android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE or android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES else 0
  if(multiline){minLines=2;maxLines=5}
  contentDescription=hintText
 }
 fun shape(color:Int)=GradientDrawable().apply{setColor(color);cornerRadius=dp(if(settings.bool("rounded",true))settings.int("bubbleRadius",20).coerceIn(4,28) else 4).toFloat()}
 fun card():LinearLayout=column().apply{setPadding(dp(16),dp(12),dp(16),dp(12));background=shape(p.surface);layoutParams=LinearLayout.LayoutParams(-1,-2).apply{bottomMargin=dp(12)}}
 fun addButtons(parent:LinearLayout,vararg buttons:View){val r=row();buttons.forEach{r.addView(it,LinearLayout.LayoutParams(0,-2,1f).apply{marginEnd=dp(4)})};parent.addView(r)}
 fun section(parent:LinearLayout,title:String,description:String=""){parent.addView(label(title,22,true));if(description.isNotEmpty())parent.addView(label(description,14,secondary=true))}
 fun scroll(content:View)=ScrollView(context).apply{isFillViewport=true;addView(content)}
}
object MessageFormatting {
 fun render(text:String,markdown:Boolean,highlight:Boolean,accent:Int):CharSequence{
  if(!markdown)return text
  val clean=text.replace(Regex("<think>\\s*</think>\\s*"),"")
  val out=SpannableStringBuilder(clean)
  fun span(regex:Regex,f:(MatchResult)->Any){regex.findAll(clean).forEach{m->out.setSpan(f(m),m.range.first,m.range.last+1,Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)}}
  span(Regex("(?s)```.*?```")){TypefaceSpan("monospace")}
  span(Regex("\\*\\*[^*]+\\*\\*")){StyleSpan(Typeface.BOLD)}
  span(Regex("(?m)^#{1,3} .+$")){StyleSpan(Typeface.BOLD)}
  span(Regex("`[^`\\n]+`")){TypefaceSpan("monospace")}
  if(highlight){Regex("(?s)```.*?```").findAll(clean).forEach{block->
   Regex("\\b(fun|val|var|class|return|if|else|for|while|def|import|const|let|public|private|true|false|null)\\b").findAll(block.value).forEach{m->out.setSpan(ForegroundColorSpan(accent),block.range.first+m.range.first,block.range.first+m.range.last+1,Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)}
  }}
  return out
 }
}
