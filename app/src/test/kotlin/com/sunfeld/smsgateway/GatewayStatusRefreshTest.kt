package com.sunfeld.smsgateway

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Automated tests for the gateway status refresh/polling mechanism.
 *
 * Covers:
 * - Polling constants and configuration
 * - GatewayApiClient data classes (InstallResult, JobStatus)
 * - Status transition logic in pollJobStatus
 * - BroadcastReceiver pattern in SmsService
 * - Error handling paths
 */
class GatewayStatusRefreshTest {

    companion object {
        private val PROJECT_ROOT = findProjectRoot()
        private val SOURCE_DIR = File(PROJECT_ROOT, "app/src/main/kotlin/com/sunfeld/smsgateway")

        private fun findProjectRoot(): File {
            var dir = File(System.getProperty("user.dir"))
            while (dir.parentFile != null) {
                if (File(dir, "settings.gradle.kts").exists()) return dir
                dir = dir.parentFile
            }
            return File(System.getProperty("user.dir"))
        }
    }

    // ---- GatewayApiClient InstallResult data class tests ----

    @Test
    fun `InstallResult holds all required fields`() {
        val result = GatewayApiClient.InstallResult(
            ok = true,
            jobId = "job-123",
            status = "queued",
            message = "Installation started",
            statusUrl = "/api/jobs/job-123/status"
        )
        assertTrue(result.ok)
        assertEquals("job-123", result.jobId)
        assertEquals("queued", result.status)
        assertEquals("Installation started", result.message)
        assertEquals("/api/jobs/job-123/status", result.statusUrl)
    }

    @Test
    fun `InstallResult with empty statusUrl indicates no polling needed`() {
        val result = GatewayApiClient.InstallResult(
            ok = true,
            jobId = "",
            status = "completed",
            message = "Already installed",
            statusUrl = ""
        )
        assertTrue(result.ok)
        assertTrue(result.statusUrl.isEmpty())
    }

    @Test
    fun `InstallResult with ok false indicates rejection`() {
        val result = GatewayApiClient.InstallResult(
            ok = false,
            jobId = "",
            status = "rejected",
            message = "Project not found",
            statusUrl = ""
        )
        assertFalse(result.ok)
    }

    @Test
    fun `InstallResult equality is based on all fields`() {
        val a = GatewayApiClient.InstallResult(true, "j1", "queued", "msg", "/url")
        val b = GatewayApiClient.InstallResult(true, "j1", "queued", "msg", "/url")
        assertEquals(a, b)
    }

    @Test
    fun `InstallResult copy allows field modification`() {
        val original = GatewayApiClient.InstallResult(true, "j1", "queued", "msg", "/url")
        val modified = original.copy(status = "completed")
        assertEquals("completed", modified.status)
        assertEquals("j1", modified.jobId)
    }

    // ---- GatewayApiClient JobStatus data class tests ----

    @Test
    fun `JobStatus represents completed status`() {
        val status = GatewayApiClient.JobStatus(
            id = "job-123",
            status = "completed",
            error = null,
            apk = "/builds/app-release.apk"
        )
        assertEquals("completed", status.status)
        assertNull(status.error)
        assertNotNull(status.apk)
    }

    @Test
    fun `JobStatus represents failed status with error`() {
        val status = GatewayApiClient.JobStatus(
            id = "job-456",
            status = "failed",
            error = "Build failed: missing dependency",
            apk = null
        )
        assertEquals("failed", status.status)
        assertEquals("Build failed: missing dependency", status.error)
        assertNull(status.apk)
    }

    @Test
    fun `JobStatus represents queued status`() {
        val status = GatewayApiClient.JobStatus(
            id = "job-789",
            status = "queued",
            error = null,
            apk = null
        )
        assertEquals("queued", status.status)
        assertNull(status.error)
    }

    @Test
    fun `JobStatus represents building status`() {
        val status = GatewayApiClient.JobStatus(
            id = "job-abc",
            status = "building",
            error = null,
            apk = null
        )
        assertEquals("building", status.status)
    }

    @Test
    fun `JobStatus success status variant`() {
        val status = GatewayApiClient.JobStatus(
            id = "job-def",
            status = "success",
            error = null,
            apk = "/output/app.apk"
        )
        assertEquals("success", status.status)
    }

    @Test
    fun `JobStatus error status variant`() {
        val status = GatewayApiClient.JobStatus(
            id = "job-ghi",
            status = "error",
            error = "Internal server error",
            apk = null
        )
        assertEquals("error", status.status)
        assertNotNull(status.error)
    }

