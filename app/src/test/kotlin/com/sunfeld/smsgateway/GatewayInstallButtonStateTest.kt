package com.sunfeld.smsgateway

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * Unit tests for GatewayInstallButton.State enum values and transitions.
 */
class GatewayInstallButtonStateTest {

    @Test
    fun `state enum has exactly three values`() {
        val states = GatewayInstallButton.State.values()
        assertEquals(3, states.size)
    }

    @Test
    fun `state enum contains IDLE INSTALLING and ERROR`() {
        val states = GatewayInstallButton.State.values().map { it.name }
        assertEquals(listOf("IDLE", "INSTALLING", "ERROR"), states)
    }

    @Test
    fun `state valueOf resolves correctly`() {
        assertEquals(GatewayInstallButton.State.IDLE, GatewayInstallButton.State.valueOf("IDLE"))
        assertEquals(GatewayInstallButton.State.INSTALLING, GatewayInstallButton.State.valueOf("INSTALLING"))
        assertEquals(GatewayInstallButton.State.ERROR, GatewayInstallButton.State.valueOf("ERROR"))
    }

    @Test
    fun `states are distinct`() {
        assertNotEquals(GatewayInstallButton.State.IDLE, GatewayInstallButton.State.INSTALLING)
        assertNotEquals(GatewayInstallButton.State.IDLE, GatewayInstallButton.State.ERROR)
        assertNotEquals(GatewayInstallButton.State.INSTALLING, GatewayInstallButton.State.ERROR)
    }
}
