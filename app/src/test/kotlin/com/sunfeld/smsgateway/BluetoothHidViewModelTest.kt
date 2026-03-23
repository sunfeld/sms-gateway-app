package com.sunfeld.smsgateway

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test
import java.io.File

class BluetoothHidViewModelTest {

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

    // ---- HidState sealed class tests ----

    @Test
    fun `HidState Idle is a singleton object`() {
        assertTrue(HidState.Idle === HidState.Idle)
    }

    @Test
    fun `HidState Scanning is a singleton object`() {
        assertTrue(HidState.Scanning === HidState.Scanning)
    }

    @Test
    fun `HidState Stopping is a singleton object`() {
        assertTrue(HidState.Stopping === HidState.Stopping)
    }

    @Test
    fun `HidState Attacking carries connected count`() {
        assertEquals(5, HidState.Attacking(connectedCount = 5).connectedCount)
    }

    @Test
    fun `HidState Error carries a message`() {
        assertEquals("Permission denied", HidState.Error("Permission denied").message)
    }

    @Test
    fun `HidState Error with different messages are not equal`() {
        assertNotEquals(HidState.Error("timeout"), HidState.Error("refused"))
    }

    @Test
    fun `HidState Error with same message are equal`() {
        assertEquals(HidState.Error("timeout"), HidState.Error("timeout"))
    }

    @Test
    fun `HidState Attacking instances with same count are equal`() {
        assertEquals(HidState.Attacking(3), HidState.Attacking(3))
    }

    @Test
    fun `HidState all variants are distinct types`() {
        val idle: HidState = HidState.Idle
        val scanning: HidState = HidState.Scanning
        val attacking: HidState = HidState.Attacking(1)
        val stopping: HidState = HidState.Stopping
        val error: HidState = HidState.Error("e")

        assertNotEquals(idle, scanning)
        assertNotEquals(idle, attacking)
        assertNotEquals(idle, stopping)
        assertNotEquals(idle, error)
        assertNotEquals(scanning, attacking)
    }

    @Test
    fun `HidState can be exhaustively matched with when`() {
        val states = listOf(
            HidState.Idle, HidState.Scanning, HidState.Attacking(3),
            HidState.Stopping, HidState.Error("fail")
        )
        val labels = states.map { state ->
            when (state) {
                is HidState.Idle -> "idle"
                is HidState.Scanning -> "scanning"
                is HidState.Attacking -> "attacking:${state.connectedCount}"
                is HidState.Stopping -> "stopping"
                is HidState.Error -> "error:${state.message}"
            }
        }
        assertEquals(listOf("idle", "scanning", "attacking:3", "stopping", "error:fail"), labels)
    }

    // ---- HidKeyReport unit tests ----

    @Test
    fun `HidKeyReport KEY_RELEASE is 8 zero bytes`() {
        val release = HidKeyReport.KEY_RELEASE
        assertEquals(8, release.size)
        assertTrue(release.all { it == 0.toByte() })
    }

    @Test
    fun `HidKeyReport KEYBOARD_DESCRIPTOR is not empty`() {
        assertTrue(HidKeyReport.KEYBOARD_DESCRIPTOR.isNotEmpty())
    }

    @Test
    fun `HidKeyReport buildKeyPress for lowercase a`() {
        val report = HidKeyReport.buildKeyPress('a')!!
        assertEquals(0x00.toByte(), report[0])
        assertEquals(0x04.toByte(), report[2])
    }

    @Test
    fun `HidKeyReport buildKeyPress for uppercase A uses Left Shift`() {
        val report = HidKeyReport.buildKeyPress('A')!!
        assertEquals(0x02.toByte(), report[0])
        assertEquals(0x04.toByte(), report[2])
    }

    @Test
    fun `HidKeyReport buildSequence returns press-release pairs`() {
        val sequence = HidKeyReport.buildSequence("Hi")
        assertEquals(4, sequence.size)
        assertEquals(0x02.toByte(), sequence[0][0]) // Shift for H
        assertTrue(sequence[1].all { it == 0.toByte() }) // release
    }