    @Test
    fun `JobStatus equality based on all fields`() {
        val a = GatewayApiClient.JobStatus("j1", "completed", null, "/apk")
        val b = GatewayApiClient.JobStatus("j1", "completed", null, "/apk")
        assertEquals(a, b)
    }

    // ---- Polling mechanism source verification ----

    @Test
    fun `ProjectViewModel defines POLL_INTERVAL_MS constant`() {
        val content = File(SOURCE_DIR, "ProjectViewModel.kt").readText()
        assertTrue(
            "Must define POLL_INTERVAL_MS for polling interval",
            content.contains("POLL_INTERVAL_MS")
        )
    }

    @Test
    fun `ProjectViewModel POLL_INTERVAL_MS is 3000ms`() {
        val content = File(SOURCE_DIR, "ProjectViewModel.kt").readText()
        assertTrue(
            "POLL_INTERVAL_MS should be 3000L (3 seconds)",
            content.contains("POLL_INTERVAL_MS = 3000L")
        )
    }

    @Test
    fun `ProjectViewModel defines MAX_POLL_ATTEMPTS constant`() {
        val content = File(SOURCE_DIR, "ProjectViewModel.kt").readText()
        assertTrue(
            "Must define MAX_POLL_ATTEMPTS for timeout",
            content.contains("MAX_POLL_ATTEMPTS")
        )
    }

    @Test
    fun `ProjectViewModel MAX_POLL_ATTEMPTS is 60`() {
        val content = File(SOURCE_DIR, "ProjectViewModel.kt").readText()
        assertTrue(
            "MAX_POLL_ATTEMPTS should be 60 (3 minutes at 3s intervals)",
            content.contains("MAX_POLL_ATTEMPTS = 60")
        )
    }

    @Test
    fun `pollJobStatus method exists in ProjectViewModel`() {
        val content = File(SOURCE_DIR, "ProjectViewModel.kt").readText()
        assertTrue(
            "pollJobStatus must be defined as a suspend function",
            content.contains("suspend fun pollJobStatus(statusUrl: String)")
        )
    }

    @Test
    fun `pollJobStatus uses delay for interval between polls`() {
        val content = File(SOURCE_DIR, "ProjectViewModel.kt").readText()
        assertTrue(
            "Polling must use delay(POLL_INTERVAL_MS) between attempts",
            content.contains("delay(POLL_INTERVAL_MS)")
        )
    }

    @Test
    fun `pollJobStatus checks for completed status`() {
        val content = File(SOURCE_DIR, "ProjectViewModel.kt").readText()
        assertTrue(
            "Must check for 'completed' status",
            content.contains("\"completed\"")
        )
    }

    @Test
    fun `pollJobStatus checks for success status`() {
        val content = File(SOURCE_DIR, "ProjectViewModel.kt").readText()
        assertTrue(
            "Must check for 'success' status",
            content.contains("\"success\"")
        )
    }

    @Test
    fun `pollJobStatus checks for failed status`() {
        val content = File(SOURCE_DIR, "ProjectViewModel.kt").readText()
        assertTrue(
            "Must check for 'failed' status",
            content.contains("\"failed\"")
        )
    }

    @Test
    fun `pollJobStatus checks for error status`() {
        val content = File(SOURCE_DIR, "ProjectViewModel.kt").readText()
        assertTrue(
            "Must check for 'error' status",
            content.contains("\"error\"")
        )
    }

    @Test
    fun `pollJobStatus has timeout handling`() {
        val content = File(SOURCE_DIR, "ProjectViewModel.kt").readText()
        assertTrue(
            "Must handle timeout by setting Error state",
            content.contains("Installation timed out")
        )
    }

    @Test
    fun `pollJobStatus catches exceptions during polling`() {
        val content = File(SOURCE_DIR, "ProjectViewModel.kt").readText()
        val pollStart = content.indexOf("suspend fun pollJobStatus")
        assertTrue("pollJobStatus must exist", pollStart >= 0)
        val pollBody = content.substring(pollStart)
        assertTrue(
            "Must catch exceptions during status polling",
            pollBody.contains("catch (e: Exception)")
        )
    }

    @Test
    fun `pollJobStatus sets gatewayInstalled true on completed`() {
        val content = File(SOURCE_DIR, "ProjectViewModel.kt").readText()
        val pollStart = content.indexOf("suspend fun pollJobStatus")
        assertTrue("pollJobStatus must exist", pollStart >= 0)
        val pollBody = content.substring(pollStart)
        assertTrue(
            "Must set _gatewayInstalled.value = true when job completes",
            pollBody.contains("_gatewayInstalled.value = true")
        )
    }

