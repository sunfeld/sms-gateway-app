package com.sunfeld.smsgateway

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for phone number formatting and validation logic
 * used by SmsService. Tests the regex pattern and stripping logic
 * without requiring an Android context.
 */
class SmsValidationTest {

    companion object {
        // Mirror the pattern from SmsService.PHONE_PATTERN
        private val PHONE_PATTERN = Regex("^\\+?[1-9]\\d{6,14}$")

        /** Strip formatting characters the same way SmsService does. */
        fun stripPhone(phoneNumber: String): String =
            phoneNumber.replace(Regex("[\\s\\-().]+"), "")

        /** Validate using the same regex SmsService uses (without the Android-only PhoneNumberUtils fallback). */
        fun isValidStripped(phoneNumber: String): Boolean =
            stripPhone(phoneNumber).matches(PHONE_PATTERN)
    }

    // ---- Stripping / formatting ----

    @Test
    fun `strip removes spaces`() {
        assertEquals("+15551234567", stripPhone("+1 555 123 4567"))
    }

    @Test
    fun `strip removes hyphens`() {
        assertEquals("+15551234567", stripPhone("+1-555-123-4567"))
    }

    @Test
    fun `strip removes parentheses`() {
        assertEquals("+15551234567", stripPhone("+1(555)1234567"))
    }

    @Test
    fun `strip removes dots`() {
        assertEquals("+15551234567", stripPhone("+1.555.123.4567"))
    }

    @Test
    fun `strip removes mixed formatting`() {
        assertEquals("+15551234567", stripPhone("+1 (555) 123-4567"))
    }

    @Test
    fun `strip preserves already-clean number`() {
        assertEquals("+15551234567", stripPhone("+15551234567"))
    }

    // ---- Valid phone numbers ----

    @Test
    fun `valid E164 US number`() {
        assertTrue(isValidStripped("+15551234567"))
    }

    @Test
    fun `valid E164 UK number`() {
        assertTrue(isValidStripped("+447911123456"))
    }

    @Test
    fun `valid number without plus prefix`() {
        assertTrue(isValidStripped("15551234567"))
    }

    @Test
    fun `valid short international number (7 digits)`() {
        assertTrue(isValidStripped("1234567"))
    }

    @Test
    fun `valid max-length number (15 digits)`() {
        assertTrue(isValidStripped("123456789012345"))
    }

    @Test
    fun `valid formatted US number`() {
        assertTrue(isValidStripped("+1 (555) 123-4567"))
    }

    @Test
    fun `valid formatted with dots`() {
        assertTrue(isValidStripped("1.555.123.4567"))
    }

    // ---- Invalid phone numbers ----

    @Test
    fun `invalid empty string`() {
        assertFalse(isValidStripped(""))
    }

    @Test
    fun `invalid too short (6 digits)`() {
        assertFalse(isValidStripped("123456"))
    }

    @Test
    fun `invalid too long (16 digits)`() {
        assertFalse(isValidStripped("1234567890123456"))
    }

    @Test
    fun `invalid starts with zero`() {
        assertFalse(isValidStripped("0123456789"))
    }

    @Test
    fun `invalid contains letters`() {
        assertFalse(isValidStripped("+1555ABC4567"))
    }

    @Test
    fun `invalid only whitespace`() {
        assertFalse(isValidStripped("   "))
    }

    @Test
    fun `invalid plus only`() {
        assertFalse(isValidStripped("+"))
    }

    @Test
    fun `invalid double plus`() {
        assertFalse(isValidStripped("++15551234567"))
    }
}