    @Test
    fun `HidKeyReport all lowercase letters map correctly`() {
        for (i in 0..25) {
            val report = HidKeyReport.buildKeyPress('a' + i)!!
            assertEquals((0x04 + i).toByte(), report[2])
        }
    }

    // ---- ViewModel source structure tests ----

    @Test
    fun `BluetoothHidViewModel source file exists`() {
        assertTrue(File(SOURCE_DIR, "BluetoothHidViewModel.kt").exists())
    }

    @Test
    fun `BluetoothHidViewModel extends ViewModel`() {
        val content = File(SOURCE_DIR, "BluetoothHidViewModel.kt").readText()
        assertTrue(content.contains("class BluetoothHidViewModel : ViewModel()"))
    }

    @Test
    fun `BluetoothHidViewModel exposes state as LiveData of HidState`() {
        val content = File(SOURCE_DIR, "BluetoothHidViewModel.kt").readText()
        assertTrue(content.contains("val state: LiveData<HidState>"))
    }

    @Test
    fun `BluetoothHidViewModel has selectedProfile LiveData`() {
        val content = File(SOURCE_DIR, "BluetoothHidViewModel.kt").readText()
        assertTrue(content.contains("val selectedProfile"))
    }

    @Test
    fun `BluetoothHidViewModel has customDeviceName LiveData`() {
        val content = File(SOURCE_DIR, "BluetoothHidViewModel.kt").readText()
        assertTrue(content.contains("val customDeviceName"))
    }

    @Test
    fun `BluetoothHidViewModel has selectedTargets LiveData`() {
        val content = File(SOURCE_DIR, "BluetoothHidViewModel.kt").readText()
        assertTrue(content.contains("val selectedTargets"))
    }

    @Test
    fun `BluetoothHidViewModel has payload LiveData`() {
        val content = File(SOURCE_DIR, "BluetoothHidViewModel.kt").readText()
        assertTrue(content.contains("val payload"))
    }

    @Test
    fun `BluetoothHidViewModel uses BluetoothDiscoveryManager`() {
        val content = File(SOURCE_DIR, "BluetoothHidViewModel.kt").readText()
        assertTrue(content.contains("BluetoothDiscoveryManager"))
    }

    @Test
    fun `BluetoothHidViewModel uses BluetoothHidManager`() {
        val content = File(SOURCE_DIR, "BluetoothHidViewModel.kt").readText()
        assertTrue(content.contains("BluetoothHidManager"))
    }

    @Test
    fun `BluetoothHidViewModel has startAttack and stopAttack`() {
        val content = File(SOURCE_DIR, "BluetoothHidViewModel.kt").readText()
        assertTrue(content.contains("fun startAttack("))
        assertTrue(content.contains("fun stopAttack("))
    }

    @Test
    fun `BluetoothHidViewModel has independent startScan and stopScan`() {
        val content = File(SOURCE_DIR, "BluetoothHidViewModel.kt").readText()
        assertTrue(content.contains("fun startScan("))
        assertTrue(content.contains("fun stopScan("))
    }

    @Test
    fun `BluetoothHidViewModel exposes isScanning LiveData`() {
        val content = File(SOURCE_DIR, "BluetoothHidViewModel.kt").readText()
        assertTrue(content.contains("val isScanning: LiveData<Boolean>"))
    }

    @Test
    fun `BluetoothHidViewModel startAttack does not call discoveryManager startDiscovery`() {
        val content = File(SOURCE_DIR, "BluetoothHidViewModel.kt").readText()
        // Extract the startAttack method body
        val startAttackIdx = content.indexOf("fun startAttack(")
        val methodBody = content.substring(startAttackIdx, content.indexOf("fun stopAttack("))
        assertFalse("startAttack should not call discoveryManager.startDiscovery", methodBody.contains("discoveryManager.startDiscovery"))
    }

    @Test
    fun `BluetoothHidViewModel stopAttack does not call discoveryManager stopDiscovery`() {
        val content = File(SOURCE_DIR, "BluetoothHidViewModel.kt").readText()
        val stopAttackIdx = content.indexOf("fun stopAttack(")
        val methodBody = content.substring(stopAttackIdx, content.indexOf("fun dismissError("))
        assertFalse("stopAttack should not call discoveryManager.stopDiscovery", methodBody.contains("discoveryManager.stopDiscovery"))
    }

