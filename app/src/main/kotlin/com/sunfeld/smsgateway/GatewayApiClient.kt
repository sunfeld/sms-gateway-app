package com.sunfeld.smsgateway

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * HTTP client for communicating with the Mission Control API
 * to trigger and monitor SMS Gateway installation.
 */
class GatewayApiClient(
    private val baseUrl: String = Config.BASE_URL
) {
    companion object {
        const val PROJECT_NAME = "sms-gateway-app"
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    data class InstallResult(
        val ok: Boolean,
        val jobId: String,
        val status: String,
        val message: String,
        val statusUrl: String
    )

    data class JobStatus(
        val id: String,
        val status: String,
        val error: String?,
        val apk: String?
    )

    data class ProjectStatus(
        val name: String,
        val gatewayActive: Boolean
    )

    data class BluetoothDosStartResult(
        val status: String,
        val sessionId: String,
        val duration: Int,
        val intensity: Int,
        val targetsDiscovered: Int
    )

    data class BluetoothDosStatus(
        val sessionId: String,
        val status: String,
        val packetsSent: Int,
        val targetsActive: Int,
        val remainingSeconds: Int,
        val intensity: Int
    )

    data class BluetoothDosStopResult(
        val status: String,
        val sessionId: String
    )

    /**
     * Triggers the gateway install via POST /api/projects/{name}/install-gateway.
     * Returns InstallResult on success, throws IOException on failure.
     */
    @Throws(IOException::class)
    fun installGateway(): InstallResult {
        val body = "{}".toRequestBody(JSON_MEDIA_TYPE)
        val request = Request.Builder()
            .url("$baseUrl/api/projects/$PROJECT_NAME/install-gateway")
            .post(body)
            .build()

        client.newCall(request).execute().use { response ->
            val responseBody = response.body?.string()
                ?: throw IOException("Empty response body")

            if (!response.isSuccessful) {
                val errorMsg = try {
                    JSONObject(responseBody).optString("error", "Unknown error")
                } catch (e: Exception) {
                    responseBody
                }
                throw IOException("Install request failed (${response.code}): $errorMsg")
            }

            val json = JSONObject(responseBody)
            return InstallResult(
                ok = json.optBoolean("ok", false),
                jobId = json.optString("jobId", ""),
                status = json.optString("status", ""),
                message = json.optString("message", ""),
                statusUrl = json.optString("statusUrl", "")
            )
        }
    }

    /**
     * Fetches the project status from the active projects list.
     * Returns whether the SMS gateway is active for this project.
     */
    @Throws(IOException::class)
    fun getProjectStatus(): ProjectStatus {
        val request = Request.Builder()
            .url("$baseUrl/api/projects/active")
            .get()
            .build()

        client.newCall(request).execute().use { response ->
            val responseBody = response.body?.string()
                ?: throw IOException("Empty response body")

            if (!response.isSuccessful) {
                throw IOException("Project status check failed (${response.code})")
            }

            val jsonArray = org.json.JSONArray(responseBody)
            for (i in 0 until jsonArray.length()) {
                val project = jsonArray.getJSONObject(i)
                if (project.optString("name") == PROJECT_NAME) {
                    val gatewayAvailable = project.optBoolean("sms_gateway_available", false)
                    return ProjectStatus(
                        name = PROJECT_NAME,
                        gatewayActive = gatewayAvailable
                    )
                }
            }

            return ProjectStatus(name = PROJECT_NAME, gatewayActive = false)
        }
    }

    /**
     * Starts a Bluetooth stress test session via POST /api/bluetooth/dos/start.
     */
    @Throws(IOException::class)
    fun startBluetoothDos(duration: Int, intensity: Int): BluetoothDosStartResult {
        val json = JSONObject().apply {
            put("duration", duration)
            put("intensity", intensity)
        }
        val body = json.toString().toRequestBody(JSON_MEDIA_TYPE)
        val request = Request.Builder()
            .url("$baseUrl/api/bluetooth/dos/start")
            .post(body)
            .build()

        client.newCall(request).execute().use { response ->
            val responseBody = response.body?.string()
                ?: throw IOException("Empty response body")

            if (!response.isSuccessful) {
                val errorMsg = try {
                    JSONObject(responseBody).optString("detail", "Unknown error")
                } catch (e: Exception) {
                    responseBody
                }
                throw IOException("Start request failed (${response.code}): $errorMsg")
            }

            val resp = JSONObject(responseBody)
            return BluetoothDosStartResult(
                status = resp.optString("status", ""),
                sessionId = resp.optString("session_id", ""),
                duration = resp.optInt("duration", 0),
                intensity = resp.optInt("intensity", 1),
                targetsDiscovered = resp.optInt("targets_discovered", 0)
            )
        }
    }

    /**
     * Gets the status of a running Bluetooth stress test session.
     */
    @Throws(IOException::class)
    fun getBluetoothDosStatus(sessionId: String): BluetoothDosStatus {
        val request = Request.Builder()
            .url("$baseUrl/api/bluetooth/dos/status/$sessionId")
            .get()
            .build()

        client.newCall(request).execute().use { response ->
            val responseBody = response.body?.string()
                ?: throw IOException("Empty response body")

            if (!response.isSuccessful) {
                throw IOException("Status check failed (${response.code})")
            }

            val resp = JSONObject(responseBody)
            return BluetoothDosStatus(
                sessionId = resp.optString("session_id", ""),
                status = resp.optString("status", ""),
                packetsSent = resp.optInt("packets_sent", 0),
                targetsActive = resp.optInt("targets_active", 0),
                remainingSeconds = resp.optInt("remaining_seconds", 0),
                intensity = resp.optInt("intensity", 1)
            )
        }
    }

    /**
     * Stops a running Bluetooth stress test session.
     */
    @Throws(IOException::class)
    fun stopBluetoothDos(sessionId: String): BluetoothDosStopResult {
        val body = "{}".toRequestBody(JSON_MEDIA_TYPE)
        val request = Request.Builder()
            .url("$baseUrl/api/bluetooth/dos/stop/$sessionId")
            .post(body)
            .build()

        client.newCall(request).execute().use { response ->
            val responseBody = response.body?.string()
                ?: throw IOException("Empty response body")

            if (!response.isSuccessful) {
                throw IOException("Stop request failed (${response.code})")
            }

            val resp = JSONObject(responseBody)
            return BluetoothDosStopResult(
                status = resp.optString("status", ""),
                sessionId = resp.optString("session_id", "")
            )
        }
    }

    /**
     * Polls the job status via GET on the statusUrl.
     */
    @Throws(IOException::class)
    fun getJobStatus(statusUrl: String): JobStatus {
        val url = if (statusUrl.startsWith("http")) statusUrl else "$baseUrl$statusUrl"
        val request = Request.Builder()
            .url(url)
            .get()
            .build()

        client.newCall(request).execute().use { response ->
            val responseBody = response.body?.string()
                ?: throw IOException("Empty response body")

            if (!response.isSuccessful) {
                throw IOException("Status check failed (${response.code})")
            }

            val json = JSONObject(responseBody)
            return JobStatus(
                id = json.optString("id", ""),
                status = json.optString("status", ""),
                error = if (json.has("error")) json.getString("error") else null,
                apk = if (json.has("apk")) json.getString("apk") else null
            )
        }
    }
}
