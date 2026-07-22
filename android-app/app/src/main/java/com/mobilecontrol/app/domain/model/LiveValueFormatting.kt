package com.mobilecontrol.app.domain.model

/** Some states (e.g. a "json" type holding an event log) carry values many KB long. Rendering
 *  that directly in a single-line UI Text crashed the app live: Compose measures a Text's
 *  *intrinsic* (unconstrained) width before wrapping/clipping it, and an unbroken multi-KB string
 *  produced a negative remaining width for the rest of the row (IllegalArgumentException:
 *  "maxWidth must be >= minWidth" inside ListItem's measure pass). Truncating the actual string
 *  content - not just a visual ellipsis, which still requires measuring the full string first -
 *  keeps intrinsic measurement bounded regardless of how large the underlying value is. */
const val MAX_VALUE_DISPLAY_LENGTH = 120

fun formatLiveValueForDisplay(liveValue: Any?, unit: String? = null): String {
    if (liveValue == null) return "—"
    val raw = "$liveValue${unit?.let { " $it" } ?: ""}"
    return if (raw.length > MAX_VALUE_DISPLAY_LENGTH) raw.take(MAX_VALUE_DISPLAY_LENGTH) + "…" else raw
}
