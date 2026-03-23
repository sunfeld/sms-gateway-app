package com.sunfeld.smsgateway

import org.junit.Assert.*
import org.junit.Test

class DeviceProfileTest {

    @Test
    fun `ALL contains exactly 15 profiles`() {
        assertEquals(15, DeviceProfiles.ALL.size)
    }

    @Test
    fun `all IDs are unique`() {
        val ids = DeviceProfiles.ALL.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun `all display names are non-blank`() {
        DeviceProfiles.ALL.forEach { profile ->
            assertTrue("Display name should not be blank for ${profile.id}", profile.displayName.isNotBlank())
        }
    }

    @Test
    fun `all SDP names are non-blank`() {
        DeviceProfiles.ALL.forEach { profile ->
            assertTrue("SDP name should not be blank for ${profile.id}", profile.sdpName.isNotBlank())
        }
    }

    @Test
    fun `DEFAULT is in the ALL list`() {
        assertTrue(DeviceProfiles.ALL.contains(DeviceProfiles.DEFAULT))
    }

    @Test
    fun `all OUI prefixes match XX colon XX colon XX format`() {
        val ouiPattern = Regex("^[0-9A-F]{2}:[0-9A-F]{2}:[0-9A-F]{2}$")
        DeviceProfiles.ALL.forEach { profile ->
            assertTrue(
                "OUI prefix '${profile.ouiPrefix}' for ${profile.id} should match XX:XX:XX",
                ouiPattern.matches(profile.ouiPrefix)
            )
        }
    }

    @Test
    fun `findById returns correct profile`() {
        val profile = DeviceProfiles.findById("apple_magic_keyboard")
        assertNotNull(profile)
        assertEquals("Apple Magic Keyboard", profile!!.displayName)
    }

    @Test
    fun `findById returns null for unknown ID`() {
        assertNull(DeviceProfiles.findById("nonexistent"))
    }

    @Test
    fun `all providers are non-blank`() {
        DeviceProfiles.ALL.forEach { profile ->
            assertTrue("Provider should not be blank for ${profile.id}", profile.sdpProvider.isNotBlank())
        }
    }
}
