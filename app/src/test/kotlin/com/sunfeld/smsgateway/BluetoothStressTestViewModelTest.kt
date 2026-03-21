package com.sunfeld.smsgateway

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Unit tests for BluetoothStressTestViewModel, StressTestState sealed class,
 * GatewayApiClient Bluetooth DoS methods, and layout verification for the
 * "Bluetooth Stress Test" toggle dashboard feature.
 */
class BluetoothStressTestViewModelTest {

    companion object {
        private val PROJECT_ROOT = findProjectRoot()
        private val SOURCE_DIR = File(PROJECT_ROOT, "app/src/main/kotlin/com/sunfeld/smsgateway")
        private val LAYOUT_DIR = File(PROJECT_ROOT, "app/src/main/res/layout")

        private fun findProjectRoot(): File {
            var dir = File(System.getProperty("user.dir"))
            while (dir.parentFile != null) {
                if (File(dir, "settings.gradle.kts").exists()) return dir
                dir = dir.parentFile
            }
            return File(System.getProperty("user.dir"))
        }
    }

    // ---- StressTestState sealed class tests ----

    @Test
    fun `StressTestState Idle is a singleton object`() {
        val a = StressTestState.Idle
        val b = StressTestState.Idle
        assertTrue("Idle should be the same instance", a === b)
    }

    @Test
    fun `StressTestState Starting is a singleton object`() {
        val a = StressTestState.Starting
        val b = StressTestState.Starting
        assertTrue("Starting should be the same instance", a === b)
    }

    @Test
    fun `StressTestState Stopping is a singleton object`() {
        val a = StressTestState.Stopping
        val b = StressTestState.Stopping
        assertTrue("Stopping should be the same instance", a === b)
    }

    @Test
    fun `StressTestState Running carries session data`() {
        val state = StressTestState.Running(
            sessionId = "bt-sess-123",
            packetsSent = 42,
            targetsActive = 3,
            remainingSeconds = 50
        )
        assertEquals("bt-sess-123", state.sessionId)
        assertEquals(42, state.packetsSent)
        assertEquals(3, state.targetsActive)
        assertEquals(50, state.remainingSeconds)
    }

    @Test
    fun `StressTestState Error carries a message`() {
        val state = StressTestState.Error("Connection lost")
        assertEquals("Connection lost", state.message)
    }

    @Test
    fun `StressTestState Error with different messages are not equal`() {
        val a = StressTestState.Error("timeout")
        val b = StressTestState.Error("refused")
        assertNotEquals(a, b)
    }

    @Test
    fun `StressTestState Error with same message are equal`() {
        val a = StressTestState.Error("timeout")
        val b = StressTestState.Error("timeout")
        assertEquals(a, b)
    }

    @Test
    fun `StressTestState Running with same data are equal`() {
        val a = StressTestState.Running("s1", 10, 2, 30)
        val b = StressTestState.Running("s1", 10, 2, 30)
        assertEquals(a, b)
    }

    @Test
    fun `StressTestState Running with different data are not equal`() {
        val a = StressTestState.Running("s1", 10, 2, 30)
        val b = StressTestState.Running("s1", 20, 2, 30)
        assertNotEquals(a, b)
    }

    @Test
    fun `StressTestState all variants are distinct types`() {
        val idle: StressTestState = StressTestState.Idle
        val starting: StressTestState = StressTestState.Starting
        val running: StressTestState = StressTestState.Running("s", 0, 0, 0)
        val stopping: StressTestState = StressTestState.Stopping
        val error: StressTestState = StressTestState.Error("e")

        assertNotEquals(idle, starting)
        assertNotEquals(idle, running)
        assertNotEquals(idle, stopping)
        assertNotEquals(idle, error)
        assertNotEquals(starting, running)
        assertNotEquals(starting, stopping)
        assertNotEquals(starting, error)
        assertNotEquals(running, stopping)
        assertNotEquals(running, error)
        assertNotEquals(stopping, error)
    }

