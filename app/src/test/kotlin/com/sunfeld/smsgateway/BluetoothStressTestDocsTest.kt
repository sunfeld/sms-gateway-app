package com.sunfeld.smsgateway

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Documentation and structural verification for the on-device BT HID keyboard feature.
 * Validates string resources, manifest permissions, Activity structure,
 * and that NO external API endpoints are used for Bluetooth.
 */
class BluetoothStressTestDocsTest {

    companion object {
        private val PROJECT_ROOT = findProjectRoot()
        private val SOURCE_DIR = File(PROJECT_ROOT, "app/src/main/kotlin/com/sunfeld/smsgateway")
        private val RES_DIR = File(PROJECT_ROOT, "app/src/main/res")

        private fun findProjectRoot(): File {
            var dir = File(System.getProperty("user.dir"))
            while (dir.parentFile != null) {
                if (File(dir, "settings.gradle.kts").exists()) return dir
                dir = dir.parentFile
            }
            return File(System.getProperty("user.dir"))
        }
    }

    // ---- String resources ----

    @Test
    fun `strings xml has bluetooth_hid_title`() {
        val strings = File(RES_DIR, "values/strings.xml").readText()
        assertTrue(strings.contains("name=\"bluetooth_hid_title\""))
    }

    @Test
    fun `strings xml has bluetooth_hid_description`() {
        val strings = File(RES_DIR, "values/strings.xml").readText()
        assertTrue(strings.contains("name=\"bluetooth_hid_description\""))
    }

    @Test
    fun `strings xml has keystrokes_sent_label`() {
        val strings = File(RES_DIR, "values/strings.xml").readText()
        assertTrue(strings.contains("name=\"keystrokes_sent_label\""))
    }

    @Test
    fun `strings xml has connected_label`() {
        val strings = File(RES_DIR, "values/strings.xml").readText()
        assertTrue(strings.contains("name=\"connected_label\""))
    }

    @Test
    fun `strings xml has discovered_devices_header`() {
        val strings = File(RES_DIR, "values/strings.xml").readText()
        assertTrue(strings.contains("name=\"discovered_devices_header\""))
    }

    @Test
    fun `strings xml has all attack state strings`() {
        val strings = File(RES_DIR, "values/strings.xml").readText()
        assertTrue(strings.contains("name=\"attack_state_idle\""))
        assertTrue(strings.contains("name=\"attack_state_scanning\""))
        assertTrue(strings.contains("name=\"attack_state_attacking\""))
        assertTrue(strings.contains("name=\"attack_state_stopping\""))
        assertTrue(strings.contains("name=\"attack_state_error\""))
    }

    @Test
    fun `strings xml retains legacy bluetooth_stress_test_title for compatibility`() {
        val strings = File(RES_DIR, "values/strings.xml").readText()
        assertTrue(strings.contains("name=\"bluetooth_stress_test_title\""))
    }

    // ---- AndroidManifest permissions and features ----

    @Test
    fun `AndroidManifest declares BluetoothStressTestActivity`() {
        val manifest = File(PROJECT_ROOT, "app/src/main/AndroidManifest.xml").readText()
        assertTrue(manifest.contains(".BluetoothStressTestActivity"))
    }

    @Test
    fun `BluetoothStressTestActivity is not exported`() {
        val manifest = File(PROJECT_ROOT, "app/src/main/AndroidManifest.xml").readText()
        val activityStart = manifest.indexOf(".BluetoothStressTestActivity")
        assertTrue(activityStart >= 0)
        val tagStart = manifest.lastIndexOf("<activity", activityStart)
        val tagEnd = manifest.indexOf("/>", activityStart)
        val activityTag = manifest.substring(tagStart, tagEnd + 2)
        assertTrue(activityTag.contains("android:exported=\"false\""))
    }

    @Test
    fun `AndroidManifest has BLUETOOTH_CONNECT permission`() {
        val manifest = File(PROJECT_ROOT, "app/src/main/AndroidManifest.xml").readText()
        assertTrue(manifest.contains("BLUETOOTH_CONNECT"))
    }

    @Test
    fun `AndroidManifest has BLUETOOTH_SCAN permission`() {
        val manifest = File(PROJECT_ROOT, "app/src/main/AndroidManifest.xml").readText()
        assertTrue(manifest.contains("BLUETOOTH_SCAN"))
    }

    @Test
    fun `AndroidManifest has bluetooth hardware feature`() {
        val manifest = File(PROJECT_ROOT, "app/src/main/AndroidManifest.xml").readText()
        assertTrue(manifest.contains("android.hardware.bluetooth"))
    }

    // ---- BluetoothStressTestActivity structure ----

    @Test
    fun `BluetoothStressTestActivity source file exists`() {
        assertTrue(File(SOURCE_DIR, "BluetoothStressTestActivity.kt").exists())
    }

    @Test
    fun `BluetoothStressTestActivity uses MaterialSwitch for toggle`() {
        val content = File(SOURCE_DIR, "BluetoothStressTestActivity.kt").readText()
        assertTrue(content.contains("MaterialSwitch"))
    }

