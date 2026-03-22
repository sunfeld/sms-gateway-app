package com.sunfeld.smsgateway

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Unit tests for ProjectViewModel, InstallResult sealed class, and
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

    // ---- InstallResult sealed class tests ----

    @Test
    fun `InstallResult Idle is a singleton object`() {
        val a = InstallResult.Idle
        val b = InstallResult.Idle
        assertTrue("Idle should be the same instance", a === b)
    }

    @Test
    fun `InstallResult Installing is a singleton object`() {
        val a = InstallResult.Installing
        val b = InstallResult.Installing
        assertTrue("Installing should be the same instance", a === b)
    }

    @Test
    fun `InstallResult Success is a singleton object`() {
        val a = InstallResult.Success
        val b = InstallResult.Success
        assertTrue("Success should be the same instance", a === b)
    }

    @Test
    fun `InstallResult Error carries a message`() {
        val error = InstallResult.Error("Network timeout")
        assertEquals("Network timeout", error.message)
    }

    @Test
    fun `InstallResult Error with different messages are not equal`() {
        val error1 = InstallResult.Error("Network timeout")
        val error2 = InstallResult.Error("Server returned 500")
        assertNotEquals(error1, error2)
    }

    @Test
    fun `InstallResult Error with same message are equal`() {
        val error1 = InstallResult.Error("Network timeout")
        val error2 = InstallResult.Error("Network timeout")
        assertEquals(error1, error2)
    }

    @Test
    fun `InstallResult all variants are distinct types`() {
        val idle: InstallResult = InstallResult.Idle
        val installing: InstallResult = InstallResult.Installing
        val success: InstallResult = InstallResult.Success
        val error: InstallResult = InstallResult.Error("test")

        assertNotEquals(idle, installing)
        assertNotEquals(idle, success)
        assertNotEquals(idle, error)
        assertNotEquals(installing, success)
        assertNotEquals(installing, error)
        assertNotEquals(success, error)
    }

    @Test
    fun `InstallResult can be exhaustively matched with when`() {
        val states = listOf(
            InstallResult.Idle,
            InstallResult.Installing,
            InstallResult.Success,
            InstallResult.Error("fail")
        )

        val labels = states.map { state ->
            when (state) {
                is InstallResult.Idle -> "idle"
                is InstallResult.Installing -> "installing"
                is InstallResult.Success -> "success"
                is InstallResult.Error -> "error:${state.message}"
            }
        }

        assertEquals(listOf("idle", "installing", "success", "error:fail"), labels)
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
    fun `ProjectViewModel exposes installState as StateFlow`() {
        val content = File(SOURCE_DIR, "ProjectViewModel.kt").readText()
        assertTrue(
            "installState must be exposed as StateFlow<InstallResult>",
            content.contains("val installState: StateFlow<InstallResult>")
        )
    }

    @Test
    fun `ProjectViewModel exposes gatewayInstalled as StateFlow`() {
        val content = File(SOURCE_DIR, "ProjectViewModel.kt").readText()
        assertTrue(
            "gatewayInstalled must be exposed as StateFlow<Boolean>",
            content.contains("val gatewayInstalled: StateFlow<Boolean>")
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
            "Initial state must be InstallResult.Idle",
            content.contains("MutableStateFlow<InstallResult>(InstallResult.Idle)")
        )
    }

    @Test
    fun `ProjectViewModel initial gatewayInstalled is false`() {
        val content = File(SOURCE_DIR, "ProjectViewModel.kt").readText()
        assertTrue(
            "Initial gatewayInstalled must be false",
            content.contains("MutableStateFlow(false)")
        )
    }

    @Test
    fun `installGateway guards against duplicate calls during Installing`() {
        val content = File(SOURCE_DIR, "ProjectViewModel.kt").readText()
        assertTrue(
            "installGateway must guard against re-entry when Installing",
            content.contains("is InstallResult.Installing") && content.contains("return")
        )
    }

    @Test
    fun `installGateway sets Installing state before coroutine launch`() {
        val content = File(SOURCE_DIR, "ProjectViewModel.kt").readText()
        val installMethodStart = content.indexOf("fun installGateway()")
        val launchStart = content.indexOf("viewModelScope.launch", installMethodStart)

        assertTrue("installGateway must exist", installMethodStart >= 0)
        assertTrue("viewModelScope.launch must exist", launchStart >= 0)

        val installingSetIndex = content.indexOf("InstallResult.Installing", installMethodStart)
        assertTrue(
            "InstallResult.Installing must be set before launching coroutine",
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
            resetBody.contains("InstallResult.Idle")
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
    fun `ProjectDetailActivity collects installState`() {
        val content = File(SOURCE_DIR, "ProjectDetailActivity.kt").readText()
        assertTrue(
            "Activity must collect viewModel.installState",
            content.contains("viewModel.installState.collect")
        )
    }

    @Test
    fun `ProjectDetailActivity collects gatewayInstalled`() {
        val content = File(SOURCE_DIR, "ProjectDetailActivity.kt").readText()
        assertTrue(
            "Activity must collect viewModel.gatewayInstalled",
            content.contains("viewModel.gatewayInstalled.collect")
        )
    }

    @Test
    fun `ProjectDetailActivity handles all InstallResult variants`() {
        val content = File(SOURCE_DIR, "ProjectDetailActivity.kt").readText()
        assertTrue("Must handle Idle state", content.contains("is InstallResult.Idle"))
        assertTrue("Must handle Installing state", content.contains("is InstallResult.Installing"))
        assertTrue("Must handle Success state", content.contains("is InstallResult.Success"))
        assertTrue("Must handle Error state", content.contains("is InstallResult.Error"))
    }

    @Test
    fun `ProjectDetailActivity calls installGateway on button click`() {
        val content = File(SOURCE_DIR, "ProjectDetailActivity.kt").readText()
        assertTrue(
            "Activity must call viewModel.installGateway()",
            content.contains("viewModel.installGateway()")
        )
    }

    // ---- Loading spinner (CircularProgressIndicator) tests ----

    @Test
    fun `ProjectDetailActivity references CircularProgressIndicator`() {
        val content = File(SOURCE_DIR, "ProjectDetailActivity.kt").readText()
        assertTrue(
            "Activity must import CircularProgressIndicator",
            content.contains("import com.google.android.material.progressindicator.CircularProgressIndicator")
        )
    }

    @Test
    fun `ProjectDetailActivity declares progressInstalling field`() {
        val content = File(SOURCE_DIR, "ProjectDetailActivity.kt").readText()
        assertTrue(
            "Activity must declare progressInstalling field",
            content.contains("progressInstalling")
        )
    }

    @Test
    fun `ProjectDetailActivity shows spinner during Installing state`() {
        val content = File(SOURCE_DIR, "ProjectDetailActivity.kt").readText()
        assertTrue(
            "Spinner must be made VISIBLE during Installing state",
            content.contains("progressInstalling.visibility = View.VISIBLE")
        )
    }

    @Test
    fun `ProjectDetailActivity hides spinner during Idle state`() {
        val content = File(SOURCE_DIR, "ProjectDetailActivity.kt").readText()
        val idleBlock = content.indexOf("is InstallResult.Idle")
        assertTrue("Must handle Idle state", idleBlock >= 0)
        val nextBlock = content.indexOf("is InstallResult.", idleBlock + 1)
        val idleSection = content.substring(idleBlock, nextBlock)
        assertTrue(
            "Spinner must be hidden (GONE) during Idle state",
            idleSection.contains("progressInstalling.visibility = View.GONE")
        )
    }

    @Test
    fun `ProjectDetailActivity hides spinner on Success`() {
        val content = File(SOURCE_DIR, "ProjectDetailActivity.kt").readText()
        val successBlock = content.indexOf("is InstallResult.Success")
        assertTrue("Must handle Success state", successBlock >= 0)
        val nextBlock = content.indexOf("is InstallResult.", successBlock + 1)
        val successSection = content.substring(successBlock, nextBlock)
        assertTrue(
            "Spinner must be hidden (GONE) on Success",
            successSection.contains("progressInstalling.visibility = View.GONE")
        )
    }

    @Test
    fun `ProjectDetailActivity hides spinner on Error`() {
        val content = File(SOURCE_DIR, "ProjectDetailActivity.kt").readText()
        val errorBlock = content.indexOf("is InstallResult.Error")
        assertTrue("Must handle Error state", errorBlock >= 0)
        val errorSection = content.substring(errorBlock, content.indexOf("}", errorBlock + 30) + 1)
        assertTrue(
            "Spinner must be hidden (GONE) on Error",
            errorSection.contains("progressInstalling.visibility = View.GONE")
        )
    }

    @Test
    fun `ProjectDetailActivity shows success toast`() {
        val content = File(SOURCE_DIR, "ProjectDetailActivity.kt").readText()
        assertTrue(
            "Must show toast with success message",
            content.contains("Gateway installed successfully")
        )
    }

    @Test
    fun `ProjectDetailActivity shows error toast with message`() {
        val content = File(SOURCE_DIR, "ProjectDetailActivity.kt").readText()
        assertTrue(
            "Must show toast with error message from InstallResult.Error",
            content.contains("state.message") && content.contains("Toast.makeText")
        )
    }

    // ---- Layout XML verification ----

    @Test
    fun `activity_project_detail layout contains CircularProgressIndicator`() {
        val layoutFile = File(PROJECT_ROOT, "app/src/main/res/layout/activity_project_detail.xml")
        assertTrue("Layout file must exist", layoutFile.exists())
        val content = layoutFile.readText()
        assertTrue(
            "Layout must contain CircularProgressIndicator widget",
            content.contains("CircularProgressIndicator")
        )
    }

    @Test
    fun `activity_project_detail layout has progressInstalling id`() {
        val layoutFile = File(PROJECT_ROOT, "app/src/main/res/layout/activity_project_detail.xml")
        val content = layoutFile.readText()
        assertTrue(
            "Layout must define progressInstalling id",
            content.contains("@+id/progressInstalling")
        )
    }

    @Test
    fun `activity_project_detail spinner starts as gone`() {
        val layoutFile = File(PROJECT_ROOT, "app/src/main/res/layout/activity_project_detail.xml")
        val content = layoutFile.readText()
        val spinnerStart = content.indexOf("progressInstalling")
        assertTrue("progressInstalling must exist in layout", spinnerStart >= 0)
        val spinnerSection = content.substring(spinnerStart, content.indexOf("/>", spinnerStart) + 2)
        assertTrue(
            "Spinner must start with visibility=gone",
            spinnerSection.contains("android:visibility=\"gone\"")
        )
    }

    @Test
    fun `activity_project_detail spinner is indeterminate`() {
        val layoutFile = File(PROJECT_ROOT, "app/src/main/res/layout/activity_project_detail.xml")
        val content = layoutFile.readText()
        val spinnerStart = content.indexOf("progressInstalling")
        assertTrue("progressInstalling must exist in layout", spinnerStart >= 0)
        val spinnerSection = content.substring(spinnerStart, content.indexOf("/>", spinnerStart) + 2)
        assertTrue(
            "Spinner must be indeterminate",
            spinnerSection.contains("android:indeterminate=\"true\"")
        )
    }
}
