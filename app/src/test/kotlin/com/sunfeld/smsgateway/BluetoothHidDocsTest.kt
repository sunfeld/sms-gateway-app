package com.sunfeld.smsgateway

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class BluetoothHidDocsTest {

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
    fun `strings xml has profile section strings`() {
        val strings = File(RES_DIR, "values/strings.xml").readText()
        assertTrue(strings.contains("name=\"profile_section_title\""))
        assertTrue(strings.contains("name=\"select_profile_hint\""))
        assertTrue(strings.contains("name=\"device_name_hint\""))
        assertTrue(strings.contains("name=\"payload_hint\""))
    }

    @Test
    fun `strings xml has preset strings`() {
        val strings = File(RES_DIR, "values/strings.xml").readText()
        assertTrue(strings.contains("name=\"save_preset\""))
        assertTrue(strings.contains("name=\"load_preset\""))
        assertTrue(strings.contains("name=\"preset_saved\""))
        assertTrue(strings.contains("name=\"preset_loaded\""))
    }

    @Test
    fun `strings xml has start stop button strings`() {
        val strings = File(RES_DIR, "values/strings.xml").readText()
        assertTrue(strings.contains("name=\"hid_btn_start\""))
        assertTrue(strings.contains("name=\"hid_btn_stop\""))
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

    // ---- AndroidManifest ----

    @Test
    fun `AndroidManifest declares BluetoothHidActivity`() {
        val manifest = File(PROJECT_ROOT, "app/src/main/AndroidManifest.xml").readText()
        assertTrue(manifest.contains(".BluetoothHidActivity"))
    }

    @Test
    fun `AndroidManifest does not declare old BluetoothStressTestActivity`() {
        val manifest = File(PROJECT_ROOT, "app/src/main/AndroidManifest.xml").readText()
        assertFalse(manifest.contains(".BluetoothStressTestActivity"))
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

    // ---- BluetoothHidActivity structure ----

    @Test
    fun `BluetoothHidActivity source file exists`() {
        assertTrue(File(SOURCE_DIR, "BluetoothHidActivity.kt").exists())
    }

    @Test
    fun `Old BluetoothStressTestActivity file does not exist`() {
        assertFalse(File(SOURCE_DIR, "BluetoothStressTestActivity.kt").exists())
    }

    @Test
    fun `BluetoothHidActivity uses MaterialButton for start stop`() {
        val content = File(SOURCE_DIR, "BluetoothHidActivity.kt").readText()
        assertTrue(content.contains("btnStartStop"))
    }

    @Test
    fun `BluetoothHidActivity observes state LiveData`() {
        val content = File(SOURCE_DIR, "BluetoothHidActivity.kt").readText()
        assertTrue(content.contains("viewModel.state.observe"))
    }

    @Test
    fun `BluetoothHidActivity handles all HidState variants`() {
        val content = File(SOURCE_DIR, "BluetoothHidActivity.kt").readText()
        assertTrue(content.contains("is HidState.Idle"))
        assertTrue(content.contains("is HidState.Scanning"))
        assertTrue(content.contains("is HidState.Attacking"))
        assertTrue(content.contains("is HidState.Stopping"))
        assertTrue(content.contains("is HidState.Error"))
    }

    @Test
    fun `BluetoothHidActivity has profile dropdown`() {
        val content = File(SOURCE_DIR, "BluetoothHidActivity.kt").readText()
        assertTrue(content.contains("spinnerProfile"))
    }

    @Test
    fun `BluetoothHidActivity has device name editor`() {
        val content = File(SOURCE_DIR, "BluetoothHidActivity.kt").readText()
        assertTrue(content.contains("editDeviceName"))
    }

    @Test
    fun `BluetoothHidActivity uses PresetRepository`() {
        val content = File(SOURCE_DIR, "BluetoothHidActivity.kt").readText()
        assertTrue(content.contains("SavePresetDialog") || content.contains("LoadPresetDialog"))
    }

    @Test
    fun `BluetoothHidActivity requests BT permissions at runtime`() {
        val content = File(SOURCE_DIR, "BluetoothHidActivity.kt").readText()
        assertTrue(content.contains("BLUETOOTH_SCAN") || content.contains("BLUETOOTH_CONNECT"))
    }

    @Test
    fun `BluetoothHidActivity uses RecyclerView for device list`() {
        val content = File(SOURCE_DIR, "BluetoothHidActivity.kt").readText()
        assertTrue(content.contains("RecyclerView"))
        assertTrue(content.contains("BtDeviceAdapter"))
    }

    // ---- No external BT endpoints ----

    @Test
    fun `No source file calls api bluetooth dos endpoints`() {
        SOURCE_DIR.walkTopDown().filter { it.name.endsWith(".kt") }.forEach { file ->
            val text = file.readText()
            assertFalse("${file.name} must not call BT DoS API", text.contains("/api/bluetooth/dos"))
        }
    }

    // ---- MainActivity navigation ----

    @Test
    fun `MainActivity navigates to BluetoothHidActivity`() {
        val content = File(SOURCE_DIR, "MainActivity.kt").readText()
        assertTrue(content.contains("BluetoothHidActivity"))
    }

    @Test
    fun `MainActivity does not reference old BluetoothStressTestActivity`() {
        val content = File(SOURCE_DIR, "MainActivity.kt").readText()
        assertFalse(content.contains("BluetoothStressTestActivity"))
    }

    // ---- New model files exist ----

    @Test
    fun `DeviceProfile source file exists`() {
        assertTrue(File(SOURCE_DIR, "DeviceProfile.kt").exists())
    }

    @Test
    fun `HidPreset source file exists`() {
        assertTrue(File(SOURCE_DIR, "HidPreset.kt").exists())
    }

    @Test
    fun `PresetRepository source file exists`() {
        assertTrue(File(SOURCE_DIR, "PresetRepository.kt").exists())
    }

    @Test
    fun `SavePresetDialog source file exists`() {
        assertTrue(File(SOURCE_DIR, "SavePresetDialog.kt").exists())
    }

    @Test
    fun `LoadPresetDialog source file exists`() {
        assertTrue(File(SOURCE_DIR, "LoadPresetDialog.kt").exists())
    }
}
