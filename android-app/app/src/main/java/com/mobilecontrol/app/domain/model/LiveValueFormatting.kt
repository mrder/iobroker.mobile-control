package com.mobilecontrol.app.domain.model

/** Some states (e.g. a "json" type holding an event log) carry values many KB long. Rendering
 *  that directly in a single-line UI Text crashed the app live: Compose measures a Text's
 *  *intrinsic* (unconstrained) width before wrapping/clipping it, and an unbroken string
 *  produced a negative remaining width for the rest of the row (IllegalArgumentException:
 *  "maxWidth must be >= minWidth" inside ListItem's measure pass).
 *
 *  A first attempt truncated to 120 characters, which reduced but did not eliminate the crash
 *  (confirmed live: the width deficit shrank from -250 to -90, not to >= 0) - dense text like
 *  minified JSON has few word-break opportunities, so even a "short" 120-character string can
 *  still have a large unbroken intrinsic width. There is no character count that's safe for
 *  every possible value, so the list-row preview is now short enough to always be safe in
 *  practice (see ObjectListRow, which additionally hard-caps the Text's width via a Modifier as
 *  a second line of defense) and the *full, untruncated* value is only ever shown inside a
 *  dialog on tap (see ValueDetailDialog), where Compose measures against the dialog's own
 *  already-bounded width instead of needing an unconstrained intrinsic measurement. */
const val MAX_VALUE_PREVIEW_LENGTH = 24

fun formatLiveValueForDisplay(liveValue: Any?, unit: String? = null, maxLength: Int = MAX_VALUE_PREVIEW_LENGTH): String {
    if (liveValue == null) return "—"
    val raw = "$liveValue${unit?.let { " $it" } ?: ""}"
    return if (raw.length > maxLength) raw.take(maxLength) + "…" else raw
}
