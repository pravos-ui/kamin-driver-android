# Add project specific ProGuard rules here.

# Moshi — keep generated adapters and model classes
-keepclassmembers class com.pravos.kamindriver.api.model.** { *; }
-keep @com.squareup.moshi.JsonClass class * { *; }
-keepnames @com.squareup.moshi.JsonClass class *

# Retrofit
-keepattributes Signature
-keepattributes Exceptions
-keep class retrofit2.** { *; }
-keepclassmembernames interface * {
    @retrofit2.http.* <methods>;
}

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**

# Mapbox — do not obfuscate SDK internal classes
-keep class com.mapbox.** { *; }
-dontwarn com.mapbox.**