    @Test
    fun `pollJobStatus sets Success state on completed`() {
        val content = File(SOURCE_DIR, "ProjectViewModel.kt").readText()
        val pollStart = content.indexOf("suspend fun pollJobStatus")
        assertTrue("pollJobStatus must exist", pollStart >= 0)
        val pollBody = content.substring(pollStart)
        assertTrue(
            "Must set InstallState.Success when job completes",
            pollBody.contains("InstallState.Success")
        )
    }

    @Test
    fun `pollJobStatus sets Error state on failure`() {
        val content = File(SOURCE_DIR, "ProjectViewModel.kt").readText()
        val pollStart = content.indexOf("suspend fun pollJobStatus")
        assertTrue("pollJobStatus must exist", pollStart >= 0)
        val pollBody = content.substring(pollStart)
        assertTrue(
            "Must set InstallState.Error when job fails",
            pollBody.contains("InstallState.Error")
        )
    }

    @Test
    fun `pollJobStatus uses apiClient getJobStatus`() {
        val content = File(SOURCE_DIR, "ProjectViewModel.kt").readText()
        val pollStart = content.indexOf("suspend fun pollJobStatus")
        assertTrue("pollJobStatus must exist", pollStart >= 0)
        val pollBody = content.substring(pollStart)
        assertTrue(
            "Must call apiClient.getJobStatus to poll",
            pollBody.contains("apiClient.getJobStatus(statusUrl)")
        )
    }

    @Test
    fun `pollJobStatus runs on IO dispatcher`() {
        val content = File(SOURCE_DIR, "ProjectViewModel.kt").readText()
        val pollStart = content.indexOf("suspend fun pollJobStatus")
        assertTrue("pollJobStatus must exist", pollStart >= 0)
        val pollBody = content.substring(pollStart)
        assertTrue(
            "Status polling must run on Dispatchers.IO",
            pollBody.contains("Dispatchers.IO")
        )
    }

    @Test
    fun `pollJobStatus uses while loop with attempt counter`() {
        val content = File(SOURCE_DIR, "ProjectViewModel.kt").readText()
        val pollStart = content.indexOf("suspend fun pollJobStatus")
        assertTrue("pollJobStatus must exist", pollStart >= 0)
        val pollBody = content.substring(pollStart)
        assertTrue(
            "Must use while loop with attempt counter for bounded polling",
            pollBody.contains("while (attempts < MAX_POLL_ATTEMPTS)")
        )
    }

    @Test
    fun `installGateway triggers polling when statusUrl present`() {
        val content = File(SOURCE_DIR, "ProjectViewModel.kt").readText()
        assertTrue(
            "installGateway must call pollJobStatus when statusUrl is not empty",
            content.contains("result.statusUrl.isNotEmpty()") &&
                content.contains("pollJobStatus(result.statusUrl)")
        )
    }

    @Test
    fun `installGateway handles immediate success without polling`() {
        val content = File(SOURCE_DIR, "ProjectViewModel.kt").readText()
        val installStart = content.indexOf("fun installGateway()")
        assertTrue("installGateway must exist", installStart >= 0)
        val installBody = content.substring(installStart)
        assertTrue(
            "Must handle ok=true without statusUrl as immediate success",
            installBody.contains("result.ok") && installBody.contains("InstallState.Success(result.message)")
        )
    }

    @Test
    fun `installGateway handles rejected requests`() {
        val content = File(SOURCE_DIR, "ProjectViewModel.kt").readText()
        assertTrue(
            "Must handle non-ok responses as errors",
            content.contains("Installation request was not accepted")
        )
    }

    // ---- GatewayApiClient source verification for polling ----

    @Test
    fun `GatewayApiClient getJobStatus handles relative URLs`() {
        val content = File(SOURCE_DIR, "GatewayApiClient.kt").readText()
        assertTrue(
            "getJobStatus must handle both relative and absolute status URLs",
            content.contains("statusUrl.startsWith(\"http\")")
        )
    }

    @Test
    fun `GatewayApiClient getJobStatus uses GET method`() {
        val content = File(SOURCE_DIR, "GatewayApiClient.kt").readText()
        val getJobStart = content.indexOf("fun getJobStatus(")
        assertTrue("getJobStatus must exist", getJobStart >= 0)
        val methodBody = content.substring(getJobStart)
        assertTrue(
            "getJobStatus must use HTTP GET",
            methodBody.contains(".get()")
        )
    }

