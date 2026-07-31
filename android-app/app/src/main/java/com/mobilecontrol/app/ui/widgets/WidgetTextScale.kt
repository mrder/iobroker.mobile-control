package com.mobilecontrol.app.ui.widgets

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.TextUnitType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * How much smaller/larger widget text and icons should render relative to their MaterialTheme
 * baseline. Set once per widget in [DashboardGrid], derived from that widget's own actual
 * rendered cell size (cellWidth * w, rowHeight * h) - live-requested (2026-07-30) because a
 * denser grid only reflowed text onto more lines before, which then got clipped by the now-
 * shorter cell instead of actually shrinking to fit. Used as [AutoSizeText]'s starting guess
 * (below) - a dp-only estimate turned out unreliable on its own (doesn't account for the card's
 * own padding or real per-character glyph width), so the core "Kern-Info" value texts also
 * measure and shrink further for real via [AutoSizeText] rather than trusting this number alone.
 */
val LocalWidgetTextScale = compositionLocalOf { 1f }

@Composable
fun TextStyle.scaledForWidget(): TextStyle {
    val scale = LocalWidgetTextScale.current
    if (scale == 1f) return this
    return copy(
        fontSize = fontSize * scale,
        lineHeight = if (lineHeight.type == TextUnitType.Sp) lineHeight * scale else lineHeight,
    )
}

@Composable
fun widgetIconSize(base: Dp = 24.dp): Dp = base * LocalWidgetTextScale.current

/**
 * A single-line [Text] that shrinks its own font size step by step until it actually fits the
 * available width, instead of guessing a scale from cell dimensions alone (see
 * [LocalWidgetTextScale] - that guess is still used as the starting size, since it usually needs
 * few or no correction steps, but real measurement is what guarantees the "Kern-Info" value
 * (e.g. "35.4 °C") is never silently truncated to "35...." again like the dp-only heuristic did
 * on a live device, live-reported 2026-07-30).
 *
 * Deliberately checks [TextLayoutResult.isLineEllipsized] rather than `didOverflowWidth` to
 * decide whether to shrink further - with `overflow = TextOverflow.Ellipsis` already set,
 * Compose truncates the line to fit *before* computing `didOverflowWidth`, so that flag reports
 * "false" (no overflow) for a line that was only made to fit *because* it got ellipsized. Using
 * it as the shrink trigger meant the search never engaged at all: root-caused live on a real
 * device (2026-07-30) by logging the actual layout result, which showed `didOverflowWidth=false`
 * for a value that was visibly cut down to "88...." on screen.
 */
@Composable
fun AutoSizeText(text: String, style: TextStyle, modifier: Modifier = Modifier, minFontSize: TextUnit = 11.sp) {
    val scale = LocalWidgetTextScale.current
    val startingFontSize = style.fontSize * scale
    // Deliberately NOT keyed on `text`: a live value (e.g. a temperature reading) can change on
    // almost every recomposition, and re-starting the shrink search from full size on every one
    // of those updates meant it never got to actually converge before the next value arrived.
    // Only resets when the available space itself changes (startingFontSize, driven by
    // LocalWidgetTextScale - see DashboardGrid).
    var fontSize by remember(startingFontSize) { mutableStateOf(startingFontSize) }
    Text(
        text = text,
        style = style,
        fontSize = fontSize,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier,
        onTextLayout = { result ->
            if (result.isLineEllipsized(0) && fontSize.value > minFontSize.value) {
                fontSize = (fontSize.value * 0.9f).coerceAtLeast(minFontSize.value).sp
            }
        },
    )
}
