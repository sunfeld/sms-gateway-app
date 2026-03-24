package com.sunfeld.smsgateway

/**
 * Pure-Kotlin device filter that de-duplicates discovered Bluetooth devices by MAC address
 * and tracks the most recent RSSI (signal strength) for each device.
 *
 * Designed to be testable without Android framework dependencies.
 */
class DeviceFilter {

    data class Entry(
        val address: String,
        val name: String?,
        val rssi: Short,
        val timestampMs: Long = System.currentTimeMillis()
    )

    private val entries = LinkedHashMap<String, Entry>()

    /**
     * Adds a new device or updates the RSSI/name of an existing one.
     * Returns `true` if a **new** device was added, `false` if an existing entry was updated.
     */
    fun addOrUpdate(address: String, name: String?, rssi: Short): Boolean {
        val existing = entries[address]
        return if (existing == null) {
            entries[address] = Entry(address, name, rssi)
            true
        } else {
            // Update RSSI and name (prefer non-null name)
            entries[address] = existing.copy(
                rssi = rssi,
                name = name ?: existing.name,
                timestampMs = System.currentTimeMillis()
            )
            false
        }
    }

    /** Returns all known devices in discovery order. */
    fun getAll(): List<Entry> = entries.values.toList()

    /** Returns all devices sorted by signal strength (closest first, strongest RSSI = closest). */
    fun getAllSortedByRssi(): List<Entry> = entries.values.sortedByDescending { it.rssi }

    /** Number of unique devices tracked. */
    val size: Int get() = entries.size

    /** Look up a single device by address, or null. */
    fun get(address: String): Entry? = entries[address]

    /** Returns true if the given address is already tracked. */
    fun contains(address: String): Boolean = entries.containsKey(address)

    /** Removes all tracked devices. */
    fun clear() {
        entries.clear()
    }
}