    @Test
    fun `StressTestState can be exhaustively matched with when`() {
        val states = listOf(
            StressTestState.Idle,
            StressTestState.Starting,
            StressTestState.Running("s1", 5, 2, 10),
            StressTestState.Stopping,
            StressTestState.Error("fail")
        )

        val labels = states.map { state ->
            when (state) {
                is StressTestState.Idle -> "idle"
                is StressTestState.Starting -> "starting"
                is StressTestState.Running -> "running:${state.packetsSent}"
                is StressTestState.Stopping -> "stopping"
                is StressTestState.Error -> "error:${state.message}"
            }
        }

        assertEquals(
            listOf("idle", "starting", "running:5", "stopping", "error:fail"),
            labels
        )
    }

    // ---- BluetoothStressTestViewModel source verification ----

    @Test
    fun `BluetoothStressTestViewModel source file exists`() {
        val file = File(SOURCE_DIR, "BluetoothStressTestViewModel.kt")
        assertTrue("BluetoothStressTestViewModel.kt must exist", file.exists())
    }

    @Test
    fun `BluetoothStressTestViewModel extends ViewModel`() {
        val content = File(SOURCE_DIR, "BluetoothStressTestViewModel.kt").readText()
        assertTrue(
            "BluetoothStressTestViewModel must extend ViewModel",
            content.contains("class BluetoothStressTestViewModel : ViewModel()")
        )
    }

    @Test
    fun `BluetoothStressTestViewModel exposes state as LiveData`() {
        val content = File(SOURCE_DIR, "BluetoothStressTestViewModel.kt").readText()
        assertTrue(
            "state must be exposed as LiveData<StressTestState>",
            content.contains("val state: LiveData<StressTestState>")
        )
    }

    @Test
    fun `BluetoothStressTestViewModel exposes packetsSent as LiveData`() {
        val content = File(SOURCE_DIR, "BluetoothStressTestViewModel.kt").readText()
        assertTrue(
            "packetsSent must be exposed as LiveData<Int>",
            content.contains("val packetsSent: LiveData<Int>")
        )
    }

    @Test
    fun `BluetoothStressTestViewModel exposes devicesTargeted as LiveData`() {
        val content = File(SOURCE_DIR, "BluetoothStressTestViewModel.kt").readText()
        assertTrue(
            "devicesTargeted must be exposed as LiveData<Int>",
            content.contains("val devicesTargeted: LiveData<Int>")
        )
    }

    @Test
    fun `BluetoothStressTestViewModel initial state is Idle`() {
        val content = File(SOURCE_DIR, "BluetoothStressTestViewModel.kt").readText()
        assertTrue(
            "Initial state must be StressTestState.Idle",
            content.contains("MutableLiveData<StressTestState>(StressTestState.Idle)")
        )
    }

    @Test
    fun `BluetoothStressTestViewModel initial packetsSent is 0`() {
        val content = File(SOURCE_DIR, "BluetoothStressTestViewModel.kt").readText()
        assertTrue(
            "Initial packetsSent must be 0",
            content.contains("MutableLiveData(0)") || content.contains("MutableLiveData<Int>(0)")
        )
    }

    @Test
    fun `BluetoothStressTestViewModel has startStressTest method`() {
        val content = File(SOURCE_DIR, "BluetoothStressTestViewModel.kt").readText()
        assertTrue(
            "startStressTest method must exist",
            content.contains("fun startStressTest(")
        )
    }

    @Test
    fun `BluetoothStressTestViewModel has stopStressTest method`() {
        val content = File(SOURCE_DIR, "BluetoothStressTestViewModel.kt").readText()
        assertTrue(
            "stopStressTest method must exist",
            content.contains("fun stopStressTest()")
        )
    }

    @Test
    fun `BluetoothStressTestViewModel has dismissError method`() {
        val content = File(SOURCE_DIR, "BluetoothStressTestViewModel.kt").readText()
        assertTrue(
            "dismissError method must exist",
            content.contains("fun dismissError()")
        )
    }

    @Test
    fun `startStressTest guards against duplicate calls when Running`() {
        val content = File(SOURCE_DIR, "BluetoothStressTestViewModel.kt").readText()
        assertTrue(
            "startStressTest must guard against Running state",
            content.contains("StressTestState.Running") && content.contains("return")
        )
    }

