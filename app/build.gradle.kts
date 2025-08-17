plugins {
    id("com.android.application") version "8.7.1"
    id("org.jetbrains.kotlin.android") version "2.0.0"
    id("org.jetbrains.kotlin.plugin.parcelize") version "2.0.0"
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.0"

    // Hilt + KSP
    id("com.google.devtools.ksp") version "2.0.0-1.0.24"
    id("com.google.dagger.hilt.android") version "2.51.1"
}

val bitcoinjVersion = "0.16.1" // must match libdohj's bitcoinj target

android {
    namespace = "com.dogechat.android"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.dogechat.android"
        minSdk = 26
        targetSdk = 34
        versionCode = 8
        versionName = "0.8.2" // first signed release for dogechat

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables.useSupportLibrary = true
    }

    signingConfigs {
        create("release") {
            storeFile = file("dogechat-release-key.jks")
            storePassword = "YOUR_KEYSTORE_PASSWORD"  // Replace with your actual keystore password
            keyAlias = "dogechat-key"
            keyPassword = "YOUR_KEY_PASSWORD"  // Replace with your actual key password
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

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }

    packaging {
        resources {
            // Avoid resource merge conflict for paymentrequest.proto (present in multiple jars).
            // Pick first copy so the merge step doesn't fail.
            pickFirsts += listOf("paymentrequest.proto")

            // keep existing excludes
            excludes += listOf("META-INF/AL2.0", "META-INF/LGPL2.1")
        }
    }

    lint {
        baseline = file("lint-baseline.xml")
        abortOnError = false
        checkReleaseBuilds = false
    }
}

// Force consistent bitcoinj across dependency graph so we don't have two versions
configurations.all {
    resolutionStrategy {
        force("org.bitcoinj:bitcoinj-core:$bitcoinjVersion")
    }
}

dependencies {
    // Compose BOM
    implementation(platform("androidx.compose:compose-bom:2024.05.00"))

    // Compose + Material 3
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended:1.6.0")

    // Lifecycle
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.0")

    // Core Android
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.compose.runtime:runtime-livedata")

    // Navigation
    implementation("androidx.navigation:navigation-compose:2.7.7")

    // Hilt & KSP
    implementation("com.google.dagger:hilt-android:2.51.1")
    ksp("com.google.dagger:hilt-compiler:2.51.1")
    implementation("androidx.hilt:hilt-navigation-compose:1.2.0")

    // Permissions (accompanist)
    implementation("com.google.accompanist:accompanist-permissions:0.34.0")

    // Material & layouts
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.constraintlayout:constraintlayout:2.2.0")

    // Bluetooth
    implementation("no.nordicsemi.android:ble:2.7.2")

    // Logging
    implementation("org.slf4j:slf4j-api:2.0.7")
    implementation("org.slf4j:slf4j-simple:2.0.7")

    // JSON
    implementation("com.google.code.gson:gson:2.10.1")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // Security
    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    // Local libdohj 0.16 SNAPSHOT (built and included this jar)
    implementation(files("libs/libdohj-core-0.16-SNAPSHOT.jar"))

    // Ensure bitcoinj version matches libdohj (0.16.1)
    implementation("org.bitcoinj:bitcoinj-core:$bitcoinjVersion") {
        // exclude bcprov from bitcoinj since we provide a newer bcprov
        exclude(group = "org.bouncycastle", module = "bcprov-jdk15to18")
    }

    // Add direct dependencies libdohj-core expects at runtime
    implementation("com.google.protobuf:protobuf-javalite:3.18.0")
    implementation("com.squareup.okhttp3:okhttp:3.14.9")

    // Crypto provider and utilities
    implementation("org.bouncycastle:bcprov-jdk18on:1.81")
    implementation("com.google.guava:guava:31.1-android")

    // Compression
    implementation("org.lz4:lz4-java:1.8.0")

    // Testing
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation(platform("androidx.compose:compose-bom:2024.05.00"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")

    // Debug tooling
    debugImplementation("androidx.compose.ui:ui-tooling")
}
