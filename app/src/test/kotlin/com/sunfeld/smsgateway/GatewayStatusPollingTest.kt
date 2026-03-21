package com.sunfeld.smsgateway

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Unit tests for the gateway status polling mechanism and BroadcastReceiver.
 * Validates source structure, API integration, and lifecycle wiring.
 */
class GatewayStatusPollingTest {

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

    // ---- GatewayApiClient: getProjectStatus ----

    @Test
    fun `GatewayApiClient has getProjectStatus method`() {
        val content = File(SOURCE_DIR, "GatewayApiClient.kt").readText()
        assertTrue(
            "GatewayApiClient must have getProjectStatus method",
            content.contains("fun getProjectStatus()")
        )
    }

    @Test
    fun `GatewayApiClient has ProjectStatus data class`() {
        val content = File(SOURCE_DIR, "GatewayApiClient.kt").readText()
        assertTrue(
            "ProjectStatus data class must exist",
            content.contains("data class ProjectStatus")
        )
    }

    @Test
    fun `ProjectStatus has gatewayActive field`() {
        val content = File(SOURCE_DIR, "GatewayApiClient.kt").readText()
        assertTrue(
            "ProjectStatus must have gatewayActive Boolean field",
            content.contains("val gatewayActive: Boolean")
        )
    }

    @Test
    fun `getProjectStatus queries active projects endpoint`() {
        val content = File(SOURCE_DIR, "GatewayApiClient.kt").readText()
        assertTrue(
            "getProjectStatus must query /api/projects/active",
            content.contains("/api/projects/active")
        )
    }

    @Test
    fun `getProjectStatus checks sms_gateway_available field`() {
        val content = File(SOURCE_DIR, "GatewayApiClient.kt").readText()
        assertTrue(
            "getProjectStatus must check sms_gateway_available from response",
            content.contains("sms_gateway_available")
        )
    }

    // ---- ProjectViewModel: status polling ----

    @Test
    fun `ProjectViewModel has startStatusPolling method`() {
        val content = File(SOURCE_DIR, "ProjectViewModel.kt").readText()
        assertTrue(
            "ProjectViewModel must have startStatusPolling method",
            content.contains("fun startStatusPolling()")
        )
    }

    @Test
    fun `ProjectViewModel has stopStatusPolling method`() {
        val content = File(SOURCE_DIR, "ProjectViewModel.kt").readText()
        assertTrue(
            "ProjectViewModel must have stopStatusPolling method",
            content.contains("fun stopStatusPolling()")
        )
    }

    @Test
    fun `ProjectViewModel has refreshProjectStatus method`() {
        val content = File(SOURCE_DIR, "ProjectViewModel.kt").readText()
        assertTrue(
            "ProjectViewModel must have refreshProjectStatus method",
            content.contains("fun refreshProjectStatus()")
        )
    }

    @Test
    fun `startStatusPolling guards against duplicate polling`() {
        val content = File(SOURCE_DIR, "ProjectViewModel.kt").readText()
        val methodStart = content.indexOf("fun startStatusPolling()")
        assertTrue("startStatusPolling must exist", methodStart >= 0)

        val methodBody = content.substring(methodStart, content.indexOf("\n    fun ", methodStart + 1))
        assertTrue(
            "startStatusPolling must guard against duplicate polling with isActive check",
            methodBody.contains("statusPollingJob?.isActive == true")
        )
    }

    @Test
    fun `startStatusPolling skips if gateway already installed`() {
        val content = File(SOURCE_DIR, "ProjectViewModel.kt").readText()
        val methodStart = content.indexOf("fun startStatusPolling()")
        assertTrue("startStatusPolling must exist", methodStart >= 0)

        val methodBody = content.substring(methodStart, content.indexOf("\n    fun ", methodStart + 1))
        assertTrue(
            "startStatusPolling must skip if already installed",
            methodBody.contains("_gatewayInstalled.value == true")
        )
    }

    @Test
    fun `startStatusPolling uses getProjectStatus from apiClient`() {
        val content = File(SOURCE_DIR, "ProjectViewModel.kt").readText()
        val methodStart = content.indexOf("fun startStatusPolling()")
        assertTrue("startStatusPolling must exist", methodStart >= 0)

        val methodBody = content.substring(methodStart, content.indexOf("\n    fun ", methodStart + 1))
        assertTrue(
            "startStatusPolling must call apiClient.getProjectStatus()",
            methodBody.contains("apiClient.getProjectStatus()")
        )
    }

