plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.android)
  alias(libs.plugins.kotlin.compose)
}

kotlin {
  compilerOptions {
    jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11
  }
}

android {
  namespace = "com.lyihub.archiveassistant"
  compileSdk {
    version = release(36)
  }

  defaultConfig {
    applicationId = "com.lyihub.archiveassistant"
    minSdk = 31
    targetSdk = 36
    versionCode = 1
    versionName = "1.0"

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }

  buildTypes {
    release {
      isMinifyEnabled = true
      proguardFiles(
        getDefaultProguardFile("proguard-android-optimize.txt"),
        "proguard-rules.pro",
      )
    }
  }
  compileOptions {
    isCoreLibraryDesugaringEnabled = true
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }
  buildFeatures {
    compose = true
  }
  packaging {
    jniLibs {
      keepDebugSymbols +=
        setOf(
          "**/libLiteRt.so",
          "**/libLiteRtClGlAccelerator.so",
          "**/libandroidx.graphics.path.so",
          "**/libdatastore_shared_counter.so",
          "**/liblitertlm_jni.so",
        )
    }
  }
}

dependencies {
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.activity.compose)
  implementation(libs.androidx.datastore.preferences)
  implementation(libs.litertlm.android)
  implementation(libs.okhttp)
  implementation(libs.jsoup)
  implementation(libs.pdfbox.android)
  coreLibraryDesugaring(libs.desugar.jdk.libs.nio)
  implementation(platform(libs.androidx.compose.bom))
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.graphics)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.compose.material3)
  testImplementation(libs.junit)
  testImplementation(libs.json)
  testImplementation(libs.kotlinx.coroutines.test)
  androidTestImplementation(libs.androidx.junit)
  androidTestImplementation(libs.androidx.espresso.core)
  androidTestImplementation(platform(libs.androidx.compose.bom))
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  debugImplementation(libs.androidx.compose.ui.tooling)
  debugImplementation(libs.androidx.compose.ui.test.manifest)
}
