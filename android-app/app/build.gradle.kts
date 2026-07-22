plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.mobilecontrol.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.mobilecontrol.app"
        // Lowered from the original concept's minSdk 34 (see docs/MASTERKONZEPT.md §12/§"Android-Version")
        // deliberately, to support testing on older real hardware (e.g. Fire OS 7.x tablets, Android 9).
        // No code in this app actually requires API 34 - Keystore/EC-P256, BiometricPrompt, CameraX,
        // ML Kit (bundled model, no Play Services dependency), Compose, Room and Hilt all work fine
        // down to API 26. compileSdk/targetSdk stay at the current release per the concept.
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Consumed by RemoteConfig / diagnostics screen; kept in sync with the API contract version.
        buildConfigField("String", "API_VERSION", "\"v1\"")
    }

    signingConfigs {
        getByName("debug") {
            // Without this, Gradle falls back to its own auto-generated ~/.android/debug.keystore
            // - fine locally (persists on your machine), but on CI every job runs in a fresh,
            // ephemeral VM with no prior state, so a brand new random debug keystore got created
            // on literally every single build. Every CI-built APK was signed differently from the
            // last one, so every update required uninstalling first (adb: INSTALL_FAILED_UPDATE_
            // INCOMPATIBLE) - which wipes the Keystore-held device key pair and the paired-device
            // profile, forcing a full re-pair via QR code on every single app update. This fixed,
            // committed keystore (debug-only, never used for release signing - not sensitive)
            // makes every debug/staging build reproducibly signed the same way, so in-place
            // updates (adb install -r) work and pairing survives an app update.
            storeFile = file("../debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            signingConfig = signingConfigs.getByName("debug")
        }
        create("staging") {
            // Looks like a release build (minified/shrunk, closer to what will actually ship) but
            // stays debuggable/test-friendly like `debug` - initWith(debug) copies debug's flags
            // (isDebuggable, testCoverageEnabled, etc.) before the release-like overrides below.
            // No real release keystore exists in this repo (see README "Store-Release-Signing-
            // Pipeline"), so staging deliberately reuses the fixed debug signing config (see above)
            // - that keeps it installable side-by-side with debug/release without creating any new
            // signing material.
            initWith(getByName("debug"))
            applicationIdSuffix = ".staging"
            versionNameSuffix = "-staging"
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("debug")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.fragment.ktx)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    implementation(libs.androidx.navigation.compose)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.retrofit.core)
    implementation(libs.retrofit.converter.kotlinx.serialization)
    implementation(libs.okhttp.core)
    implementation(libs.okhttp.logging.interceptor)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)

    implementation(libs.androidx.datastore.preferences)

    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    implementation(libs.mlkit.barcode.scanning)

    implementation(libs.androidx.biometric)
    implementation(libs.androidx.security.crypto)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
}
