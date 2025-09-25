plugins {
    // Upgrade AGP and Kotlin to satisfy the new AndroidX deps
    id("com.android.application") version "8.6.1"
    id("org.jetbrains.kotlin.android") version "2.0.20"
    id("org.jetbrains.kotlin.plugin.parcelize") version "2.0.20"
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.20"

    // Keep annotation processors aligned with Kotlin 2.0.20
    id("org.jetbrains.kotlin.kapt") version "2.0.20"
    id("com.google.devtools.ksp") version "2.0.20-1.0.24"

    // Same Hilt version you’re using
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

        versionCode = 13
        versionName = "1.1.2"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    dependenciesInfo {
        includeInApk = false
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
        // AGP 8.6 runs on JDK 17; bytecode target 1.8 is fine for app code
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

    // For all filled/extended icons (AudioFile, AttachFile, Image, etc.)
    implementation("androidx.compose.material:material-icons-extended")

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
    implementation(files("libs/libdohj-core-0.16-SNAPSHOT.jar"))

    implementation("org.bitcoinj:bitcoinj-core:$bitcoinjVersion") {
        exclude(group = "org.bouncycastle", module = "bcprov-jdk15to18")
    }

    // libdohj runtime deps
    implementation(libs.scrypt)
    implementation(libs.protobuf.javalite)

    // ---- Networking ----
    implementation(libs.okhttp)

    // ---- Tor stacks ----
    implementation(libs.arti.mobile.ex)

    // ---- Location ----
    implementation(libs.gms.location)

    // ---- Compression ----
    implementation(libs.lz4)

    // ---- Testing ----
    testImplementation(libs.bundles.testing)

    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.bundles.compose.testing)
    debugImplementation(libs.androidx.compose.ui.tooling)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.espresso.core)

    // If you enabled desugaring above, add:
    // coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.0.4")
}

kapt {
    correctErrorTypes = true
}