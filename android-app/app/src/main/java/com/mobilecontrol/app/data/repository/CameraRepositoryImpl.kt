package com.mobilecontrol.app.data.repository

import com.mobilecontrol.app.data.remote.ApiService
import com.mobilecontrol.app.data.remote.safeApiCall
import com.mobilecontrol.app.domain.model.ApiErrorCode
import com.mobilecontrol.app.domain.model.ApiException
import com.mobilecontrol.app.domain.repository.CameraRepository
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CameraRepositoryImpl @Inject constructor(
    private val apiService: ApiService,
) : CameraRepository {

    override suspend fun fetchSnapshot(objectId: String): Result<ByteArray> {
        val result = safeApiCall { apiService.getSnapshot(objectId) }
        return result.fold(
            onSuccess = { body ->
                // Reading the streamed body can itself fail (connection dropped mid-transfer) -
                // caught here rather than via Result.map, which would let it escape uncaught.
                try {
                    Result.success(body.bytes())
                } catch (io: IOException) {
                    Result.failure(ApiException(ApiErrorCode.SERVER_UNAVAILABLE, "Network error reading snapshot", io))
                } finally {
                    body.close()
                }
            },
            onFailure = { Result.failure(it) },
        )
    }
}
