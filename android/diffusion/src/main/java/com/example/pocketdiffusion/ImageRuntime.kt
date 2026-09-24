package com.example.pocketdiffusion
class ImageRuntime {
 init { System.loadLibrary("pocket-diffusion") }
 external fun generate(path:String,prompt:String,negative:String,width:Int,height:Int,steps:Int,cfg:Float,seed:Long,threads:Int):ByteArray
 external fun cancel()
 external fun progress():Int
}
