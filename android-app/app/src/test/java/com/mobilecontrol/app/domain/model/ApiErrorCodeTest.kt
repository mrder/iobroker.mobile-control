package com.mobilecontrol.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class ApiErrorCodeTest {

    @Test
    fun `fromWireName matches every code the server contract defines`() {
        for (code in ApiErrorCode.entries) {
            if (code == ApiErrorCode.UNKNOWN) continue
            assertEquals(code, ApiErrorCode.fromWireName(code.name))
        }
    }

    @Test
    fun `fromWireName falls back to UNKNOWN for null or an unrecognized code`() {
        assertEquals(ApiErrorCode.UNKNOWN, ApiErrorCode.fromWireName(null))
        assertEquals(ApiErrorCode.UNKNOWN, ApiErrorCode.fromWireName("SOME_NEW_SERVER_ERROR_CODE"))
    }

    @Test
    fun `only AUTH_REQUIRED and TOKEN_EXPIRED require a silent re-auth`() {
        val requireReauth = ApiErrorCode.entries.filter { it.requiresReauth }.toSet()
        assertEquals(setOf(ApiErrorCode.AUTH_REQUIRED, ApiErrorCode.TOKEN_EXPIRED), requireReauth)
    }

    @Test
    fun `only DEVICE_REVOKED and SESSION_REVOKED require a full logout`() {
        val requireLogout = ApiErrorCode.entries.filter { it.requiresFullLogout }.toSet()
        assertEquals(setOf(ApiErrorCode.DEVICE_REVOKED, ApiErrorCode.SESSION_REVOKED), requireLogout)
    }

    @Test
    fun `reauth and full-logout categories never overlap`() {
        for (code in ApiErrorCode.entries) {
            assertFalse("$code cannot require both a silent re-auth and a full logout", code.requiresReauth && code.requiresFullLogout)
        }
    }

    @Test
    fun `a plain validation error requires neither reauth nor logout`() {
        assertFalse(ApiErrorCode.VALUE_INVALID.requiresReauth)
        assertFalse(ApiErrorCode.VALUE_INVALID.requiresFullLogout)
    }
}