    @Test
    fun `startStressTest guards against duplicate calls when Starting`() {
        val content = File(SOURCE_DIR, "BluetoothStressTestViewModel.kt").readText()
        assertTrue(
            "startStressTest must guard against Starting state",
            content.contains("StressTestState.Starting") && content.contains("return")
        )
    }

    @Test
    fun `startStressTest sets Starting state before API call`() {
        val content = File(SOURCE_DIR, "BluetoothStressTestViewModel.kt").readText()
        val methodStart = content.indexOf("fun startStressTest(")
        val launchStart = content.indexOf("viewModelScope.launch", methodStart)

        assertTrue("startStressTest must exist", methodStart >= 0)
        assertTrue("viewModelScope.launch must exist", launchStart >= 0)

        val startingSet = content.indexOf("StressTestState.Starting", methodStart)
        assertTrue(
            "StressTestState.Starting must be set before launching coroutine",
            startingSet in (methodStart + 1) until launchStart
        )
    }

    @Test
    fun `startStressTest uses Dispatchers IO for network call`() {
        val content = File(SOURCE_DIR, "BluetoothStressTestViewModel.kt").readText()
        assertTrue(
            "Network calls must run on Dispatchers.IO",
            content.contains("Dispatchers.IO")
        )
    }

    @Test
    fun `startStressTest calls apiClient startBluetoothDos`() {
        val content = File(SOURCE_DIR, "BluetoothStressTestViewModel.kt").readText()
        assertTrue(
            "Must call apiClient.startBluetoothDos",
            content.contains("apiClient.startBluetoothDos(")
        )
    }

    @Test
    fun `stopStressTest calls apiClient stopBluetoothDos`() {
        val content = File(SOURCE_DIR, "BluetoothStressTestViewModel.kt").readText()
        assertTrue(
            "Must call apiClient.stopBluetoothDos",
            content.contains("apiClient.stopBluetoothDos(")
        )
    }

    @Test
    fun `stopStressTest guards against non-Running state`() {
        val content = File(SOURCE_DIR, "BluetoothStressTestViewModel.kt").readText()
        val methodStart = content.indexOf("fun stopStressTest()")
        assertTrue("stopStressTest must exist", methodStart >= 0)
        val methodBody = content.substring(methodStart, content.indexOf("fun ", methodStart + 1))
        assertTrue(
            "stopStressTest must check for Running state",
            methodBody.contains("StressTestState.Running")
        )
    }

    @Test
    fun `stopStressTest sets Stopping state before API call`() {
        val content = File(SOURCE_DIR, "BluetoothStressTestViewModel.kt").readText()
        val methodStart = content.indexOf("fun stopStressTest()")
        assertTrue("stopStressTest must exist", methodStart >= 0)
        val methodBody = content.substring(methodStart, content.indexOf("fun ", methodStart + 1))
        assertTrue(
            "Must set Stopping state",
            methodBody.contains("StressTestState.Stopping")
        )
    }

    @Test
    fun `BluetoothStressTestViewModel has polling mechanism`() {
        val content = File(SOURCE_DIR, "BluetoothStressTestViewModel.kt").readText()
        assertTrue(
            "Must have polling job",
            content.contains("pollingJob")
        )
    }

    @Test
    fun `BluetoothStressTestViewModel polls via getBluetoothDosStatus`() {
        val content = File(SOURCE_DIR, "BluetoothStressTestViewModel.kt").readText()
        assertTrue(
            "Must poll status via apiClient.getBluetoothDosStatus",
            content.contains("apiClient.getBluetoothDosStatus(")
        )
    }

    @Test
    fun `BluetoothStressTestViewModel handles completed status by returning to Idle`() {
        val content = File(SOURCE_DIR, "BluetoothStressTestViewModel.kt").readText()
        assertTrue(
            "Must handle completed status",
            content.contains("\"completed\"")
        )
        assertTrue(
            "Must return to Idle when session ends",
            content.contains("StressTestState.Idle")
        )
    }

