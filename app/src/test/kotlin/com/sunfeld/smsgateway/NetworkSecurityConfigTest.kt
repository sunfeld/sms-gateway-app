package com.sunfeld.smsgateway

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Unit tests verifying that network_security_config.xml disables cleartext
 * traffic (all communication via HTTPS to sms.sunfeld.nl) and that
 * AndroidManifest.xml references the config.
 */
class NetworkSecurityConfigTest {

    private val projectRoot = File(System.getProperty("user.dir") ?: ".").let { dir ->
        if (dir.name == "app") dir.parentFile else dir
    }

    private val networkSecurityConfigFile =
        File(projectRoot, "app/src/main/res/xml/network_security_config.xml")

    private val manifestFile =
        File(projectRoot, "app/src/main/AndroidManifest.xml")

    @Test
    fun `network_security_config xml file exists`() {
        assertTrue(networkSecurityConfigFile.exists())
    }

    @Test
    fun `network_security_config xml is valid XML`() {
        val doc = DocumentBuilderFactory.newInstance()
            .newDocumentBuilder()
            .parse(networkSecurityConfigFile)
        assertNotNull(doc)
        assertEquals("network-security-config", doc.documentElement.tagName)
    }

    @Test
    fun `network_security_config has base-config disabling cleartext`() {
        val doc = DocumentBuilderFactory.newInstance()
            .newDocumentBuilder()
            .parse(networkSecurityConfigFile)
        val baseConfigs = doc.getElementsByTagName("base-config")
        assertTrue("Should have base-config element", baseConfigs.length > 0)
        val cleartextAttr = baseConfigs.item(0).attributes.getNamedItem("cleartextTrafficPermitted")
        assertNotNull(cleartextAttr)
        assertEquals("false", cleartextAttr.nodeValue)
    }

    @Test
    fun `no cleartext domain-config for 10_0_0_2`() {
        val content = networkSecurityConfigFile.readText()
        assertTrue(
            "Should NOT contain 10.0.0.2 cleartext domain anymore",
            !content.contains("10.0.0.2")
        )
    }

    @Test
    fun `AndroidManifest references network_security_config`() {
        val content = manifestFile.readText()
        assertTrue(content.contains("@xml/network_security_config"))
    }

    @Test
    fun `AndroidManifest has INTERNET permission`() {
        val content = manifestFile.readText()
        assertTrue(content.contains("android.permission.INTERNET"))
    }
}
