// The real Android app. Included by ../settings.gradle.kts only when an Android SDK is discoverable
// (ANDROID_HOME / ANDROID_SDK_ROOT / local.properties). Mirrors the conventions of the repository's existing app module.
plugins { id("com.android.application") version "9.1.1" }

android {
  namespace = "com.pogoascent.android"
  compileSdk { version = release(36) { minorApiLevel = 1 } }

  defaultConfig {
    applicationId = "com.pogoascent.game"
    minSdk = 24
    targetSdk = 36
    versionCode = 1
    versionName = "0.1.0"
  }

  signingConfigs {
    create("release") {
      val keystorePath = System.getenv("KEYSTORE_PATH")
      if (keystorePath != null) {
        storeFile = file(keystorePath)
        storePassword = System.getenv("STORE_PASSWORD")
        keyAlias = System.getenv("KEY_ALIAS") ?: "upload"
        keyPassword = System.getenv("KEY_PASSWORD")
      }
    }
  }

  buildTypes {
    release {
      isMinifyEnabled = true
      isShrinkResources = true
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      if (System.getenv("KEYSTORE_PATH") != null) signingConfig = signingConfigs.getByName("release")
    }
  }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }

  packaging { resources { excludes += setOf("/META-INF/{AL2.0,LGPL2.1}", "META-INF/*.kotlin_module", "kotlin/**", "DebugProbesKt.bin") } }
}

dependencies {
  implementation(project(":core"))
}