    @Test
    fun `BluetoothStressTestViewModel handles polling errors`() {
        val content = File(SOURCE_DIR, "BluetoothStressTestViewModel.kt").readText()
        assertTrue(
            "Must handle polling errors with StressTestState.Error",
            content.contains("StressTestState.Error(")
        )
    }

    @Test
    fun `BluetoothStressTestViewModel overrides onCleared to stop polling`() {
        val content = File(SOURCE_DIR, "BluetoothStressTestViewModel.kt").readText()
        assertTrue(
            "Must override onCleared",
            content.contains("override fun onCleared()")
        )
        val onClearedStart = content.indexOf("override fun onCleared()")
        val onClearedBody = content.substring(onClearedStart)
        assertTrue(
            "onCleared must stop polling",
            onClearedBody.contains("stopPolling()")
        )
    }

    @Test
    fun `BluetoothStressTestViewModel has configurable apiClient`() {
        val content = File(SOURCE_DIR, "BluetoothStressTestViewModel.kt").readText()
        assertTrue(
            "apiClient must be assignable for testing",
            content.contains("var apiClient")
        )
    }

    @Test
    fun `startStressTest accepts duration and intensity parameters`() {
        val content = File(SOURCE_DIR, "BluetoothStressTestViewModel.kt").readText()
        assertTrue(
            "startStressTest must accept duration parameter",
            content.contains("duration: Int")
        )
        assertTrue(
            "startStressTest must accept intensity parameter",
            content.contains("intensity: Int")
        )
    }

    @Test
    fun `dismissError resets state to Idle`() {
        val content = File(SOURCE_DIR, "BluetoothStressTestViewModel.kt").readText()
        val dismissStart = content.indexOf("fun dismissError()")
        assertTrue("dismissError must exist", dismissStart >= 0)
        val dismissBody = content.substring(dismissStart, content.indexOf("}", dismissStart) + 1)
        assertTrue(
            "dismissError must set state to Idle",
            dismissBody.contains("StressTestState.Idle")
        )
    }

    // ---- GatewayApiClient Bluetooth DoS methods ----

    @Test
    fun `GatewayApiClient has startBluetoothDos method`() {
        val content = File(SOURCE_DIR, "GatewayApiClient.kt").readText()
        assertTrue(
            "GatewayApiClient must have startBluetoothDos method",
            content.contains("fun startBluetoothDos(")
        )
    }

    @Test
    fun `GatewayApiClient startBluetoothDos accepts duration and intensity`() {
        val content = File(SOURCE_DIR, "GatewayApiClient.kt").readText()
        assertTrue(
            "startBluetoothDos must accept duration: Int",
            content.contains("fun startBluetoothDos(duration: Int, intensity: Int)")
        )
    }

    @Test
    fun `GatewayApiClient startBluetoothDos returns BluetoothDosStartResult`() {
        val content = File(SOURCE_DIR, "GatewayApiClient.kt").readText()
        assertTrue(
            "startBluetoothDos must return BluetoothDosStartResult",
            content.contains("fun startBluetoothDos(duration: Int, intensity: Int): BluetoothDosStartResult")
        )
    }

    @Test
    fun `GatewayApiClient has getBluetoothDosStatus method`() {
        val content = File(SOURCE_DIR, "GatewayApiClient.kt").readText()
        assertTrue(
            "GatewayApiClient must have getBluetoothDosStatus method",
            content.contains("fun getBluetoothDosStatus(")
        )
    }

    @Test
    fun `GatewayApiClient getBluetoothDosStatus returns BluetoothDosStatus`() {
        val content = File(SOURCE_DIR, "GatewayApiClient.kt").readText()
        assertTrue(
            "getBluetoothDosStatus must return BluetoothDosStatus",
            content.contains("fun getBluetoothDosStatus(sessionId: String): BluetoothDosStatus")
        )
    }

    @Test
    fun `GatewayApiClient has stopBluetoothDos method`() {
        val content = File(SOURCE_DIR, "GatewayApiClient.kt").readText()
        assertTrue(
            "GatewayApiClient must have stopBluetoothDos method",
            content.contains("fun stopBluetoothDos(")
        )
    }

