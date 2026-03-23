package com.sunfeld.smsgateway

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Integration tests that simulate a Bluetooth discovery stream flowing through
 * [DeviceFilter] and into a UI list, verifying correct population without duplicates.
 *
 * Uses [MutableStateFlow] as a stand-in for the real Bluetooth BroadcastReceiver stream,
 * exercising the same add-or-update + de-dup pipeline that [BluetoothDiscoveryManager] uses.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DeviceStreamIntegrationTest {

    /**
     * Lightweight device representation matching what the adapter would display.
     * Mirrors [DeviceFilter.Entry] but serves as the "UI model".
     */
    data class UiDevice(val address: String, val name: String?, val rssi: Short)

    // ---- Helpers ----

    /** Simulates the pipeline: raw BT events → DeviceFilter → StateFlow<List<UiDevice>> */
    private class FakeDiscoveryPipeline {
        val filter = DeviceFilter()
        private val _uiDevices = MutableStateFlow<List<UiDevice>>(emptyList())
        val uiDevices = _uiDevices

        /** Simulate a device being discovered (like ACTION_FOUND broadcast). */
        fun onDeviceFound(address: String, name: String?, rssi: Short) {
            filter.addOrUpdate(address, name, rssi)
            // Push filtered list to UI state (mirrors ViewModel.postValue)
            _uiDevices.value = filter.getAll().map { UiDevice(it.address, it.name, it.rssi) }
        }
    }

    // ---- Stream → Filter → UI tests ----

    @Test
    fun `single device discovery populates UI list with one entry`() = runTest {
        val pipeline = FakeDiscoveryPipeline()
        pipeline.onDeviceFound("AA:BB:CC:DD:EE:01", "Speaker", -50)

        val ui = pipeline.uiDevices.value
        assertEquals(1, ui.size)
        assertEquals("Speaker", ui[0].name)
        assertEquals("AA:BB:CC:DD:EE:01", ui[0].address)
    }

    @Test
    fun `multiple unique devices populate UI list correctly`() = runTest {
        val pipeline = FakeDiscoveryPipeline()
        pipeline.onDeviceFound("AA:BB:CC:DD:EE:01", "Speaker", -50)
        pipeline.onDeviceFound("AA:BB:CC:DD:EE:02", "Headset", -60)
        pipeline.onDeviceFound("AA:BB:CC:DD:EE:03", "Keyboard", -45)

        val ui = pipeline.uiDevices.value
        assertEquals(3, ui.size)
        assertEquals(listOf("Speaker", "Headset", "Keyboard"), ui.map { it.name })
    }

    @Test
    fun `duplicate device from stream does not create second UI entry`() = runTest {
        val pipeline = FakeDiscoveryPipeline()
        pipeline.onDeviceFound("AA:BB:CC:DD:EE:01", "Speaker", -50)
        pipeline.onDeviceFound("AA:BB:CC:DD:EE:01", "Speaker", -50)
        pipeline.onDeviceFound("AA:BB:CC:DD:EE:01", "Speaker", -50)

        assertEquals("UI should have exactly 1 device after 3 identical discoveries", 1, pipeline.uiDevices.value.size)
    }

    @Test
    fun `RSSI fluctuations in stream update UI value without adding entries`() = runTest {
        val pipeline = FakeDiscoveryPipeline()
        pipeline.onDeviceFound("AA:BB:CC:DD:EE:01", "Speaker", -80)
        pipeline.onDeviceFound("AA:BB:CC:DD:EE:01", "Speaker", -50)
        pipeline.onDeviceFound("AA:BB:CC:DD:EE:01", "Speaker", -35)

        val ui = pipeline.uiDevices.value
        assertEquals(1, ui.size)
        assertEquals((-35).toShort(), ui[0].rssi)
    }

    @Test
    fun `interleaved discovery of multiple devices with RSSI updates`() = runTest {
        val pipeline = FakeDiscoveryPipeline()
        // Device A appears
        pipeline.onDeviceFound("AA:BB:CC:DD:EE:01", "Device A", -50)
        // Device B appears
        pipeline.onDeviceFound("AA:BB:CC:DD:EE:02", "Device B", -60)
        // Device A RSSI update
        pipeline.onDeviceFound("AA:BB:CC:DD:EE:01", "Device A", -30)
        // Device C appears
        pipeline.onDeviceFound("AA:BB:CC:DD:EE:03", "Device C", -70)
        // Device B RSSI update
        pipeline.onDeviceFound("AA:BB:CC:DD:EE:02", "Device B", -40)

        val ui = pipeline.uiDevices.value
        assertEquals("Should have exactly 3 unique devices", 3, ui.size)
        assertEquals((-30).toShort(), ui.first { it.address == "AA:BB:CC:DD:EE:01" }.rssi)
        assertEquals((-40).toShort(), ui.first { it.address == "AA:BB:CC:DD:EE:02" }.rssi)
        assertEquals((-70).toShort(), ui.first { it.address == "AA:BB:CC:DD:EE:03" }.rssi)
    }

    @Test
    fun `StateFlow collectors receive deduplicated updates`() = runTest {
        val pipeline = FakeDiscoveryPipeline()
        val collected = mutableListOf<List<UiDevice>>()

        val job = launch(UnconfinedTestDispatcher(testScheduler)) {
            pipeline.uiDevices.toList(collected)
        }

        pipeline.onDeviceFound("AA:BB:CC:DD:EE:01", "Speaker", -50)
        pipeline.onDeviceFound("AA:BB:CC:DD:EE:01", "Speaker", -30)
        pipeline.onDeviceFound("AA:BB:CC:DD:EE:02", "Headset", -60)

        job.cancel()

        // Every emitted list must have unique addresses
        for (emission in collected) {
            val addresses = emission.map { it.address }
            assertEquals("No duplicate addresses in any emission", addresses.distinct(), addresses)
        }

        // Final emission should have 2 devices
        val last = collected.last()
        assertEquals(2, last.size)
    }

    @Test
    fun `rapid burst of 50 discoveries with duplicates produces correct count`() = runTest {
        val pipeline = FakeDiscoveryPipeline()
        // 10 unique devices, each discovered 5 times with varying RSSI
        val addresses = (1..10).map { String.format("AA:BB:CC:DD:EE:%02X", it) }
        repeat(5) { round ->
            for (addr in addresses) {
                pipeline.onDeviceFound(addr, "Dev-$addr", (-30 - round * 10).toShort())
            }
        }

        val ui = pipeline.uiDevices.value
        assertEquals("50 events from 10 devices should yield exactly 10 UI entries", 10, ui.size)
    }

    @Test
    fun `device name resolved on later discovery updates UI`() = runTest {
        val pipeline = FakeDiscoveryPipeline()
        // First discovery: name unknown
        pipeline.onDeviceFound("AA:BB:CC:DD:EE:01", null, -60)
        assertEquals(null, pipeline.uiDevices.value[0].name)

        // Second discovery: name resolved
        pipeline.onDeviceFound("AA:BB:CC:DD:EE:01", "JBL Flip 6", -55)
        val ui = pipeline.uiDevices.value
        assertEquals(1, ui.size)
        assertEquals("JBL Flip 6", ui[0].name)
    }

    // ---- Source-level integration: verify BtDeviceAdapter handles updates ----

    companion object {
        private val PROJECT_ROOT = findProjectRoot()
        private val SOURCE_DIR = File(PROJECT_ROOT, "app/src/main/kotlin/com/sunfeld/smsgateway")

        private fun findProjectRoot(): File {
            var dir: File? = File(System.getProperty("user.dir") ?: ".")
            while (dir != null) {
                if (File(dir, "settings.gradle.kts").exists()) return dir
                dir = dir.parentFile
            }
            return File(System.getProperty("user.dir") ?: ".")
        }
    }

    @Test
    fun `BtDeviceAdapter updateDevices method exists and takes List parameter`() {
        val content = File(SOURCE_DIR, "BtDeviceAdapter.kt").readText()
        assertTrue(
            "BtDeviceAdapter should have updateDevices accepting a list",
            content.contains("fun updateDevices(newDevices: List<BluetoothDevice>)")
        )
    }

    @Test
    fun `BtDeviceAdapter clears and replaces on updateDevices preventing stale duplicates`() {
        val content = File(SOURCE_DIR, "BtDeviceAdapter.kt").readText()
        // Verify clear-then-addAll pattern (prevents duplicates from accumulating)
        val updateBody = content.substringAfter("fun updateDevices(")
            .substringBefore("fun updateConnected(")
        assertTrue("updateDevices should clear old list", updateBody.contains("devices.clear()"))
        assertTrue("updateDevices should addAll new devices", updateBody.contains("devices.addAll("))
    }

    @Test
    fun `BluetoothDiscoveryManager uses DeviceFilter for deduplication`() {
        val content = File(SOURCE_DIR, "BluetoothDiscoveryManager.kt").readText()
        assertTrue(
            "BluetoothDiscoveryManager should use DeviceFilter",
            content.contains("filter.addOrUpdate(")
        )
    }

    @Test
    fun `BluetoothHidViewModel collects from discoveryManager devices flow`() {
        val content = File(SOURCE_DIR, "BluetoothHidViewModel.kt").readText()
        assertTrue(
            "ViewModel should collect discovered devices",
            content.contains("discoveryManager.devices.collect")
        )
    }

    @Test
    fun `BluetoothHidViewModel posts discovered devices to LiveData for UI observation`() {
        val content = File(SOURCE_DIR, "BluetoothHidViewModel.kt").readText()
        assertTrue(
            "ViewModel should post devices to LiveData",
            content.contains("_discoveredDevices.postValue(")
        )
    }
}