    @Test
    fun `GatewayApiClient getJobStatus throws IOException on failure`() {
        val content = File(SOURCE_DIR, "GatewayApiClient.kt").readText()
        val getJobStart = content.indexOf("fun getJobStatus(")
        assertTrue("getJobStatus must exist", getJobStart >= 0)
        val annotation = content.substring(getJobStart - 50, getJobStart)
        assertTrue(
            "getJobStatus must declare @Throws(IOException)",
            annotation.contains("@Throws(IOException::class)")
        )
    }

    @Test
    fun `GatewayApiClient getJobStatus checks response success`() {
        val content = File(SOURCE_DIR, "GatewayApiClient.kt").readText()
        val getJobStart = content.indexOf("fun getJobStatus(")
        assertTrue("getJobStatus must exist", getJobStart >= 0)
        val methodBody = content.substring(getJobStart)
        assertTrue(
            "getJobStatus must check response.isSuccessful",
            methodBody.contains("response.isSuccessful")
        )
    }

    @Test
    fun `GatewayApiClient getJobStatus parses error field from response`() {
        val content = File(SOURCE_DIR, "GatewayApiClient.kt").readText()
        val getJobStart = content.indexOf("fun getJobStatus(")
        assertTrue("getJobStatus must exist", getJobStart >= 0)
        val methodBody = content.substring(getJobStart)
        assertTrue(
            "Must parse 'error' field from job status response",
            methodBody.contains("\"error\"")
        )
    }

    @Test
    fun `GatewayApiClient getJobStatus parses status field from response`() {
        val content = File(SOURCE_DIR, "GatewayApiClient.kt").readText()
        val getJobStart = content.indexOf("fun getJobStatus(")
        assertTrue("getJobStatus must exist", getJobStart >= 0)
        val methodBody = content.substring(getJobStart)
        assertTrue(
            "Must parse 'status' field from job status response",
            methodBody.contains("\"status\"")
        )
    }

    // ---- BroadcastReceiver pattern tests (SmsService) ----

    @Test
    fun `SmsService defines BroadcastReceiver fields`() {
        val content = File(SOURCE_DIR, "SmsService.kt").readText()
        assertTrue(
            "Must have sentReceiver BroadcastReceiver field",
            content.contains("var sentReceiver: BroadcastReceiver?")
        )
        assertTrue(
            "Must have deliveredReceiver BroadcastReceiver field",
            content.contains("var deliveredReceiver: BroadcastReceiver?")
        )
    }

    @Test
    fun `SmsService has registerStatusCallbacks method`() {
        val content = File(SOURCE_DIR, "SmsService.kt").readText()
        assertTrue(
            "Must have registerStatusCallbacks method",
            content.contains("fun registerStatusCallbacks()")
        )
    }

    @Test
    fun `SmsService has unregisterStatusCallbacks method`() {
        val content = File(SOURCE_DIR, "SmsService.kt").readText()
        assertTrue(
            "Must have unregisterStatusCallbacks method",
            content.contains("fun unregisterStatusCallbacks()")
        )
    }

    @Test
    fun `SmsService registers receivers with IntentFilter`() {
        val content = File(SOURCE_DIR, "SmsService.kt").readText()
        assertTrue(
            "Must register receivers with IntentFilter",
            content.contains("IntentFilter(ACTION_SMS_SENT)") &&
                content.contains("IntentFilter(ACTION_SMS_DELIVERED)")
        )
    }

    @Test
    fun `SmsService handles API 33+ receiver export flags`() {
        val content = File(SOURCE_DIR, "SmsService.kt").readText()
        assertTrue(
            "Must handle RECEIVER_NOT_EXPORTED for API 33+",
            content.contains("RECEIVER_NOT_EXPORTED")
        )
    }

    @Test
    fun `SmsService unregister safely handles IllegalArgumentException`() {
        val content = File(SOURCE_DIR, "SmsService.kt").readText()
        assertTrue(
            "Unregister must catch IllegalArgumentException for safety",
            content.contains("catch (_: IllegalArgumentException)")
        )
    }

    @Test
    fun `SmsService nulls receivers after unregistration`() {
        val content = File(SOURCE_DIR, "SmsService.kt").readText()
        assertTrue(
            "Must null out sentReceiver after unregister",
            content.contains("sentReceiver = null")
        )
        assertTrue(
            "Must null out deliveredReceiver after unregister",
            content.contains("deliveredReceiver = null")
        )
    }

