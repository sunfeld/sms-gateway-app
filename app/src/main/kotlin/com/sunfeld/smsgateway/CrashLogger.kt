package com.sunfeld.smsgateway

import android.content.Context
import android.os.Build
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Persistent crash/error logger.
 * - Writes to local file (survives restarts)
 * - Sends to remote relay server at sms.sunfeld.nl/api/crash-report
 * - Viewable via "View Debug Log" in the app
 */
object CrashLogger {

    private const val TAG = "CrashLogger"
    private const val LOG_FILE = "crash_log.txt"
    private const val MAX_LINES = 500

    fun log(context: Context, tag: String, message: String, throwable: Throwable? = null) {
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(Date())
        val stackTrace = throwable?.let { "\n${it.stackTraceToString().take(2000)}" } ?: ""
        val entry = "[$timestamp] [$tag] $message$stackTrace\n"

        Log.d(TAG, entry.trimEnd())

        try {
            val file = getLogFile(context)
            file.appendText(entry)
            trimIfNeeded(file)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write crash log", e)
        }

        // Send to remote server asynchronously (best-effort)
        if (throwable != null || tag == "FATAL") {
            sendRemote(context, tag, entry)
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
     * Logs the crash to file AND sends to remote server before the app dies.
     */
    fun installGlobalHandler(context: Context) {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            log(context, "FATAL", "Uncaught exception on ${thread.name}: ${throwable.message}", throwable)
            // Give remote send a moment to complete
            try { Thread.sleep(500) } catch (_: Exception) { }
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }

    /**
     * Send crash log to sms.sunfeld.nl/api/crash-report (best-effort, async)
     */
    private fun sendRemote(context: Context, tag: String, logEntry: String) {
        Thread {
            try {
                val url = java.net.URL("${Config.RELAY_BASE_URL}/api/crash-report")
                val conn = url.openConnection() as java.net.HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/json")
                conn.doOutput = true
                conn.connectTimeout = 5000
                conn.readTimeout = 5000

                val json = org.json.JSONObject().apply {
                    put("device", "${Build.MANUFACTURER} ${Build.MODEL}")
                    put("sdk", Build.VERSION.SDK_INT)
                    put("tag", tag)
                    put("log", logEntry)
                    put("timestamp", SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ", Locale.US).format(Date()))
                }

                conn.outputStream.use { it.write(json.toString().toByteArray()) }
                val code = conn.responseCode
                Log.d(TAG, "Remote crash report sent: HTTP $code")
            } catch (e: Exception) {
                Log.w(TAG, "Failed to send remote crash report: ${e.message}")
            }
        }.start()
    }
}
