package com.sunfeld.smsgateway

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test
import java.io.File

/**
 * Unit tests for the on-device BT HID keyboard impersonation feature:
 * AttackState sealed class, BluetoothStressTestViewModel source structure,
 * HidKeyReport report generation, and layout verification.
 *
 * All Bluetooth functionality runs on-device — no API calls needed.
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

    // ---- AttackState sealed class tests ----

    @Test
    fun `AttackState Idle is a singleton object`() {
        val a = AttackState.Idle
        val b = AttackState.Idle
        assertTrue("Idle should be the same instance", a === b)
    }

    @Test
    fun `AttackState Scanning is a singleton object`() {
        val a = AttackState.Scanning
        val b = AttackState.Scanning
        assertTrue("Scanning should be the same instance", a === b)
    }

    @Test
    fun `AttackState Stopping is a singleton object`() {
        val a = AttackState.Stopping
        val b = AttackState.Stopping
        assertTrue("Stopping should be the same instance", a === b)
    }

    @Test
    fun `AttackState Attacking carries connected count`() {
        val state = AttackState.Attacking(connectedCount = 5)
        assertEquals(5, state.connectedCount)
    }

    @Test
    fun `AttackState Error carries a message`() {
        val state = AttackState.Error("Permission denied")
        assertEquals("Permission denied", state.message)
    }

    @Test
    fun `AttackState Error with different messages are not equal`() {
        val a = AttackState.Error("timeout")
        val b = AttackState.Error("refused")
        assertNotEquals(a, b)
    }

    @Test
    fun `AttackState Error with same message are equal`() {
        val a = AttackState.Error("timeout")
        val b = AttackState.Error("timeout")
        assertEquals(a, b)
    }

    @Test
    fun `AttackState Attacking instances with same count are equal`() {
        val a = AttackState.Attacking(3)
        val b = AttackState.Attacking(3)
        assertEquals(a, b)
    }

    @Test
    fun `AttackState all variants are distinct types`() {
        val idle: AttackState = AttackState.Idle
        val scanning: AttackState = AttackState.Scanning
        val attacking: AttackState = AttackState.Attacking(1)
        val stopping: AttackState = AttackState.Stopping
        val error: AttackState = AttackState.Error("e")

        assertNotEquals(idle, scanning)
        assertNotEquals(idle, attacking)
        assertNotEquals(idle, stopping)
        assertNotEquals(idle, error)
        assertNotEquals(scanning, attacking)
        assertNotEquals(scanning, stopping)
        assertNotEquals(scanning, error)
        assertNotEquals(attacking, stopping)
        assertNotEquals(attacking, error)
        assertNotEquals(stopping, error)
    }

    @Test
    fun `AttackState can be exhaustively matched with when`() {
        val states = listOf(
            AttackState.Idle,
            AttackState.Scanning,
            AttackState.Attacking(3),
            AttackState.Stopping,
            AttackState.Error("fail")
        )

        val labels = states.map { state ->
            when (state) {
                is AttackState.Idle -> "idle"
                is AttackState.Scanning -> "scanning"
                is AttackState.Attacking -> "attacking:${state.connectedCount}"
                is AttackState.Stopping -> "stopping"
                is AttackState.Error -> "error:${state.message}"
            }
        }

        assertEquals(
            listOf("idle", "scanning", "attacking:3", "stopping", "error:fail"),
            labels
        )
    }

    // ---- HidKeyReport unit tests ----

    @Test
    fun `HidKeyReport KEY_RELEASE is 8 zero bytes`() {
        val release = HidKeyReport.KEY_RELEASE
        assertEquals(8, release.size)
        assertTrue("All bytes must be zero", release.all { it == 0.toByte() })
    }

    @Test
    fun `HidKeyReport KEYBOARD_DESCRIPTOR is not empty`() {
        assertTrue(HidKeyReport.KEYBOARD_DESCRIPTOR.isNotEmpty())
    }

    @Test
    fun `HidKeyReport buildKeyPress for lowercase a returns correct report`() {
        val report = HidKeyReport.buildKeyPress('a')!!
        assertEquals(8, report.size)
        assertEquals(0x00.toByte(), report[0]) // no modifier
        assertEquals(0x00.toByte(), report[1]) // reserved
        assertEquals(0x04.toByte(), report[2]) // HID keycode for 'a'
    }

    @Test
    fun `HidKeyReport buildKeyPress for uppercase A uses Left Shift modifier`() {
        val report = HidKeyReport.buildKeyPress('A')!!
        assertEquals(8, report.size)
        assertEquals(0x02.toByte(), report[0]) // Left Shift
        assertEquals(0x00.toByte(), report[1]) // reserved
        assertEquals(0x04.toByte(), report[2]) // HID keycode for 'a'/'A'
    }

    @Test
    fun `HidKeyReport buildKeyPress for space returns 0x2C keycode`() {
        val report = HidKeyReport.buildKeyPress(' ')!!
        assertEquals(0x2C.toByte(), report[2])
        assertEquals(0x00.toByte(), report[0]) // no modifier
    }

    @Test
    fun `HidKeyReport buildKeyPress for newline returns 0x28 keycode`() {
        val report = HidKeyReport.buildKeyPress('\n')!!
        assertEquals(0x28.toByte(), report[2])
    }

    @Test
    fun `HidKeyReport buildKeyPress for digit 1 returns 0x1E keycode`() {
        val report = HidKeyReport.buildKeyPress('1')!!
        assertEquals(0x1E.toByte(), report[2])
        assertEquals(0x00.toByte(), report[0]) // no modifier
    }

    @Test
    fun `HidKeyReport buildKeyPress for unmapped char returns null`() {
        val report = HidKeyReport.buildKeyPress('\u0001') // control char
        assertTrue("Unmapped char must return null", report == null)
    }

    @Test
    fun `HidKeyReport buildSequence returns press-release pairs`() {
        val sequence = HidKeyReport.buildSequence("Hi")
        // H = press + release, i = press + release = 4 reports
        assertEquals(4, sequence.size)
        // First report = H press (shift + 0x0B)
        assertEquals(0x02.toByte(), sequence[0][0]) // Left Shift for 'H'
        // Second report = release (all zeros)
        assertTrue(sequence[1].all { it == 0.toByte() })
    }

    @Test
    fun `HidKeyReport buildSequence skips unmapped chars`() {
        val sequence = HidKeyReport.buildSequence("a\u0001b") // \u0001 is unmapped
        // a + b = 4 reports (press+release each), \u0001 skipped
        assertEquals(4, sequence.size)
    }

    @Test
    fun `HidKeyReport all lowercase letters a-z map to keycodes 0x04-0x1D`() {
        for (i in 0..25) {
            val c = 'a' + i
            val report = HidKeyReport.buildKeyPress(c)
            assertTrue("$c must be mappable", report != null)
            assertEquals("$c must map to keycode ${0x04 + i}", (0x04 + i).toByte(), report!![2])
            assertEquals("$c must have no modifier", 0x00.toByte(), report[0])
        }
    }

    @Test
    fun `HidKeyReport all uppercase letters A-Z map to keycodes 0x04-0x1D with shift`() {
        for (i in 0..25) {
            val c = 'A' + i
            val report = HidKeyReport.buildKeyPress(c)
            assertTrue("$c must be mappable", report != null)
            assertEquals("$c must map to keycode ${0x04 + i}", (0x04 + i).toByte(), report!![2])
            assertEquals("$c must have Left Shift modifier", 0x02.toByte(), report[0])
        }
    }

    // ---- ViewModel source structure tests ----

    @Test
    fun `BluetoothStressTestViewModel source file exists`() {
        assertTrue(File(SOURCE_DIR, "BluetoothStressTestViewModel.kt").exists())
    }

    @Test
    fun `BluetoothStressTestViewModel extends ViewModel`() {
        val content = File(SOURCE_DIR, "BluetoothStressTestViewModel.kt").readText()
        assertTrue(content.contains("class BluetoothStressTestViewModel : ViewModel()"))
    }

    @Test
    fun `BluetoothStressTestViewModel exposes state as LiveData of AttackState`() {
        val content = File(SOURCE_DIR, "BluetoothStressTestViewModel.kt").readText()
        assertTrue(content.contains("val state: LiveData<AttackState>"))
    }

    @Test
    fun `BluetoothStressTestViewModel exposes keystrokesSent as LiveData`() {
        val content = File(SOURCE_DIR, "BluetoothStressTestViewModel.kt").readText()
        assertTrue(content.contains("val keystrokesSent: LiveData<Int>"))
    }

    @Test
    fun `BluetoothStressTestViewModel exposes connectedCount as LiveData`() {
        val content = File(SOURCE_DIR, "BluetoothStressTestViewModel.kt").readText()
        assertTrue(content.contains("val connectedCount: LiveData<Int>"))
    }

    @Test
    fun `BluetoothStressTestViewModel exposes discoveredDevices as LiveData`() {
        val content = File(SOURCE_DIR, "BluetoothStressTestViewModel.kt").readText()
        assertTrue(content.contains("val discoveredDevices: LiveData<List<BluetoothDevice>>"))
    }

    @Test
    fun `BluetoothStressTestViewModel initial state is Idle`() {
        val content = File(SOURCE_DIR, "BluetoothStressTestViewModel.kt").readText()
        assertTrue(content.contains("MutableLiveData<AttackState>(AttackState.Idle)"))
    }

    @Test
    fun `BluetoothStressTestViewModel has startAttack method`() {
        val content = File(SOURCE_DIR, "BluetoothStressTestViewModel.kt").readText()
        assertTrue(content.contains("fun startAttack("))
    }

    @Test
    fun `BluetoothStressTestViewModel has stopAttack method`() {
        val content = File(SOURCE_DIR, "BluetoothStressTestViewModel.kt").readText()
        assertTrue(content.contains("fun stopAttack("))
    }

    @Test
    fun `BluetoothStressTestViewModel has dismissError method`() {
        val content = File(SOURCE_DIR, "BluetoothStressTestViewModel.kt").readText()
        assertTrue(content.contains("fun dismissError()"))
    }

    @Test
    fun `BluetoothStressTestViewModel uses BluetoothScanner not GatewayApiClient for BT`() {
        val content = File(SOURCE_DIR, "BluetoothStressTestViewModel.kt").readText()
        assertTrue("Must use BluetoothScanner", content.contains("BluetoothScanner"))
        assertFalse("Must NOT call startBluetoothDos", content.contains("startBluetoothDos"))
        assertFalse("Must NOT call getBluetoothDosStatus", content.contains("getBluetoothDosStatus"))
        assertFalse("Must NOT call stopBluetoothDos", content.contains("stopBluetoothDos"))
    }

    @Test
    fun `BluetoothStressTestViewModel uses BluetoothHidManager`() {
        val content = File(SOURCE_DIR, "BluetoothStressTestViewModel.kt").readText()
        assertTrue(content.contains("BluetoothHidManager"))
    }

    @Test
    fun `BluetoothStressTestViewModel overrides onCleared`() {
        val content = File(SOURCE_DIR, "BluetoothStressTestViewModel.kt").readText()
        assertTrue(content.contains("override fun onCleared()"))
    }

    @Test
    fun `dismissError resets state to Idle`() {
        val content = File(SOURCE_DIR, "BluetoothStressTestViewModel.kt").readText()
        val start = content.indexOf("fun dismissError()")
        assertTrue(start >= 0)
        val body = content.substring(start, content.indexOf("}", start) + 1)
        assertTrue(body.contains("AttackState.Idle"))
    }

    // ---- GatewayApiClient does NOT contain BT DoS methods ----

    @Test
    fun `GatewayApiClient has no startBluetoothDos method`() {
        val content = File(SOURCE_DIR, "GatewayApiClient.kt").readText()
        assertFalse(
            "BT DoS is on-device now — GatewayApiClient must NOT have startBluetoothDos",
            content.contains("fun startBluetoothDos(")
        )
    }

    @Test
    fun `GatewayApiClient has no getBluetoothDosStatus method`() {
        val content = File(SOURCE_DIR, "GatewayApiClient.kt").readText()
        assertFalse(
            "BT DoS is on-device now — GatewayApiClient must NOT have getBluetoothDosStatus",
            content.contains("fun getBluetoothDosStatus(")
        )
    }

    @Test
    fun `GatewayApiClient has no stopBluetoothDos method`() {
        val content = File(SOURCE_DIR, "GatewayApiClient.kt").readText()
        assertFalse(
            "BT DoS is on-device now — GatewayApiClient must NOT have stopBluetoothDos",
            content.contains("fun stopBluetoothDos(")
        )
    }

    @Test
    fun `GatewayApiClient still has installGateway method`() {
        val content = File(SOURCE_DIR, "GatewayApiClient.kt").readText()
        assertTrue(content.contains("fun installGateway()"))
    }

    // ---- Layout verification ----

    @Test
    fun `Stress test layout file exists`() {
        assertTrue(File(LAYOUT_DIR, "activity_bluetooth_stress_test.xml").exists())
    }

    @Test
    fun `Layout has MaterialSwitch toggle`() {
        val content = File(LAYOUT_DIR, "activity_bluetooth_stress_test.xml").readText()
        assertTrue(content.contains("MaterialSwitch"))
        assertTrue(content.contains("@+id/switchStressTest"))
    }

    @Test
    fun `Layout has keystrokes sent counter`() {
        val content = File(LAYOUT_DIR, "activity_bluetooth_stress_test.xml").readText()
        assertTrue(content.contains("@+id/txtPacketsSentCount"))
    }

    @Test
    fun `Layout has connected devices counter`() {
        val content = File(LAYOUT_DIR, "activity_bluetooth_stress_test.xml").readText()
        assertTrue(content.contains("@+id/txtDevicesTargetedCount"))
    }

    @Test
    fun `Layout has status text view`() {
        val content = File(LAYOUT_DIR, "activity_bluetooth_stress_test.xml").readText()
        assertTrue(content.contains("@+id/txtStatus"))
    }

    @Test
    fun `Layout has RecyclerView for discovered devices`() {
        val content = File(LAYOUT_DIR, "activity_bluetooth_stress_test.xml").readText()
        assertTrue(content.contains("RecyclerView"))
        assertTrue(content.contains("@+id/recyclerDevices"))
    }

    @Test
    fun `Layout uses MaterialCardView for counters`() {
        val content = File(LAYOUT_DIR, "activity_bluetooth_stress_test.xml").readText()
        assertTrue(content.contains("MaterialCardView"))
        assertTrue(content.contains("@+id/cardCounters"))
    }

    @Test
    fun `Layout counters use DisplaySmall text appearance`() {
        val content = File(LAYOUT_DIR, "activity_bluetooth_stress_test.xml").readText()
        assertTrue(content.contains("textAppearanceDisplaySmall"))
    }

    @Test
    fun `Layout has title text view`() {
        val content = File(LAYOUT_DIR, "activity_bluetooth_stress_test.xml").readText()
        assertTrue(content.contains("@+id/txtTitle"))
    }

    // ---- HidKeyReport source verification ----

    @Test
    fun `HidKeyReport source file exists`() {
        assertTrue(File(SOURCE_DIR, "HidKeyReport.kt").exists())
    }

    @Test
    fun `BluetoothScanner source file exists`() {
        assertTrue(File(SOURCE_DIR, "BluetoothScanner.kt").exists())
    }

    @Test
    fun `BluetoothHidManager source file exists`() {
        assertTrue(File(SOURCE_DIR, "BluetoothHidManager.kt").exists())
    }
}
