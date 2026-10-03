plugins { alias(libs.plugins.kotlin.jvm) }

java {
  sourceCompatibility = JavaVersion.VERSION_11
  targetCompatibility = JavaVersion.VERSION_11
}

kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11) } }

dependencies {
  implementation(project(":core"))
  testImplementation(libs.junit)
}

fun registerTool(name: String, main: String, description: String) = tasks.register<JavaExec>(name) {
  group = "pogo tools"
  this.description = description
  classpath = sourceSets["main"].runtimeClasspath
  mainClass.set(main)
  args(rootProject.projectDir.absolutePath)
  systemProperty("java.awt.headless", "true")
  System.getProperty("trace")?.let { systemProperty("trace", it) }
  System.getProperty("spots")?.let { systemProperty("spots", it) }
}

registerTool("validateLevels", "com.pogoascent.devtools.ValidateLevelsKt", "Structural + reachability validation of every bundled level")
registerTool("renderLevels", "com.pogoascent.devtools.RenderLevelsKt", "Render PNG frames of every level (debug software renderer)")
registerTool("runScenarios", "com.pogoascent.devtools.RunScenariosKt", "Run the Physics Test Scene scenarios headless and print measurements")

tasks.register<JavaExec>("probeJumps") {
  group = "pogo tools"
  description = "Print where a grid of jumps from one surface point ends (level-design aid): -Pprobe=levelId,x,y"
  classpath = sourceSets["main"].runtimeClasspath
  mainClass.set("com.pogoascent.devtools.ProbeJumpsKt")
  val probe = (project.findProperty("probe") as String?)?.split(",") ?: listOf("level_01", "0", "0")
  args(listOf(rootProject.projectDir.absolutePath) + probe)
}

tasks.register<JavaExec>("generateLevels") {
  group = "pogo tools"
  description = "Regenerate bundled level JSON from the generator profiles (validated by the real-physics reachability search): -Plevels=level_01"
  classpath = sourceSets["main"].runtimeClasspath
  mainClass.set("com.pogoascent.devtools.GenerateLevelsKt")
  val which = (project.findProperty("levels") as String?)?.split(",") ?: emptyList()
  args(listOf(rootProject.projectDir.absolutePath) + which)
  System.getProperty("verbose")?.let { systemProperty("verbose", it) }
}

tasks.register<JavaExec>("generateDemoRoute") {
  group = "pogo tools"
  description = "Write the autopilot route used as the main-menu backdrop"
  classpath = sourceSets["main"].runtimeClasspath
  mainClass.set("com.pogoascent.devtools.GenerateDemoRouteKt")
  args(rootProject.projectDir.absolutePath)
}
