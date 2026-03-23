package com.sunfeld.smsgateway

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 * Tests for [BluetoothDiscoveryManager] covering:
 * - Source-level structure verification (StateFlow API, BroadcastReceiver, DeviceFilter usage)
 * - Functional tests of the internal DeviceFilter pipeline and addDevice logic
 * - Integration verification with BluetoothPermissionManager and BluetoothHidViewModel
 */
class BluetoothDiscoveryManagerTest {

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

    private lateinit var sourceContent: String

    @Before
    fun setUp() {
        sourceContent = File(SOURCE_DIR, "BluetoothDiscoveryManager.kt").readText()
    }

    // ---- Source file existence ----

    @Test
    fun `BluetoothDiscoveryManager source file exists`() {
        assertTrue(File(SOURCE_DIR, "BluetoothDiscoveryManager.kt").exists())
    }

    // ---- StateFlow API ----

    @Test
    fun `exposes devices as StateFlow of List BluetoothDevice`() {
        assertTrue(
            "Should expose devices as StateFlow<List<BluetoothDevice>>",
            sourceContent.contains("val devices: StateFlow<List<BluetoothDevice>>")
        )
    }

    @Test
    fun `exposes isDiscovering as StateFlow of Boolean`() {
        assertTrue(
            "Should expose isDiscovering as StateFlow<Boolean>",
            sourceContent.contains("val isDiscovering: StateFlow<Boolean>")
        )
    }

    @Test
    fun `devices backed by MutableStateFlow`() {
        assertTrue(
            "Internal _devices should be MutableStateFlow",
            sourceContent.contains("MutableStateFlow<List<BluetoothDevice>>(emptyList())")
        )
    }

    @Test
    fun `isDiscovering backed by MutableStateFlow with false initial value`() {
        assertTrue(
            "Internal _isDiscovering should start as false",
            sourceContent.contains("MutableStateFlow(false)")
        )
    }

    @Test
    fun `devices exposed via asStateFlow for immutability`() {
        assertTrue(
            "devices should use asStateFlow() to prevent external mutation",
            sourceContent.contains("_devices.asStateFlow()")
        )
    }

    @Test
    fun `isDiscovering exposed via asStateFlow for immutability`() {
        assertTrue(
            "isDiscovering should use asStateFlow() to prevent external mutation",
            sourceContent.contains("_isDiscovering.asStateFlow()")
        )
    }

    // ---- BroadcastReceiver ----

    @Test
    fun `registers BroadcastReceiver for ACTION_FOUND`() {
        assertTrue(
            "Should listen for BluetoothDevice.ACTION_FOUND",
            sourceContent.contains("BluetoothDevice.ACTION_FOUND")
        )
    }

    @Test
    fun `registers BroadcastReceiver for ACTION_DISCOVERY_FINISHED`() {
        assertTrue(
            "Should listen for BluetoothAdapter.ACTION_DISCOVERY_FINISHED",
            sourceContent.contains("BluetoothAdapter.ACTION_DISCOVERY_FINISHED")
        )
    }

    @Test
    fun `receiver implements onReceive`() {
        assertTrue(
            "BroadcastReceiver should implement onReceive",
            sourceContent.contains("override fun onReceive(context: Context, intent: Intent)")
        )
    }

    @Test
    fun `extracts EXTRA_DEVICE from intent`() {
        assertTrue(
            "Should extract BluetoothDevice from intent",
            sourceContent.contains("BluetoothDevice.EXTRA_DEVICE")
        )
    }

    @Test
    fun `extracts EXTRA_RSSI from intent`() {
        assertTrue(
            "Should extract RSSI from intent",
            sourceContent.contains("BluetoothDevice.EXTRA_RSSI")
        )
    }

    // ---- Auto-restart discovery ----

    @Test
    fun `auto-restarts discovery when ACTION_DISCOVERY_FINISHED and isDiscovering is true`() {
        // Verify the auto-restart pattern: check isDiscovering then startDiscovery
        assertTrue(
            "Should check _isDiscovering.value before restarting",
            sourceContent.contains("_isDiscovering.value")
        )
        assertTrue(
            "Should call adapter?.startDiscovery() to restart",
            sourceContent.contains("adapter?.startDiscovery()")
        )
    }

