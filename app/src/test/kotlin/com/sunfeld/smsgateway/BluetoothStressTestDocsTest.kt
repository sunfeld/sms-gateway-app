package com.sunfeld.smsgateway

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Documentation verification tests for the Bluetooth Stress Test dashboard toggle.
 * Validates that README.md documents the feature, string resources are defined,
 * AndroidManifest declares the activity, and source files reference the API endpoints.
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

        private fun loadReadme(): String {
            return File(PROJECT_ROOT, "README.md").readText()
        }
    }

    // ---- README documentation section tests ----

    @Test
    fun `README has Bluetooth Stress Test Dashboard Toggle section`() {
        val readme = loadReadme()
        assertTrue(
            "README must have a Bluetooth Stress Test Dashboard Toggle section",
            readme.contains("## Bluetooth Stress Test Dashboard Toggle")
        )
    }

    @Test
    fun `README documents UI Components table`() {
        val readme = loadReadme()
        assertTrue(
            "README must document UI Components",
            readme.contains("### UI Components") && readme.contains("Stress Test Toggle")
        )
    }

    @Test
    fun `README documents Packets Sent Counter`() {
        val readme = loadReadme()
        assertTrue(
            "README must document Packets Sent Counter",
            readme.contains("Packets Sent Counter")
        )
    }

    @Test
    fun `README documents Devices Targeted Counter`() {
        val readme = loadReadme()
        assertTrue(
            "README must document Devices Targeted Counter",
            readme.contains("Devices Targeted Counter")
        )
    }

    @Test
    fun `README documents Session Timer`() {
        val readme = loadReadme()
        assertTrue(
            "README must document Session Timer",
            readme.contains("Session Timer")
        )
    }

    @Test
    fun `README documents Data Flow for toggle`() {
        val readme = loadReadme()
        assertTrue(
            "README must document the data flow",
            readme.contains("### Data Flow") && readme.contains("Toggle ON")
        )
    }

    @Test
    fun `README documents polling interval`() {
        val readme = loadReadme()
        assertTrue(
            "README must document 1-second polling interval",
            readme.contains("1-second intervals") || readme.contains("every 1s")
        )
    }

    @Test
    fun `README documents Toggle States table`() {
        val readme = loadReadme()
        assertTrue(
            "README must document Toggle States",
            readme.contains("### Toggle States")
        )
    }

    @Test
    fun `README documents Idle toggle state`() {
        val readme = loadReadme()
        val toggleSection = readme.substring(readme.indexOf("### Toggle States"))
        assertTrue("Must document Idle state", toggleSection.contains("Idle"))
    }

    @Test
    fun `README documents Starting toggle state`() {
        val readme = loadReadme()
        val toggleSection = readme.substring(readme.indexOf("### Toggle States"))
        assertTrue("Must document Starting state", toggleSection.contains("Starting"))
    }

    @Test
    fun `README documents Running toggle state`() {
        val readme = loadReadme()
        val toggleSection = readme.substring(readme.indexOf("### Toggle States"))
        assertTrue("Must document Running state", toggleSection.contains("Running"))
    }

    @Test
    fun `README documents Stopping toggle state`() {
        val readme = loadReadme()
        val toggleSection = readme.substring(readme.indexOf("### Toggle States"))
        assertTrue("Must document Stopping state", toggleSection.contains("Stopping"))
    }

    @Test
    fun `README documents Error toggle state`() {
        val readme = loadReadme()
        val toggleSection = readme.substring(readme.indexOf("### Toggle States"))
        assertTrue("Must document Error state", toggleSection.contains("Error"))
    }

    @Test
    fun `README documents status endpoint polling`() {
        val readme = loadReadme()
        assertTrue(
            "README must reference status polling endpoint",
            readme.contains("/api/bluetooth/dos/status/")
        )
    }

    @Test
    fun `README documents stop endpoint`() {
        val readme = loadReadme()
        assertTrue(
            "README must reference stop endpoint",
            readme.contains("/api/bluetooth/dos/stop/")
        )
    }

    @Test
    fun `README documents Real-Time Counter Updates section`() {
        val readme = loadReadme()
        assertTrue(
            "README must have Real-Time Counter Updates section",
            readme.contains("### Real-Time Counter Updates")
        )
    }

    @Test
    fun `README documents status response JSON fields`() {
        val readme = loadReadme()
        assertTrue("Must document packets_sent field", readme.contains("packets_sent"))
        assertTrue("Must document targets_active field", readme.contains("targets_active"))
        assertTrue("Must document remaining_seconds field", readme.contains("remaining_seconds"))
    }

    // ---- String resources verification ----

    @Test
    fun `strings xml has bluetooth_stress_test_title`() {
        val strings = File(RES_DIR, "values/strings.xml").readText()
        assertTrue(
            "strings.xml must define bluetooth_stress_test_title",
            strings.contains("name=\"bluetooth_stress_test_title\"")
        )
    }

    @Test
    fun `strings xml has bluetooth_stress_test_description`() {
        val strings = File(RES_DIR, "values/strings.xml").readText()
        assertTrue(
            "strings.xml must define bluetooth_stress_test_description",
            strings.contains("name=\"bluetooth_stress_test_description\"")
        )
    }

    @Test
    fun `strings xml has bluetooth_stress_test_toggle`() {
        val strings = File(RES_DIR, "values/strings.xml").readText()
        assertTrue(
            "strings.xml must define bluetooth_stress_test_toggle",
            strings.contains("name=\"bluetooth_stress_test_toggle\"")
        )
    }

    @Test
    fun `strings xml has packets_sent_label`() {
        val strings = File(RES_DIR, "values/strings.xml").readText()
        assertTrue(
            "strings.xml must define packets_sent_label",
            strings.contains("name=\"packets_sent_label\"")
        )
    }

    @Test
    fun `strings xml has devices_targeted_label`() {
        val strings = File(RES_DIR, "values/strings.xml").readText()
        assertTrue(
            "strings.xml must define devices_targeted_label",
            strings.contains("name=\"devices_targeted_label\"")
        )
    }

    @Test
    fun `strings xml has all stress test status strings`() {
        val strings = File(RES_DIR, "values/strings.xml").readText()
        assertTrue("Must have idle status", strings.contains("name=\"stress_test_status_idle\""))
        assertTrue("Must have starting status", strings.contains("name=\"stress_test_status_starting\""))
        assertTrue("Must have running status", strings.contains("name=\"stress_test_status_running\""))
        assertTrue("Must have stopping status", strings.contains("name=\"stress_test_status_stopping\""))
        assertTrue("Must have error status", strings.contains("name=\"stress_test_status_error\""))
    }

    @Test
    fun `strings xml has stress_test_remaining_time with format placeholder`() {
        val strings = File(RES_DIR, "values/strings.xml").readText()
        assertTrue(
            "strings.xml must define stress_test_remaining_time with format placeholder",
            strings.contains("name=\"stress_test_remaining_time\"") && strings.contains("%1\$d")
        )
    }

    // ---- AndroidManifest verification ----

    @Test
    fun `AndroidManifest declares BluetoothStressTestActivity`() {
        val manifest = File(PROJECT_ROOT, "app/src/main/AndroidManifest.xml").readText()
        assertTrue(
            "AndroidManifest must declare BluetoothStressTestActivity",
            manifest.contains(".BluetoothStressTestActivity")
        )
    }

    @Test
    fun `BluetoothStressTestActivity is not exported`() {
        val manifest = File(PROJECT_ROOT, "app/src/main/AndroidManifest.xml").readText()
        val activityStart = manifest.indexOf(".BluetoothStressTestActivity")
        assertTrue("Activity must be declared", activityStart >= 0)
        val activityTag = manifest.substring(
            manifest.lastIndexOf("<activity", activityStart),
            manifest.indexOf("/>", activityStart) + 2
        )
        assertTrue(
            "BluetoothStressTestActivity must have exported=false",
            activityTag.contains("android:exported=\"false\"")
        )
    }

    // ---- BluetoothStressTestActivity source documentation ----

    @Test
    fun `BluetoothStressTestActivity source file exists`() {
        val file = File(SOURCE_DIR, "BluetoothStressTestActivity.kt")
        assertTrue("BluetoothStressTestActivity.kt must exist", file.exists())
    }

    @Test
    fun `BluetoothStressTestActivity uses MaterialSwitch for toggle`() {
        val content = File(SOURCE_DIR, "BluetoothStressTestActivity.kt").readText()
        assertTrue(
            "Activity must use MaterialSwitch",
            content.contains("MaterialSwitch")
        )
    }

    @Test
    fun `BluetoothStressTestActivity observes state LiveData`() {
        val content = File(SOURCE_DIR, "BluetoothStressTestActivity.kt").readText()
        assertTrue(
            "Activity must observe viewModel.state",
            content.contains("viewModel.state.observe")
        )
    }

    @Test
    fun `BluetoothStressTestActivity observes packetsSent LiveData`() {
        val content = File(SOURCE_DIR, "BluetoothStressTestActivity.kt").readText()
        assertTrue(
            "Activity must observe viewModel.packetsSent",
            content.contains("viewModel.packetsSent.observe")
        )
    }

    @Test
    fun `BluetoothStressTestActivity observes devicesTargeted LiveData`() {
        val content = File(SOURCE_DIR, "BluetoothStressTestActivity.kt").readText()
        assertTrue(
            "Activity must observe viewModel.devicesTargeted",
            content.contains("viewModel.devicesTargeted.observe")
        )
    }

    @Test
    fun `BluetoothStressTestActivity handles all StressTestState variants in updateUI`() {
        val content = File(SOURCE_DIR, "BluetoothStressTestActivity.kt").readText()
        assertTrue("Must handle Idle", content.contains("is StressTestState.Idle"))
        assertTrue("Must handle Starting", content.contains("is StressTestState.Starting"))
        assertTrue("Must handle Running", content.contains("is StressTestState.Running"))
        assertTrue("Must handle Stopping", content.contains("is StressTestState.Stopping"))
        assertTrue("Must handle Error", content.contains("is StressTestState.Error"))
    }

    @Test
    fun `BluetoothStressTestActivity has formatCount method for large numbers`() {
        val content = File(SOURCE_DIR, "BluetoothStressTestActivity.kt").readText()
        assertTrue(
            "Must have formatCount method for human-readable counters",
            content.contains("formatCount")
        )
    }

    @Test
    fun `BluetoothStressTestActivity formatCount handles thousands`() {
        val content = File(SOURCE_DIR, "BluetoothStressTestActivity.kt").readText()
        assertTrue(
            "formatCount must handle K suffix for thousands",
            content.contains("1_000") && content.contains("K")
        )
    }

    @Test
    fun `BluetoothStressTestActivity formatCount handles millions`() {
        val content = File(SOURCE_DIR, "BluetoothStressTestActivity.kt").readText()
        assertTrue(
            "formatCount must handle M suffix for millions",
            content.contains("1_000_000") && content.contains("M")
        )
    }

    @Test
    fun `BluetoothStressTestActivity toggle calls startStressTest on check`() {
        val content = File(SOURCE_DIR, "BluetoothStressTestActivity.kt").readText()
        assertTrue(
            "Toggle must call viewModel.startStressTest() when checked",
            content.contains("viewModel.startStressTest()")
        )
    }

    @Test
    fun `BluetoothStressTestActivity toggle calls stopStressTest on uncheck`() {
        val content = File(SOURCE_DIR, "BluetoothStressTestActivity.kt").readText()
        assertTrue(
            "Toggle must call viewModel.stopStressTest() when unchecked",
            content.contains("viewModel.stopStressTest()")
        )
    }

    @Test
    fun `BluetoothStressTestActivity shows remaining time only during Running state`() {
        val content = File(SOURCE_DIR, "BluetoothStressTestActivity.kt").readText()
        assertTrue(
            "Remaining time must use View.VISIBLE in Running state",
            content.contains("View.VISIBLE")
        )
        assertTrue(
            "Remaining time must use View.GONE in non-Running states",
            content.contains("View.GONE")
        )
    }

    @Test
    fun `BluetoothStressTestActivity shows toast on error`() {
        val content = File(SOURCE_DIR, "BluetoothStressTestActivity.kt").readText()
        assertTrue(
            "Must show Toast on error state",
            content.contains("Toast.makeText") && content.contains("state.message")
        )
    }

    // ---- MainActivity navigation to stress test ----

    @Test
    fun `MainActivity has button to navigate to BluetoothStressTestActivity`() {
        val content = File(SOURCE_DIR, "MainActivity.kt").readText()
        assertTrue(
            "MainActivity must have a BluetoothStressTest button",
            content.contains("btnBluetoothStressTest")
        )
    }

    @Test
    fun `MainActivity launches BluetoothStressTestActivity via Intent`() {
        val content = File(SOURCE_DIR, "MainActivity.kt").readText()
        assertTrue(
            "MainActivity must create Intent for BluetoothStressTestActivity",
            content.contains("BluetoothStressTestActivity::class.java")
        )
    }
}
