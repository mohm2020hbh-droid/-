plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose)
}

android {
  namespace = "com.voiceduel"
  compileSdk { version = release(36) { minorApiLevel = 1 } }

  defaultConfig {
    applicationId = "com.voiceduel.app"
    minSdk = 24
    targetSdk = 36
    versionCode = 1
    versionName = "1.0"

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

    // Default server for debug builds. Override per machine in
    // `local.properties` (voiceduel.serverUrl=ws://192.168.1.10:8000/ws) or at
    // runtime from the app's home screen.
    buildConfigField(
      "String",
      "DEFAULT_SERVER_URL",
      "\"${project.findProperty("voiceduel.serverUrl") ?: "ws://10.0.2.2:8000/ws"}\"",
    )
  }

  buildTypes {
    release {
      isMinifyEnabled = false
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
    }
  }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }

  buildFeatures {
    compose = true
    buildConfig = true
  }

  testOptions { unitTests { isIncludeAndroidResources = true } }
}

dependencies {
  implementation(platform(libs.androidx.compose.bom))
  implementation(libs.androidx.activity.compose)
  implementation(libs.androidx.compose.material.icons.extended)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.graphics)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  implementation(libs.androidx.navigation.compose)
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.coroutines.core)
  implementation(libs.okhttp)

  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.robolectric)
  testImplementation(libs.androidx.core)
  testImplementation(libs.androidx.junit)

  debugImplementation(libs.androidx.compose.ui.tooling)
}