    // ---- DeviceFilter usage ----

    @Test
    fun `uses DeviceFilter for deduplication`() {
        assertTrue(
            "Should declare a DeviceFilter instance",
            sourceContent.contains("val filter = DeviceFilter()")
        )
    }

    @Test
    fun `calls filter addOrUpdate when device found`() {
        assertTrue(
            "Should pass device data to filter.addOrUpdate",
            sourceContent.contains("filter.addOrUpdate(device.address, name, rssi)")
        )
    }

    @Test
    fun `maps filtered entries back to BluetoothDevice objects`() {
        assertTrue(
            "Should map filter results to actual BluetoothDevice instances",
            sourceContent.contains("filter.getAll().mapNotNull")
        )
    }

    // ---- Start and stop discovery ----

    @Test
    fun `startDiscovery sets isDiscovering to true`() {
        assertTrue(
            "startDiscovery should set _isDiscovering.value = true",
            sourceContent.contains("_isDiscovering.value = true")
        )
    }

    @Test
    fun `startDiscovery clears previous state`() {
        assertTrue("Should clear the filter on start", sourceContent.contains("filter.clear()"))
        assertTrue("Should clear devicesByAddress on start", sourceContent.contains("devicesByAddress.clear()"))
        assertTrue("Should reset devices flow on start", sourceContent.contains("_devices.value = emptyList()"))
    }

    @Test
    fun `startDiscovery cancels existing discovery before starting`() {
        // Extract the startDiscovery method body and verify cancel comes before start within it
        val methodBody = sourceContent.substringAfter("fun startDiscovery(context: Context)")
            .substringBefore("fun stopDiscovery(")
        val cancelIdx = methodBody.indexOf("adapter?.cancelDiscovery()")
        val startIdx = methodBody.indexOf("adapter?.startDiscovery()")
        assertTrue("cancelDiscovery should be called in startDiscovery", cancelIdx >= 0)
        assertTrue("startDiscovery should be called in startDiscovery", startIdx >= 0)
        assertTrue("cancelDiscovery should come before startDiscovery", cancelIdx < startIdx)
    }

    @Test
    fun `stopDiscovery sets isDiscovering to false`() {
        assertTrue(
            "stopDiscovery should set _isDiscovering.value = false",
            sourceContent.contains("_isDiscovering.value = false")
        )
    }

    @Test
    fun `stopDiscovery unregisters receiver`() {
        assertTrue(
            "stopDiscovery should unregister the BroadcastReceiver",
            sourceContent.contains("context.unregisterReceiver(receiver)")
        )
    }

    @Test
    fun `stopDiscovery handles already-unregistered receiver`() {
        assertTrue(
            "Should catch IllegalArgumentException when receiver already unregistered",
            sourceContent.contains("catch (_: IllegalArgumentException)")
        )
    }

    // ---- Permission check delegation ----

    @Test
    fun `delegates permission check to BluetoothPermissionManager`() {
        assertTrue(
            "Should use BluetoothPermissionManager.hasScanPermissions",
            sourceContent.contains("BluetoothPermissionManager.hasScanPermissions")
        )
    }

    @Test
    fun `does not duplicate permission check logic`() {
        assertFalse(
            "Should not directly reference BLUETOOTH_SCAN permission",
            sourceContent.contains("Manifest.permission.BLUETOOTH_SCAN")
        )
    }

    // ---- Android API compatibility ----

    @Test
    fun `handles Tiramisu parcelable API for API 33+`() {
        assertTrue(
            "Should use getParcelableExtra with class parameter for API 33+",
            sourceContent.contains("Build.VERSION_CODES.TIRAMISU")
        )
    }

    @Test
    fun `handles deprecated getParcelableExtra for older APIs`() {
        assertTrue(
            "Should suppress deprecation for older API path",
            sourceContent.contains("@Suppress(\"DEPRECATION\")")
        )
    }

    // ---- Functional test: DeviceFilter pipeline (same pipeline as BluetoothDiscoveryManager.addDevice) ----

