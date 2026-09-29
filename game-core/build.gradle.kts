import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Pure Kotlin game engine: physics, level data, rules and progress. No Android dependency, so it
// is unit-tested on the JVM and could be reused by another front end.
plugins { alias(libs.plugins.kotlin.jvm) }

java {
  sourceCompatibility = JavaVersion.VERSION_11
  targetCompatibility = JavaVersion.VERSION_11
}

kotlin { compilerOptions { jvmTarget.set(JvmTarget.JVM_11) } }

dependencies { testImplementation(libs.junit) }

// ShippedLevelsTest validates and solves the levels that ship in the game module.
val shippedLevels: File = rootProject.file("game/src/main/assets/levels")

tasks.test {
  inputs.dir(shippedLevels)
  systemProperty("levels.dir", shippedLevels.absolutePath)
  // `-Dpreview.dir=<folder>` also renders every level with a sample solution to PNG sheets.
  providers.systemProperty("preview.dir").orNull?.let {
    systemProperty("preview.dir", it)
    outputs.upToDateWhen { false }
  }
}
