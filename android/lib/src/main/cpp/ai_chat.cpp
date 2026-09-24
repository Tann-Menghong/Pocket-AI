#include <jni.h>
#include <atomic>
#include <string>
#include <vector>
#include <algorithm>
#include <stdexcept>
#include <codecvt>
#include <locale>
#include "llama.h"
#include "ggml-backend.h"

static llama_model *model=nullptr;
static llama_context *ctx=nullptr;
static llama_sampler *sampler=nullptr;
static std::atomic<bool> cancelled{false};
static int position=0, remaining=0, generated=0;
static void fail(JNIEnv *e,const char *m){e->ThrowNew(e->FindClass("java/io/IOException"),m);}
static std::string utf8(JNIEnv *e,jstring s){
 const jchar *p=e->GetStringChars(s,nullptr);
 std::u16string u(reinterpret_cast<const char16_t*>(p),e->GetStringLength(s));
 e->ReleaseStringChars(s,p);
 return std::wstring_convert<std::codecvt_utf8_utf16<char16_t>,char16_t>{}.to_bytes(u);
}
static void release(){llama_sampler_free(sampler);sampler=nullptr;llama_free(ctx);ctx=nullptr;llama_model_free(model);model=nullptr;}
static bool abort_eval(void *){return cancelled.load();}
static bool load_progress(float,void *){return !cancelled.load();}
extern "C" JNIEXPORT void JNICALL Java_com_arm_aichat_PocketRuntime_nativeInit(JNIEnv *e,jobject,jstring dir){
 try{auto path=utf8(e,dir);llama_log_set([](ggml_log_level,const char*,void*){},nullptr);ggml_backend_load_all_from_path(path.c_str());llama_backend_init();}
 catch(const std::exception&){fail(e,"The local text runtime could not start.");}
}
extern "C" JNIEXPORT void JNICALL Java_com_arm_aichat_PocketRuntime_nativeLoad(JNIEnv *e,jobject,jstring path,jint context,jint threads){
 try{
  release();cancelled=false;
  if(context<1024||context>8192)throw std::runtime_error("Invalid context length.");
  auto p=llama_model_default_params();p.n_gpu_layers=0;p.load_mode=LLAMA_LOAD_MODE_MMAP;p.progress_callback=load_progress;
  auto file=utf8(e,path);model=llama_model_load_from_file(file.c_str(),p);
  if(!model)throw std::runtime_error("The model could not be loaded. It may be corrupt, unsupported, cancelled, or too large for available memory.");
  auto cp=llama_context_default_params();cp.n_ctx=context;cp.n_batch=256;cp.n_ubatch=128;cp.n_threads=std::clamp((int)threads,1,8);cp.n_threads_batch=cp.n_threads;cp.abort_callback=abort_eval;
  ctx=llama_init_from_model(model,cp);
  if(!ctx)throw std::runtime_error("Not enough memory for this context. Reduce context length or choose a smaller model.");
 }catch(const std::exception&x){release();fail(e,x.what());}
}
static std::vector<llama_token> tokenize(const std::string &s){
 auto v=llama_model_get_vocab(model);int n=llama_tokenize(v,s.data(),s.size(),nullptr,0,true,true);
 if(n==INT32_MIN)throw std::runtime_error("The prompt is too long.");
 std::vector<llama_token> t(std::abs(n));n=llama_tokenize(v,s.data(),s.size(),t.data(),t.size(),true,true);
 if(n<0)throw std::runtime_error("The prompt could not be tokenized.");t.resize(n);return t;
}
extern "C" JNIEXPORT jint JNICALL Java_com_arm_aichat_PocketRuntime_nativePrepare(JNIEnv *e,jobject,jobjectArray roles,jobjectArray messages,jint max,jfloat temp,jfloat topP,jint topK,jfloat repeat,jlong seed,jint threads){
 try{
  if(!ctx)throw std::runtime_error("No model is loaded.");
  cancelled=false;generated=0;remaining=max;position=0;
  llama_memory_clear(llama_get_memory(ctx),true);
  llama_set_n_threads(ctx,std::clamp((int)threads,1,8),std::clamp((int)threads,1,8));
  std::vector<std::string> rs,ms;int count=e->GetArrayLength(roles);
  if(count!=e->GetArrayLength(messages)||count<1||count>1000)throw std::runtime_error("Invalid conversation.");
  for(int i=0;i<count;i++){auto r=(jstring)e->GetObjectArrayElement(roles,i);auto m=(jstring)e->GetObjectArrayElement(messages,i);rs.push_back(utf8(e,r));ms.push_back(utf8(e,m));e->DeleteLocalRef(r);e->DeleteLocalRef(m);}
  std::vector<llama_token> tokens;
  for(;;){
   std::vector<llama_chat_message> chat;for(size_t i=0;i<rs.size();i++)chat.push_back({rs[i].c_str(),ms[i].c_str()});
   const char *tmpl=llama_model_chat_template(model,nullptr);
   int length=llama_chat_apply_template(tmpl,chat.data(),chat.size(),true,nullptr,0);
   if(length<0||length>2000000)throw std::runtime_error("This model's chat template is unsupported. Choose a curated chat model.");
   std::string formatted(length,'\0');llama_chat_apply_template(tmpl,chat.data(),chat.size(),true,formatted.data(),formatted.size());
   tokens=tokenize(formatted);
   if((int)tokens.size()+max+8<=(int)llama_n_ctx(ctx))break;
   size_t first=rs.front()=="system"?1:0;
   if(rs.size()<=first+1)throw std::runtime_error("This prompt is too long. Shorten it or increase context length.");
   rs.erase(rs.begin()+first);ms.erase(ms.begin()+first);
   if(rs.size()>first+1 && rs[first]=="assistant"){rs.erase(rs.begin()+first);ms.erase(ms.begin()+first);}
  }
  llama_sampler_free(sampler);sampler=llama_sampler_chain_init(llama_sampler_chain_default_params());
  auto vocab=llama_model_get_vocab(model);
  llama_sampler_chain_add(sampler,llama_sampler_init_top_k(topK));
  llama_sampler_chain_add(sampler,llama_sampler_init_penalties(llama_vocab_n_tokens(vocab),64,repeat,0,0));
  if(temp<=0)llama_sampler_chain_add(sampler,llama_sampler_init_greedy());
  else{llama_sampler_chain_add(sampler,llama_sampler_init_top_p(topP,1));llama_sampler_chain_add(sampler,llama_sampler_init_temp(temp));llama_sampler_chain_add(sampler,llama_sampler_init_dist(seed<0?LLAMA_DEFAULT_SEED:(uint32_t)seed));}
  for(auto t:tokens)llama_sampler_accept(sampler,t);
  for(size_t i=0;i<tokens.size();i+=256){
   if(cancelled)throw std::runtime_error("Generation cancelled.");
   int n=std::min<size_t>(256,tokens.size()-i);
   auto batch=llama_batch_get_one(tokens.data()+i,n);
   if(llama_decode(ctx,batch)!=0)throw std::runtime_error("Prompt evaluation failed or was cancelled. Try a smaller context.");
  }
  position=tokens.size();return position;
 }catch(const std::exception&x){fail(e,x.what());return 0;}
}
extern "C" JNIEXPORT jbyteArray JNICALL Java_com_arm_aichat_PocketRuntime_nativeNext(JNIEnv *e,jobject){
 try{
  if(cancelled||remaining<=0||!ctx||position>=(int)llama_n_ctx(ctx)-1)return nullptr;
  auto v=llama_model_get_vocab(model);auto t=llama_sampler_sample(sampler,ctx,-1);
  if(llama_vocab_is_eog(v,t))return nullptr;
  auto batch=llama_batch_get_one(&t,1);
  if(llama_decode(ctx,batch)!=0){if(cancelled)return nullptr;throw std::runtime_error("Generation failed. Try a smaller model or context.");}
  remaining--;position++;generated++;
  std::vector<char> chars(256);int n=llama_token_to_piece(v,t,chars.data(),chars.size(),0,false);
  if(n<0){chars.resize(-n);n=llama_token_to_piece(v,t,chars.data(),chars.size(),0,false);}
  if(n<0)throw std::runtime_error("Could not decode model output.");
  auto out=e->NewByteArray(n);if(out)e->SetByteArrayRegion(out,0,n,reinterpret_cast<jbyte*>(chars.data()));return out;
 }catch(const std::exception&x){fail(e,x.what());return nullptr;}
}
extern "C" JNIEXPORT void JNICALL Java_com_arm_aichat_PocketRuntime_nativeCancel(JNIEnv*,jobject){cancelled=true;}
extern "C" JNIEXPORT void JNICALL Java_com_arm_aichat_PocketRuntime_nativeUnload(JNIEnv*,jobject){release();}
extern "C" JNIEXPORT jint JNICALL Java_com_arm_aichat_PocketRuntime_nativeCount(JNIEnv*,jobject){return generated;}
