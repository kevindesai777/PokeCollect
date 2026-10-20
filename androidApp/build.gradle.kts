import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeCompiler)
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_11
    }
}
dependencies {
    implementation(project(":shared"))

    implementation(libs.androidx.activity.compose)
    implementation(libs.koin.android)

    implementation(libs.compose.uiToolingPreview)
    debugImplementation(libs.compose.uiTooling)
}

// Upload key for Play, kept out of the repo. keystore.properties (gitignored) holds
// storeFile, storePassword, keyAlias and keyPassword. Without it the release bundle is unsigned.
val uploadKey = rootProject.file("keystore.properties").takeIf { it.exists() }?.let { file ->
    Properties().apply { file.inputStream().use(::load) }
}

android {
    namespace = "com.pixel.pokecollect"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.pixel.pokecollect"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = 1
        versionName = "1.0"
        ndk {
            // LiteRT-LM ships its native library for these two only. Without the filter, Play would
            // also serve 32-bit phones (sqlite-bundled has 32-bit libraries), which crash on the model.
            abiFilters += listOf("arm64-v8a", "x86_64")
        }
    }
    // The model, downloaded by Play after install (fast-follow). Only bundles carry it, not APKs.
    assetPacks += ":gemma_pack"
    signingConfigs {
        if (uploadKey != null) {
            create("upload") {
                storeFile = rootProject.file(uploadKey.getProperty("storeFile"))
                storePassword = uploadKey.getProperty("storePassword")
                keyAlias = uploadKey.getProperty("keyAlias")
                keyPassword = uploadKey.getProperty("keyPassword")
            }
        }
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    buildTypes {
        release {
            // Off for the first release: R8 against LiteRT-LM's JNI classes isn't tested yet.
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.findByName("upload")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
}

// A bundle without the model would upload fine and then fail on every phone (PACK_UNAVAILABLE);
// one without the Gemma Terms would break the licence.
tasks.matching { it.name == "bundleDebug" || it.name == "bundleRelease" }.configureEach {
    // Local vals: the configuration cache can't hold references to the build script itself.
    val stagedModel = rootProject.file("gemma_pack/src/main/assets/gemma3-1b-it-int4.litertlm")
    val gemmaTerms = rootProject.file("licenses/Gemma-Terms-of-Use.txt")
    doFirst {
        check(stagedModel.isFile) { "Model not staged for the AI pack: run scripts/stage_model.sh first." }
        // Shipping Gemma means giving everyone a copy of its terms (About screen, licence texts).
        check(gemmaTerms.isFile) { "Save the Gemma Terms of Use (ai.google.dev/gemma/terms) as licenses/Gemma-Terms-of-Use.txt." }
    }
}
