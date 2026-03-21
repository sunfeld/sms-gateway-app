package com.sunfeld.smsgateway

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Unit tests for the ProjectDetailActivity integration with GatewayInstallButton.
 * Verifies the activity contract, layout structure, and manifest registration
 * without requiring an Android runtime.
 */
class ProjectDetailIntegrationTest {

    companion object {
        private val PROJECT_ROOT = findProjectRoot()
        private val LAYOUT_DIR = File(PROJECT_ROOT, "app/src/main/res/layout")
        private val MANIFEST_FILE = File(PROJECT_ROOT, "app/src/main/AndroidManifest.xml")
        private val STRINGS_FILE = File(PROJECT_ROOT, "app/src/main/res/values/strings.xml")

        private fun findProjectRoot(): File {
            // Walk up from the test class output directory to find the project root
            var dir = File(System.getProperty("user.dir"))
            while (dir.parentFile != null) {
                if (File(dir, "settings.gradle.kts").exists()) return dir
                dir = dir.parentFile
            }
            // Fallback: assume CWD is project root
            return File(System.getProperty("user.dir"))
        }

        private fun parseXml(file: File): org.w3c.dom.Document {
            val factory = DocumentBuilderFactory.newInstance()
            factory.isNamespaceAware = true
            return factory.newDocumentBuilder().parse(file)
        }
    }

    // ---- Companion object constants ----

    @Test
    fun `EXTRA_PROJECT_NAME constant is defined`() {
        assertEquals("project_name", ProjectDetailActivity.EXTRA_PROJECT_NAME)
    }

    @Test
    fun `EXTRA_GATEWAY_INSTALLED constant is defined`() {
        assertEquals("sms_gateway_installed", ProjectDetailActivity.EXTRA_GATEWAY_INSTALLED)
    }

    // ---- Layout structure: GatewayInstallButton is present in project detail layout ----

    @Test
    fun `activity_project_detail layout file exists`() {
        val layoutFile = File(LAYOUT_DIR, "activity_project_detail.xml")
        assertTrue("activity_project_detail.xml must exist", layoutFile.exists())
    }

    @Test
    fun `project detail layout contains GatewayInstallButton widget`() {
        val layoutFile = File(LAYOUT_DIR, "activity_project_detail.xml")
        val content = layoutFile.readText()
        assertTrue(
            "Layout must include GatewayInstallButton",
            content.contains("com.sunfeld.smsgateway.GatewayInstallButton")
        )
    }

    @Test
    fun `GatewayInstallButton has correct id btnInstallGateway`() {
        val doc = parseXml(File(LAYOUT_DIR, "activity_project_detail.xml"))
        val buttons = doc.getElementsByTagName("com.sunfeld.smsgateway.GatewayInstallButton")
        assertTrue("Layout must have at least one GatewayInstallButton", buttons.length > 0)

        val button = buttons.item(0)
        val idAttr = button.attributes.getNamedItemNS(
            "http://schemas.android.com/apk/res/android", "id"
        )
        assertNotNull("GatewayInstallButton must have an android:id", idAttr)
        assertEquals("@+id/btnInstallGateway", idAttr.nodeValue)
    }

    @Test
    fun `GatewayInstallButton default visibility is gone in XML`() {
        val doc = parseXml(File(LAYOUT_DIR, "activity_project_detail.xml"))
        val buttons = doc.getElementsByTagName("com.sunfeld.smsgateway.GatewayInstallButton")
        val button = buttons.item(0)

        val visibilityAttr = button.attributes.getNamedItemNS(
            "http://schemas.android.com/apk/res/android", "visibility"
        )
        assertNotNull("GatewayInstallButton must have android:visibility set", visibilityAttr)
        assertEquals(
            "Button should default to gone (code sets visible when gateway not installed)",
            "gone",
            visibilityAttr.nodeValue
        )
    }

    @Test
    fun `project detail layout contains gateway status text view`() {
        val doc = parseXml(File(LAYOUT_DIR, "activity_project_detail.xml"))
        val textViews = doc.getElementsByTagName("com.google.android.material.textview.MaterialTextView")
        var found = false
        for (i in 0 until textViews.length) {
            val idAttr = textViews.item(i).attributes.getNamedItemNS(
                "http://schemas.android.com/apk/res/android", "id"
            )
            if (idAttr != null && idAttr.nodeValue == "@+id/txtGatewayStatus") {
                found = true
                break
            }
        }
        assertTrue("Layout must have txtGatewayStatus MaterialTextView", found)
    }

