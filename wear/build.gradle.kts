import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.parcelize)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.dogechat.watch"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.dogechat.watch"
        minSdk = 33 // Wear OS 4 (Pixel Watch 1+): the S+ Bluetooth permissions the app
        // declares only exist from API 31, and API 30 would additionally require location
        // for BLE scan results, which the app deliberately refuses.
        targetSdk = libs.versions.targetSdk.get().toInt()
        versionCode = 1
        versionName = "0.1.0"

        vectorDrawables {
            useSupportLibrary = true
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
            // Sign with the debug key so release builds can be installed over the
            // debug app during development (same signature = seamless upgrade).
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
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
    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
}

// Shared dogechat protocol stack: compiled from :app sources in place (never moved/copied by hand).
// AGP's source directory sets no longer support include/exclude filters, so a Sync task
// materializes a filtered mirror into build/sharedSrc and that directory is added as a source
// root. The app sources remain the single source of truth; extend the include list below (don't
// copy files into wear/src) when the compiler reveals a missing transitive dependency.
// Deliberately excluded: ui (except the DebugSettingsManager the mesh layer references),
// onboarding, nostr (except pure-Kotlin Bech32), net, geohash, wifi-aware, hotspot, voice
// features, and the phone's foreground service.
val sharedSourceIncludes = listOf(
    "com/dogechat/android/protocol/**",
    "com/dogechat/android/noise/**",
    "com/dogechat/android/crypto/**",
    "com/dogechat/android/identity/**",
    "com/dogechat/android/mesh/**",
    "com/dogechat/android/model/**",
    "com/dogechat/android/sync/**",
    "com/dogechat/android/favorites/**",
    "com/dogechat/android/services/AppStateStore.kt",
    "com/dogechat/android/services/ContactDirectory.kt",
    "com/dogechat/android/services/ContactIdentityResolver.kt",
    "com/dogechat/android/services/ConversationRepository.kt",
    "com/dogechat/android/services/ConversationStorageCipher.kt",
    "com/dogechat/android/services/PrivateMessageArrivalOrder.kt",
    "com/dogechat/android/services/SeenMessageStore.kt",
    "com/dogechat/android/services/VerificationService.kt",
    "com/dogechat/android/services/meshgraph/**",
    "com/dogechat/android/service/TransportBridgeService.kt",
    "com/dogechat/android/nostr/Bech32.kt",
    "com/dogechat/android/nostr/GeohashAliasRegistry.kt",
    "com/dogechat/android/features/file/FileUtils.kt",
    "com/dogechat/android/features/voice/**",
    "com/dogechat/android/ui/debug/DebugSettingsManager.kt",
    "com/dogechat/android/ui/debug/DebugPreferenceManager.kt",
    "com/dogechat/android/ui/NotificationTextUtils.kt",
    "com/dogechat/android/util/AppConstants.kt",
    "com/dogechat/android/util/ByteArrayExtensions.kt",
    "com/dogechat/android/util/ByteArrayWrapper.kt",
    "com/dogechat/android/util/BinaryEncodingUtils.kt",
)
val sharedSourceExcludes = listOf(
    "com/dogechat/android/model/FileSharingManager.kt",
    // Legacy phone monolith and Wi-Fi Aware multiplexer; the watch composes its own service
    // (MeshCore-style) in M2 instead of reusing these.
    "com/dogechat/android/mesh/BluetoothMeshService.kt",
    "com/dogechat/android/mesh/UnifiedMeshService.kt",
    // Phone permission policy additionally requires location (legacy BLE); the watch app
    // declares Bluetooth permissions only, so it ships its own same-FQN variant in
    // wear/src/main (Bluetooth-only check).
    "com/dogechat/android/mesh/BluetoothPermissionManager.kt",
)

val syncSharedAppSources = tasks.register<Sync>("syncSharedAppSources") {
    from("../app/src/main/java") {
        include(sharedSourceIncludes)
        exclude(sharedSourceExcludes)
    }
    into(layout.buildDirectory.dir("sharedSrc"))
}

// The app's own unit tests for the shared packages also run in the wear module, so shared
// behavior is continuously verified on both targets. Includes the app's JVM shims for
// android.util.Log/Base64 (app/src/test/kotlin/android) that the shared code needs on the JVM.
val syncSharedAppTests = tasks.register<Sync>("syncSharedAppTests") {
    from("../app/src/test/java") {
        include(
            "com/dogechat/android/protocol/**",
            "com/dogechat/android/crypto/**",
            "com/dogechat/android/mesh/**",
        )
    }
    from("../app/src/test/kotlin") {
        include(
            "android/**",
            "com/dogechat/android/mesh/**",
            "com/dogechat/FileTransferTest.kt",
        )
    }
    into(layout.buildDirectory.dir("sharedTestSrc"))
}

android {
    sourceSets {
        getByName("main") {
            java.srcDir("build/sharedSrc")
            kotlin.srcDir("build/sharedSrc")
        }
        getByName("test") {
            java.srcDir("build/sharedTestSrc")
            kotlin.srcDir("build/sharedTestSrc")
        }
    }
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    dependsOn(syncSharedAppSources)
}
tasks.matching { it.name.contains("UnitTest", ignoreCase = true) }.configureEach {
    dependsOn(syncSharedAppTests)
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_11)
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)

    // Wear Compose
    implementation(libs.androidx.wear.compose.foundation)
    implementation(libs.androidx.wear.compose.material3)
    implementation(libs.androidx.wear.tooling.preview)
    implementation(libs.androidx.compose.material.icons.extended)

    // Lifecycle
    implementation(libs.bundles.lifecycle)
    implementation(libs.androidx.lifecycle.process)

    // Coroutines
    implementation(libs.kotlinx.coroutines.android)

    // Cryptography (shared Noise/encryption stack)
    implementation(libs.bouncycastle.bcprov)

    // JSON (DogechatMessage model)
    implementation(libs.gson)

    // Security preferences (Noise identity persistence)
    implementation(libs.androidx.security.crypto)

    // Testing
    testImplementation(libs.bundles.testing)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
