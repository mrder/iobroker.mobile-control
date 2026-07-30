package com.mobilecontrol.app.ui.widgets

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnitType
import androidx.compose.ui.unit.dp

/**
 * How much smaller/larger widget text and icons should render relative to their MaterialTheme
 * baseline. Set once per dashboard render (see DashboardEditorScreen) from the user-adjustable
 * grid density (columns/row height, see DashboardEditorViewModel.updateGridSize) - live-requested
 * (2026-07-30) because a denser grid only reflowed text onto more lines before, which then got
 * clipped by the now-shorter cell instead of actually shrinking to fit.
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
