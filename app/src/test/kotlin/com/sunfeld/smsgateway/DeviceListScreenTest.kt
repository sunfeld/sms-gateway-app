package com.sunfeld.smsgateway

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Tests for [DeviceListScreen] business logic: MAC address deduplication,
 * selection toggling, and UI state transitions driven by StateFlow inputs.
 *
 * Since Compose UI test dependencies are not available, these tests exercise
 * the logic paths and verify source-level correctness of the composable.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DeviceListScreenTest {

    // ---- Deduplication logic (mirrors distinctBy { it.address }) ----

    data class SimpleDevice(val address: String, val name: String?)

    private fun deduplicateByAddress(devices: List<SimpleDevice>): List<SimpleDevice> =
        devices.distinctBy { it.address }

    @Test
    fun `distinctBy address removes duplicate MACs keeping first occurrence`() {
        val devices = listOf(
            SimpleDevice("AA:BB:CC:DD:EE:01", "First"),
            SimpleDevice("AA:BB:CC:DD:EE:02", "Second"),
            SimpleDevice("AA:BB:CC:DD:EE:01", "First-dup")
        )
        val unique = deduplicateByAddress(devices)
        assertEquals(2, unique.size)
        assertEquals("First", unique[0].name)
        assertEquals("Second", unique[1].name)
    }

    @Test
    fun `all unique addresses produces same list`() {
        val devices = (1..5).map { SimpleDevice("AA:BB:CC:DD:EE:%02X".format(it), "Dev$it") }
        val unique = deduplicateByAddress(devices)
        assertEquals(5, unique.size)
    }

    @Test
    fun `empty list produces empty result`() {
        assertEquals(0, deduplicateByAddress(emptyList()).size)
    }

    @Test
    fun `single device produces single result`() {
        val devices = listOf(SimpleDevice("AA:BB:CC:DD:EE:01", "Solo"))
        assertEquals(1, deduplicateByAddress(devices).size)
    }

    @Test
    fun `ten duplicates of same MAC produce single entry`() {
        val devices = (1..10).map { SimpleDevice("AA:BB:CC:DD:EE:01", "Dev-$it") }
        val unique = deduplicateByAddress(devices)
        assertEquals(1, unique.size)
        assertEquals("Dev-1", unique[0].name)
    }

    // ---- Selection toggle logic (mirrors onToggleSelection in DeviceListScreen) ----

    private fun toggleSelection(current: Set<String>, address: String, selected: Boolean): Set<String> =
        if (selected) current + address else current - address

    @Test
    fun `selecting a device adds its address to the set`() {
        val result = toggleSelection(emptySet(), "AA:BB:CC:DD:EE:01", true)
        assertEquals(setOf("AA:BB:CC:DD:EE:01"), result)
    }

    @Test
    fun `deselecting a device removes its address from the set`() {
        val initial = setOf("AA:BB:CC:DD:EE:01", "AA:BB:CC:DD:EE:02")
        val result = toggleSelection(initial, "AA:BB:CC:DD:EE:01", false)
        assertEquals(setOf("AA:BB:CC:DD:EE:02"), result)
    }

    @Test
    fun `selecting already-selected device is idempotent`() {
        val initial = setOf("AA:BB:CC:DD:EE:01")
        val result = toggleSelection(initial, "AA:BB:CC:DD:EE:01", true)
        assertEquals(setOf("AA:BB:CC:DD:EE:01"), result)
    }

    @Test
    fun `deselecting non-selected device is idempotent`() {
        val initial = setOf("AA:BB:CC:DD:EE:02")
        val result = toggleSelection(initial, "AA:BB:CC:DD:EE:01", false)
        assertEquals(setOf("AA:BB:CC:DD:EE:02"), result)
    }

    @Test
    fun `multiple selections accumulate`() {
        var selected = emptySet<String>()
        selected = toggleSelection(selected, "AA:BB:CC:DD:EE:01", true)
        selected = toggleSelection(selected, "AA:BB:CC:DD:EE:02", true)
        selected = toggleSelection(selected, "AA:BB:CC:DD:EE:03", true)
        assertEquals(3, selected.size)
    }

    @Test
    fun `select all then deselect all yields empty set`() {
        val addresses = (1..5).map { "AA:BB:CC:DD:EE:%02X".format(it) }
        var selected = emptySet<String>()
        addresses.forEach { selected = toggleSelection(selected, it, true) }
        assertEquals(5, selected.size)
        addresses.forEach { selected = toggleSelection(selected, it, false) }
        assertTrue(selected.isEmpty())
    }

    // ---- UI state transitions ----

    enum class ScreenState { SCANNING_INDICATOR, EMPTY_STATE, DEVICE_LIST }

    private fun resolveScreenState(isScanning: Boolean, deviceCount: Int): ScreenState = when {
        isScanning && deviceCount == 0 -> ScreenState.SCANNING_INDICATOR
        !isScanning && deviceCount == 0 -> ScreenState.EMPTY_STATE
        else -> ScreenState.DEVICE_LIST
    }

    @Test
    fun `scanning with no devices shows ScanningIndicator`() {
        assertEquals(ScreenState.SCANNING_INDICATOR, resolveScreenState(true, 0))
    }

    @Test
    fun `not scanning with no devices shows EmptyState`() {
        assertEquals(ScreenState.EMPTY_STATE, resolveScreenState(false, 0))
    }

    @Test
    fun `scanning with devices shows DeviceList`() {
        assertEquals(ScreenState.DEVICE_LIST, resolveScreenState(true, 3))
    }

    @Test
    fun `not scanning with devices shows DeviceList`() {
        assertEquals(ScreenState.DEVICE_LIST, resolveScreenState(false, 5))
    }

    @Test
    fun `single device is enough to show DeviceList`() {
        assertEquals(ScreenState.DEVICE_LIST, resolveScreenState(false, 1))
        assertEquals(ScreenState.DEVICE_LIST, resolveScreenState(true, 1))
    }

    // ---- Flow-based state management ----

    @Test
    fun `StateFlow selection updates propagate correctly`() = runTest {
        val selectedTargetsFlow = MutableStateFlow<Set<String>>(emptySet())

        // Simulate user selecting a device
        selectedTargetsFlow.value = selectedTargetsFlow.value + "AA:BB:CC:DD:EE:01"
        assertEquals(setOf("AA:BB:CC:DD:EE:01"), selectedTargetsFlow.value)

        // Simulate selecting another
        selectedTargetsFlow.value = selectedTargetsFlow.value + "AA:BB:CC:DD:EE:02"
        assertEquals(setOf("AA:BB:CC:DD:EE:01", "AA:BB:CC:DD:EE:02"), selectedTargetsFlow.value)

        // Simulate deselecting first
        selectedTargetsFlow.value = selectedTargetsFlow.value - "AA:BB:CC:DD:EE:01"
        assertEquals(setOf("AA:BB:CC:DD:EE:02"), selectedTargetsFlow.value)
    }

    @Test
    fun `isScanningFlow transitions drive correct state`() = runTest {
        val isScanningFlow = MutableStateFlow(false)
        val devicesFlow = MutableStateFlow<List<SimpleDevice>>(emptyList())

        // Initial: not scanning, no devices → EmptyState
        assertEquals(ScreenState.EMPTY_STATE, resolveScreenState(isScanningFlow.value, devicesFlow.value.size))

        // Start scanning
        isScanningFlow.value = true
        assertEquals(ScreenState.SCANNING_INDICATOR, resolveScreenState(isScanningFlow.value, devicesFlow.value.size))

        // Device found during scan
        devicesFlow.value = listOf(SimpleDevice("AA:BB:CC:DD:EE:01", "Speaker"))
        assertEquals(ScreenState.DEVICE_LIST, resolveScreenState(isScanningFlow.value, devicesFlow.value.size))

        // Scanning stops, device remains
        isScanningFlow.value = false
        assertEquals(ScreenState.DEVICE_LIST, resolveScreenState(isScanningFlow.value, devicesFlow.value.size))
    }

    @Test
    fun `device list with duplicates deduplicates after flow emission`() = runTest {
        val devicesFlow = MutableStateFlow<List<SimpleDevice>>(emptyList())

        // Emit list with duplicate MACs
        devicesFlow.value = listOf(
            SimpleDevice("AA:BB:CC:DD:EE:01", "Dev-A"),
            SimpleDevice("AA:BB:CC:DD:EE:02", "Dev-B"),
            SimpleDevice("AA:BB:CC:DD:EE:01", "Dev-A-dup"),
            SimpleDevice("AA:BB:CC:DD:EE:03", "Dev-C"),
            SimpleDevice("AA:BB:CC:DD:EE:02", "Dev-B-dup")
        )

        val unique = deduplicateByAddress(devicesFlow.value)
        assertEquals(3, unique.size)
        assertEquals(listOf("AA:BB:CC:DD:EE:01", "AA:BB:CC:DD:EE:02", "AA:BB:CC:DD:EE:03"), unique.map { it.address })
    }

    @Test
    fun `selection state includes only addresses present in device list`() = runTest {
        val devices = listOf(
            SimpleDevice("AA:BB:CC:DD:EE:01", "Dev-A"),
            SimpleDevice("AA:BB:CC:DD:EE:02", "Dev-B")
        )
        val selectedTargets = setOf("AA:BB:CC:DD:EE:01", "AA:BB:CC:DD:EE:02")

        // Check each device's isSelected status
        val uniqueDevices = deduplicateByAddress(devices)
        for (device in uniqueDevices) {
            assertTrue(
                "Device ${device.address} should be selected",
                selectedTargets.contains(device.address)
            )
        }
    }

    // ---- Source-level integration: verify DeviceListScreen composable structure ----

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
    fun `DeviceListScreen accepts StateFlow parameters for devices, scanning, and selection`() {
        val content = File(SOURCE_DIR, "DeviceListScreen.kt").readText()
        assertTrue(
            "Should accept devicesFlow parameter",
            content.contains("devicesFlow: StateFlow<List<BluetoothDevice>>")
        )
        assertTrue(
            "Should accept isScanningFlow parameter",
            content.contains("isScanningFlow: StateFlow<Boolean>")
        )
        assertTrue(
            "Should accept selectedTargetsFlow parameter",
            content.contains("selectedTargetsFlow: StateFlow<Set<String>>")
        )
    }

    @Test
    fun `DeviceListScreen uses distinctBy address for MAC deduplication`() {
        val content = File(SOURCE_DIR, "DeviceListScreen.kt").readText()
        assertTrue(
            "Should use distinctBy for MAC deduplication",
            content.contains("distinctBy { it.address }")
        )
    }

    @Test
    fun `DeviceListScreen uses collectAsStateWithLifecycle for lifecycle-aware collection`() {
        val content = File(SOURCE_DIR, "DeviceListScreen.kt").readText()
        assertTrue(
            "Should use collectAsStateWithLifecycle for devicesFlow",
            content.contains("devicesFlow.collectAsStateWithLifecycle()")
        )
        assertTrue(
            "Should use collectAsStateWithLifecycle for isScanningFlow",
            content.contains("isScanningFlow.collectAsStateWithLifecycle()")
        )
        assertTrue(
            "Should use collectAsStateWithLifecycle for selectedTargetsFlow",
            content.contains("selectedTargetsFlow.collectAsStateWithLifecycle()")
        )
    }

    @Test
    fun `DeviceListScreen uses Column with forEach for NestedScrollView compatibility`() {
        val content = File(SOURCE_DIR, "DeviceListScreen.kt").readText()
        assertTrue("Should use Column forEach (not LazyColumn — crashes with infinite height in NestedScrollView)",
            content.contains("uniqueDevices.forEach"))
    }

    @Test
    fun `DeviceListScreen handles selection via onSelectionChanged callback`() {
        val content = File(SOURCE_DIR, "DeviceListScreen.kt").readText()
        assertTrue(
            "Should have onSelectionChanged callback parameter",
            content.contains("onSelectionChanged: (Set<String>) -> Unit")
        )
        assertTrue(
            "Should call onSelectionChanged with updated set",
            content.contains("onSelectionChanged(updated)")
        )
    }

    @Test
    fun `DeviceCard handles SecurityException for device name`() {
        val content = File(SOURCE_DIR, "DeviceListScreen.kt").readText()
        assertTrue(
            "Should catch SecurityException when reading device.name",
            content.contains("catch (_: SecurityException)")
        )
        assertTrue(
            "Should fall back to 'Unknown device'",
            content.contains("\"Unknown device\"")
        )
    }

    @Test
    fun `DeviceListScreen shows ScanningIndicator when scanning and no devices`() {
        val content = File(SOURCE_DIR, "DeviceListScreen.kt").readText()
        assertTrue(
            "Should check isScanning && empty devices for ScanningIndicator",
            content.contains("if (isScanning && uniqueDevices.isEmpty())")
        )
        assertTrue(
            "Should show ScanningIndicator composable",
            content.contains("ScanningIndicator()")
        )
    }

    @Test
    fun `DeviceListScreen shows EmptyStateNoDevices when not scanning and no devices`() {
        val content = File(SOURCE_DIR, "DeviceListScreen.kt").readText()
        assertTrue(
            "Should check !isScanning && empty devices for EmptyState",
            content.contains("!isScanning && uniqueDevices.isEmpty()")
        )
        assertTrue(
            "Should show EmptyStateNoDevices composable",
            content.contains("EmptyStateNoDevices()")
        )
    }

    @Test
    fun `BluetoothHidActivity passes ViewModel flows to DeviceListScreen`() {
        val content = File(SOURCE_DIR, "BluetoothHidActivity.kt").readText()
        assertTrue(
            "Should pass discoveredDevicesFlow",
            content.contains("devicesFlow = viewModel.discoveredDevicesFlow")
        )
        assertTrue(
            "Should pass isScanningFlow",
            content.contains("isScanningFlow = viewModel.isScanningFlow")
        )
        assertTrue(
            "Should pass selectedTargetsFlow",
            content.contains("selectedTargetsFlow = viewModel.selectedTargetsFlow")
        )
    }
}
