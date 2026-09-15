plugins { kotlin("multiplatform") }

repositories { mavenCentral() }

kotlin {
    js(IR) {
        browser()
    }
    jvm {
        compilations.all { compilerOptions.configure { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11) } }
        testRuns["test"].executionTask.configure { useJUnitPlatform() }
    }
    sourceSets {
        val commonMain by getting
        val jvmTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation(kotlin("test-junit5"))
            }
        }
    }
}
