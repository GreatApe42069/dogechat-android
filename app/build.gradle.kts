plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.parcelize)
    alias(libs.plugins.kotlin.compose)

    // Hilt + KSP
    id("com.google.devtools.ksp") version "2.0.0-1.0.24"
    id("com.google.dagger.hilt.android") version "2.51.1"
}

val bitcoinjVersion = libs.versions.bitcoinj.get() // 0.16.1

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
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }

    buildFeatures { compose = true }

    packaging {
        resources {
            pickFirsts += listOf("paymentrequest.proto")
            excludes += listOf("META-INF/AL2.0", "META-INF/LGPL2.1")
            excludes += listOf("**/*.dylib", "root/**", "lib/**")
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

// Force consistent bitcoinj version
configurations.all {
    resolutionStrategy {
        force("org.bitcoinj:bitcoinj-core:$bitcoinjVersion")
    }
}

dependencies {
    // ---- Compose BOM & UI ----
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.runtime.livedata)
    debugImplementation(libs.androidx.compose.ui.tooling)

    // ---- AndroidX Core / Lifecycle / Navigation ----
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.livedata.ktx)

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

    // ---- Logging ----
    implementation(libs.slf4j.api)
    implementation(libs.slf4j.simple)

    // ---- Local libdohj 0.16 SNAPSHOT ----
    implementation(files("libs/libdohj-core-0.16-SNAPSHOT.jar"))
    implementation("com.lambdaworks:scrypt:1.4.0")
    implementation("org.bitcoinj:bitcoinj-core:$bitcoinjVersion") {
        exclude(group = "org.bouncycastle", module = "bcprov-jdk15to18")
    }
    implementation("com.google.protobuf:protobuf-javalite:3.18.0")

    // ---- WebSocket / HTTP ----
    implementation(libs.okhttp)
    implementation(libs.tor.android.binary)

    // ---- Google Play Services ----
    implementation(libs.gms.location)

    // ---- Compression ----
    implementation("org.lz4:lz4-java:1.8.0")

    // ---- Testing ----
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.espresso.core)
}
