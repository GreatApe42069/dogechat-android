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

        versionCode = 11
        versionName = "0.9.5"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables.useSupportLibrary = true
    }

    signingConfigs {
        create("release") {
            storeFile = file("dogechat-release-key.jks")
            storePassword = "Your_Keystore_pass_goes_Here"
            keyAlias = "dogechat-key"
            keyPassword = "Your_Key_Pass_goes_Here"
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
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        // If you target devices < 26 and use java.time from dependencies, consider enabling desugaring:
        // isCoreLibraryDesugaringEnabled = true
    }
    kotlinOptions { jvmTarget = "17" }

    buildFeatures { compose = true }

    packaging {
        resources {
            // Existing rules
            pickFirsts += listOf(
                "paymentrequest.proto",
                // Resolve duplicate merge for multi-release jars (bcprov/jspecify)
                "META-INF/versions/9/OSGI-INF/MANIFEST.MF"
            )
            excludes += listOf(
                "META-INF/AL2.0",
                "META-INF/LGPL2.1",
                // Keep native Tor/Arti libs: DO NOT exclude lib/** or root/**
                "**/*.dylib"
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

    // ---- Compose UI ----
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.runtime:runtime-livedata")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")

    // ---- AndroidX Core / Lifecycle / Navigation ----
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation("androidx.lifecycle:lifecycle-runtime-compose")
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.livedata.ktx)

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

    // ---- Security / Crypto ----
    implementation(libs.androidx.security.crypto)
    implementation(libs.bouncycastle.bcprov)
    implementation(libs.google.tink.android)

    // ---- JSON ----
    implementation(libs.gson)

    // ---- ZXing ----
    implementation("com.google.zxing:core:3.5.1")

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
    implementation("com.lambdaworks:scrypt:1.4.0")
    implementation("com.google.protobuf:protobuf-javalite:3.18.0")

    // ---- Networking ----
    implementation("com.squareup.okhttp3:okhttp:3.14.9")

    // ---- Tor stacks ----
    // Classic Tor (native)
    implementation("org.torproject:tor-android-binary:0.4.4.6")
    // Arti (Rust-based Tor)
    implementation("info.guardianproject:arti-mobile-ex:1.2.3")

    // ---- Location ----
    implementation("com.google.android.gms:play-services-location:21.2.0")

    // ---- Compression ----
    implementation("org.lz4:lz4-java:1.8.0")

    // ---- Testing ----
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.espresso.core)

    // If you enabled desugaring above, add:
    // coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.0.4")
}

// KAPT configuration for Hilt
kapt {
    correctErrorTypes = true
}