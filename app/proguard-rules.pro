# DIAGNOSTIC R8 CONFIG (ChatGPT suggestion - 2026-10-08)
# Purpose: Isolate whether R8's shrinking phase is the crash cause.
# -keep class ** keeps everything, but shrinking phase still runs.
# If this launches: shrinking was removing something needed.
# If this crashes: shrinking is NOT the cause.
-dontoptimize
-dontobfuscate

# Keep every program class, but DO NOT use -dontshrink.
-keep class ** { *; }

-keepattributes *Annotation*
-keepattributes Signature
-keepattributes InnerClasses
-keepattributes EnclosingMethod

-printusage build/outputs/logs/r8-usage.txt

# --- DontWarns (prevent build failures from absent optional deps) ---
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
# Gson & Google API Client keeps (needed for Drive JSON parsing)
-keepclassmembers class * {
    @com.google.api.client.util.Key <fields>;
    @com.google.gson.annotations.SerializedName <fields>;
}