    @Test
    fun `startStatusPolling sets gatewayInstalled true when active`() {
        val content = File(SOURCE_DIR, "ProjectViewModel.kt").readText()
        val methodStart = content.indexOf("fun startStatusPolling()")
        assertTrue("startStatusPolling must exist", methodStart >= 0)

        val methodBody = content.substring(methodStart, content.indexOf("\n    fun ", methodStart + 1))
        assertTrue(
            "startStatusPolling must set _gatewayInstalled.value = true when gateway is active",
            methodBody.contains("_gatewayInstalled.value = true")
        )
    }

    @Test
    fun `startStatusPolling runs on IO dispatcher`() {
        val content = File(SOURCE_DIR, "ProjectViewModel.kt").readText()
        val methodStart = content.indexOf("fun startStatusPolling()")
        assertTrue("startStatusPolling must exist", methodStart >= 0)

        val methodBody = content.substring(methodStart, content.indexOf("\n    fun ", methodStart + 1))
        assertTrue(
            "startStatusPolling must run network call on Dispatchers.IO",
            methodBody.contains("Dispatchers.IO")
        )
    }

    @Test
    fun `stopStatusPolling cancels the polling job`() {
        val content = File(SOURCE_DIR, "ProjectViewModel.kt").readText()
        val methodStart = content.indexOf("fun stopStatusPolling()")
        assertTrue("stopStatusPolling must exist", methodStart >= 0)

        val methodBody = content.substring(methodStart, content.indexOf("\n", content.indexOf("}", methodStart + 1)))
        assertTrue(
            "stopStatusPolling must cancel statusPollingJob",
            methodBody.contains("statusPollingJob?.cancel()")
        )
    }

    @Test
    fun `refreshProjectStatus calls getProjectStatus`() {
        val content = File(SOURCE_DIR, "ProjectViewModel.kt").readText()
        val methodStart = content.indexOf("fun refreshProjectStatus()")
        assertTrue("refreshProjectStatus must exist", methodStart >= 0)

        val nextMethodOrEnd = content.indexOf("\n    fun ", methodStart + 1).let {
            if (it == -1) content.indexOf("\n    override fun ", methodStart + 1) else it
        }
        val methodBody = content.substring(methodStart, nextMethodOrEnd)
        assertTrue(
            "refreshProjectStatus must call apiClient.getProjectStatus()",
            methodBody.contains("apiClient.getProjectStatus()")
        )
    }

    @Test
    fun `ProjectViewModel has ACTION_GATEWAY_STATUS_CHANGED constant`() {
        val content = File(SOURCE_DIR, "ProjectViewModel.kt").readText()
        assertTrue(
            "ACTION_GATEWAY_STATUS_CHANGED constant must be defined",
            content.contains("ACTION_GATEWAY_STATUS_CHANGED")
        )
    }

    @Test
    fun `ProjectViewModel has EXTRA_GATEWAY_ACTIVE constant`() {
        val content = File(SOURCE_DIR, "ProjectViewModel.kt").readText()
        assertTrue(
            "EXTRA_GATEWAY_ACTIVE constant must be defined",
            content.contains("EXTRA_GATEWAY_ACTIVE")
        )
    }

    @Test
    fun `ProjectViewModel cleans up polling in onCleared`() {
        val content = File(SOURCE_DIR, "ProjectViewModel.kt").readText()
        assertTrue(
            "onCleared must call stopStatusPolling",
            content.contains("override fun onCleared()") && content.contains("stopStatusPolling()")
        )
    }

    @Test
    fun `ProjectViewModel has STATUS_POLL_INTERVAL_MS constant`() {
        val content = File(SOURCE_DIR, "ProjectViewModel.kt").readText()
        assertTrue(
            "STATUS_POLL_INTERVAL_MS constant must be defined",
            content.contains("STATUS_POLL_INTERVAL_MS")
        )
    }

    @Test
    fun `ProjectViewModel has MAX_STATUS_POLL_ATTEMPTS constant`() {
        val content = File(SOURCE_DIR, "ProjectViewModel.kt").readText()
        assertTrue(
            "MAX_STATUS_POLL_ATTEMPTS constant must be defined",
            content.contains("MAX_STATUS_POLL_ATTEMPTS")
        )
    }

