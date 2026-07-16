package org.fossify.gallery.helpers

import android.app.Application
import android.content.Context
import android.os.Build
import org.fossify.gallery.BuildConfig
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Persists uncaught-exception stacktraces to app-internal storage so crashes on a device without
 * any store console attached don't vanish without a trace. Chains to the previously installed
 * handler (the system's), so the normal crash dialog / process kill still happens - this only
 * observes. Logs are viewable and shareable from Settings > Information.
 */
object CrashLogger {

    private const val MAX_LOGS = 5

    fun logDir(context: Context) = File(context.filesDir, "crash_logs")

    fun install(app: Application) {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            // Never let logging itself break crash delivery to the system handler.
            try {
                writeLog(app, thread, throwable)
            } catch (_: Exception) {
            }
            previous?.uncaughtException(thread, throwable)
        }
    }

    /** Newest first. */
    fun logs(context: Context): List<File> =
        logDir(context).listFiles()?.sortedByDescending { it.name } ?: emptyList()

    fun clear(context: Context) {
        logDir(context).listFiles()?.forEach { it.delete() }
    }

    private fun writeLog(app: Application, thread: Thread, throwable: Throwable) {
        val dir = logDir(app).apply { mkdirs() }
        val timestamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(Date())
        val stacktrace = StringWriter().also { throwable.printStackTrace(PrintWriter(it)) }.toString()
        File(dir, "crash_$timestamp.txt").writeText(
            """
            App: ${BuildConfig.APPLICATION_ID} ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}, ${BuildConfig.FLAVOR}${if (BuildConfig.DEBUG) ", debug" else ""})
            Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})
            Device: ${Build.MANUFACTURER} ${Build.MODEL}
            Thread: ${thread.name}
            Time: $timestamp

            $stacktrace
            """.trimIndent()
        )
        logs(app).drop(MAX_LOGS).forEach { it.delete() }
    }
}
