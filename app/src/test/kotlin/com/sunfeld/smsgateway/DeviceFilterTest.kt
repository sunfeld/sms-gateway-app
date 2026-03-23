package com.sunfeld.smsgateway

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class DeviceFilterTest {

    private lateinit var filter: DeviceFilter

    @Before
    fun setUp() {
        filter = DeviceFilter()
    }

    // ---- Basic add behaviour ----

    @Test
    fun `new device returns true`() {
        assertTrue(filter.addOrUpdate("AA:BB:CC:DD:EE:01", "Device1", -50))
    }

    @Test
    fun `adding a device increases size`() {
        filter.addOrUpdate("AA:BB:CC:DD:EE:01", "Device1", -50)
        assertEquals(1, filter.size)
    }

    @Test
    fun `multiple unique devices all tracked`() {
        filter.addOrUpdate("AA:BB:CC:DD:EE:01", "Device1", -50)
        filter.addOrUpdate("AA:BB:CC:DD:EE:02", "Device2", -60)
        filter.addOrUpdate("AA:BB:CC:DD:EE:03", "Device3", -70)
        assertEquals(3, filter.size)
    }

    // ---- Duplicate prevention ----

    @Test
    fun `same MAC added twice does not create duplicate`() {
        filter.addOrUpdate("AA:BB:CC:DD:EE:01", "Device1", -50)
        filter.addOrUpdate("AA:BB:CC:DD:EE:01", "Device1", -55)
        assertEquals(1, filter.size)
    }

    @Test
    fun `duplicate MAC returns false`() {
        filter.addOrUpdate("AA:BB:CC:DD:EE:01", "Device1", -50)
        assertFalse(filter.addOrUpdate("AA:BB:CC:DD:EE:01", "Device1", -55))
    }

    @Test
    fun `ten RSSI updates for same MAC still one entry`() {
        repeat(10) { i ->
            filter.addOrUpdate("AA:BB:CC:DD:EE:01", "Device1", (-40 - i).toShort())
        }
        assertEquals(1, filter.size)
    }

    @Test
    fun `interleaved updates for two devices keep exactly two entries`() {
        repeat(5) {
            filter.addOrUpdate("AA:BB:CC:DD:EE:01", "Dev1", (-40).toShort())
            filter.addOrUpdate("AA:BB:CC:DD:EE:02", "Dev2", (-60).toShort())
        }
        assertEquals(2, filter.size)
    }

    // ---- RSSI update behaviour ----

    @Test
    fun `RSSI is updated on re-discovery`() {
        filter.addOrUpdate("AA:BB:CC:DD:EE:01", "Device1", -80)
        filter.addOrUpdate("AA:BB:CC:DD:EE:01", "Device1", -45)
        assertEquals((-45).toShort(), filter.get("AA:BB:CC:DD:EE:01")!!.rssi)
    }

    @Test
    fun `RSSI update to weaker signal is recorded`() {
        filter.addOrUpdate("AA:BB:CC:DD:EE:01", "Device1", -30)
        filter.addOrUpdate("AA:BB:CC:DD:EE:01", "Device1", -90)
        assertEquals((-90).toShort(), filter.get("AA:BB:CC:DD:EE:01")!!.rssi)
    }

    @Test
    fun `RSSI boundary values are stored correctly`() {
        filter.addOrUpdate("AA:BB:CC:DD:EE:01", "Near", Short.MAX_VALUE)
        assertEquals(Short.MAX_VALUE, filter.get("AA:BB:CC:DD:EE:01")!!.rssi)

        filter.addOrUpdate("AA:BB:CC:DD:EE:02", "Far", Short.MIN_VALUE)
        assertEquals(Short.MIN_VALUE, filter.get("AA:BB:CC:DD:EE:02")!!.rssi)
    }

    // ---- Name handling ----

    @Test
    fun `name is updated on re-discovery when non-null`() {
        filter.addOrUpdate("AA:BB:CC:DD:EE:01", "OldName", -50)
        filter.addOrUpdate("AA:BB:CC:DD:EE:01", "NewName", -50)
        assertEquals("NewName", filter.get("AA:BB:CC:DD:EE:01")!!.name)
    }

    @Test
    fun `null name does not overwrite existing name`() {
        filter.addOrUpdate("AA:BB:CC:DD:EE:01", "KnownName", -50)
        filter.addOrUpdate("AA:BB:CC:DD:EE:01", null, -45)
        assertEquals("KnownName", filter.get("AA:BB:CC:DD:EE:01")!!.name)
    }

    @Test
    fun `null name accepted for first discovery`() {
        filter.addOrUpdate("AA:BB:CC:DD:EE:01", null, -50)
        assertNull(filter.get("AA:BB:CC:DD:EE:01")!!.name)
    }

    // ---- Lookup methods ----

    @Test
    fun `contains returns true for tracked address`() {
        filter.addOrUpdate("AA:BB:CC:DD:EE:01", "Dev", -50)
        assertTrue(filter.contains("AA:BB:CC:DD:EE:01"))
    }

    @Test
    fun `contains returns false for unknown address`() {
        assertFalse(filter.contains("FF:FF:FF:FF:FF:FF"))
    }

    @Test
    fun `get returns null for unknown address`() {
        assertNull(filter.get("FF:FF:FF:FF:FF:FF"))
    }

    @Test
    fun `get returns entry with correct fields`() {
        filter.addOrUpdate("AA:BB:CC:DD:EE:01", "TestDev", -72)
        val entry = filter.get("AA:BB:CC:DD:EE:01")!!
        assertEquals("AA:BB:CC:DD:EE:01", entry.address)
        assertEquals("TestDev", entry.name)
        assertEquals((-72).toShort(), entry.rssi)
    }

    // ---- getAll and ordering ----

    @Test
    fun `getAll returns devices in discovery order`() {
        filter.addOrUpdate("AA:BB:CC:DD:EE:03", "Third", -70)
        filter.addOrUpdate("AA:BB:CC:DD:EE:01", "First", -50)
        filter.addOrUpdate("AA:BB:CC:DD:EE:02", "Second", -60)
        val addresses = filter.getAll().map { it.address }
        assertEquals(
            listOf("AA:BB:CC:DD:EE:03", "AA:BB:CC:DD:EE:01", "AA:BB:CC:DD:EE:02"),
            addresses
        )
    }

    @Test
    fun `getAll after RSSI update preserves original order`() {
        filter.addOrUpdate("AA:BB:CC:DD:EE:01", "A", -50)
        filter.addOrUpdate("AA:BB:CC:DD:EE:02", "B", -60)
        // Update first device RSSI — should NOT move it
        filter.addOrUpdate("AA:BB:CC:DD:EE:01", "A", -30)
        val addresses = filter.getAll().map { it.address }
        assertEquals(listOf("AA:BB:CC:DD:EE:01", "AA:BB:CC:DD:EE:02"), addresses)
    }

    @Test
    fun `getAll returns empty list initially`() {
        assertTrue(filter.getAll().isEmpty())
    }

    // ---- Clear ----

    @Test
    fun `clear removes all devices`() {
        filter.addOrUpdate("AA:BB:CC:DD:EE:01", "Dev1", -50)
        filter.addOrUpdate("AA:BB:CC:DD:EE:02", "Dev2", -60)
        filter.clear()
        assertEquals(0, filter.size)
        assertTrue(filter.getAll().isEmpty())
    }

    @Test
    fun `devices can be added after clear`() {
        filter.addOrUpdate("AA:BB:CC:DD:EE:01", "Dev1", -50)
        filter.clear()
        assertTrue(filter.addOrUpdate("AA:BB:CC:DD:EE:01", "Dev1", -50))
        assertEquals(1, filter.size)
    }

    // ---- Timestamp ----

    @Test
    fun `timestamp is set on first add`() {
        val before = System.currentTimeMillis()
        filter.addOrUpdate("AA:BB:CC:DD:EE:01", "Dev", -50)
        val after = System.currentTimeMillis()
        val ts = filter.get("AA:BB:CC:DD:EE:01")!!.timestampMs
        assertTrue("timestamp should be >= before", ts >= before)
        assertTrue("timestamp should be <= after", ts <= after)
    }

    @Test
    fun `timestamp is updated on RSSI update`() {
        filter.addOrUpdate("AA:BB:CC:DD:EE:01", "Dev", -50)
        val firstTs = filter.get("AA:BB:CC:DD:EE:01")!!.timestampMs
        Thread.sleep(2)
        filter.addOrUpdate("AA:BB:CC:DD:EE:01", "Dev", -40)
        val secondTs = filter.get("AA:BB:CC:DD:EE:01")!!.timestampMs
        assertTrue("timestamp should advance on update", secondTs >= firstTs)
    }

    // ---- Entry data class ----

    @Test
    fun `Entry data class equality by fields`() {
        val a = DeviceFilter.Entry("AA:BB:CC:DD:EE:01", "Dev", -50, 1000L)
        val b = DeviceFilter.Entry("AA:BB:CC:DD:EE:01", "Dev", -50, 1000L)
        assertEquals(a, b)
    }

    @Test
    fun `Entry data class inequality on different RSSI`() {
        val a = DeviceFilter.Entry("AA:BB:CC:DD:EE:01", "Dev", -50, 1000L)
        val b = DeviceFilter.Entry("AA:BB:CC:DD:EE:01", "Dev", -60, 1000L)
        assertFalse(a == b)
    }

    // ---- Integration-style: simulated scan cycle ----

    @Test
    fun `simulated scan cycle - devices discovered and rediscovered`() {
        // First scan pass: discover 3 devices
        filter.addOrUpdate("AA:BB:CC:DD:EE:01", "Phone", -45)
        filter.addOrUpdate("AA:BB:CC:DD:EE:02", "Laptop", -60)
        filter.addOrUpdate("AA:BB:CC:DD:EE:03", "Speaker", -75)
        assertEquals(3, filter.size)

        // Second scan pass: rediscover 2 devices with updated RSSI + 1 new
        filter.addOrUpdate("AA:BB:CC:DD:EE:01", "Phone", -42)
        filter.addOrUpdate("AA:BB:CC:DD:EE:03", "Speaker", -80)
        filter.addOrUpdate("AA:BB:CC:DD:EE:04", "Watch", -55)
        assertEquals(4, filter.size)

        // Verify RSSI updated, no duplicates
        assertEquals((-42).toShort(), filter.get("AA:BB:CC:DD:EE:01")!!.rssi)
        assertEquals((-60).toShort(), filter.get("AA:BB:CC:DD:EE:02")!!.rssi)
        assertEquals((-80).toShort(), filter.get("AA:BB:CC:DD:EE:03")!!.rssi)
        assertEquals((-55).toShort(), filter.get("AA:BB:CC:DD:EE:04")!!.rssi)
    }

    @Test
    fun `clear between scan cycles resets state`() {
        filter.addOrUpdate("AA:BB:CC:DD:EE:01", "Dev1", -50)
        filter.clear()
        assertEquals(0, filter.size)

        // Same MAC should be treated as new after clear
        assertTrue(filter.addOrUpdate("AA:BB:CC:DD:EE:01", "Dev1", -55))
        assertEquals(1, filter.size)
        assertEquals((-55).toShort(), filter.get("AA:BB:CC:DD:EE:01")!!.rssi)
    }
}
