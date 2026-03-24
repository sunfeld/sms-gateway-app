package com.sunfeld.smsgateway

import android.content.Context
import android.content.SharedPreferences

/**
 * Persistent storage for [BluetoothPayload] objects.
 * Payloads can be attached to presets and reused across sessions.
 */
object PayloadRepository {

    private const val PREFS_NAME = "bt_payloads"
    private const val KEY_PAYLOADS = "saved_payloads"

    private fun getPrefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun save(context: Context, payload: BluetoothPayload) {
        val all = getAll(context).toMutableList()
        val existing = all.indexOfFirst { it.id == payload.id }
        if (existing >= 0) {
            all[existing] = payload
        } else {
            all.add(payload)
        }
        getPrefs(context).edit().putString(KEY_PAYLOADS, BluetoothPayload.toJson(all)).apply()
    }

    fun delete(context: Context, id: String) {
        val all = getAll(context).filter { it.id != id }
        getPrefs(context).edit().putString(KEY_PAYLOADS, BluetoothPayload.toJson(all)).apply()
    }

    fun getAll(context: Context): List<BluetoothPayload> {
        val json = getPrefs(context).getString(KEY_PAYLOADS, null) ?: return emptyList()
        return BluetoothPayload.fromJson(json)
    }

    fun getById(context: Context, id: String): BluetoothPayload? {
        return getAll(context).find { it.id == id }
    }

    /**
     * Create default payload templates if none exist.
     */
    fun ensureDefaults(context: Context) {
        if (getAll(context).isNotEmpty()) return

        val defaults = listOf(
            BluetoothPayload.pairingName("Quick Message", "Hello!"),
            BluetoothPayload.contact(
                name = "My Contact Card",
                fullName = "John Doe",
                phone = "+31612345678",
                email = "john@example.com",
                org = "Sunfeld",
                note = "Sent via Bluetooth"
            ),
            BluetoothPayload.contact(
                name = "Phone Number Only",
                fullName = "Call Me",
                phone = "+31600000000"
            ),
            BluetoothPayload.contact(
                name = "Email Only",
                fullName = "Email Me",
                email = "hello@example.com"
            ),
            BluetoothPayload.calendarEvent(
                name = "Meeting Invite",
                summary = "Quick Meeting",
                description = "Let's meet up",
                location = "Amsterdam"
            ),
            BluetoothPayload.note("Hello Note", "This is a Bluetooth message"),
            BluetoothPayload(
                name = "Custom Text File",
                type = BluetoothPayload.PayloadType.TEXT,
                data = mapOf("text" to "Hello from SMS Gateway!")
            )
        )

        defaults.forEach { save(context, it) }
    }
}
