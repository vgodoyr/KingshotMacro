package com.kingshot.macro

import android.app.Application
import android.os.Environment
import android.util.Log
import android.widget.Toast
import java.io.File
import java.io.FileWriter
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Application class with a global uncaught exception handler.
 * Captures ANY crash on the main or background threads — including crashes
 * during class loading, static initialization, or onCreate — and writes the
 * stack trace to a file the user can retrieve via USB or file manager.
 *
 * File: /sdcard/Download/kingshot_crash.log
 * Backup file: app's internal cache dir (always writable, no permission needed).
 */
class KingshotApp : Application() {

    companion object {
        const val BUILD_TAG = "v3-uncaught-handler"
    }

    override fun onCreate() {
        super.onCreate()

        // Install handler ASAP so any subsequent crash on any thread is captured.
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                writeCrashReport(thread, throwable)
            } catch (_: Throwable) {
                // never let the handler itself loop
            }
            previous?.uncaughtException(thread, throwable)
        }

        Log.i("KingshotApp", "Application started — build $BUILD_TAG")

        // Visible signal: the app's process is alive and reaching Application.onCreate
        try {
            Toast.makeText(this, "Kingshot Macro $BUILD_TAG iniciando…", Toast.LENGTH_LONG).show()
        } catch (_: Throwable) {
            // Toast may fail in unusual contexts, ignore
        }
    }

    private fun writeCrashReport(thread: Thread, throwable: Throwable) {
        val sw = StringWriter()
        val pw = PrintWriter(sw)
        val ts = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
        pw.println("=== Kingshot Macro crash report ($BUILD_TAG) ===")
        pw.println("Time: $ts")
        pw.println("Thread: ${thread.name}")
        pw.println("Build.MANUFACTURER: ${android.os.Build.MANUFACTURER}")
        pw.println("Build.MODEL: ${android.os.Build.MODEL}")
        pw.println("Build.VERSION.SDK_INT: ${android.os.Build.VERSION.SDK_INT}")
        pw.println("--- Stack trace ---")
        throwable.printStackTrace(pw)
        pw.flush()
        val report = sw.toString()

        // 1. Always log to logcat
        Log.e("KingshotApp", report)

        // 2. Try external Downloads (visible to user)
        try {
            val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            if (dir != null && (dir.exists() || dir.mkdirs())) {
                FileWriter(File(dir, "kingshot_crash.log"), true).use { it.write(report + "\n") }
            }
        } catch (_: Throwable) { /* ignore — fall through to internal */ }

        // 3. Always write to app's internal files dir as a backup
        try {
            FileWriter(File(filesDir, "kingshot_crash.log"), true).use { it.write(report + "\n") }
        } catch (_: Throwable) { /* if this fails, we've done all we reasonably can */ }

        // 4. Show a Toast so the user gets immediate feedback
        try {
            Toast.makeText(
                this,
                "Crash detectado. Reporte en Downloads/kingshot_crash.log",
                Toast.LENGTH_LONG
            ).show()
        } catch (_: Throwable) { /* ignore */ }
    }
}
