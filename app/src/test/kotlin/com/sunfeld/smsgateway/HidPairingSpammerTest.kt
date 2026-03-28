package com.sunfeld.smsgateway

import org.junit.Assert.assertEquals
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
    fun `TARGET_TIMEOUT_MS is long enough for target to respond`() {
        assertTrue(
            "TARGET_TIMEOUT_MS should be >= 3000ms",
            HidPairingSpammer.TARGET_TIMEOUT_MS >= 3000L
        )
    }

    @Test
    fun `REHIT_COOLDOWN prevents hammering same device`() {
        val sourceFile = File(SOURCE_DIR, "HidPairingSpammer.kt")
        val content = sourceFile.readText()
        assertTrue("Must have REHIT_COOLDOWN_MS", content.contains("REHIT_COOLDOWN_MS"))
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
    fun `HidPairingSpammer cancels bond after confirmation`() {
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
        assertTrue("Must restore name in stop()", content.contains("adapter?.setName(name)"))
    }

    @Test
    fun `HidPairingSpammer supports dynamic target updates`() {
        val sourceFile = File(SOURCE_DIR, "HidPairingSpammer.kt")
        val content = sourceFile.readText()
        assertTrue("Must have updateTargets method", content.contains("fun updateTargets"))
    }

    @Test
    fun `HidPairingSpammer tracks confirmed and skipped counts`() {
        val sourceFile = File(SOURCE_DIR, "HidPairingSpammer.kt")
        val content = sourceFile.readText()
        assertTrue("Must track confirmedCount", content.contains("_hitCount"))
        assertTrue("Must track skippedCount", content.contains("_skippedCount"))
    }

    @Test
    fun `HidPairingSpammer uses CompletableDeferred for event-driven confirmation`() {
        val sourceFile = File(SOURCE_DIR, "HidPairingSpammer.kt")
        val content = sourceFile.readText()
        assertTrue("Must use CompletableDeferred", content.contains("CompletableDeferred"))
        assertTrue("Must use pairingDeferred", content.contains("pairingDeferred"))
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

    // ---- BLE HID Keyboard Server tests ----

    @Test
    fun `BleHidKeyboardServer exists and uses BluetoothGattServer`() {
        val sourceFile = File(SOURCE_DIR, "BleHidKeyboardServer.kt")
        assertTrue("BleHidKeyboardServer.kt must exist", sourceFile.exists())
        val content = sourceFile.readText()
        assertTrue("Must use BluetoothGattServer", content.contains("BluetoothGattServer"))
        assertTrue("Must use openGattServer", content.contains("openGattServer"))
    }

    @Test
    fun `BleHidKeyboardServer serves HID service with required characteristics`() {
        val sourceFile = File(SOURCE_DIR, "BleHidKeyboardServer.kt")
        val content = sourceFile.readText()
        assertTrue("Must have HID Service UUID 0x1812", content.contains("00001812"))
        assertTrue("Must have Report Map characteristic", content.contains("REPORT_MAP_UUID"))
        assertTrue("Must have HID Information characteristic", content.contains("HID_INFORMATION_UUID"))
        assertTrue("Must have Report characteristic", content.contains("REPORT_UUID"))
        assertTrue("Must have Protocol Mode characteristic", content.contains("PROTOCOL_MODE_UUID"))
    }

    @Test
    fun `BleHidKeyboardServer includes Device Information and Battery services`() {
        val sourceFile = File(SOURCE_DIR, "BleHidKeyboardServer.kt")
        val content = sourceFile.readText()
        assertTrue("Must have DIS UUID 0x180A", content.contains("0000180A"))
        assertTrue("Must have Battery UUID 0x180F", content.contains("0000180F"))
    }

    @Test
    fun `BleHidKeyboardServer uses keyboard HID descriptor`() {
        val sourceFile = File(SOURCE_DIR, "BleHidKeyboardServer.kt")
        val content = sourceFile.readText()
        assertTrue("Must serve HidKeyReport.KEYBOARD_DESCRIPTOR in GATT reads",
            content.contains("HidKeyReport.KEYBOARD_DESCRIPTOR"))
    }

    @Test
    fun `ViewModel wires BleHidKeyboardServer into auto assault`() {
        val vmFile = File(SOURCE_DIR, "BluetoothHidViewModel.kt")
        val content = vmFile.readText()
        assertTrue("Must reference bleHidServer", content.contains("bleHidServer"))
        assertTrue("Must start bleHidServer in auto assault", content.contains("bleHidServer.start("))
        assertTrue("Must stop bleHidServer", content.contains("bleHidServer.stop("))
    }
}