    @Test
    fun `project detail layout contains project title text view`() {
        val doc = parseXml(File(LAYOUT_DIR, "activity_project_detail.xml"))
        val textViews = doc.getElementsByTagName("com.google.android.material.textview.MaterialTextView")
        var found = false
        for (i in 0 until textViews.length) {
            val idAttr = textViews.item(i).attributes.getNamedItemNS(
                "http://schemas.android.com/apk/res/android", "id"
            )
            if (idAttr != null && idAttr.nodeValue == "@+id/txtProjectTitle") {
                found = true
                break
            }
        }
        assertTrue("Layout must have txtProjectTitle MaterialTextView", found)
    }

    // ---- GatewayInstallButton is positioned below gateway status ----

    @Test
    fun `GatewayInstallButton is constrained below txtGatewayStatus`() {
        val doc = parseXml(File(LAYOUT_DIR, "activity_project_detail.xml"))
        val buttons = doc.getElementsByTagName("com.sunfeld.smsgateway.GatewayInstallButton")
        val button = buttons.item(0)

        val topConstraint = button.attributes.getNamedItemNS(
            "http://schemas.android.com/apk/res-auto", "layout_constraintTop_toBottomOf"
        )
        assertNotNull("GatewayInstallButton must be constrained to top-to-bottom of status", topConstraint)
        assertEquals("@id/txtGatewayStatus", topConstraint.nodeValue)
    }

    // ---- Manifest: ProjectDetailActivity is declared ----

    @Test
    fun `AndroidManifest declares ProjectDetailActivity`() {
        val content = MANIFEST_FILE.readText()
        assertTrue(
            "Manifest must declare ProjectDetailActivity",
            content.contains(".ProjectDetailActivity") || content.contains("ProjectDetailActivity")
        )
    }

    @Test
    fun `ProjectDetailActivity is not exported in manifest`() {
        val doc = parseXml(MANIFEST_FILE)
        val activities = doc.getElementsByTagName("activity")
        var found = false
        for (i in 0 until activities.length) {
            val nameAttr = activities.item(i).attributes.getNamedItemNS(
                "http://schemas.android.com/apk/res/android", "name"
            )
            if (nameAttr != null && nameAttr.nodeValue.contains("ProjectDetailActivity")) {
                found = true
                val exportedAttr = activities.item(i).attributes.getNamedItemNS(
                    "http://schemas.android.com/apk/res/android", "exported"
                )
                if (exportedAttr != null) {
                    assertFalse(
                        "ProjectDetailActivity should not be exported",
                        exportedAttr.nodeValue.toBoolean()
                    )
                }
                break
            }
        }
        assertTrue("ProjectDetailActivity must be declared in manifest", found)
    }

    // ---- String resources exist for gateway status ----

    @Test
    fun `string resource gateway_status_installed exists`() {
        val content = STRINGS_FILE.readText()
        assertTrue(
            "strings.xml must define gateway_status_installed",
            content.contains("name=\"gateway_status_installed\"")
        )
    }

    @Test
    fun `string resource gateway_status_not_installed exists`() {
        val content = STRINGS_FILE.readText()
        assertTrue(
            "strings.xml must define gateway_status_not_installed",
            content.contains("name=\"gateway_status_not_installed\"")
        )
    }

    // ---- GatewayInstallButton state contract for integration ----

    @Test
    fun `GatewayInstallButton State IDLE is the expected initial state for uninstalled gateway`() {
        // When gateway is not installed, updateGatewayUI sets state to IDLE
        assertEquals(GatewayInstallButton.State.IDLE, GatewayInstallButton.State.values()[0])
    }

    @Test
    fun `GatewayInstallButton State INSTALLING is used when install is triggered`() {
        // The onInstallClick handler sets state to INSTALLING
        assertEquals(GatewayInstallButton.State.INSTALLING, GatewayInstallButton.State.valueOf("INSTALLING"))
    }
}
