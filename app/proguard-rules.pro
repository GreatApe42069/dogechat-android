# Core project specifics
-keep class com.dogechat.android.protocol.** { *; }
-keep class com.dogechat.android.crypto.** { *; }

-dontwarn org.bouncycastle.**
-keep class org.bouncycastle.** { *; }

# Identity manager (reflection safety)
-keep class com.dogechat.android.identity.SecureIdentityStateManager {
    private android.content.SharedPreferences prefs;
    *;
}

# Reflection-heavy / model packages
-keep class com.dogechat.android.favorites.** { *; }
-keep class com.dogechat.android.nostr.** { *; }
-keep class com.dogechat.android.identity.** { *; }

# Tor / Arti
-keep class info.guardianproject.arti.** { *; }
-keep class org.torproject.jni.** { *; }
-keepnames class org.torproject.jni.**
-dontwarn info.guardianproject.arti.**
-dontwarn org.torproject.jni.**

# GSON models
-keepclassmembers class com.dogechat.android.** {
    public <fields>;
}

# Dagger / Hilt
-keep class dagger.** { *; }
-keep class javax.inject.** { *; }
-dontwarn dagger.**
-dontwarn javax.inject.**

# Protobuf lite
-keep class com.google.protobuf.** { *; }
-dontwarn com.google.protobuf.**

# SCrypt
-keep class com.lambdaworks.crypto.** { *; }
-keepclassmembers class com.lambdaworks.crypto.** { *; }

# bitcoinj (now supplied via dogecoinj-core jar)
-keep class org.bitcoinj.** { *; }
-dontwarn org.bitcoinj.**

# (Removed) libdohj keep rules unless still present:
# -keep class org.libdohj.** { *; }
# -dontwarn org.libdohj.**

# BouncyCastle already handled above
# OkHttp
-keep class okhttp3.** { *; }
-dontwarn okhttp3.**

# Room / Compose / Keep annotations
-keepclassmembers class * {
    @androidx.annotation.Keep *;
}
-keep @androidx.room.* class *
-keepclassmembers class * {
    @androidx.room.* <fields>;
}

# Compose runtime (defensive)
-keep class androidx.compose.runtime.** { *; }
-dontwarn androidx.compose.runtime.**