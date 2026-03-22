package com.sunfeld.smsgateway

/**
 * Builds standard USB HID keyboard reports (8-byte boot protocol).
 *
 * Report format:
 *   Byte 0: Modifier bitmap (LCtrl|LShift|LAlt|LGui|RCtrl|RShift|RAlt|RGui)
 *   Byte 1: Reserved (0x00)
 *   Bytes 2–7: Up to 6 simultaneous HID keycodes
 */
object HidKeyReport {

    // Modifier bitmasks
    const val MOD_NONE: Byte = 0x00
    const val MOD_LEFT_SHIFT: Byte = 0x02

    /** 8-byte key-release report (all keys up). */
    val KEY_RELEASE: ByteArray = ByteArray(8)

    /**
     * Standard USB HID boot keyboard descriptor.
     * Covers 8 modifier bits, 1 reserved byte, and 6-key rollover.
     */
    val KEYBOARD_DESCRIPTOR: ByteArray = byteArrayOf(
        0x05.toByte(), 0x01,              // Usage Page (Generic Desktop)
        0x09.toByte(), 0x06,              // Usage (Keyboard)
        0xA1.toByte(), 0x01,              // Collection (Application)
        // Modifier keys (8 bits, one per key)
        0x05.toByte(), 0x07,              //   Usage Page (Keyboard/Keypad)
        0x19.toByte(), 0xE0.toByte(),     //   Usage Minimum (Left Control)
        0x29.toByte(), 0xE7.toByte(),     //   Usage Maximum (Right GUI)
        0x15.toByte(), 0x00,              //   Logical Minimum (0)
        0x25.toByte(), 0x01,              //   Logical Maximum (1)
        0x75.toByte(), 0x01,              //   Report Size (1 bit)
        0x95.toByte(), 0x08,              //   Report Count (8)
        0x81.toByte(), 0x02,              //   Input (Data, Var, Abs)
        // Reserved byte
        0x95.toByte(), 0x01,              //   Report Count (1)
        0x75.toByte(), 0x08,              //   Report Size (8 bits)
        0x81.toByte(), 0x01,              //   Input (Const)
        // Key array (6 keycodes)
        0x95.toByte(), 0x06,              //   Report Count (6)
        0x75.toByte(), 0x08,              //   Report Size (8 bits)
        0x15.toByte(), 0x00,              //   Logical Minimum (0)
        0x25.toByte(), 0x65,              //   Logical Maximum (101)
        0x05.toByte(), 0x07,              //   Usage Page (Keyboard/Keypad)
        0x19.toByte(), 0x00,              //   Usage Minimum (0)
        0x29.toByte(), 0x65,              //   Usage Maximum (101)
        0x81.toByte(), 0x00,              //   Input (Data, Array, Abs)
        0xC0.toByte()                     // End Collection
    )

    /**
     * Maps ASCII character to (modifier, keycode) pair.
     * modifier: 0x00 = none, 0x02 = Left Shift
     */
    private val ASCII_MAP: Map<Char, Pair<Byte, Byte>> = buildMap {
        // a-z → keycodes 0x04–0x1D, no modifier
        for (i in 0..25) {
            put('a' + i, MOD_NONE to (0x04 + i).toByte())
        }
        // A-Z → same keycodes, Left Shift modifier
        for (i in 0..25) {
            put('A' + i, MOD_LEFT_SHIFT to (0x04 + i).toByte())
        }
        // 1-9 → 0x1E–0x26, 0 → 0x27
        put('1', MOD_NONE to 0x1E.toByte())
        put('2', MOD_NONE to 0x1F.toByte())
        put('3', MOD_NONE to 0x20.toByte())
        put('4', MOD_NONE to 0x21.toByte())
        put('5', MOD_NONE to 0x22.toByte())
        put('6', MOD_NONE to 0x23.toByte())
        put('7', MOD_NONE to 0x24.toByte())
        put('8', MOD_NONE to 0x25.toByte())
        put('9', MOD_NONE to 0x26.toByte())
        put('0', MOD_NONE to 0x27.toByte())
        // Common punctuation
        put(' ', MOD_NONE to 0x2C.toByte())
        put('\n', MOD_NONE to 0x28.toByte())
        put('\t', MOD_NONE to 0x2B.toByte())
        put('\b', MOD_NONE to 0x2A.toByte())
        put('-', MOD_NONE to 0x2D.toByte())
        put('=', MOD_NONE to 0x2E.toByte())
        put('[', MOD_NONE to 0x2F.toByte())
        put(']', MOD_NONE to 0x30.toByte())
        put('\\', MOD_NONE to 0x31.toByte())
        put(';', MOD_NONE to 0x33.toByte())
        put('\'', MOD_NONE to 0x34.toByte())
        put('`', MOD_NONE to 0x35.toByte())
        put(',', MOD_NONE to 0x36.toByte())
        put('.', MOD_NONE to 0x37.toByte())
        put('/', MOD_NONE to 0x38.toByte())
        // Shifted symbols
        put('!', MOD_LEFT_SHIFT to 0x1E.toByte())
        put('@', MOD_LEFT_SHIFT to 0x1F.toByte())
        put('#', MOD_LEFT_SHIFT to 0x20.toByte())
        put('$', MOD_LEFT_SHIFT to 0x21.toByte())
        put('%', MOD_LEFT_SHIFT to 0x22.toByte())
        put('^', MOD_LEFT_SHIFT to 0x23.toByte())
        put('&', MOD_LEFT_SHIFT to 0x24.toByte())
        put('*', MOD_LEFT_SHIFT to 0x25.toByte())
        put('(', MOD_LEFT_SHIFT to 0x26.toByte())
        put(')', MOD_LEFT_SHIFT to 0x27.toByte())
        put('_', MOD_LEFT_SHIFT to 0x2D.toByte())
        put('+', MOD_LEFT_SHIFT to 0x2E.toByte())
        put('{', MOD_LEFT_SHIFT to 0x2F.toByte())
        put('}', MOD_LEFT_SHIFT to 0x30.toByte())
        put('|', MOD_LEFT_SHIFT to 0x31.toByte())
        put(':', MOD_LEFT_SHIFT to 0x33.toByte())
        put('"', MOD_LEFT_SHIFT to 0x34.toByte())
        put('~', MOD_LEFT_SHIFT to 0x35.toByte())
        put('<', MOD_LEFT_SHIFT to 0x36.toByte())
        put('>', MOD_LEFT_SHIFT to 0x37.toByte())
        put('?', MOD_LEFT_SHIFT to 0x38.toByte())
    }

    /**
     * Builds an 8-byte HID key-press report for a single character.
     * Returns null if the character has no mapping.
     */
    fun buildKeyPress(char: Char): ByteArray? {
        val (modifier, keycode) = ASCII_MAP[char] ?: return null
        return byteArrayOf(modifier, 0x00, keycode, 0, 0, 0, 0, 0)
    }

    /**
     * Returns the sequence of (press, release) report pairs for a string.
     * Unmapped characters are silently skipped.
     */
    fun buildSequence(text: String): List<ByteArray> {
        val reports = mutableListOf<ByteArray>()
        for (char in text) {
            val press = buildKeyPress(char) ?: continue
            reports.add(press)
            reports.add(KEY_RELEASE)
        }
        return reports
    }
}