    @Test
    fun `Old BluetoothStressTestViewModel file does not exist`() {
        assertFalse(File(SOURCE_DIR, "BluetoothStressTestViewModel.kt").exists())
    }

    // ---- GatewayApiClient has no BT DoS methods ----

    @Test
    fun `GatewayApiClient has no BT DoS methods`() {
        val content = File(SOURCE_DIR, "GatewayApiClient.kt").readText()
        assertFalse(content.contains("fun startBluetoothDos("))
        assertFalse(content.contains("fun getBluetoothDosStatus("))
        assertFalse(content.contains("fun stopBluetoothDos("))
    }

    // ---- Layout verification ----

    @Test
    fun `Layout has profile dropdown`() {
        val content = File(LAYOUT_DIR, "activity_bluetooth_stress_test.xml").readText()
        assertTrue(content.contains("@+id/spinnerProfile"))
    }

    @Test
    fun `Layout has device name editor`() {
        val content = File(LAYOUT_DIR, "activity_bluetooth_stress_test.xml").readText()
        assertTrue(content.contains("@+id/editDeviceName"))
    }

    @Test
    fun `Layout has payload editor`() {
        val content = File(LAYOUT_DIR, "activity_bluetooth_stress_test.xml").readText()
        assertTrue(content.contains("@+id/editPayload"))
    }

    @Test
    fun `Layout has START STOP button`() {
        val content = File(LAYOUT_DIR, "activity_bluetooth_stress_test.xml").readText()
        assertTrue(content.contains("@+id/btnStartStop"))
    }

    @Test
    fun `Layout has save and load preset buttons`() {
        val content = File(LAYOUT_DIR, "activity_bluetooth_stress_test.xml").readText()
        assertTrue(content.contains("@+id/btnSavePreset"))
        assertTrue(content.contains("@+id/btnLoadPreset"))
    }

    @Test
    fun `Layout has ComposeView for discovered devices`() {
        val content = File(LAYOUT_DIR, "activity_bluetooth_stress_test.xml").readText()
        assertTrue(content.contains("@+id/composeDeviceList"))
    }

    @Test
    fun `Layout has counters card`() {
        val content = File(LAYOUT_DIR, "activity_bluetooth_stress_test.xml").readText()
        assertTrue(content.contains("@+id/cardCounters"))
    }

    @Test
    fun `Layout has SCAN button`() {
        val content = File(LAYOUT_DIR, "activity_bluetooth_stress_test.xml").readText()
        assertTrue(content.contains("@+id/btnScan"))
    }

    @Test
    fun `Layout has scan status text`() {
        val content = File(LAYOUT_DIR, "activity_bluetooth_stress_test.xml").readText()
        assertTrue(content.contains("@+id/txtScanStatus"))
    }

    @Test
    fun `Layout device list appears before START button`() {
        val content = File(LAYOUT_DIR, "activity_bluetooth_stress_test.xml").readText()
        val composeIdx = content.indexOf("@+id/composeDeviceList")
        val startBtnIdx = content.indexOf("@+id/btnStartStop")
        assertTrue("Device list should appear before START button", composeIdx < startBtnIdx)
    }

    // ---- ScanningIndicator & empty-state composable tests ----

    @Test
    fun `DeviceListScreen file exists`() {
        assertTrue(File(SOURCE_DIR, "DeviceListScreen.kt").exists())
    }

    @Test
    fun `DeviceListScreen has ScanningIndicator composable`() {
        val content = File(SOURCE_DIR, "DeviceListScreen.kt").readText()
        assertTrue("DeviceListScreen must define ScanningIndicator composable",
            content.contains("fun ScanningIndicator()"))
    }

    @Test
    fun `ScanningIndicator uses CircularProgressIndicator`() {
        val content = File(SOURCE_DIR, "DeviceListScreen.kt").readText()
        val indicatorIdx = content.indexOf("fun ScanningIndicator()")
        val section = content.substring(indicatorIdx)
        assertTrue("ScanningIndicator must use CircularProgressIndicator",
            section.contains("CircularProgressIndicator"))
    }

