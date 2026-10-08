# R8 / ProGuard rules for FilesApp
# NOTE: The app package is kept entirely (-keep com.example.filesapp.**).
# A previous "simplified" ruleset obfuscated app code and caused an
# immediate crash on launch ("Files keeps stopping"). Do NOT remove the
# broad app keep rule below.

# Disable R8's aggressive optimizations (method inlining etc.) which broke
# the app on launch.
-dontoptimize
# Disable obfuscation (class/method renaming) - R8's renaming breaks the app
# on launch (proven by nuclear test). Shrinking stays enabled for size.
-dontobfuscate

# 1. SLF4J / Logging
# org.slf4j.impl.StaticLoggerBinder is an optional logging binding that slf4j-api
# references statically at compile time (via LoggerFactory.bind()).
# The app relies on the default fallback (no-op/NOP logger); suppressing is safe.
-dontwarn org.slf4j.impl.StaticLoggerBinder

# 2. KEEP THE ENTIRE APP PACKAGE - prevents R8 from obfuscating app code.
# Jetpack Compose, Navigation, ViewModels and DI rely on reflection and
# compiler-generated code that breaks when app classes are renamed/removed.
-keep class com.example.filesapp.** { *; }
-keepattributes *Annotation*, Signature, InnerClasses, EnclosingMethod

# 3. Gson & Google Play Services / API Client
-keepclassmembers class * {
    @com.google.api.client.util.Key <fields>;
    @com.google.gson.annotations.SerializedName <fields>;
}
-keep class com.google.api.client.** { *; }
-keep class com.google.api.services.drive.** { *; }
-keep class com.google.gson.** { *; }
-dontwarn com.google.api.client.**
-dontwarn com.google.api.services.drive.**
-dontwarn org.apache.http.**

# 4. Jetpack Compose (extra safety on top of consumer rules)
-dontwarn androidx.compose.**

# 5. Kotlin Coroutines
-dontwarn kotlinx.coroutines.**

# 6. Coil Image Loader
-dontwarn coil.**

# 7. Apache Commons Compress & XZ & Junrar
-dontwarn org.apache.commons.compress.**
-dontwarn org.tukaani.xz.**
-dontwarn com.github.junrar.**

# 8. Commons Net (FTP)
-dontwarn org.apache.commons.net.**

# 9. PDFBox Android
# References desktop Java AWT classes absent on Android; suppressing is required.
-dontwarn com.tom_roush.pdfbox.**

# 10. ZXing QR Code
-dontwarn com.google.zxing.**

# 11. WorkManager
-dontwarn androidx.work.**
