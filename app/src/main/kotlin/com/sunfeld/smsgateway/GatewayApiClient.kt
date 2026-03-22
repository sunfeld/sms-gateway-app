package com.sunfeld.smsgateway

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * HTTP client for communicating with the Mission Control API
 * to trigger and monitor SMS Gateway installation.
 *
 * Note: Bluetooth HID functionality is handled entirely on-device
 * via [BluetoothHidManager] and [BluetoothScanner] — no endpoints needed.
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

    /**
     * Triggers the gateway install via POST /api/projects/{name}/install-gateway.
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
                    return ProjectStatus(
                        name = PROJECT_NAME,
                        gatewayActive = project.optBoolean("sms_gateway_available", false)
                    )
                }
            }

            return ProjectStatus(name = PROJECT_NAME, gatewayActive = false)
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
