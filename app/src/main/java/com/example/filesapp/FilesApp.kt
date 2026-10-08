package com.example.filesapp

import android.app.Application
import java.io.File

/**
 * Emergency crash trap (Gemini suggestion - 2026-10-08).
 * Catches uncaught exceptions (e.g. R8-stripped classes) and writes the
 * stacktrace to a file the user can share. Registered in AndroidManifest.
 */
class FilesApp : Application() {
    override fun onCreate() {
        super.onCreate()
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val crashFile = File(filesDir, "r8_crash.txt")
                crashFile.writeText(
                    "Thread: ${thread.name}\n" +
                    throwable.stackTraceToString()
                )
            } catch (_: Exception) {
                // If we can't write the file, just crash normally
            }
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }
}
