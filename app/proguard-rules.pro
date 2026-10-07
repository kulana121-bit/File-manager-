# Base Proguard / R8 rules for production Android app
-optimizationpasses 5
-dontusemixedcaseclassnames
-dontskipnonpubliclibraryclasses
-verbose

# Keep application package and data models
-keep class com.example.filesapp.** { *; }
-keep class com.example.filesapp.data.** { *; }
-keep class com.example.filesapp.domain.** { *; }

# Kotlin Coroutines
-keepattributes *Annotation*, InnerClasses, EnclosingMethod, Signature
-keepclassmembers class kotlinx.coroutines.** { *; }
-dontwarn kotlinx.coroutines.**

# AndroidX Compose
-keep class androidx.compose.** { *; }
-dontwarn androidx.compose.**

# Coil Image Loader
-keep class coil.** { *; }
-dontwarn coil.**

# Google Play Services & Drive API / Google API Client & Gson
-keep class com.google.android.gms.** { *; }
-keep class com.google.api.client.** { *; }
-keep class com.google.api.services.drive.** { *; }
-keepclassmembers class * {
    @com.google.api.client.util.Key <fields>;
}
-keep class com.google.gson.** { *; }
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}
-dontwarn com.google.api.client.**
-dontwarn com.google.api.services.drive.**
-dontwarn org.apache.http.**

# Apache Commons Compress & XZ & Junrar
-keep class org.apache.commons.compress.** { *; }
-keep class org.tukaani.xz.** { *; }
-keep class com.github.junrar.** { *; }
-dontwarn org.apache.commons.compress.**
-dontwarn org.tukaani.xz.**
-dontwarn com.github.junrar.**

# Commons Net (FTP)
-keep class org.apache.commons.net.** { *; }
-dontwarn org.apache.commons.net.**

# PDFBox Android
-keep class com.tom_roush.pdfbox.** { *; }
-dontwarn com.tom_roush.pdfbox.**

# ZXing QR Code
-keep class com.google.zxing.** { *; }
-dontwarn com.google.zxing.**
