# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
-keep class com.dogechat.android.protocol.** { *; }
-keep class com.dogechat.android.crypto.** { *; }
-dontwarn org.bouncycastle.**
-keep class org.bouncycastle.** { *; }

# Keep SecureIdentityStateManager from being obfuscated to prevent reflection issues
-keep class com.dogechat.android.identity.SecureIdentityStateManager {
    private android.content.SharedPreferences prefs;
    *;
}

# Keep all classes that might use reflection
-keep class com.dogechat.android.favorites.** { *; }
-keep class com.dogechat.android.nostr.** { *; }
-keep class com.dogechat.android.identity.** { *; }

# Keep model classes used in reflection (GSON)
-keepclassmembers class com.dogechat.android.** {
    public <fields>;
}

# Keep Dagger / Hilt generated classes
-keep class dagger.** { *; }
-keep class javax.inject.** { *; }
-dontwarn dagger.**
-dontwarn javax.inject.**

# Keep protobuf lite generated classes
-keep class com.google.protobuf.** { *; }
-dontwarn com.google.protobuf.**

# Keep SCrypt classes used by libdohj
-keep class com.lambdaworks.crypto.** { *; }
-keepclassmembers class com.lambdaworks.crypto.** { *; }

# Keep libdohj class
-keep class org.libdohj.** { *; }
-dontwarn org.libdohj.**

# Keep bitcoinj/net and crypto libs
-keep class org.bitcoinj.** { *; }
-dontwarn org.bitcoinj.**

# Keep BouncyCastle crypto providers
-keep class org.bouncycastle.** { *; }
-dontwarn org.bouncycastle.**

# OkHttp3 + logging
-keep class okhttp3.** { *; }
-dontwarn okhttp3.**

# Keep AndroidX Hilt/Room/LiveData reflection bits (if present)
-keepclassmembers class * {
    @androidx.annotation.Keep *;
}
-keep @androidx.room.* class *
-keepclassmembers class * {
    @androidx.room.* <fields>;
}

# Keep Compose runtime classes that may be reflection accessed
-keep class androidx.compose.runtime.** { *; }
-dontwarn androidx.compose.runtime.**
