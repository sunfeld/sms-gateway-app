package com.sunfeld.smsgateway

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Unit tests verifying that network_security_config.xml correctly permits
 * cleartext HTTP traffic to 10.0.0.2 and that AndroidManifest.xml references it.
 */
class NetworkSecurityConfigTest {

    private val projectRoot = File(System.getProperty("user.dir") ?: ".").let { dir ->
        // Gradle runs tests from the module directory (app/), go up if needed
        if (dir.name == "app") dir.parentFile else dir
    }

    private val networkSecurityConfigFile =
        File(projectRoot, "app/src/main/res/xml/network_security_config.xml")

    private val manifestFile =
        File(projectRoot, "app/src/main/AndroidManifest.xml")

    // --- network_security_config.xml existence and structure ---

    @Test
    fun `network_security_config xml file exists`() {
        assertTrue(
            "network_security_config.xml should exist at ${networkSecurityConfigFile.absolutePath}",
            networkSecurityConfigFile.exists()
        )
    }

    @Test
    fun `network_security_config xml is valid XML`() {
        val doc = DocumentBuilderFactory.newInstance()
            .newDocumentBuilder()
            .parse(networkSecurityConfigFile)
        assertNotNull("Parsed document should not be null", doc)
        assertEquals(
            "Root element should be network-security-config",
            "network-security-config",
            doc.documentElement.tagName
        )
    }

    @Test
    fun `network_security_config has domain-config element`() {
        val doc = DocumentBuilderFactory.newInstance()
            .newDocumentBuilder()
            .parse(networkSecurityConfigFile)
        val domainConfigs = doc.getElementsByTagName("domain-config")
        assertTrue(
            "Should have at least one domain-config element",
            domainConfigs.length > 0
        )
    }

    @Test
    fun `domain-config permits cleartext traffic`() {
        val doc = DocumentBuilderFactory.newInstance()
            .newDocumentBuilder()
            .parse(networkSecurityConfigFile)
        val domainConfig = doc.getElementsByTagName("domain-config").item(0)
        val cleartextAttr = domainConfig.attributes.getNamedItem("cleartextTrafficPermitted")
        assertNotNull("cleartextTrafficPermitted attribute should exist", cleartextAttr)
        assertEquals(
            "cleartextTrafficPermitted should be true",
            "true",
            cleartextAttr.nodeValue
        )
    }

    @Test
    fun `domain-config targets 10_0_0_2`() {
        val doc = DocumentBuilderFactory.newInstance()
            .newDocumentBuilder()
            .parse(networkSecurityConfigFile)
        val domains = doc.getElementsByTagName("domain")
        assertTrue("Should have at least one domain element", domains.length > 0)

        val domainValues = (0 until domains.length).map { domains.item(it).textContent.trim() }
        assertTrue(
            "Domain list should contain 10.0.0.2, but found: $domainValues",
            domainValues.contains("10.0.0.2")
        )
    }

    @Test
    fun `domain does not include subdomains`() {
        val doc = DocumentBuilderFactory.newInstance()
            .newDocumentBuilder()
            .parse(networkSecurityConfigFile)
        val domain = doc.getElementsByTagName("domain").item(0)
        val includeSubdomains = domain.attributes.getNamedItem("includeSubdomains")
        assertNotNull("includeSubdomains attribute should exist", includeSubdomains)
        assertEquals(
            "includeSubdomains should be false for IP address",
            "false",
            includeSubdomains.nodeValue
        )
    }

    // --- AndroidManifest.xml references the config ---

    @Test
    fun `AndroidManifest references network_security_config`() {
        assertTrue("AndroidManifest.xml should exist", manifestFile.exists())
        val manifestContent = manifestFile.readText()
        assertTrue(
            "AndroidManifest should reference network_security_config",
            manifestContent.contains("@xml/network_security_config")
        )
    }

    @Test
    fun `AndroidManifest uses networkSecurityConfig attribute`() {
        val manifestContent = manifestFile.readText()
        assertTrue(
            "AndroidManifest should have android:networkSecurityConfig attribute",
            manifestContent.contains("android:networkSecurityConfig")
        )
    }

    @Test
    fun `AndroidManifest has INTERNET permission`() {
        val manifestContent = manifestFile.readText()
        assertTrue(
            "AndroidManifest should declare INTERNET permission for network access",
            manifestContent.contains("android.permission.INTERNET")
        )
    }
}
