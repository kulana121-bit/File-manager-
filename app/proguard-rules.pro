# R8 / ProGuard rules for FilesApp

# 1. SLF4J / Logging
# org.slf4j.impl.StaticLoggerBinder is an optional logging binding that slf4j-api
# references statically at compile time (via LoggerFactory.bind()).
# Since this Android app relies on the default fallback (no-op/NOP logger) and
# does not package an active SLF4J backend binder, suppressing this warning is 100% safe.
-dontwarn org.slf4j.impl.StaticLoggerBinder

# 2. Gson & Google Play Services / API Client
# Keep attributes and class members used for JSON serialization and Google client keys
-keepattributes *Annotation*, Signature, InnerClasses, EnclosingMethod

-keepclassmembers class * {
    @com.google.api.client.util.Key <fields>;
    @com.google.gson.annotations.SerializedName <fields>;
}

# Keep Google API Client and Gson classes used in reflection / model mapping
-keep class com.google.api.client.** { *; }
-keep class com.google.api.services.drive.** { *; }
-keep class com.google.gson.** { *; }

-dontwarn com.google.api.client.**
-dontwarn com.google.api.services.drive.**
-dontwarn org.apache.http.**

# 3. Apache Commons Compress & XZ & Junrar
# Suppress warnings for optional / alternative classes not present on Android (like native bindings or optional libraries)
-dontwarn org.apache.commons.compress.**
-dontwarn org.tukaani.xz.**
-dontwarn com.github.junrar.**

# 4. Commons Net (FTP)
-dontwarn org.apache.commons.net.**

# 5. PDFBox Android
# PDFBox references desktop Java AWT classes (like java.awt.print.PrinterJob) which are absent on Android.
# Suppressing these warnings is required for correct R8 compilation.
-dontwarn com.tom_roush.pdfbox.**

# 6. ZXing QR Code
-dontwarn com.google.zxing.**

# 7. WorkManager
-dontwarn androidx.work.**

# 8. Keep specific data models to avoid shrinking issues during local JSON serialization (e.g. Backup history, duplicates)
-keep class com.example.filesapp.data.** { *; }
-keep class com.example.filesapp.domain.backup.BackupJobConfig { *; }
-keep class com.example.filesapp.domain.backup.BackupHistoryRecord { *; }
-keep class com.example.filesapp.domain.backup.BackupExecutionProgress { *; }
