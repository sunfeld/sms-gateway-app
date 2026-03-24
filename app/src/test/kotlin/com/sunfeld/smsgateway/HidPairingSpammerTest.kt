package com.sunfeld.smsgateway

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class HidPairingSpammerTest {

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

    // ---- Profile rotation tests ----

    @Test
    fun `KEYBOARD_PROFILES contains all DeviceProfiles`() {
        // HidPairingSpammer should rotate through ALL keyboard profiles
        val sourceFile = File(SOURCE_DIR, "HidPairingSpammer.kt")
        assertTrue("HidPairingSpammer.kt must exist", sourceFile.exists())
        val content = sourceFile.readText()
        assertTrue(
            "KEYBOARD_PROFILES should reference DeviceProfiles.ALL",
            content.contains("DeviceProfiles.ALL")
        )
    }

    @Test
    fun `all 15 keyboard profiles are available for rotation`() {
        assertEquals("DeviceProfiles should have 15 entries", 15, DeviceProfiles.ALL.size)
    }

    @Test
    fun `each profile has valid SDP keyboard settings`() {
        for (profile in DeviceProfiles.ALL) {
            assertTrue("sdpName must not be blank: ${profile.id}", profile.sdpName.isNotBlank())
            assertTrue("sdpDescription must not be blank: ${profile.id}", profile.sdpDescription.isNotBlank())
            assertTrue("sdpProvider must not be blank: ${profile.id}", profile.sdpProvider.isNotBlank())
        }
    }

    // ---- Timing constant tests ----

    @Test
    fun `DIALOG_DWELL_MS is long enough for PIN dialog to render`() {
        // PIN pairing dialog needs at least 1.5s to fully render on most devices
        assertTrue(
            "DIALOG_DWELL_MS should be >= 1500ms",
            HidPairingSpammer.DIALOG_DWELL_MS >= 1500L
        )
    }

    @Test
    fun `TARGET_CYCLE_MS allows brief settle between targets`() {
        assertTrue(
            "TARGET_CYCLE_MS should be >= 100ms for settle time",
            HidPairingSpammer.TARGET_CYCLE_MS >= 100L
        )
    }

    @Test
    fun `total cycle time per target is reasonable`() {
        // Each target: dwell + cycle = total time before next target
        val totalPerTarget = HidPairingSpammer.DIALOG_DWELL_MS + HidPairingSpammer.TARGET_CYCLE_MS
        assertTrue(
            "Total cycle per target should be <= 5000ms to keep attack flowing",
            totalPerTarget <= 5000L
        )
    }

    // ---- Source code structural tests ----

    @Test
    fun `HidPairingSpammer uses BluetoothHidDevice API`() {
        val sourceFile = File(SOURCE_DIR, "HidPairingSpammer.kt")
        val content = sourceFile.readText()
        assertTrue("Must import BluetoothHidDevice", content.contains("BluetoothHidDevice"))
        assertTrue("Must use hidDevice.connect", content.contains("hid.connect("))
        assertTrue("Must use SUBCLASS1_KEYBOARD", content.contains("SUBCLASS1_KEYBOARD"))
    }

    @Test
    fun `HidPairingSpammer listens for ACTION_PAIRING_REQUEST`() {
        val sourceFile = File(SOURCE_DIR, "HidPairingSpammer.kt")
        val content = sourceFile.readText()
        assertTrue(
            "Must register for ACTION_PAIRING_REQUEST to detect PIN dialog",
            content.contains("ACTION_PAIRING_REQUEST")
        )
    }

    @Test
    fun `HidPairingSpammer cancels bond after dwell`() {
        val sourceFile = File(SOURCE_DIR, "HidPairingSpammer.kt")
        val content = sourceFile.readText()
        assertTrue("Must call cancelBondProcess", content.contains("cancelBondProcess"))
        assertTrue("Must call removeBond", content.contains("removeBond"))
    }

    @Test
    fun `HidPairingSpammer uses RequiresApi P annotation`() {
        val sourceFile = File(SOURCE_DIR, "HidPairingSpammer.kt")
        val content = sourceFile.readText()
        assertTrue(
            "Must be annotated with @RequiresApi(P) since HID Device API is P+",
            content.contains("@RequiresApi")
        )
    }

    @Test
    fun `HidPairingSpammer restores adapter name on stop`() {
        val sourceFile = File(SOURCE_DIR, "HidPairingSpammer.kt")
        val content = sourceFile.readText()
        assertTrue("Must save originalAdapterName", content.contains("originalAdapterName"))
        // Check restore logic exists in stop()
        assertTrue("Must restore name in stop()", content.contains("adapter?.setName(name)"))
    }

    @Test
    fun `HidPairingSpammer is wired into BluetoothHidViewModel`() {
        val vmFile = File(SOURCE_DIR, "BluetoothHidViewModel.kt")
        val content = vmFile.readText()
        assertTrue("ViewModel must reference hidPairingSpammer", content.contains("hidPairingSpammer"))
        assertTrue("Must start hidPairingSpammer in cray mode", content.contains("hidPairingSpammer?.start("))
        assertTrue("Must stop hidPairingSpammer", content.contains("hidPairingSpammer?.stop("))
    }

    @Test
    fun `HidPairingSpammer counter is included in Cray Mode total`() {
        val vmFile = File(SOURCE_DIR, "BluetoothHidViewModel.kt")
        val content = vmFile.readText()
        assertTrue(
            "hitCount must be included in combined counter",
            content.contains("hidHits()")  || content.contains("hitCount")
        )
    }

    @Test
    fun `HidPairingSpammer uses HidKeyReport KEYBOARD_DESCRIPTOR`() {
        val sourceFile = File(SOURCE_DIR, "HidPairingSpammer.kt")
        val content = sourceFile.readText()
        assertTrue(
            "Must use HidKeyReport.KEYBOARD_DESCRIPTOR for authentic HID registration",
            content.contains("HidKeyReport.KEYBOARD_DESCRIPTOR")
        )
    }
}