    @Test
    fun `installGateway starts status polling after job polling if not yet installed`() {
        val content = File(SOURCE_DIR, "ProjectViewModel.kt").readText()
        val installMethodStart = content.indexOf("fun installGateway()")
        assertTrue("installGateway must exist", installMethodStart >= 0)

        val methodEnd = content.indexOf("\n    private suspend fun pollJobStatus", installMethodStart)
        val methodBody = content.substring(installMethodStart, methodEnd)
        assertTrue(
            "installGateway must call startStatusPolling after job polling",
            methodBody.contains("startStatusPolling()")
        )
    }

    // ---- GatewayStatusReceiver: BroadcastReceiver ----

    @Test
    fun `GatewayStatusReceiver source file exists`() {
        val receiverFile = File(SOURCE_DIR, "GatewayStatusReceiver.kt")
        assertTrue("GatewayStatusReceiver.kt must exist", receiverFile.exists())
    }

    @Test
    fun `GatewayStatusReceiver extends BroadcastReceiver`() {
        val content = File(SOURCE_DIR, "GatewayStatusReceiver.kt").readText()
        assertTrue(
            "GatewayStatusReceiver must extend BroadcastReceiver",
            content.contains(": BroadcastReceiver()")
        )
    }

    @Test
    fun `GatewayStatusReceiver has onReceive method`() {
        val content = File(SOURCE_DIR, "GatewayStatusReceiver.kt").readText()
        assertTrue(
            "GatewayStatusReceiver must override onReceive",
            content.contains("override fun onReceive(")
        )
    }

    @Test
    fun `GatewayStatusReceiver has register method`() {
        val content = File(SOURCE_DIR, "GatewayStatusReceiver.kt").readText()
        assertTrue(
            "GatewayStatusReceiver must have register method",
            content.contains("fun register(context: Context)")
        )
    }

    @Test
    fun `GatewayStatusReceiver has unregister method`() {
        val content = File(SOURCE_DIR, "GatewayStatusReceiver.kt").readText()
        assertTrue(
            "GatewayStatusReceiver must have unregister method",
            content.contains("fun unregister(context: Context)")
        )
    }

    @Test
    fun `GatewayStatusReceiver has sendStatusBroadcast companion method`() {
        val content = File(SOURCE_DIR, "GatewayStatusReceiver.kt").readText()
        assertTrue(
            "sendStatusBroadcast must exist in companion object",
            content.contains("fun sendStatusBroadcast(")
        )
    }

    @Test
    fun `GatewayStatusReceiver uses ACTION_GATEWAY_STATUS_CHANGED action`() {
        val content = File(SOURCE_DIR, "GatewayStatusReceiver.kt").readText()
        assertTrue(
            "GatewayStatusReceiver must use ACTION_GATEWAY_STATUS_CHANGED",
            content.contains("ACTION_GATEWAY_STATUS_CHANGED")
        )
    }

    @Test
    fun `GatewayStatusReceiver uses EXTRA_GATEWAY_ACTIVE extra`() {
        val content = File(SOURCE_DIR, "GatewayStatusReceiver.kt").readText()
        assertTrue(
            "GatewayStatusReceiver must use EXTRA_GATEWAY_ACTIVE",
            content.contains("EXTRA_GATEWAY_ACTIVE")
        )
    }

    @Test
    fun `GatewayStatusReceiver handles RECEIVER_NOT_EXPORTED for Android 13+`() {
        val content = File(SOURCE_DIR, "GatewayStatusReceiver.kt").readText()
        assertTrue(
            "register must handle RECEIVER_NOT_EXPORTED for Android 13+",
            content.contains("RECEIVER_NOT_EXPORTED")
        )
    }

    @Test
    fun `GatewayStatusReceiver accepts callback in constructor`() {
        val content = File(SOURCE_DIR, "GatewayStatusReceiver.kt").readText()
        assertTrue(
            "GatewayStatusReceiver must accept a callback parameter",
            content.contains("class GatewayStatusReceiver(") &&
            content.contains("onStatusChanged")
        )
    }

    @Test
    fun `sendStatusBroadcast sets package to prevent leaking to other apps`() {
        val content = File(SOURCE_DIR, "GatewayStatusReceiver.kt").readText()
        assertTrue(
            "sendStatusBroadcast must set package name for security",
            content.contains("setPackage(context.packageName)")
        )
    }

