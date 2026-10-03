plugins { alias(libs.plugins.kotlin.jvm) }

java {
  sourceCompatibility = JavaVersion.VERSION_11
  targetCompatibility = JavaVersion.VERSION_11
}

kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11) } }

// The real Android sources, compiled here against the genuine Android 14 framework classes so API misuse is a compile error
// even on machines without an Android SDK. (The APK itself is built by :android.)
sourceSets { main { kotlin.srcDir("../android/src/main/kotlin") } }

dependencies {
  implementation(project(":core"))
  compileOnly(libs.android.all)
  testImplementation(libs.junit)
  testImplementation(libs.robolectric)
  testImplementation(libs.android.all)
}

tasks.test {
  useJUnit()
  // Robolectric runs the real framework code; give it room and keep logs quiet.
  maxHeapSize = "2g"
  systemProperty("robolectric.logging.enabled", "false")
  testLogging {
    events("failed", "skipped")
    exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
  }
}
