// Pure-JVM unit tests for the shell's decision logic (ShellLogic.java has no android.* imports) — no SDK, no AGP, no Google Maven.
plugins { java }
repositories { mavenCentral() }
java { toolchain { languageVersion.set(JavaLanguageVersion.of(21)) } }
sourceSets { main { java.srcDir("../app/src/main/java"); java.include("**/ShellLogic.java") } }
dependencies { testImplementation("junit:junit:4.13.2") }
tasks.withType<JavaCompile> { options.release.set(17) }
tasks.test {
    useJUnit()
    testLogging { events("passed", "failed", "skipped"); exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL }
}