    // ---- ProjectDetailActivity: integration with polling and receiver ----

    @Test
    fun `ProjectDetailActivity registers GatewayStatusReceiver`() {
        val content = File(SOURCE_DIR, "ProjectDetailActivity.kt").readText()
        assertTrue(
            "ProjectDetailActivity must have a GatewayStatusReceiver instance",
            content.contains("GatewayStatusReceiver")
        )
    }

    @Test
    fun `ProjectDetailActivity registers receiver in onStart`() {
        val content = File(SOURCE_DIR, "ProjectDetailActivity.kt").readText()
        assertTrue(
            "Activity must register receiver in onStart",
            content.contains("override fun onStart()") && content.contains("gatewayStatusReceiver.register")
        )
    }

    @Test
    fun `ProjectDetailActivity unregisters receiver in onStop`() {
        val content = File(SOURCE_DIR, "ProjectDetailActivity.kt").readText()
        assertTrue(
            "Activity must unregister receiver in onStop",
            content.contains("override fun onStop()") && content.contains("gatewayStatusReceiver.unregister")
        )
    }

    @Test
    fun `ProjectDetailActivity starts polling in onResume`() {
        val content = File(SOURCE_DIR, "ProjectDetailActivity.kt").readText()
        assertTrue(
            "Activity must start status polling in onResume",
            content.contains("override fun onResume()") && content.contains("viewModel.startStatusPolling()")
        )
    }

    @Test
    fun `ProjectDetailActivity stops polling in onPause`() {
        val content = File(SOURCE_DIR, "ProjectDetailActivity.kt").readText()
        assertTrue(
            "Activity must stop status polling in onPause",
            content.contains("override fun onPause()") && content.contains("viewModel.stopStatusPolling()")
        )
    }

    @Test
    fun `ProjectDetailActivity calls refreshProjectStatus in onResume`() {
        val content = File(SOURCE_DIR, "ProjectDetailActivity.kt").readText()
        assertTrue(
            "Activity must call refreshProjectStatus in onResume",
            content.contains("viewModel.refreshProjectStatus()")
        )
    }

    @Test
    fun `ProjectDetailActivity sends broadcast when gateway becomes installed`() {
        val content = File(SOURCE_DIR, "ProjectDetailActivity.kt").readText()
        assertTrue(
            "updateGatewayUI must send broadcast via GatewayStatusReceiver.sendStatusBroadcast",
            content.contains("GatewayStatusReceiver.sendStatusBroadcast")
        )
    }

    @Test
    fun `ProjectDetailActivity receiver callback calls setGatewayInstalled`() {
        val content = File(SOURCE_DIR, "ProjectDetailActivity.kt").readText()
        assertTrue(
            "GatewayStatusReceiver callback must call viewModel.setGatewayInstalled(true)",
            content.contains("viewModel.setGatewayInstalled(true)")
        )
    }

    @Test
    fun `ProjectDetailActivity receiver callback stops polling on active`() {
        val content = File(SOURCE_DIR, "ProjectDetailActivity.kt").readText()
        // The receiver callback should stop polling when it receives an active status
        val receiverDef = content.indexOf("GatewayStatusReceiver {")
        assertTrue("Receiver definition must exist", receiverDef >= 0)

        val receiverBody = content.substring(receiverDef, content.indexOf("}", content.indexOf("}", receiverDef) + 1) + 1)
        assertTrue(
            "Receiver callback must stop polling",
            receiverBody.contains("viewModel.stopStatusPolling()")
        )
    }

    // ---- Data class tests ----

    @Test
    fun `ProjectStatus data class can be instantiated`() {
        val status = GatewayApiClient.ProjectStatus(
            name = "test-project",
            gatewayActive = true
        )
        assertEquals("test-project", status.name)
        assertTrue(status.gatewayActive)
    }

    @Test
    fun `ProjectStatus with gatewayActive false`() {
        val status = GatewayApiClient.ProjectStatus(
            name = "test-project",
            gatewayActive = false
        )
        assertEquals(false, status.gatewayActive)
    }

    @Test
    fun `ProjectStatus equality works correctly`() {
        val a = GatewayApiClient.ProjectStatus("project-a", true)
        val b = GatewayApiClient.ProjectStatus("project-a", true)
        val c = GatewayApiClient.ProjectStatus("project-a", false)
        assertEquals(a, b)
        assertTrue(a != c)
    }
}
