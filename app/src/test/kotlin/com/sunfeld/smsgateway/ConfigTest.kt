package com.sunfeld.smsgateway

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.URL

/**
 * Unit tests for the Config object.
 * Verifies relay URLs point to sms.sunfeld.nl with HTTPS/WSS.
 */
class ConfigTest {

    @Test
    fun `RELAY_BASE_URL is not blank`() {
        assertTrue(Config.RELAY_BASE_URL.isNotBlank())
    }

    @Test
    fun `RELAY_BASE_URL uses https scheme`() {
        assertTrue(Config.RELAY_BASE_URL.startsWith("https://"))
    }

    @Test
    fun `RELAY_BASE_URL targets sms_sunfeld_nl`() {
        val url = URL(Config.RELAY_BASE_URL)
        assertEquals("sms.sunfeld.nl", url.host)
    }

    @Test
    fun `RELAY_WS_URL uses wss scheme`() {
        assertTrue(Config.RELAY_WS_URL.startsWith("wss://"))
    }

    @Test
    fun `RELAY_WS_URL targets sms_sunfeld_nl`() {
        assertTrue(Config.RELAY_WS_URL.contains("sms.sunfeld.nl"))
    }

    @Test
    fun `RELAY_WS_URL has ws path`() {
        assertTrue(Config.RELAY_WS_URL.endsWith("/ws"))
    }

    @Test
    fun `RELAY_BASE_URL has no trailing slash`() {
        assertTrue(!Config.RELAY_BASE_URL.endsWith("/"))
    }

    @Test
    fun `Config is a singleton object`() {
        assertTrue(Config === Config)
    }

    @Suppress("DEPRECATION")
    @Test
    fun `deprecated BASE_URL equals RELAY_BASE_URL`() {
        assertEquals(Config.RELAY_BASE_URL, Config.BASE_URL)
    }
}
