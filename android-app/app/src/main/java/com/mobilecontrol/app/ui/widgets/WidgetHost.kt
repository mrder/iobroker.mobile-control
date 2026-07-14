package com.mobilecontrol.app.ui.widgets

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.mobilecontrol.app.domain.model.Widget
import com.mobilecontrol.app.domain.model.WidgetType

@Composable
fun WidgetHost(
    widget: Widget,
    state: WidgetState,
    modifier: Modifier = Modifier,
    canWrite: Boolean = false,
    isOnline: Boolean = true,
    onToggle: (Boolean) -> Unit = {},
) {
    when (widget.type) {
        WidgetType.TEXT_VALUE -> TextValueWidget(widget.title, widget.config["unit"], state, modifier)
        WidgetType.TEMPERATURE -> TemperatureWidget(widget.title, state, modifier)
        WidgetType.HUMIDITY -> HumidityWidget(widget.title, state, modifier)
        WidgetType.BOOLEAN_STATUS -> BooleanStatusWidget(widget.title, state, modifier)
        WidgetType.SWITCH -> SwitchWidget(widget.title, state, enabled = canWrite && isOnline, modifier = modifier, onToggle = onToggle)
        WidgetType.HISTORY_PLACEHOLDER -> HistoryPlaceholderWidget(widget.title, state, modifier)
    }
}
