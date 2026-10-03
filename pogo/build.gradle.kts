// Root build file for the Pogo Ascent game. Plugins are applied per module.
// The Android Gradle Plugin is declared only inside :android (included when an SDK exists), so the
// JVM modules build on machines that cannot reach Google's Maven repository.
plugins {
  alias(libs.plugins.kotlin.jvm) apply false
  alias(libs.plugins.kotlin.serialization) apply false
}
