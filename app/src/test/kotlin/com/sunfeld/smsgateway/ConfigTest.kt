package com.sunfeld.smsgateway

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.URL

/**
 * Unit tests for the Config object that centralizes API targeting.
 * Verifies BASE_URL is correctly defined for cleartext HTTP to 10.0.0.2:8080.
 */
class ConfigTest {

    @Test
    fun `BASE_URL is not null`() {
        assertNotNull("BASE_URL should not be null", Config.BASE_URL)
    }

    @Test
    fun `BASE_URL is not blank`() {
        assertTrue("BASE_URL should not be blank", Config.BASE_URL.isNotBlank())
    }

    @Test
    fun `BASE_URL uses http scheme for cleartext`() {
        assertTrue(
            "BASE_URL should use http:// (cleartext) scheme, got: ${Config.BASE_URL}",
            Config.BASE_URL.startsWith("http://")
        )
    }

    @Test
    fun `BASE_URL targets 10_0_0_2 host`() {
        val url = URL(Config.BASE_URL)
        assertEquals(
            "BASE_URL host should be 10.0.0.2",
            "10.0.0.2",
            url.host
        )
    }

    @Test
    fun `BASE_URL targets port 8080`() {
        val url = URL(Config.BASE_URL)
        assertEquals(
            "BASE_URL port should be 8080",
            8080,
            url.port
        )
    }

    @Test
    fun `BASE_URL is a valid URL`() {
        val url = URL(Config.BASE_URL)
        assertNotNull("BASE_URL should parse as a valid URL", url)
        assertEquals("http", url.protocol)
    }

    @Test
    fun `BASE_URL has no trailing slash`() {
        assertTrue(
            "BASE_URL should not end with / to avoid double-slash issues in path construction",
            !Config.BASE_URL.endsWith("/")
        )
    }

    @Test
    fun `BASE_URL equals expected value`() {
        assertEquals(
            "BASE_URL should be http://10.0.0.2:8080",
            "http://10.0.0.2:8080",
            Config.BASE_URL
        )
    }

    @Test
    fun `Config is a singleton object`() {
        val config1 = Config
        val config2 = Config
        assertTrue(
            "Config should be a singleton (same reference)",
            config1 === config2
        )
    }

    @Test
    fun `BASE_URL is a compile-time constant`() {
        // const val fields are inlined at compile time; verify via reflection
        // that the field exists and has the expected value
        val field = Config::class.java.getDeclaredField("BASE_URL")
        assertNotNull("BASE_URL field should exist on Config", field)
        assertEquals(
            "BASE_URL should be accessible as a static field",
            "http://10.0.0.2:8080",
            field.get(null) as String
        )
    }
}
