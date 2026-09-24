#include <jni.h>
#include <mutex>
#include <atomic>
#include <string>
#include <codecvt>
#include <locale>
#include <stdexcept>
#include <algorithm>
#include "stable-diffusion.h"
static std::mutex gate;
static sd_ctx_t *current=nullptr;
static std::atomic<bool> stopped{false};
static std::atomic<int> progress{0};
static std::string text(JNIEnv *e,jstring s){auto p=e->GetStringChars(s,nullptr);std::u16string u((const char16_t*)p,e->GetStringLength(s));e->ReleaseStringChars(s,p);return std::wstring_convert<std::codecvt_utf8_utf16<char16_t>,char16_t>{}.to_bytes(u);}
extern "C" JNIEXPORT jbyteArray JNICALL Java_com_example_pocketdiffusion_ImageRuntime_generate(JNIEnv *e,jobject,jstring path,jstring prompt,jstring negative,jint width,jint height,jint steps,jfloat cfg,jlong seed,jint threads){
 sd_ctx_t *ctx=nullptr;sd_image_t *images=nullptr;int count=0;
 auto cleanup=[&](){std::lock_guard<std::mutex> lock(gate);current=nullptr;if(images)free_sd_images(images,count);images=nullptr;if(ctx)free_sd_ctx(ctx);ctx=nullptr;};
 try{
  if(width<256||height<256||width>512||height>512||width%64||height%64||steps<1||steps>30)throw std::runtime_error("Image settings exceed the mobile safety limits.");
  stopped=false;progress=0;
  auto file=text(e,path),positive=text(e,prompt),neg=text(e,negative);
  sd_set_log_callback([](sd_log_level_t,const char*,void*){},nullptr);
  sd_set_progress_callback([](int step,int total,float,void*){if(total>0)progress=std::min(99,step*100/total);},nullptr);
  sd_ctx_params_t p;sd_ctx_params_init(&p);p.model_path=file.c_str();p.n_threads=std::clamp((int)threads,1,6);p.enable_mmap=true;p.rng_type=CPU_RNG;p.backend="CPU";p.params_backend="CPU";p.conditioning_cache_size=0;
  ctx=new_sd_ctx(&p);if(!ctx)throw std::runtime_error("The image model could not load. Check the file and available memory.");
  {std::lock_guard<std::mutex> lock(gate);current=ctx;}
  if(stopped)throw std::runtime_error("Image generation cancelled.");
  if(!sd_ctx_supports_image_generation(ctx))throw std::runtime_error("This model cannot generate images.");
  sd_img_gen_params_t params;sd_img_gen_params_init(&params);
  params.prompt=positive.c_str();params.negative_prompt=neg.c_str();params.width=width;params.height=height;params.seed=seed;params.batch_count=1;
  params.sample_params.sample_steps=steps;params.sample_params.sample_method=EULER_A_SAMPLE_METHOD;params.sample_params.guidance.txt_cfg=cfg;
  params.vae_tiling_params.enabled=true;params.vae_tiling_params.tile_size_x=256;params.vae_tiling_params.tile_size_y=256;
  if(!generate_image(ctx,&params,&images,&count)||!images||count<1)throw std::runtime_error(stopped?"Image generation cancelled.":"Image generation failed. Try fewer steps or a smaller resolution.");
  if(stopped)throw std::runtime_error("Image generation cancelled.");
  if(images[0].width!=(uint32_t)width||images[0].height!=(uint32_t)height||images[0].channel!=3)throw std::runtime_error("Unexpected image format from runtime.");
  auto result=e->NewByteArray(width*height*3);
  if(result)e->SetByteArrayRegion(result,0,width*height*3,(const jbyte*)images[0].data);
  cleanup();progress=100;return result;
 }catch(const std::exception &x){cleanup();e->ThrowNew(e->FindClass("java/io/IOException"),x.what());return nullptr;}
}
extern "C" JNIEXPORT void JNICALL Java_com_example_pocketdiffusion_ImageRuntime_cancel(JNIEnv*,jobject){stopped=true;std::lock_guard<std::mutex> lock(gate);if(current)sd_cancel_generation(current,SD_CANCEL_ALL);}
extern "C" JNIEXPORT jint JNICALL Java_com_example_pocketdiffusion_ImageRuntime_progress(JNIEnv*,jobject){return progress.load();}