    @Test
    fun `GatewayApiClient stopBluetoothDos returns BluetoothDosStopResult`() {
        val content = File(SOURCE_DIR, "GatewayApiClient.kt").readText()
        assertTrue(
            "stopBluetoothDos must return BluetoothDosStopResult",
            content.contains("fun stopBluetoothDos(sessionId: String): BluetoothDosStopResult")
        )
    }

    @Test
    fun `GatewayApiClient posts to bluetooth dos start endpoint`() {
        val content = File(SOURCE_DIR, "GatewayApiClient.kt").readText()
        assertTrue(
            "Must POST to /api/bluetooth/dos/start",
            content.contains("/api/bluetooth/dos/start")
        )
    }

    @Test
    fun `GatewayApiClient gets bluetooth dos status endpoint`() {
        val content = File(SOURCE_DIR, "GatewayApiClient.kt").readText()
        assertTrue(
            "Must GET /api/bluetooth/dos/status/",
            content.contains("/api/bluetooth/dos/status/")
        )
    }

    @Test
    fun `GatewayApiClient posts to bluetooth dos stop endpoint`() {
        val content = File(SOURCE_DIR, "GatewayApiClient.kt").readText()
        assertTrue(
            "Must POST to /api/bluetooth/dos/stop/",
            content.contains("/api/bluetooth/dos/stop/")
        )
    }

    @Test
    fun `BluetoothDosStartResult data class has required fields`() {
        val content = File(SOURCE_DIR, "GatewayApiClient.kt").readText()
        assertTrue("Must have status field", content.contains("data class BluetoothDosStartResult"))
        val classStart = content.indexOf("data class BluetoothDosStartResult")
        val classBody = content.substring(classStart, content.indexOf(")", classStart) + 1)
        assertTrue("Must have status", classBody.contains("val status: String"))
        assertTrue("Must have sessionId", classBody.contains("val sessionId: String"))
        assertTrue("Must have duration", classBody.contains("val duration: Int"))
        assertTrue("Must have intensity", classBody.contains("val intensity: Int"))
        assertTrue("Must have targetsDiscovered", classBody.contains("val targetsDiscovered: Int"))
    }

    @Test
    fun `BluetoothDosStatus data class has required fields`() {
        val content = File(SOURCE_DIR, "GatewayApiClient.kt").readText()
        assertTrue("Must have BluetoothDosStatus class", content.contains("data class BluetoothDosStatus"))
        val classStart = content.indexOf("data class BluetoothDosStatus")
        val classBody = content.substring(classStart, content.indexOf(")", classStart) + 1)
        assertTrue("Must have sessionId", classBody.contains("val sessionId: String"))
        assertTrue("Must have status", classBody.contains("val status: String"))
        assertTrue("Must have packetsSent", classBody.contains("val packetsSent: Int"))
        assertTrue("Must have targetsActive", classBody.contains("val targetsActive: Int"))
        assertTrue("Must have remainingSeconds", classBody.contains("val remainingSeconds: Int"))
        assertTrue("Must have intensity", classBody.contains("val intensity: Int"))
    }

    @Test
    fun `BluetoothDosStopResult data class has required fields`() {
        val content = File(SOURCE_DIR, "GatewayApiClient.kt").readText()
        assertTrue("Must have BluetoothDosStopResult class", content.contains("data class BluetoothDosStopResult"))
        val classStart = content.indexOf("data class BluetoothDosStopResult")
        val classBody = content.substring(classStart, content.indexOf(")", classStart) + 1)
        assertTrue("Must have status", classBody.contains("val status: String"))
        assertTrue("Must have sessionId", classBody.contains("val sessionId: String"))
    }

    // ---- Layout verification for activity_bluetooth_stress_test.xml ----

    @Test
    fun `Stress test layout file exists`() {
        val layoutFile = File(LAYOUT_DIR, "activity_bluetooth_stress_test.xml")
        assertTrue("activity_bluetooth_stress_test.xml must exist", layoutFile.exists())
    }

