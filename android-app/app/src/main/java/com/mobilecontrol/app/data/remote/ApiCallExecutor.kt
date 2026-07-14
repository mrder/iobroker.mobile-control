package com.mobilecontrol.app.data.remote

import com.mobilecontrol.app.data.remote.dto.ErrorEnvelopeDto
import com.mobilecontrol.app.domain.model.ApiErrorCode
import com.mobilecontrol.app.domain.model.ApiException
import kotlinx.serialization.json.Json
import retrofit2.Response
import java.io.IOException

private val errorJson = Json { ignoreUnknownKeys = true }

/** Runs a Retrofit call and maps HTTP/network failures onto the typed [ApiErrorCode] contract. */
suspend fun <T> safeApiCall(block: suspend () -> Response<T>): Result<T> {
    return try {
        val response = block()
        if (response.isSuccessful) {
            val body = response.body()
            if (body != null) {
                Result.success(body)
            } else {
                // Some endpoints (e.g. DELETE) legitimately return an empty body.
                @Suppress("UNCHECKED_CAST")
                Result.success(Unit as T)
            }
        } else {
            Result.failure(mapHttpError(response))
        }
    } catch (io: IOException) {
        Result.failure(ApiException(ApiErrorCode.SERVER_UNAVAILABLE, "Network error", io))
    } catch (ex: Exception) {
        Result.failure(ApiException(ApiErrorCode.UNKNOWN, ex.message, ex))
    }
}

private fun <T> mapHttpError(response: Response<T>): ApiException {
    val bodyString = response.errorBody()?.string()
    val parsedCode = bodyString
        ?.let { runCatching { errorJson.decodeFromString(ErrorEnvelopeDto.serializer(), it) }.getOrNull() }
        ?.error?.code

    val code = parsedCode?.let { ApiErrorCode.fromWireName(it) } ?: when (response.code()) {
        401 -> ApiErrorCode.TOKEN_EXPIRED
        403 -> ApiErrorCode.WRITE_FORBIDDEN
        404 -> ApiErrorCode.OBJECT_NOT_FOUND
        409 -> ApiErrorCode.REVISION_CONFLICT
        429 -> ApiErrorCode.RATE_LIMITED
        in 500..599 -> ApiErrorCode.SERVER_UNAVAILABLE
        else -> ApiErrorCode.UNKNOWN
    }
    return ApiException(code, "HTTP ${response.code()}")
}
