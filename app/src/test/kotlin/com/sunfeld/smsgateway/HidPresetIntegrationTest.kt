package com.sunfeld.smsgateway

import org.junit.Assert.*
import org.junit.Test

class HidPresetIntegrationTest {

    @Test
    fun `preset created from DeviceProfile has valid profileId`() {
        val profile = DeviceProfiles.DEFAULT
        val preset = HidPreset(
            id = "test-1",
            name = "Test Preset",
            profileId = profile.id,
            customDeviceName = profile.sdpName,
            targetAddresses = listOf("AA:BB:CC:DD:EE:FF"),
            payload = "Hello\n",
            createdAt = System.currentTimeMillis()
        )
        assertNotNull(DeviceProfiles.findById(preset.profileId))
        assertEquals(profile, DeviceProfiles.findById(preset.profileId))
    }

    @Test
    fun `Gson round-trip of preset with all fields`() {
        val preset = HidPreset(
            id = "uuid-123",
            name = "Office KB",
            profileId = "logitech_k380",
            customDeviceName = "My Logitech",
            targetAddresses = listOf("11:22:33:44:55:66", "AA:BB:CC:DD:EE:FF"),
            payload = "Test payload\n",
            createdAt = 1711036800000L
        )
        val json = PresetRepository.serializePresets(listOf(preset))
        val result = PresetRepository.deserializePresets(json)
        assertEquals(1, result.size)
        assertEquals(preset, result[0])
    }

    @Test
    fun `loaded preset profileId maps to valid DeviceProfile`() {
        val preset = HidPreset(
            id = "uuid-456",
            name = "Apple Setup",
            profileId = "apple_magic_keyboard",
            customDeviceName = "Magic Keyboard",
            targetAddresses = emptyList(),
            payload = "",
            createdAt = 0L
        )
        val json = PresetRepository.serializePresets(listOf(preset))
        val loaded = PresetRepository.deserializePresets(json).first()
        val profile = DeviceProfiles.findById(loaded.profileId)
        assertNotNull(profile)
        assertEquals("Apple Magic Keyboard", profile!!.displayName)
        assertEquals("Apple Inc.", profile.sdpProvider)
    }

    @Test
    fun `all DeviceProfiles can be used as preset profileIds`() {
        DeviceProfiles.ALL.forEach { profile ->
            val preset = HidPreset(
                id = "test-${profile.id}",
                name = "${profile.displayName} Preset",
                profileId = profile.id,
                customDeviceName = profile.sdpName,
                targetAddresses = emptyList(),
                payload = "",
                createdAt = 0L
            )
            val json = PresetRepository.serializePresets(listOf(preset))
            val loaded = PresetRepository.deserializePresets(json).first()
            assertEquals(profile.id, loaded.profileId)
            assertNotNull(DeviceProfiles.findById(loaded.profileId))
        }
    }

    @Test
    fun `multiple presets with different profiles round-trip correctly`() {
        val presets = DeviceProfiles.ALL.take(5).mapIndexed { i, profile ->
            HidPreset(
                id = "multi-$i",
                name = "Preset $i",
                profileId = profile.id,
                customDeviceName = "Custom Name $i",
                targetAddresses = listOf("AA:BB:CC:DD:EE:0${i}"),
                payload = "Payload $i\n",
                createdAt = 1000L * i
            )
        }
        val json = PresetRepository.serializePresets(presets)
        val loaded = PresetRepository.deserializePresets(json)
        assertEquals(5, loaded.size)
        loaded.forEachIndexed { i, preset ->
            assertEquals(presets[i], preset)
        }
    }
}