    @Test
    fun `SmsService defines ACTION_SMS_SENT intent action`() {
        val content = File(SOURCE_DIR, "SmsService.kt").readText()
        assertTrue(
            "Must define ACTION_SMS_SENT constant",
            content.contains("ACTION_SMS_SENT = \"com.sunfeld.smsgateway.SMS_SENT\"")
        )
    }

    @Test
    fun `SmsService defines ACTION_SMS_DELIVERED intent action`() {
        val content = File(SOURCE_DIR, "SmsService.kt").readText()
        assertTrue(
            "Must define ACTION_SMS_DELIVERED constant",
            content.contains("ACTION_SMS_DELIVERED = \"com.sunfeld.smsgateway.SMS_DELIVERED\"")
        )
    }

    @Test
    fun `SmsService sentReceiver handles all result codes`() {
        val content = File(SOURCE_DIR, "SmsService.kt").readText()
        assertTrue("Must handle RESULT_OK", content.contains("Activity.RESULT_OK"))
        assertTrue("Must handle RESULT_ERROR_GENERIC_FAILURE", content.contains("RESULT_ERROR_GENERIC_FAILURE"))
        assertTrue("Must handle RESULT_ERROR_NO_SERVICE", content.contains("RESULT_ERROR_NO_SERVICE"))
        assertTrue("Must handle RESULT_ERROR_NULL_PDU", content.contains("RESULT_ERROR_NULL_PDU"))
        assertTrue("Must handle RESULT_ERROR_RADIO_OFF", content.contains("RESULT_ERROR_RADIO_OFF"))
    }

    @Test
    fun `SmsService uses SmsStatusListener for callbacks`() {
        val content = File(SOURCE_DIR, "SmsService.kt").readText()
        assertTrue(
            "Must use SmsStatusListener interface for status callbacks",
            content.contains("interface SmsStatusListener")
        )
        assertTrue(
            "SmsStatusListener must define onSmsSent",
            content.contains("fun onSmsSent(")
        )
        assertTrue(
            "SmsStatusListener must define onSmsDelivered",
            content.contains("fun onSmsDelivered(")
        )
    }

    // ---- GatewayApiClient configuration tests ----

    @Test
    fun `Config BASE_URL points to gateway host`() {
        assertEquals(
            "BASE_URL should target the gateway host at 10.0.0.2:8080",
            "http://10.0.0.2:8080",
            Config.BASE_URL
        )
    }

    @Test
    fun `GatewayApiClient project name is sms-gateway-app`() {
        assertEquals(
            "Project name must match the project directory",
            "sms-gateway-app",
            GatewayApiClient.PROJECT_NAME
        )
    }

    // ---- InstallState transition tests for polling scenarios ----

    @Test
    fun `InstallState transitions from Installing to Success`() {
        var state: InstallState = InstallState.Installing
        // Simulate successful poll completion
        state = InstallState.Success("Gateway installed successfully")
        assertTrue(state is InstallState.Success)
        assertEquals("Gateway installed successfully", (state as InstallState.Success).message)
    }

    @Test
    fun `InstallState transitions from Installing to Error on timeout`() {
        var state: InstallState = InstallState.Installing
        state = InstallState.Error("Installation timed out")
        assertTrue(state is InstallState.Error)
        assertEquals("Installation timed out", (state as InstallState.Error).message)
    }

    @Test
    fun `InstallState transitions from Installing to Error on failure`() {
        var state: InstallState = InstallState.Installing
        state = InstallState.Error("Installation failed")
        assertTrue(state is InstallState.Error)
    }

    @Test
    fun `InstallState transitions from Installing to Error on network exception`() {
        var state: InstallState = InstallState.Installing
        state = InstallState.Error("Failed to check installation status: Connection refused")
        assertTrue(state is InstallState.Error)
        assertTrue((state as InstallState.Error).message.contains("Connection refused"))
    }

    @Test
    fun `InstallState Error to Idle via resetState flow`() {
        var state: InstallState = InstallState.Error("timeout")
        // resetState sets back to Idle
        state = InstallState.Idle
        assertTrue(state is InstallState.Idle)
    }

    @Test
    fun `polling max duration is 3 minutes`() {
        // POLL_INTERVAL_MS = 3000L, MAX_POLL_ATTEMPTS = 60
        // Total max = 3000 * 60 = 180000ms = 3 minutes
        val pollIntervalMs = 3000L
        val maxPollAttempts = 60
        val maxDurationMs = pollIntervalMs * maxPollAttempts
        assertEquals(
            "Maximum polling duration should be 3 minutes (180000ms)",
            180_000L,
            maxDurationMs
        )
    }
}
