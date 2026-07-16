package com.mobilecontrol.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CommandStatusTest {

    @Test
    fun `fromWireName matches case-insensitively`() {
        assertEquals(CommandStatus.CONFIRMED, CommandStatus.fromWireName("confirmed"))
        assertEquals(CommandStatus.CONFIRMED, CommandStatus.fromWireName("CONFIRMED"))
    }

    @Test
    fun `terminal statuses are exactly confirmed, timeout, rejected and blocked`() {
        val terminal = CommandStatus.entries.filter { it.isTerminal }.toSet()
        assertEquals(setOf(CommandStatus.CONFIRMED, CommandStatus.TIMEOUT, CommandStatus.REJECTED, CommandStatus.BLOCKED), terminal)
    }

    @Test
    fun `accepted and executed are not terminal`() {
        assertFalse(CommandStatus.ACCEPTED.isTerminal)
        assertFalse(CommandStatus.EXECUTED.isTerminal)
    }

    @Test
    fun `an unrecognized wire name falls back to TIMEOUT (a safe, terminal default)`() {
        val status = CommandStatus.fromWireName("some_future_status_the_client_does_not_know_yet")
        assertEquals(CommandStatus.TIMEOUT, status)
        assertTrue(status.isTerminal)
    }
}
