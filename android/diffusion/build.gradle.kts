plugins { alias(libs.plugins.android.library); alias(libs.plugins.jetbrains.kotlin.android) }
android {
 namespace = "com.example.pocketdiffusion"
 compileSdk = 36
 ndkVersion = "27.0.12077973"
 defaultConfig {
  minSdk = 33
  ndk { abiFilters += listOf("arm64-v8a", "x86_64") }
  externalNativeBuild { cmake { arguments += listOf("-DANDROID_STL=c++_shared","-DANDROID_SUPPORT_FLEXIBLE_PAGE_SIZES=ON","-DCMAKE_BUILD_TYPE=Release") } }
 }
 externalNativeBuild { cmake { path("src/main/cpp/CMakeLists.txt"); version = "3.22.1" } }
 compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
}
kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }
