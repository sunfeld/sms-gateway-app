package com.sunfeld.smsgateway

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Unit tests for ProjectViewModel, InstallState sealed class, and
 * source-level verification of ViewModel/Activity integration.
 */
class ProjectViewModelTest {

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

    // ---- InstallState sealed class tests ----

    @Test
    fun `InstallState Idle is a singleton object`() {
        val a = InstallState.Idle
        val b = InstallState.Idle
        assertTrue("Idle should be the same instance", a === b)
    }

    @Test
    fun `InstallState Installing is a singleton object`() {
        val a = InstallState.Installing
        val b = InstallState.Installing
        assertTrue("Installing should be the same instance", a === b)
    }

    @Test
    fun `InstallState Success carries a message`() {
        val state = InstallState.Success("Gateway installed successfully")
        assertEquals("Gateway installed successfully", state.message)
    }

    @Test
    fun `InstallState Error carries a message`() {
        val error = InstallState.Error("Network timeout")
        assertEquals("Network timeout", error.message)
    }

    @Test
    fun `InstallState Error with different messages are not equal`() {
        val error1 = InstallState.Error("Network timeout")
        val error2 = InstallState.Error("Server returned 500")
        assertNotEquals(error1, error2)
    }

    @Test
    fun `InstallState Error with same message are equal`() {
        val error1 = InstallState.Error("Network timeout")
        val error2 = InstallState.Error("Network timeout")
        assertEquals(error1, error2)
    }

    @Test
    fun `InstallState Success with same message are equal`() {
        val a = InstallState.Success("done")
        val b = InstallState.Success("done")
        assertEquals(a, b)
    }

    @Test
    fun `InstallState Success with different messages are not equal`() {
        val a = InstallState.Success("done")
        val b = InstallState.Success("completed")
        assertNotEquals(a, b)
    }

    @Test
    fun `InstallState all variants are distinct types`() {
        val idle: InstallState = InstallState.Idle
        val installing: InstallState = InstallState.Installing
        val success: InstallState = InstallState.Success("ok")
        val error: InstallState = InstallState.Error("test")

        assertNotEquals(idle, installing)
        assertNotEquals(idle, success)
        assertNotEquals(idle, error)
        assertNotEquals(installing, success)
        assertNotEquals(installing, error)
        assertNotEquals(success, error)
    }

    @Test
    fun `InstallState can be exhaustively matched with when`() {
        val states = listOf(
            InstallState.Idle,
            InstallState.Installing,
            InstallState.Success("ok"),
            InstallState.Error("fail")
        )

        val labels = states.map { state ->
            when (state) {
                is InstallState.Idle -> "idle"
                is InstallState.Installing -> "installing"
                is InstallState.Success -> "success:${state.message}"
                is InstallState.Error -> "error:${state.message}"
            }
        }

        assertEquals(listOf("idle", "installing", "success:ok", "error:fail"), labels)
    }

    // ---- ProjectViewModel source verification ----

    @Test
    fun `ProjectViewModel source file exists`() {
        val viewModelFile = File(SOURCE_DIR, "ProjectViewModel.kt")
        assertTrue("ProjectViewModel.kt must exist", viewModelFile.exists())
    }

    @Test
    fun `ProjectViewModel extends ViewModel`() {
        val content = File(SOURCE_DIR, "ProjectViewModel.kt").readText()
        assertTrue(
            "ProjectViewModel must extend ViewModel",
            content.contains("class ProjectViewModel : ViewModel()")
        )
    }

    @Test
    fun `ProjectViewModel exposes installState as LiveData`() {
        val content = File(SOURCE_DIR, "ProjectViewModel.kt").readText()
        assertTrue(
            "installState must be exposed as LiveData<InstallState>",
            content.contains("val installState: LiveData<InstallState>")
        )
    }

    @Test
    fun `ProjectViewModel exposes gatewayInstalled as LiveData`() {
        val content = File(SOURCE_DIR, "ProjectViewModel.kt").readText()
        assertTrue(
            "gatewayInstalled must be exposed as LiveData<Boolean>",
            content.contains("val gatewayInstalled: LiveData<Boolean>")
        )
    }

    @Test
    fun `ProjectViewModel has installGateway method`() {
        val content = File(SOURCE_DIR, "ProjectViewModel.kt").readText()
        assertTrue(
            "installGateway method must exist",
            content.contains("fun installGateway()")
        )
    }

    @Test
    fun `ProjectViewModel has resetState method`() {
        val content = File(SOURCE_DIR, "ProjectViewModel.kt").readText()
        assertTrue(
            "resetState method must exist",
            content.contains("fun resetState()")
        )
    }

    @Test
    fun `ProjectViewModel has setGatewayInstalled method`() {
        val content = File(SOURCE_DIR, "ProjectViewModel.kt").readText()
        assertTrue(
            "setGatewayInstalled method must exist",
            content.contains("fun setGatewayInstalled(installed: Boolean)")
        )
    }

    @Test
    fun `ProjectViewModel initial installState is Idle`() {
        val content = File(SOURCE_DIR, "ProjectViewModel.kt").readText()
        assertTrue(
            "Initial state must be InstallState.Idle",
            content.contains("MutableLiveData<InstallState>(InstallState.Idle)")
        )
    }

    @Test
    fun `ProjectViewModel initial gatewayInstalled is false`() {
        val content = File(SOURCE_DIR, "ProjectViewModel.kt").readText()
        assertTrue(
            "Initial gatewayInstalled must be false",
            content.contains("MutableLiveData<Boolean>(false)")
        )
    }

    @Test
    fun `installGateway guards against duplicate calls during Installing`() {
        val content = File(SOURCE_DIR, "ProjectViewModel.kt").readText()
        assertTrue(
            "installGateway must guard against re-entry when Installing",
            content.contains("is InstallState.Installing") && content.contains("return")
        )
    }