    @Test
    fun `Layout has MaterialSwitch toggle for stress test`() {
        val content = File(LAYOUT_DIR, "activity_bluetooth_stress_test.xml").readText()
        assertTrue(
            "Layout must contain a MaterialSwitch toggle",
            content.contains("MaterialSwitch")
        )
        assertTrue(
            "Toggle must have id switchStressTest",
            content.contains("@+id/switchStressTest")
        )
    }

    @Test
    fun `Layout has packets sent counter`() {
        val content = File(LAYOUT_DIR, "activity_bluetooth_stress_test.xml").readText()
        assertTrue(
            "Layout must have a packets sent counter view",
            content.contains("@+id/txtPacketsSentCount")
        )
    }

    @Test
    fun `Layout has devices targeted counter`() {
        val content = File(LAYOUT_DIR, "activity_bluetooth_stress_test.xml").readText()
        assertTrue(
            "Layout must have a devices targeted counter view",
            content.contains("@+id/txtDevicesTargetedCount")
        )
    }

    @Test
    fun `Layout has packets sent label`() {
        val content = File(LAYOUT_DIR, "activity_bluetooth_stress_test.xml").readText()
        assertTrue(
            "Layout must reference packets_sent_label string",
            content.contains("@string/packets_sent_label")
        )
    }

    @Test
    fun `Layout has devices targeted label`() {
        val content = File(LAYOUT_DIR, "activity_bluetooth_stress_test.xml").readText()
        assertTrue(
            "Layout must reference devices_targeted_label string",
            content.contains("@string/devices_targeted_label")
        )
    }

    @Test
    fun `Layout has status text view`() {
        val content = File(LAYOUT_DIR, "activity_bluetooth_stress_test.xml").readText()
        assertTrue(
            "Layout must have a status text view",
            content.contains("@+id/txtStatus")
        )
    }

    @Test
    fun `Layout has remaining time text view`() {
        val content = File(LAYOUT_DIR, "activity_bluetooth_stress_test.xml").readText()
        assertTrue(
            "Layout must have a remaining time text view",
            content.contains("@+id/txtRemainingTime")
        )
    }

    @Test
    fun `Layout uses ConstraintLayout as root`() {
        val content = File(LAYOUT_DIR, "activity_bluetooth_stress_test.xml").readText()
        assertTrue(
            "Root layout must be ConstraintLayout",
            content.contains("androidx.constraintlayout.widget.ConstraintLayout")
        )
    }

    @Test
    fun `Layout uses MaterialCardView for counters`() {
        val content = File(LAYOUT_DIR, "activity_bluetooth_stress_test.xml").readText()
        assertTrue(
            "Counters must be wrapped in MaterialCardView",
            content.contains("MaterialCardView")
        )
        assertTrue(
            "CardView must have id cardCounters",
            content.contains("@+id/cardCounters")
        )
    }

    @Test
    fun `Layout counters use DisplaySmall text appearance`() {
        val content = File(LAYOUT_DIR, "activity_bluetooth_stress_test.xml").readText()
        assertTrue(
            "Counter values must use DisplaySmall appearance for prominence",
            content.contains("textAppearanceDisplaySmall")
        )
    }

    @Test
    fun `Layout has title text view`() {
        val content = File(LAYOUT_DIR, "activity_bluetooth_stress_test.xml").readText()
        assertTrue(
            "Layout must have a title",
            content.contains("@+id/txtTitle")
        )
        assertTrue(
            "Title must reference bluetooth_stress_test_title string",
            content.contains("@string/bluetooth_stress_test_title")
        )
    }

    @Test
    fun `Layout remaining time is initially gone`() {
        val content = File(LAYOUT_DIR, "activity_bluetooth_stress_test.xml").readText()
        val remainingStart = content.indexOf("txtRemainingTime")
        assertTrue("txtRemainingTime must exist", remainingStart >= 0)
        val remainingSection = content.substring(remainingStart, content.indexOf("/>", remainingStart) + 2)
        assertTrue(
            "Remaining time must be initially hidden",
            remainingSection.contains("android:visibility=\"gone\"")
        )
    }
}
