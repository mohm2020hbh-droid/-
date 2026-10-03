plugins {
  alias(libs.plugins.kotlin.jvm)
  alias(libs.plugins.kotlin.serialization)
}

// Android minSdk is 24, so keep bytecode/API level conservative (no java.nio.file, no JDK 17+ APIs).
java {
  sourceCompatibility = JavaVersion.VERSION_11
  targetCompatibility = JavaVersion.VERSION_11
}

kotlin {
  compilerOptions {
    jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
    allWarningsAsErrors.set(true)
  }
}

dependencies {
  api(libs.kotlinx.serialization.json)
  testImplementation(libs.junit)
}

tasks.test {
  useJUnit()
  testLogging {
    events("failed", "skipped")
    showStandardStreams = false
    exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
  }
}

// Regenerates PHYSICS_MASTER.md and data/physics_config.json from the code registry.
tasks.register<JavaExec>("generateDocs") {
  group = "documentation"
  description = "Regenerate PHYSICS_MASTER.md and physics_config.json from PhysicsParams / PhysicsConfig"
  classpath = sourceSets["main"].runtimeClasspath
  mainClass.set("com.pogoascent.tools.PhysicsDocs")
  args(rootProject.projectDir.absolutePath)
}
