package com.sunfeld.smsgateway

import org.junit.Assert.*
import org.junit.Test

class PresetRepositoryTest {

    @Test
    fun `HidPreset data class equality works`() {
        val a = HidPreset("id1", "My Preset", "apple_magic_keyboard", "Magic Keyboard", listOf("AA:BB:CC:DD:EE:FF"), "Hello\n", 1000L)
        val b = HidPreset("id1", "My Preset", "apple_magic_keyboard", "Magic Keyboard", listOf("AA:BB:CC:DD:EE:FF"), "Hello\n", 1000L)
        assertEquals(a, b)
    }

    @Test
    fun `HidPreset data class inequality works`() {
        val a = HidPreset("id1", "Preset A", "apple_magic_keyboard", "KB", emptyList(), "", 1000L)
        val b = HidPreset("id2", "Preset B", "logitech_k380", "KB", emptyList(), "", 2000L)
        assertNotEquals(a, b)
    }

    @Test
    fun `Gson round-trip preserves all fields`() {
        val original = listOf(
            HidPreset(
                id = "test-uuid-1",
                name = "Office Setup",
                profileId = "apple_magic_keyboard",
                customDeviceName = "My Magic KB",
                targetAddresses = listOf("AA:BB:CC:DD:EE:FF", "11:22:33:44:55:66"),
                payload = "Hello from keyboard!\n",
                createdAt = 1711036800000L
            ),
            HidPreset(
                id = "test-uuid-2",
                name = "Gaming",
                profileId = "razer_blackwidow_v3",
                customDeviceName = "Razer BW",
                targetAddresses = emptyList(),
                payload = "GG\n",
                createdAt = 1711036900000L
            )
        )

        val json = PresetRepository.serializePresets(original)
        val deserialized = PresetRepository.deserializePresets(json)

        assertEquals(original.size, deserialized.size)
        assertEquals(original[0], deserialized[0])
        assertEquals(original[1], deserialized[1])
        assertEquals("AA:BB:CC:DD:EE:FF", deserialized[0].targetAddresses[0])
        assertEquals("11:22:33:44:55:66", deserialized[0].targetAddresses[1])
    }

    @Test
    fun `empty list round-trips correctly`() {
        val json = PresetRepository.serializePresets(emptyList())
        val result = PresetRepository.deserializePresets(json)
        assertTrue(result.isEmpty())
    }

    @Test
    fun `preset with empty target addresses round-trips`() {
        val preset = HidPreset("id1", "Empty", "logitech_k380", "K380", emptyList(), "", 0L)
        val json = PresetRepository.serializePresets(listOf(preset))
        val result = PresetRepository.deserializePresets(json)
        assertEquals(1, result.size)
        assertTrue(result[0].targetAddresses.isEmpty())
    }

    @Test
    fun `profileId maps to valid DeviceProfile`() {
        val preset = HidPreset("id1", "Test", "apple_magic_keyboard", "KB", emptyList(), "", 0L)
        val profile = DeviceProfiles.findById(preset.profileId)
        assertNotNull(profile)
        assertEquals("Apple Magic Keyboard", profile!!.displayName)
    }
}
