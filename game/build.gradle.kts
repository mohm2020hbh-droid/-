plugins { alias(libs.plugins.android.application) }

android {
  namespace = "com.carom.game"
  compileSdk { version = release(36) { minorApiLevel = 1 } }

  defaultConfig {
    applicationId = "com.carom.game"
    minSdk = 24
    targetSdk = 36
    versionCode = 1
    versionName = "1.0"
  }

  buildTypes {
    release {
      // R8 removes unused code (most of the Kotlin standard library) to keep the APK tiny.
      isMinifyEnabled = true
      isShrinkResources = true
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
    }
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }
  testOptions { unitTests { isIncludeAndroidResources = true } }
}

// The game uses only the Android platform: no AndroidX, no engine, no third-party libraries.
dependencies {
  implementation(project(":game-core"))
  testImplementation(libs.junit)
  testImplementation(libs.robolectric)
}