    @Test
    fun `installGateway sets Installing state before coroutine launch`() {
        val content = File(SOURCE_DIR, "ProjectViewModel.kt").readText()
        val installMethodStart = content.indexOf("fun installGateway()")
        val launchStart = content.indexOf("viewModelScope.launch", installMethodStart)

        assertTrue("installGateway must exist", installMethodStart >= 0)
        assertTrue("viewModelScope.launch must exist", launchStart >= 0)

        val installingSetIndex = content.indexOf("InstallState.Installing", installMethodStart)
        assertTrue(
            "InstallState.Installing must be set before launching coroutine",
            installingSetIndex in (installMethodStart + 1) until launchStart
        )
    }

    @Test
    fun `installGateway uses GatewayApiClient`() {
        val content = File(SOURCE_DIR, "ProjectViewModel.kt").readText()
        assertTrue(
            "Must use apiClient.installGateway()",
            content.contains("apiClient.installGateway()")
        )
    }

    @Test
    fun `installGateway runs on IO dispatcher`() {
        val content = File(SOURCE_DIR, "ProjectViewModel.kt").readText()
        assertTrue(
            "Network call must run on Dispatchers.IO",
            content.contains("Dispatchers.IO")
        )
    }

    @Test
    fun `ProjectViewModel handles polling via statusUrl`() {
        val content = File(SOURCE_DIR, "ProjectViewModel.kt").readText()
        assertTrue(
            "Must poll job status when statusUrl is available",
            content.contains("pollJobStatus") && content.contains("statusUrl")
        )
    }

    @Test
    fun `ProjectViewModel sets gatewayInstalled true on success`() {
        val content = File(SOURCE_DIR, "ProjectViewModel.kt").readText()
        assertTrue(
            "Must set _gatewayInstalled.value = true on successful install",
            content.contains("_gatewayInstalled.value = true")
        )
    }

    @Test
    fun `resetState sets state back to Idle`() {
        val content = File(SOURCE_DIR, "ProjectViewModel.kt").readText()
        val resetIndex = content.indexOf("fun resetState()")
        assertTrue("resetState must exist", resetIndex >= 0)

        val resetBody = content.substring(resetIndex, content.indexOf("}", resetIndex) + 1)
        assertTrue(
            "resetState must set state to Idle",
            resetBody.contains("InstallState.Idle")
        )
    }

    // ---- GatewayApiClient source verification ----

    @Test
    fun `GatewayApiClient source file exists`() {
        val apiClientFile = File(SOURCE_DIR, "GatewayApiClient.kt")
        assertTrue("GatewayApiClient.kt must exist", apiClientFile.exists())
    }

    @Test
    fun `GatewayApiClient has installGateway method`() {
        val content = File(SOURCE_DIR, "GatewayApiClient.kt").readText()
        assertTrue(
            "GatewayApiClient must have installGateway method",
            content.contains("fun installGateway()")
        )
    }

    @Test
    fun `GatewayApiClient has getJobStatus method`() {
        val content = File(SOURCE_DIR, "GatewayApiClient.kt").readText()
        assertTrue(
            "GatewayApiClient must have getJobStatus method",
            content.contains("fun getJobStatus(")
        )
    }

    @Test
    fun `GatewayApiClient posts to install-gateway endpoint`() {
        val content = File(SOURCE_DIR, "GatewayApiClient.kt").readText()
        assertTrue(
            "Must POST to /api/projects/{name}/install-gateway",
            content.contains("install-gateway")
        )
    }

    @Test
    fun `GatewayApiClient configures OkHttp timeouts`() {
        val content = File(SOURCE_DIR, "GatewayApiClient.kt").readText()
        assertTrue("Must configure connectTimeout", content.contains("connectTimeout"))
        assertTrue("Must configure readTimeout", content.contains("readTimeout"))
    }

    // ---- Integration: ProjectDetailActivity uses ProjectViewModel correctly ----

    @Test
    fun `ProjectDetailActivity references ProjectViewModel via viewModels delegate`() {
        val content = File(SOURCE_DIR, "ProjectDetailActivity.kt").readText()
        assertTrue(
            "Activity must use viewModels() delegate for ProjectViewModel",
            content.contains("by viewModels()")
        )
    }

    @Test
    fun `ProjectDetailActivity observes installState`() {
        val content = File(SOURCE_DIR, "ProjectDetailActivity.kt").readText()
        assertTrue(
            "Activity must observe viewModel.installState",
            content.contains("viewModel.installState.observe")
        )
    }

    @Test
    fun `ProjectDetailActivity observes gatewayInstalled`() {
        val content = File(SOURCE_DIR, "ProjectDetailActivity.kt").readText()
        assertTrue(
            "Activity must observe viewModel.gatewayInstalled",
            content.contains("viewModel.gatewayInstalled.observe")
        )
    }

    @Test
    fun `ProjectDetailActivity handles all InstallState variants`() {
        val content = File(SOURCE_DIR, "ProjectDetailActivity.kt").readText()
        assertTrue("Must handle Idle state", content.contains("is InstallState.Idle"))
        assertTrue("Must handle Installing state", content.contains("is InstallState.Installing"))
        assertTrue("Must handle Success state", content.contains("is InstallState.Success"))
        assertTrue("Must handle Error state", content.contains("is InstallState.Error"))
    }

    @Test
    fun `ProjectDetailActivity calls installGateway on button click`() {
        val content = File(SOURCE_DIR, "ProjectDetailActivity.kt").readText()
        assertTrue(
            "Activity must call viewModel.installGateway()",
            content.contains("viewModel.installGateway()")
        )
    }
}
