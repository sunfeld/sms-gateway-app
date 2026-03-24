package com.sunfeld.smsgateway

import android.content.Context
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Persistent crash/error logger that writes to a file on the device.
 * Survives app restarts — viewable in the app's debug log screen.
 */
object CrashLogger {

    private const val TAG = "CrashLogger"
    private const val LOG_FILE = "crash_log.txt"
    private const val MAX_LINES = 500

    fun log(context: Context, tag: String, message: String, throwable: Throwable? = null) {
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(Date())
        val stackTrace = throwable?.let { "\n${it.stackTraceToString().take(1000)}" } ?: ""
        val entry = "[$timestamp] [$tag] $message$stackTrace\n"

        Log.d(TAG, "Logging: $entry")

        try {
            val file = getLogFile(context)
            file.appendText(entry)
            trimIfNeeded(file)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write crash log", e)
        }
    }

    fun readLog(context: Context): String {
        return try {
            val file = getLogFile(context)
            if (file.exists()) file.readText() else "No log entries"
        } catch (e: Exception) {
            "Error reading log: ${e.message}"
        }
    }

    fun clearLog(context: Context) {
        try {
            getLogFile(context).writeText("")
        } catch (_: Exception) { }
    }

    private fun getLogFile(context: Context): File {
        return File(context.filesDir, LOG_FILE)
    }

    private fun trimIfNeeded(file: File) {
        try {
            val lines = file.readLines()
            if (lines.size > MAX_LINES) {
                file.writeText(lines.takeLast(MAX_LINES).joinToString("\n") + "\n")
            }
        } catch (_: Exception) { }
    }

    /**
     * Install as global uncaught exception handler.
     * Logs the crash to file before the app dies.
     */
    fun installGlobalHandler(context: Context) {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            log(context, "FATAL", "Uncaught exception on ${thread.name}: ${throwable.message}", throwable)
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }
}
