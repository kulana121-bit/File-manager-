# TARGETED R8 CONFIG (2026-10-08)
# Diagnostic proved: -keep ** works, so shrinking removes something needed.
# Now using targeted keeps instead of global -keep **.
-dontoptimize
-dontobfuscate

# --- App code ---
-keep class com.example.filesapp.** { *; }
# Keep the Application class (crash trap must survive R8)
-keep class com.example.filesapp.FilesApp { *; }

# --- Startup path (ChatGPT suggestion) ---
-keep class com.google.android.gms.auth.api.signin.** { *; }
-keep class androidx.activity.** { *; }
-keep class androidx.core.** { *; }

# --- Reflection/SPI libraries (Gemini suggestion) ---
# ServiceLoader providers
-keep class * implements java.util.ServiceLoader$Provider { *; }
# Apache Commons Compress (archive handling)
-keep class org.apache.commons.compress.** { *; }
# SLF4J (logging)
-keep class org.slf4j.** { *; }
# Google API Client & Guava (Drive + reflection)
-keep class com.google.api.client.** { *; }
-keep class com.google.common.** { *; }
-keepclassmembers class * {
    @com.google.api.client.util.Key <fields>;
    @com.google.gson.annotations.SerializedName <fields>;
}
# PDFBox (reflection for fonts/assets)
-keep class com.tom_roush.pdfbox.** { *; }
# AndroidX Startup initializers
-keep class * extends androidx.startup.Initializer {
    <init>();
}

-keepattributes *Annotation*
-keepattributes Signature
-keepattributes InnerClasses
-keepattributes EnclosingMethod

# --- DontWarns (prevent build failures) ---
-dontwarn org.slf4j.impl.StaticLoggerBinder
-dontwarn org.slf4j.impl.StaticMDCBinder
-dontwarn org.slf4j.impl.StaticMarkerBinder
-dontwarn java.lang.invoke.MethodHandleProxies
-dontwarn java.lang.reflect.AnnotatedType
-dontwarn javax.servlet.**
-dontwarn org.apache.avalon.framework.**
-dontwarn org.apache.log.**
-dontwarn org.apache.log4j.**
-dontwarn com.google.api.client.**
-dontwarn com.google.api.services.drive.**
-dontwarn org.apache.http.**
-dontwarn androidx.compose.**
-dontwarn kotlinx.coroutines.**
-dontwarn coil.**
-dontwarn org.apache.commons.compress.**
-dontwarn org.tukaani.xz.**
-dontwarn com.github.junrar.**
-dontwarn org.apache.commons.net.**
-dontwarn com.tom_roush.pdfbox.**
-dontwarn com.google.zxing.**
-dontwarn androidx.work.**
