// Is an Android SDK discoverable? Only then is the APK module (:android) and Google's Maven repository used.
val sdkFromProps =
  file("local.properties").takeIf { it.exists() }?.readLines()?.firstOrNull { it.startsWith("sdk.dir=") }
val hasAndroidSdk =
  !System.getenv("ANDROID_HOME").isNullOrBlank() ||
    !System.getenv("ANDROID_SDK_ROOT").isNullOrBlank() ||
    sdkFromProps != null

pluginManagement {
  val sdkProps =
    file("local.properties").takeIf { it.exists() }?.readLines()?.firstOrNull { it.startsWith("sdk.dir=") }
  val sdk =
    !System.getenv("ANDROID_HOME").isNullOrBlank() ||
      !System.getenv("ANDROID_SDK_ROOT").isNullOrBlank() ||
      sdkProps != null
  repositories {
    if (sdk) {
      google {
        content {
          includeGroupByRegex("com\\.android.*")
          includeGroupByRegex("com\\.google.*")
          includeGroupByRegex("androidx.*")
        }
      }
    }
    mavenCentral()
    gradlePluginPortal()
  }
}

dependencyResolutionManagement {
  repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
  repositories {
    if (hasAndroidSdk) google()
    mavenCentral()
  }
}

rootProject.name = "pogo-ascent"

// :core is pure Kotlin/JVM (all game logic, no Android dependency) and is always built.
include(":core")

// :android-check compiles and tests the Android layer against the Android 14 framework jar
// (org.robolectric:android-all) on a plain JVM. It needs no Android SDK.
include(":android-check")

// :devtools holds JVM-only developer tools (level validator CLI, software debug renderer, scenario runner).
include(":devtools")

// :android is the real APK module. It needs the Android SDK (and Google's Maven for AGP).
if (hasAndroidSdk) include(":android")
