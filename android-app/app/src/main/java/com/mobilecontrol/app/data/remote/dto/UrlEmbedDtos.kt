package com.mobilecontrol.app.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class UrlEmbedDto(
    val id: String,
    val name: String,
)

@Serializable
data class UrlEmbedListResponseDto(
    val embeds: List<UrlEmbedDto> = emptyList(),
)

@Serializable
data class UrlEmbedResolveResponseDto(
    val url: String,
)

@Serializable
data class TunnelTokenResponseDto(
    val token: String,
    val expiresAt: Long,
)
