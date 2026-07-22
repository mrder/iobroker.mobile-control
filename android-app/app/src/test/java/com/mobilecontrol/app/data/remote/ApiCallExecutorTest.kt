package com.mobilecontrol.app.data.remote

import com.mobilecontrol.app.domain.model.ApiErrorCode
import com.mobilecontrol.app.domain.model.ApiException
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Test
import retrofit2.Response

/**
 * Regression test for a real bug: this DTO used to expect {"error": {"code": "...", ...}}, but
 * the backend's actual wire format (see ApiError.toBody() in src/lib/errors.ts) is
 * {"error": "CODE_STRING", "message": "..."} - a flat string, not a nested object. That mismatch
 * meant every error body silently failed to deserialize and the app always fell back to guessing
 * the error purely from the HTTP status code, discarding the server's more specific error.
 */
class ApiCallExecutorTest {

    private fun errorResponse(httpStatus: Int, json: String): Response<Unit> =
        Response.error(httpStatus, json.toResponseBody("application/json".toMediaType()))

    @Test
    fun `parses the real backend error envelope shape`() = runTest {
        val response = errorResponse(410, """{"error":"PAIRING_EXPIRED","message":"pairing invite already used"}""")
        val result = safeApiCall { response }

        val error = result.exceptionOrNull() as? ApiException
        assertEquals(ApiErrorCode.PAIRING_EXPIRED, error?.errorCode)
        assertEquals("pairing invite already used", error?.message)
    }

    @Test
    fun `falls back to a status-derived code when the body is missing or unparseable`() = runTest {
        val response = errorResponse(410, "not json")
        val result = safeApiCall { response }

        val error = result.exceptionOrNull() as? ApiException
        assertEquals(ApiErrorCode.PAIRING_EXPIRED, error?.errorCode)
    }

    @Test
    fun `an unrecognized server code still surfaces as UNKNOWN rather than crashing`() = runTest {
        val response = errorResponse(400, """{"error":"SOME_FUTURE_CODE","message":"n/a"}""")
        val result = safeApiCall { response }

        val error = result.exceptionOrNull() as? ApiException
        assertEquals(ApiErrorCode.UNKNOWN, error?.errorCode)
    }
}
