plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.parcelize)
    alias(libs.plugins.kotlin.compose)

    // Hilt via KAPT (stable) + keep KSP for other processors if needed
    id("org.jetbrains.kotlin.kapt") version "2.0.0"
    id("com.google.devtools.ksp") version "2.0.0-1.0.24"
    id("com.google.dagger.hilt.android") version "2.51.1"
}

val bitcoinjVersion = "0.16.1" // must match libdohj's bitcoinj target

android {
    namespace = "com.dogechat.android"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.dogechat.android"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()

        versionCode = 12
        versionName = "0.9.6"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    dependenciesInfo {
        // Disables dependency metadata when building APKs.
        includeInApk = false
        // Disables dependency metadata when building Android App Bundles.
        includeInBundle = false
    }

    signingConfigs {
        create("release") {
            storeFile = file("dogechat-release-key.jks")
            storePassword = "MichaelHailey0608!"  // Replace with your actual keystore password
            keyAlias = "dogechat-key"
            keyPassword = "MichaelHailey0608!"  // Replace with your actual key password
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release")
        }
        debug {
            // Faster local iteration
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }

    buildFeatures {
        compose = true
    }

    packaging {
        resources {
            // Keep native Tor/Arti libs: DO NOT exclude lib/** or root/**
            excludes += listOf(
                "/META-INF/{AL2.0,LGPL2.1}",
                "META-INF/AL2.0",
                "META-INF/LGPL2.1",
                "**/*.dylib"
            )
            // Resolve duplicate merge for multi-release jars (bcprov/jspecify)
            pickFirsts += listOf(
                "paymentrequest.proto",
                "META-INF/versions/9/OSGI-INF/MANIFEST.MF"
            )
        }
        jniLibs {
            useLegacyPackaging = false
        }
    }

    lint {
        baseline = file("lint-baseline.xml")
        abortOnError = false
        checkReleaseBuilds = false
    }
}

// Keep bitcoinj consistent across the graph
configurations.all {
    resolutionStrategy {
        force("org.bitcoinj:bitcoinj-core:$bitcoinjVersion")
    }
}

dependencies {
    // ---- Compose BOM ----
    implementation(platform(libs.androidx.compose.bom))

    // ---- Compose UI (via catalog + BOM) ----
    implementation(libs.bundles.compose)
    implementation(libs.androidx.compose.foundation)
    debugImplementation(libs.androidx.compose.ui.tooling)

    // ---- AndroidX Core / Lifecycle / Navigation ----
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.appcompat)

    // Lifecycle
    implementation(libs.bundles.lifecycle)
    implementation(libs.androidx.lifecycle.runtime.compose)

    // Navigation
    implementation(libs.androidx.navigation.compose)

    // ---- Hilt + Navigation ----
    implementation("com.google.dagger:hilt-android:2.51.1")
    // Use KAPT for Hilt to avoid KSP NonExistentClass issues
    kapt("com.google.dagger:hilt-compiler:2.51.1")
    implementation("androidx.hilt:hilt-navigation-compose:1.2.0")

    // ---- Permissions (Accompanist) ----
    implementation(libs.accompanist.permissions)

    // ---- Material / ConstraintLayout ----
    implementation(libs.material)
    implementation(libs.constraintlayout)

    // ---- Bluetooth (Nordic) ----
    implementation(libs.nordic.ble)

    // ---- Coroutines ----
    implementation(libs.kotlinx.coroutines.android)

    // ---- Security / Cryptography ----
    implementation(libs.androidx.security.crypto)
    implementation(libs.bundles.cryptography)

    // ---- JSON ----
    implementation(libs.gson)

    // ---- ZXing ----
    implementation(libs.zxing.core)

    // ---- Logging ----
    implementation(libs.slf4j.api)
    implementation(libs.slf4j.simple)

    // ---- Dogecoin (libdohj snapshot jar + bitcoinj 0.16.1) ----
    // Ensure this file exists: app/libs/libdohj-core-0.16-SNAPSHOT.jar
    implementation(files("libs/libdohj-core-0.16-SNAPSHOT.jar"))

    // bitcoinj must match libdohj's target; exclude its older bcprov
    implementation("org.bitcoinj:bitcoinj-core:$bitcoinjVersion") {
        exclude(group = "org.bouncycastle", module = "bcprov-jdk15to18")
    }

    // libdohj runtime deps
    implementation(libs.scrypt)
    implementation(libs.protobuf.javalite)

    // ---- Networking ----
    implementation(libs.okhttp)

    // ---- Tor stacks ----
    // Arti (Rust-based Tor)
    implementation(libs.arti.mobile.ex)

    // ---- Location ----
    implementation(libs.gms.location)

    // ---- Compression ----
    implementation(libs.lz4)

    // ---- Testing ----
    testImplementation(libs.bundles.testing)

    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.bundles.compose.testing)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.espresso.core)

    // If you enabled desugaring above, add:
    // coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.0.4")
}

// KAPT configuration for Hilt
kapt {
    correctErrorTypes = true
}