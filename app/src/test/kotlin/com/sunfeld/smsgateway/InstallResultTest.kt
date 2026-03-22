package com.sunfeld.smsgateway

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for the InstallResult sealed class that manages
 * UI lifecycle states for gateway installation.
 */
class InstallResultTest {

    // ---- Subtype identity tests ----

    @Test
    fun `Idle is an InstallResult`() {
        val result: InstallResult = InstallResult.Idle
        assertTrue(result is InstallResult.Idle)
    }

    @Test
    fun `Installing is an InstallResult`() {
        val result: InstallResult = InstallResult.Installing
        assertTrue(result is InstallResult.Installing)
    }

    @Test
    fun `Success is an InstallResult`() {
        val result: InstallResult = InstallResult.Success
        assertTrue(result is InstallResult.Success)
    }

    @Test
    fun `Error is an InstallResult`() {
        val result: InstallResult = InstallResult.Error("fail")
        assertTrue(result is InstallResult.Error)
    }

    // ---- Singleton (data object) tests ----

    @Test
    fun `Idle is a singleton`() {
        assertTrue(InstallResult.Idle === InstallResult.Idle)
    }

    @Test
    fun `Installing is a singleton`() {
        assertTrue(InstallResult.Installing === InstallResult.Installing)
    }

    @Test
    fun `Success is a singleton`() {
        assertTrue(InstallResult.Success === InstallResult.Success)
    }

    // ---- Error message tests ----

    @Test
    fun `Error holds the provided message`() {
        val error = InstallResult.Error("Network timeout")
        assertEquals("Network timeout", error.message)
    }

    @Test
    fun `Error with empty message is valid`() {
        val error = InstallResult.Error("")
        assertEquals("", error.message)
    }

    // ---- Equality tests ----

    @Test
    fun `two Error instances with same message are equal`() {
        val a = InstallResult.Error("fail")
        val b = InstallResult.Error("fail")
        assertEquals(a, b)
    }

    @Test
    fun `two Error instances with different messages are not equal`() {
        val a = InstallResult.Error("fail")
        val b = InstallResult.Error("timeout")
        assertNotEquals(a, b)
    }

    @Test
    fun `Idle equals Idle`() {
        assertEquals(InstallResult.Idle, InstallResult.Idle)
    }

    @Test
    fun `Installing equals Installing`() {
        assertEquals(InstallResult.Installing, InstallResult.Installing)
    }

    @Test
    fun `Success equals Success`() {
        assertEquals(InstallResult.Success, InstallResult.Success)
    }

    // ---- Mutual exclusivity tests ----

    @Test
    fun `Idle is not Installing`() {
        val state: InstallResult = InstallResult.Idle
        assertFalse(state is InstallResult.Installing)
    }

    @Test
    fun `Idle is not Success`() {
        val state: InstallResult = InstallResult.Idle
        assertFalse(state is InstallResult.Success)
    }

    @Test
    fun `Idle is not Error`() {
        val state: InstallResult = InstallResult.Idle
        assertFalse(state is InstallResult.Error)
    }

    @Test
    fun `Installing is not Success`() {
        val state: InstallResult = InstallResult.Installing
        assertFalse(state is InstallResult.Success)
    }

    // ---- when expression exhaustiveness test ----

    @Test
    fun `when expression covers all states`() {
        val states: List<InstallResult> = listOf(
            InstallResult.Idle,
            InstallResult.Installing,
            InstallResult.Success,
            InstallResult.Error("err")
        )

        for (state in states) {
            val label = when (state) {
                is InstallResult.Idle -> "idle"
                is InstallResult.Installing -> "installing"
                is InstallResult.Success -> "success"
                is InstallResult.Error -> "error"
            }
            assertTrue(label.isNotEmpty())
        }
    }

    @Test
    fun `sealed class has exactly four direct subtypes`() {
        // Verify all four variants can be created and are distinct
        val allStates: List<InstallResult> = listOf(
            InstallResult.Idle,
            InstallResult.Installing,
            InstallResult.Success,
            InstallResult.Error("test")
        )
        assertEquals(4, allStates.size)
        assertEquals(4, allStates.map { it::class }.toSet().size)
    }

    // ---- Error copy test ----

    @Test
    fun `Error copy produces new instance with updated message`() {
        val original = InstallResult.Error("original")
        val copied = original.copy(message = "updated")
        assertEquals("updated", copied.message)
        assertNotEquals(original, copied)
    }

    // ---- toString tests ----

    @Test
    fun `Idle toString is readable`() {
        assertTrue(InstallResult.Idle.toString().contains("Idle"))
    }

    @Test
    fun `Error toString contains the message`() {
        val error = InstallResult.Error("something broke")
        assertTrue(error.toString().contains("something broke"))
    }

    // ---- hashCode tests ----

    @Test
    fun `equal Error instances have same hashCode`() {
        val a = InstallResult.Error("fail")
        val b = InstallResult.Error("fail")
        assertEquals(a.hashCode(), b.hashCode())
    }

    @Test
    fun `Idle hashCode is consistent`() {
        assertEquals(InstallResult.Idle.hashCode(), InstallResult.Idle.hashCode())
    }
}
