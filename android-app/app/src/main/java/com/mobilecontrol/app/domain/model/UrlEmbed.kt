package com.mobilecontrol.app.domain.model

/** An admin-approved allowlist entry (see GET /api/v1/url-embeds). Deliberately carries no url -
 *  the app only ever learns {id, name}; the actual target is resolved/fetched server-side by id. */
data class UrlEmbed(
    val id: String,
    val name: String,
)

/** A short-lived, narrowly-scoped credential for the Tunnel mode's local proxy (see
 *  com.mobilecontrol.app.tunnel) - deliberately not the same bearer token used for the rest of
 *  the API. See POST /api/v1/tunnel-token/{id} and TunnelService on the backend. */
data class TunnelToken(
    val token: String,
    val expiresAtEpochMs: Long,
)
