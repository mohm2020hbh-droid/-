import java.util.Properties

plugins { id("com.android.application") }

// Release signing: put keystore.properties (storeFile, storePassword, keyAlias, keyPassword) next to this file.
// It is git-ignored. Without it `bundleRelease`/`assembleRelease` produce an UNSIGNED artifact.
val keystoreProps = Properties().apply {
    val f = file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

android {
    namespace = "app.pogosummit.game"
    compileSdk = 35

    defaultConfig {
        applicationId = "app.pogosummit.game"   // placeholder — change before the first Play upload
        minSdk = 24
        targetSdk = 35                           // Google Play requires >= 35 for new apps
        versionCode = 1
        versionName = "0.1.0"
    }

    signingConfigs {
        if (keystoreProps.containsKey("storeFile")) {
            create("release") {
                storeFile = file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false      // Java shell is ~10 KB; the game is a JS bundle
            if (keystoreProps.containsKey("storeFile")) signingConfig = signingConfigs.getByName("release")
        }
        debug { applicationIdSuffix = ".debug"; versionNameSuffix = "-debug" }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    bundle { language { enableSplit = false } }   // single-language game; keep one AAB config
}

// Build the web game and copy it into src/main/assets/www before packaging (skip with -PskipWeb).
val buildWeb by tasks.registering(Exec::class) {
    onlyIf { !project.hasProperty("skipWeb") }
    workingDir = file("../../game")
    commandLine("npm", "run", "build", "--", "--android")
}
tasks.matching { it.name == "preBuild" }.configureEach { dependsOn(buildWeb) }
