plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.parcelize)
    alias(libs.plugins.kotlin.compose)

    // Hilt + KSP
    id("com.google.devtools.ksp") version "2.0.0-1.0.24"
    id("com.google.dagger.hilt.android") version "2.51.1"
}

val bitcoinjVersion = libs.versions.bitcoinj.get() // // must match libdohj's bitcoinj target

android {
    namespace = "com.dogechat.android"
    compileSdk = libs.versions.compileSdk.get().toInt() // 35

    defaultConfig {
        applicationId = "com.dogechat.android"
        minSdk = libs.versions.minSdk.get().toInt() // 26
        targetSdk = libs.versions.targetSdk.get().toInt() // 34

        versionCode = 11
        versionName = "0.9.5"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables.useSupportLibrary = true
    }

    signingConfigs {
        create("release") {
            storeFile = file("dogechat-release-key.jks")
            storePassword = "Your_Keystore_pass_goes_Here"  // Replace with your actual keystore password
            keyAlias = "dogechat-key"
            keyPassword = "Your_Key_Pass_goes_Here"  // Replace with your actual key password
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
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }

    buildFeatures { compose = true }

    packaging {
        resources {
            // Keep existing pick-first rules and excludes
            pickFirsts += listOf("paymentrequest.proto")
            excludes += listOf("META-INF/AL2.0", "META-INF/LGPL2.1")
            // --- QUICK / SAFE FIX ---
            // Exclude macOS / non-Android native files and reserved root/lib paths
            // This prevents .dylib or 'root/lib' entries from being packaged into the AAB
            excludes += listOf("**/*.dylib", "root/**", "lib/**")
        }
        // Use modern jni packaging (avoid legacy packaging putting files into reserved paths)
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

// Force consistent bitcoinj version
configurations.all {
    resolutionStrategy {
        force("org.bitcoinj:bitcoinj-core:$bitcoinjVersion")
    }
}

dependencies {
    // ---- Compose BOM & UI ----
    implementation(platform("androidx.compose:compose-bom:2024.05.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended:1.6.0")
    implementation("androidx.compose.runtime:runtime-livedata")
    debugImplementation("androidx.compose.ui:ui-tooling")

    // ---- AndroidX Core / Lifecycle / Navigation ----
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.navigation:navigation-compose:2.7.7")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.0")


    // ---- Hilt + Navigation ----
    implementation("com.google.dagger:hilt-android:2.51.1")
    ksp("com.google.dagger:hilt-compiler:2.51.1")
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

    // ZXing core for QR generation
    implementation("com.google.zxing:core:3.5.1")

    // ---- Logging ----
    implementation(libs.slf4j.api)
    implementation(libs.slf4j.simple)

    // ---- Local libdohj 0.16 SNAPSHOT (built and included this jar) ----
    implementation(files("libs/libdohj-core-0.16-SNAPSHOT.jar"))
    implementation("com.lambdaworks:scrypt:1.4.0")
    // Ensure bitcoinj version matches libdohj (0.16.1)
    implementation("org.bitcoinj:bitcoinj-core:$bitcoinjVersion") {
        // exclude bcprov from bitcoinj since we provide a newer bcprov
        exclude(group = "org.bouncycastle", module = "bcprov-jdk15to18")
    }
    // Add direct dependencies libdohj-core expects at runtime
    implementation("com.google.protobuf:protobuf-javalite:3.18.0")

    // ---- WebSocket / HTTP (upstream uses OkHttp). 3.14.x still fine. ----
    implementation("com.squareup.okhttp3:okhttp:3.14.9")

    // Arti (Tor in Rust) Android bridge - use published AAR with native libs
    implementation("info.guardianproject:arti-mobile-ex:1.2.3")

    // ---- Google Play Services Location (for geohash features) ----
    implementation("com.google.android.gms:play-services-location:21.2.0")

    // ---- Compression ----
    implementation("org.lz4:lz4-java:1.8.0")

    // ---- Testing ----
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.espresso.core)
}