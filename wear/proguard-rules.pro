# Gson reflection targets in the shared dogechat sources (persisted state payloads).
-keep class com.dogechat.android.favorites.** { *; }
-keep class com.dogechat.android.services.SeenMessageStore$* { *; }
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# Kotlin metadata needed by reflection-based serialization.
-keepattributes Signature, InnerClasses, EnclosingMethod

# Tink references JSR-305 annotations not present on Android.
-dontwarn javax.annotation.Nullable
-dontwarn javax.annotation.concurrent.GuardedBy
