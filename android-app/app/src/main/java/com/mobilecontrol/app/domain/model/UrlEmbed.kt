package com.mobilecontrol.app.domain.model

/** An admin-approved allowlist entry (see GET /api/v1/url-embeds). Deliberately carries no url -
 *  the app only ever learns {id, name}; the actual target is resolved/fetched server-side by id. */
data class UrlEmbed(
    val id: String,
    val name: String,
)
