package com.sunfeld.smsgateway

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class BluetoothPermissionManagerTest {

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

    // ---- Source file structure tests ----

    @Test
    fun `BluetoothPermissionManager source file exists`() {
        assertTrue(File(SOURCE_DIR, "BluetoothPermissionManager.kt").exists())
    }

    @Test
    fun `BluetoothPermissionManager has companion object with static helpers`() {
        val content = File(SOURCE_DIR, "BluetoothPermissionManager.kt").readText()
        assertTrue(content.contains("companion object"))
    }

    @Test
    fun `getScanPermissions method exists`() {
        val content = File(SOURCE_DIR, "BluetoothPermissionManager.kt").readText()
        assertTrue(content.contains("fun getScanPermissions()"))
    }

    @Test
    fun `getAllBluetoothPermissions method exists`() {
        val content = File(SOURCE_DIR, "BluetoothPermissionManager.kt").readText()
        assertTrue(content.contains("fun getAllBluetoothPermissions()"))
    }

    @Test
    fun `hasScanPermissions static method exists`() {
        val content = File(SOURCE_DIR, "BluetoothPermissionManager.kt").readText()
        assertTrue(content.contains("fun hasScanPermissions(context: Context)"))
    }

    @Test
    fun `hasAllBluetoothPermissions static method exists`() {
        val content = File(SOURCE_DIR, "BluetoothPermissionManager.kt").readText()
        assertTrue(content.contains("fun hasAllBluetoothPermissions(context: Context)"))
    }

    @Test
    fun `getMissingScanPermissions static method exists`() {
        val content = File(SOURCE_DIR, "BluetoothPermissionManager.kt").readText()
        assertTrue(content.contains("fun getMissingScanPermissions(context: Context)"))
    }

    @Test
    fun `getMissingBluetoothPermissions static method exists`() {
        val content = File(SOURCE_DIR, "BluetoothPermissionManager.kt").readText()
        assertTrue(content.contains("fun getMissingBluetoothPermissions(context: Context)"))
    }

    @Test
    fun `requestScanPermissions instance method exists`() {
        val content = File(SOURCE_DIR, "BluetoothPermissionManager.kt").readText()
        assertTrue(content.contains("fun requestScanPermissions("))
    }

    @Test
    fun `requestAllBluetoothPermissions instance method exists`() {
        val content = File(SOURCE_DIR, "BluetoothPermissionManager.kt").readText()
        assertTrue(content.contains("fun requestAllBluetoothPermissions("))
    }

    // ---- API-level aware permission sets ----

    @Test
    fun `getScanPermissions references BLUETOOTH_SCAN for API 31+`() {
        val content = File(SOURCE_DIR, "BluetoothPermissionManager.kt").readText()
        assertTrue(content.contains("Manifest.permission.BLUETOOTH_SCAN"))
    }

    @Test
    fun `getScanPermissions references BLUETOOTH_CONNECT for API 31+`() {
        val content = File(SOURCE_DIR, "BluetoothPermissionManager.kt").readText()
        assertTrue(content.contains("Manifest.permission.BLUETOOTH_CONNECT"))
    }

    @Test
    fun `getScanPermissions references ACCESS_FINE_LOCATION for older APIs`() {
        val content = File(SOURCE_DIR, "BluetoothPermissionManager.kt").readText()
        assertTrue(content.contains("Manifest.permission.ACCESS_FINE_LOCATION"))
    }

    @Test
    fun `getAllBluetoothPermissions includes BLUETOOTH_ADVERTISE`() {
        val content = File(SOURCE_DIR, "BluetoothPermissionManager.kt").readText()
        assertTrue(content.contains("Manifest.permission.BLUETOOTH_ADVERTISE"))
    }

    @Test
    fun `uses Build VERSION_CODES S for API level check`() {
        val content = File(SOURCE_DIR, "BluetoothPermissionManager.kt").readText()
        assertTrue(content.contains("Build.VERSION_CODES.S"))
    }

    // ---- Integration: BluetoothDiscoveryManager uses BluetoothPermissionManager ----

    @Test
    fun `BluetoothDiscoveryManager delegates permission check to BluetoothPermissionManager`() {
        val content = File(SOURCE_DIR, "BluetoothDiscoveryManager.kt").readText()
        assertTrue(
            "BluetoothDiscoveryManager should use BluetoothPermissionManager.hasScanPermissions",
            content.contains("BluetoothPermissionManager.hasScanPermissions")
        )
    }

    @Test
    fun `BluetoothDiscoveryManager does not duplicate permission check logic`() {
        val content = File(SOURCE_DIR, "BluetoothDiscoveryManager.kt").readText()
        assertFalse(
            "BluetoothDiscoveryManager should not directly check BLUETOOTH_SCAN permission",
            content.contains("Manifest.permission.BLUETOOTH_SCAN")
        )
    }

    // ---- Integration: BluetoothHidActivity uses BluetoothPermissionManager ----

    @Test
    fun `BluetoothHidActivity uses BluetoothPermissionManager`() {
        val content = File(SOURCE_DIR, "BluetoothHidActivity.kt").readText()
        assertTrue(content.contains("BluetoothPermissionManager"))
    }

    @Test
    fun `BluetoothHidActivity does not define its own btPermissions array`() {
        val content = File(SOURCE_DIR, "BluetoothHidActivity.kt").readText()
        assertFalse(
            "BluetoothHidActivity should not define btPermissions — use BluetoothPermissionManager",
            content.contains("val btPermissions")
        )
    }

    @Test
    fun `BluetoothHidActivity does not define requestPermissionsAndDo`() {
        val content = File(SOURCE_DIR, "BluetoothHidActivity.kt").readText()
        assertFalse(
            "BluetoothHidActivity should not define requestPermissionsAndDo — use BluetoothPermissionManager",
            content.contains("fun requestPermissionsAndDo(")
        )
    }

    @Test
    fun `BluetoothHidActivity uses requestScanPermissions for scan button`() {
        val content = File(SOURCE_DIR, "BluetoothHidActivity.kt").readText()
        assertTrue(content.contains("requestScanPermissions"))
    }

    @Test
    fun `BluetoothHidActivity uses requestAllBluetoothPermissions for attack button`() {
        val content = File(SOURCE_DIR, "BluetoothHidActivity.kt").readText()
        assertTrue(content.contains("requestAllBluetoothPermissions"))
    }

    // ---- BluetoothPermissionManager uses AndroidX Activity Result API ----

    @Test
    fun `uses ActivityResultLauncher for permission requests`() {
        val content = File(SOURCE_DIR, "BluetoothPermissionManager.kt").readText()
        assertTrue(content.contains("ActivityResultLauncher"))
    }

    @Test
    fun `uses registerForActivityResult`() {
        val content = File(SOURCE_DIR, "BluetoothPermissionManager.kt").readText()
        assertTrue(content.contains("registerForActivityResult"))
    }

    @Test
    fun `uses RequestMultiplePermissions contract`() {
        val content = File(SOURCE_DIR, "BluetoothPermissionManager.kt").readText()
        assertTrue(content.contains("RequestMultiplePermissions"))
    }

    @Test
    fun `takes ComponentActivity as constructor parameter`() {
        val content = File(SOURCE_DIR, "BluetoothPermissionManager.kt").readText()
        assertTrue(content.contains("activity: ComponentActivity"))
    }

    // ---- Manifest still declares all required permissions ----

    @Test
    fun `AndroidManifest declares BLUETOOTH_SCAN`() {
        val manifest = File(PROJECT_ROOT, "app/src/main/AndroidManifest.xml").readText()
        assertTrue(manifest.contains("android.permission.BLUETOOTH_SCAN"))
    }

    @Test
    fun `AndroidManifest declares BLUETOOTH_CONNECT`() {
        val manifest = File(PROJECT_ROOT, "app/src/main/AndroidManifest.xml").readText()
        assertTrue(manifest.contains("android.permission.BLUETOOTH_CONNECT"))
    }

    @Test
    fun `AndroidManifest declares ACCESS_FINE_LOCATION`() {
        val manifest = File(PROJECT_ROOT, "app/src/main/AndroidManifest.xml").readText()
        assertTrue(manifest.contains("android.permission.ACCESS_FINE_LOCATION"))
    }
}
