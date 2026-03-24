package com.sunfeld.smsgateway

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/**
 * Represents a data payload that can be sent via Bluetooth OBEX push.
 *
 * Supported types:
 * - VCARD: Contact information (.vcf)
 * - VCALENDAR: Calendar event/invite (.ics)
 * - VNOTE: Text note (.vnt)
 * - TEXT: Raw text message
 * - PAIRING_NAME: Device name for pairing request spam (no OBEX)
 */
data class BluetoothPayload(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val type: PayloadType,
    val data: Map<String, String>, // Key-value pairs specific to the type
    val createdAt: Long = System.currentTimeMillis()
) {

    enum class PayloadType {
        VCARD,       // Contact card
        VCALENDAR,   // Calendar event
        VNOTE,       // Text note
        TEXT,        // Raw text
        PAIRING_NAME // Just a device name for pairing dialog
    }

    /**
     * Generate the file content for OBEX push based on the payload type.
     */
    fun toFileContent(): String = when (type) {
        PayloadType.VCARD -> buildVCard()
        PayloadType.VCALENDAR -> buildVCalendar()
        PayloadType.VNOTE -> buildVNote()
        PayloadType.TEXT -> data["text"] ?: ""
        PayloadType.PAIRING_NAME -> data["name"] ?: "Hello"
    }

    /**
     * Get the MIME type for OBEX push headers.
     */
    fun mimeType(): String = when (type) {
        PayloadType.VCARD -> "text/x-vcard"
        PayloadType.VCALENDAR -> "text/x-vcalendar"
        PayloadType.VNOTE -> "text/x-vnote"
        PayloadType.TEXT -> "text/plain"
        PayloadType.PAIRING_NAME -> ""
    }

    /**
     * Get the file extension for OBEX push.
     */
    fun fileExtension(): String = when (type) {
        PayloadType.VCARD -> ".vcf"
        PayloadType.VCALENDAR -> ".ics"
        PayloadType.VNOTE -> ".vnt"
        PayloadType.TEXT -> ".txt"
        PayloadType.PAIRING_NAME -> ""
    }

    /**
     * Get the display name for the file being pushed.
     */
    fun fileName(): String = "${name.replace(" ", "_")}${fileExtension()}"

    // ---- Builders for each format ----

    private fun buildVCard(): String {
        val fn = data["fullName"] ?: "Contact"
        val phone = data["phone"] ?: ""
        val email = data["email"] ?: ""
        val org = data["organization"] ?: ""
        val title = data["title"] ?: ""
        val url = data["url"] ?: ""
        val note = data["note"] ?: ""

        return buildString {
            appendLine("BEGIN:VCARD")
            appendLine("VERSION:3.0")
            appendLine("FN:$fn")
            if (phone.isNotBlank()) appendLine("TEL;TYPE=CELL:$phone")
            if (email.isNotBlank()) appendLine("EMAIL:$email")
            if (org.isNotBlank()) appendLine("ORG:$org")
            if (title.isNotBlank()) appendLine("TITLE:$title")
            if (url.isNotBlank()) appendLine("URL:$url")
            if (note.isNotBlank()) appendLine("NOTE:$note")
            appendLine("END:VCARD")
        }
    }

    private fun buildVCalendar(): String {
        val summary = data["summary"] ?: "Event"
        val description = data["description"] ?: ""
        val location = data["location"] ?: ""
        val dtStart = data["dtStart"] ?: formatIcsDate(Date())
        val dtEnd = data["dtEnd"] ?: formatIcsDate(Date(System.currentTimeMillis() + 3600000))

        return buildString {
            appendLine("BEGIN:VCALENDAR")
            appendLine("VERSION:2.0")
            appendLine("PRODID:-//SMSGateway//BT//EN")
            appendLine("BEGIN:VEVENT")
            appendLine("SUMMARY:$summary")
            appendLine("DTSTART:$dtStart")
            appendLine("DTEND:$dtEnd")
            if (description.isNotBlank()) appendLine("DESCRIPTION:$description")
            if (location.isNotBlank()) appendLine("LOCATION:$location")
            appendLine("END:VEVENT")
            appendLine("END:VCALENDAR")
        }
    }

    private fun buildVNote(): String {
        val body = data["body"] ?: ""
        return buildString {
            appendLine("BEGIN:VNOTE")
            appendLine("VERSION:1.1")
            appendLine("BODY:$body")
            appendLine("END:VNOTE")
        }
    }

    private fun formatIcsDate(date: Date): String {
        return SimpleDateFormat("yyyyMMdd'T'HHmmss'Z'", Locale.US).format(date)
    }

    companion object {
        /** Create a simple vCard contact payload */
        fun contact(name: String, fullName: String, phone: String = "", email: String = "",
                    org: String = "", note: String = ""): BluetoothPayload {
            return BluetoothPayload(
                name = name,
                type = PayloadType.VCARD,
                data = mapOf(
                    "fullName" to fullName,
                    "phone" to phone,
                    "email" to email,
                    "organization" to org,
                    "note" to note
                )
            )
        }

        /** Create a calendar event payload */
        fun calendarEvent(name: String, summary: String, description: String = "",
                          location: String = ""): BluetoothPayload {
            return BluetoothPayload(
                name = name,
                type = PayloadType.VCALENDAR,
                data = mapOf(
                    "summary" to summary,
                    "description" to description,
                    "location" to location
                )
            )
        }

        /** Create a text note payload */
        fun note(name: String, body: String): BluetoothPayload {
            return BluetoothPayload(
                name = name,
                type = PayloadType.VNOTE,
                data = mapOf("body" to body)
            )
        }

        /** Create a pairing name payload (just sets BT adapter name) */
        fun pairingName(name: String, message: String): BluetoothPayload {
            return BluetoothPayload(
                name = name,
                type = PayloadType.PAIRING_NAME,
                data = mapOf("name" to message)
            )
        }

        // Gson serialization for preset storage
        fun toJson(payloads: List<BluetoothPayload>): String = Gson().toJson(payloads)

        fun fromJson(json: String): List<BluetoothPayload> {
            return try {
                val type = object : TypeToken<List<BluetoothPayload>>() {}.type
                Gson().fromJson(json, type)
            } catch (_: Exception) {
                emptyList()
            }
        }
    }
}
