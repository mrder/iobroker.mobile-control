package com.mobilecontrol.app.domain.repository

import com.mobilecontrol.app.domain.model.UrlEmbed

interface UrlEmbedRepository {
    /** GET /api/v1/url-embeds - the admin-approved allowlist, {id, name} only. */
    suspend fun listEmbeds(): Result<List<UrlEmbed>>

    /** GET /api/v1/url-embeds/{id}/content - proxied bytes, for image-style widgets. */
    suspend fun fetchContent(id: String): Result<ByteArray>

    /** GET /api/v1/url-embeds/{id}/resolve - the real target URL, for WebView-style widgets. */
    suspend fun resolveUrl(id: String): Result<String>
}