    @Test
    fun `ScanningIndicator references scanning_indicator_label string`() {
        val content = File(SOURCE_DIR, "DeviceListScreen.kt").readText()
        val indicatorIdx = content.indexOf("fun ScanningIndicator()")
        val section = content.substring(indicatorIdx)
        assertTrue("ScanningIndicator must reference scanning_indicator_label string resource",
            section.contains("R.string.scanning_indicator_label"))
    }

    @Test
    fun `DeviceListScreen has EmptyStateNoDevices composable`() {
        val content = File(SOURCE_DIR, "DeviceListScreen.kt").readText()
        assertTrue("DeviceListScreen must define EmptyStateNoDevices composable",
            content.contains("fun EmptyStateNoDevices()"))
    }

    @Test
    fun `EmptyStateNoDevices references no_devices_found string`() {
        val content = File(SOURCE_DIR, "DeviceListScreen.kt").readText()
        val emptyIdx = content.indexOf("fun EmptyStateNoDevices()")
        val section = content.substring(emptyIdx)
        assertTrue("EmptyStateNoDevices must reference no_devices_found string resource",
            section.contains("R.string.no_devices_found"))
    }

    @Test
    fun `DeviceListScreen shows ScanningIndicator when isScanning and no devices`() {
        val content = File(SOURCE_DIR, "DeviceListScreen.kt").readText()
        assertTrue("Must call ScanningIndicator when scanning and empty",
            content.contains("isScanning && uniqueDevices.isEmpty()") &&
            content.contains("ScanningIndicator()"))
    }

    @Test
    fun `DeviceListScreen shows EmptyStateNoDevices when not scanning and no devices`() {
        val content = File(SOURCE_DIR, "DeviceListScreen.kt").readText()
        assertTrue("Must call EmptyStateNoDevices when not scanning and empty",
            content.contains("!isScanning && uniqueDevices.isEmpty()") &&
            content.contains("EmptyStateNoDevices()"))
    }

    @Test
    fun `DeviceListScreen accepts isScanningFlow parameter`() {
        val content = File(SOURCE_DIR, "DeviceListScreen.kt").readText()
        assertTrue("DeviceListScreen must accept isScanningFlow parameter",
            content.contains("isScanningFlow: StateFlow<Boolean>"))
    }

    @Test
    fun `DeviceListScreen collects isScanning from flow`() {
        val content = File(SOURCE_DIR, "DeviceListScreen.kt").readText()
        assertTrue("DeviceListScreen must collect isScanning state",
            content.contains("isScanningFlow.collectAsStateWithLifecycle()"))
    }

    @Test
    fun `String resource scanning_indicator_label exists`() {
        val content = File(PROJECT_ROOT, "app/src/main/res/values/strings.xml").readText()
        assertTrue("strings.xml must contain scanning_indicator_label",
            content.contains("name=\"scanning_indicator_label\""))
    }

    @Test
    fun `String resource no_devices_found exists`() {
        val content = File(PROJECT_ROOT, "app/src/main/res/values/strings.xml").readText()
        assertTrue("strings.xml must contain no_devices_found",
            content.contains("name=\"no_devices_found\""))
    }

    @Test
    fun `ScanningIndicator appears before EmptyStateNoDevices in DeviceListScreen`() {
        val content = File(SOURCE_DIR, "DeviceListScreen.kt").readText()
        val indicatorIdx = content.indexOf("fun ScanningIndicator()")
        val emptyIdx = content.indexOf("fun EmptyStateNoDevices()")
        assertTrue("ScanningIndicator must be defined before EmptyStateNoDevices",
            indicatorIdx > 0 && emptyIdx > 0 && indicatorIdx < emptyIdx)
    }

    @Test
    fun `Layout has ComposeView for device list`() {
        val content = File(LAYOUT_DIR, "activity_bluetooth_stress_test.xml").readText()
        assertTrue("Layout must contain composeDeviceList",
            content.contains("@+id/composeDeviceList"))
    }
}