    @Test
    fun `filter pipeline - new device returns true and appears in getAll`() {
        val filter = DeviceFilter()
        val isNew = filter.addOrUpdate("AA:BB:CC:DD:EE:01", "Speaker", -50)
        assertTrue("First add should return true (new device)", isNew)
        assertEquals(1, filter.getAll().size)
        assertEquals("AA:BB:CC:DD:EE:01", filter.getAll()[0].address)
    }

    @Test
    fun `filter pipeline - duplicate device returns false and does not add entry`() {
        val filter = DeviceFilter()
        filter.addOrUpdate("AA:BB:CC:DD:EE:01", "Speaker", -50)
        val isNew = filter.addOrUpdate("AA:BB:CC:DD:EE:01", "Speaker", -40)
        assertFalse("Second add of same address should return false", isNew)
        assertEquals(1, filter.getAll().size)
    }

    @Test
    fun `filter pipeline - RSSI update reflected in entry`() {
        val filter = DeviceFilter()
        filter.addOrUpdate("AA:BB:CC:DD:EE:01", "Speaker", -80)
        filter.addOrUpdate("AA:BB:CC:DD:EE:01", "Speaker", -30)
        assertEquals((-30).toShort(), filter.getAll()[0].rssi)
    }

    @Test
    fun `filter pipeline - name resolved on later discovery`() {
        val filter = DeviceFilter()
        filter.addOrUpdate("AA:BB:CC:DD:EE:01", null, -60)
        assertEquals(null, filter.getAll()[0].name)
        filter.addOrUpdate("AA:BB:CC:DD:EE:01", "JBL Flip 6", -55)
        assertEquals("JBL Flip 6", filter.getAll()[0].name)
    }

    @Test
    fun `filter pipeline - clear resets state for fresh discovery session`() {
        val filter = DeviceFilter()
        filter.addOrUpdate("AA:BB:CC:DD:EE:01", "Speaker", -50)
        filter.addOrUpdate("AA:BB:CC:DD:EE:02", "Headset", -60)
        assertEquals(2, filter.size)

        filter.clear()
        assertEquals(0, filter.size)
        assertEquals(emptyList<DeviceFilter.Entry>(), filter.getAll())
    }

    @Test
    fun `filter pipeline - multiple devices maintain discovery order`() {
        val filter = DeviceFilter()
        filter.addOrUpdate("AA:BB:CC:DD:EE:03", "Third", -30)
        filter.addOrUpdate("AA:BB:CC:DD:EE:01", "First", -50)
        filter.addOrUpdate("AA:BB:CC:DD:EE:02", "Second", -40)

        val addresses = filter.getAll().map { it.address }
        assertEquals(
            listOf("AA:BB:CC:DD:EE:03", "AA:BB:CC:DD:EE:01", "AA:BB:CC:DD:EE:02"),
            addresses
        )
    }

    @Test
    fun `filter pipeline - simulates full addDevice flow with new-only emission`() {
        // Mirrors BluetoothDiscoveryManager.addDevice: only emit when filter reports isNew=true
        val filter = DeviceFilter()
        val emissions = mutableListOf<List<String>>()

        fun simulateAddDevice(address: String, name: String?, rssi: Short) {
            val isNew = filter.addOrUpdate(address, name, rssi)
            if (isNew) {
                emissions.add(filter.getAll().map { it.address })
            }
        }

        simulateAddDevice("AA:BB:CC:DD:EE:01", "A", -50)  // new → emit
        simulateAddDevice("AA:BB:CC:DD:EE:02", "B", -60)  // new → emit
        simulateAddDevice("AA:BB:CC:DD:EE:01", "A", -30)  // update → no emit
        simulateAddDevice("AA:BB:CC:DD:EE:03", "C", -70)  // new → emit

        assertEquals("Should have 3 emissions (one per new device)", 3, emissions.size)
        assertEquals(listOf("AA:BB:CC:DD:EE:01"), emissions[0])
        assertEquals(listOf("AA:BB:CC:DD:EE:01", "AA:BB:CC:DD:EE:02"), emissions[1])
        assertEquals(listOf("AA:BB:CC:DD:EE:01", "AA:BB:CC:DD:EE:02", "AA:BB:CC:DD:EE:03"), emissions[2])
    }
}