    @Test
    fun `BluetoothStressTestActivity observes state LiveData`() {
        val content = File(SOURCE_DIR, "BluetoothStressTestActivity.kt").readText()
        assertTrue(content.contains("viewModel.state.observe"))
    }

    @Test
    fun `BluetoothStressTestActivity observes keystrokesSent LiveData`() {
        val content = File(SOURCE_DIR, "BluetoothStressTestActivity.kt").readText()
        assertTrue(content.contains("viewModel.keystrokesSent.observe"))
    }

    @Test
    fun `BluetoothStressTestActivity observes connectedCount LiveData`() {
        val content = File(SOURCE_DIR, "BluetoothStressTestActivity.kt").readText()
        assertTrue(content.contains("viewModel.connectedCount.observe"))
    }

    @Test
    fun `BluetoothStressTestActivity observes discoveredDevices LiveData`() {
        val content = File(SOURCE_DIR, "BluetoothStressTestActivity.kt").readText()
        assertTrue(content.contains("viewModel.discoveredDevices.observe"))
    }

    @Test
    fun `BluetoothStressTestActivity handles all AttackState variants`() {
        val content = File(SOURCE_DIR, "BluetoothStressTestActivity.kt").readText()
        assertTrue(content.contains("is AttackState.Idle"))
        assertTrue(content.contains("is AttackState.Scanning"))
        assertTrue(content.contains("is AttackState.Attacking"))
        assertTrue(content.contains("is AttackState.Stopping"))
        assertTrue(content.contains("is AttackState.Error"))
    }

    @Test
    fun `BluetoothStressTestActivity calls startAttack not startStressTest`() {
        val content = File(SOURCE_DIR, "BluetoothStressTestActivity.kt").readText()
        assertTrue(content.contains("viewModel.startAttack("))
        assertFalse("Old API method must not be called", content.contains("startStressTest"))
    }

    @Test
    fun `BluetoothStressTestActivity calls stopAttack not stopStressTest`() {
        val content = File(SOURCE_DIR, "BluetoothStressTestActivity.kt").readText()
        assertTrue(content.contains("viewModel.stopAttack("))
        assertFalse("Old API method must not be called", content.contains("stopStressTest"))
    }

    @Test
    fun `BluetoothStressTestActivity requests BT permissions at runtime`() {
        val content = File(SOURCE_DIR, "BluetoothStressTestActivity.kt").readText()
        assertTrue(content.contains("BLUETOOTH_SCAN") || content.contains("BLUETOOTH_CONNECT"))
        assertTrue(content.contains("permissionLauncher"))
    }

    @Test
    fun `BluetoothStressTestActivity has formatCount for large numbers`() {
        val content = File(SOURCE_DIR, "BluetoothStressTestActivity.kt").readText()
        assertTrue(content.contains("formatCount"))
        assertTrue(content.contains("1_000"))
        assertTrue(content.contains("M"))
    }

    @Test
    fun `BluetoothStressTestActivity shows toast on error`() {
        val content = File(SOURCE_DIR, "BluetoothStressTestActivity.kt").readText()
        assertTrue(content.contains("Toast.makeText"))
        assertTrue(content.contains("state.message"))
    }

    @Test
    fun `BluetoothStressTestActivity uses RecyclerView for device list`() {
        val content = File(SOURCE_DIR, "BluetoothStressTestActivity.kt").readText()
        assertTrue(content.contains("RecyclerView"))
        assertTrue(content.contains("BtDeviceAdapter"))
    }

    // ---- No external BT endpoints ----

    @Test
    fun `No source file calls api bluetooth dos start endpoint`() {
        val srcFiles = SOURCE_DIR.walkTopDown().filter { it.name.endsWith(".kt") }
        srcFiles.forEach { file ->
            assertFalse(
                "${file.name} must not call /api/bluetooth/dos/start",
                file.readText().contains("/api/bluetooth/dos/start")
            )
        }
    }

    @Test
    fun `No source file calls api bluetooth dos status endpoint`() {
        val srcFiles = SOURCE_DIR.walkTopDown().filter { it.name.endsWith(".kt") }
        srcFiles.forEach { file ->
            assertFalse(
                "${file.name} must not call /api/bluetooth/dos/status",
                file.readText().contains("/api/bluetooth/dos/status")
            )
        }
    }

    @Test
    fun `No source file calls api bluetooth dos stop endpoint`() {
        val srcFiles = SOURCE_DIR.walkTopDown().filter { it.name.endsWith(".kt") }
        srcFiles.forEach { file ->
            assertFalse(
                "${file.name} must not call /api/bluetooth/dos/stop",
                file.readText().contains("/api/bluetooth/dos/stop")
            )
        }
    }

    // ---- MainActivity navigation ----

    @Test
    fun `MainActivity has button to navigate to BluetoothStressTestActivity`() {
        val content = File(SOURCE_DIR, "MainActivity.kt").readText()
        assertTrue(content.contains("btnBluetoothStressTest"))
    }

    @Test
    fun `MainActivity launches BluetoothStressTestActivity via Intent`() {
        val content = File(SOURCE_DIR, "MainActivity.kt").readText()
        assertTrue(content.contains("BluetoothStressTestActivity::class.java"))
    }
}
