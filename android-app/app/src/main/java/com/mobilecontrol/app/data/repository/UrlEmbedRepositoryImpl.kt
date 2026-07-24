package com.mobilecontrol.app.data.repository

import com.mobilecontrol.app.data.remote.ApiService
import com.mobilecontrol.app.data.remote.safeApiCall
import com.mobilecontrol.app.domain.model.ApiErrorCode
import com.mobilecontrol.app.domain.model.ApiException
import com.mobilecontrol.app.domain.model.TunnelToken
import com.mobilecontrol.app.domain.model.UrlEmbed
import com.mobilecontrol.app.domain.repository.UrlEmbedRepository
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UrlEmbedRepositoryImpl @Inject constructor(
    private val apiService: ApiService,
) : UrlEmbedRepository {

    override suspend fun listEmbeds(): Result<List<UrlEmbed>> =
        safeApiCall { apiService.getUrlEmbeds() }.map { dto -> dto.embeds.map { UrlEmbed(it.id, it.name) } }

    override suspend fun fetchContent(id: String): Result<ByteArray> {
        val result = safeApiCall { apiService.getUrlEmbedContent(id) }
        return result.fold(
            onSuccess = { body ->
                // Same reasoning as CameraRepositoryImpl.fetchSnapshot: reading the streamed body
                // can itself fail (connection dropped mid-transfer), caught here rather than via
                // Result.map, which would let it escape uncaught.
                try {
                    Result.success(body.bytes())
                } catch (io: IOException) {
                    Result.failure(ApiException(ApiErrorCode.SERVER_UNAVAILABLE, "Network error reading url embed content", io))
                } finally {
                    body.close()
                }
            },
            onFailure = { Result.failure(it) },
        )
    }

    override suspend fun resolveUrl(id: String): Result<String> =
        safeApiCall { apiService.resolveUrlEmbed(id) }.map { it.url }

    override suspend fun requestTunnelToken(id: String): Result<TunnelToken> =
        safeApiCall { apiService.requestTunnelToken(id) }.map { TunnelToken(it.token, it.expiresAt) }
}
