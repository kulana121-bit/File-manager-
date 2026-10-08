# WORKING R8 CONFIG (2026-10-08)
# This is the proven working configuration from release-21-1.
# -keep ** keeps all classes, R8 shrinking still runs but removes nothing critical.
# Size: 61MB. Works reliably. Size optimization deferred to later.
-dontoptimize
-dontobfuscate

# Keep every program class (proven to work)
-keep class ** { *; }

-keepattributes *Annotation*
-keepattributes Signature
-keepattributes InnerClasses
-keepattributes EnclosingMethod

# Gson & Google API Client keeps (needed for Drive JSON parsing)
-keepclassmembers class * {
    @com.google.api.client.util.Key <fields>;
    @com.google.gson.annotations.SerializedName <fields>;
}

# --- DontWarns (prevent build failures from absent optional deps) ---
-dontwarn net.lingala.zip4j.**
-keep class net.lingala.zip4j.** { *; }
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
