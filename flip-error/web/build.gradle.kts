plugins { kotlin("multiplatform") }

repositories { mavenCentral() }

kotlin {
    js(IR) {
        browser {
            commonWebpackConfig { outputFileName = "flip-error.js" }
        }
        binaries.executable()
    }
    sourceSets {
        val jsMain by getting { dependencies { implementation(project(":core")) } }
    }
}
