package com.mobilecontrol.app.ui.widgets

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.mobilecontrol.app.domain.model.ObjectCatalogItem
import com.mobilecontrol.app.domain.model.Widget
import com.mobilecontrol.app.domain.model.WidgetType

// Numeric bounds used when a widget's catalog entry isn't available yet (e.g. added before the
// catalog finished loading, or the object was since removed from the catalog) - continuous 0..100
// is a reasonable generic default for a slider/shutter/thermostat until real min/max arrive.
private const val DEFAULT_MIN = 0.0
private const val DEFAULT_MAX = 100.0
private const val DEFAULT_THERMOSTAT_STEP = 0.5

@Composable
fun WidgetHost(
    widget: Widget,
    state: WidgetState,
    modifier: Modifier = Modifier,
    canWrite: Boolean = false,
    isOnline: Boolean = true,
    /** The catalog entry backing widget.objectId, if resolved - carries min/max/step/confirmPolicy. */
    catalogItem: ObjectCatalogItem? = null,
    /** Called for every write command with the resolved `confirmed` flag already applied by the
     * shared confirmation gate below (see [rememberConfirmationGate]). */
    onCommand: (value: Any?, confirmed: Boolean) -> Unit = { _, _ -> },
) {
    val confirmPolicy = catalogItem?.confirmPolicy ?: "NONE"
    val blockedOnMobile = confirmPolicy == "BLOCKED_ON_MOBILE"
    val writable = canWrite && isOnline && !blockedOnMobile
    val title = if (blockedOnMobile) "${widget.title} (nicht mobil steuerbar)" else widget.title

    val min = catalogItem?.min ?: DEFAULT_MIN
    val max = catalogItem?.max ?: DEFAULT_MAX
    val step = catalogItem?.step

    // A single gate instance per WidgetHost call (i.e. per grid cell) is enough - only one
    // confirmation can be in flight for a given widget at a time.
    val gate = rememberConfirmationGate()

    fun sendGated(value: Any?) {
        // The server only needs confirmed=true when a policy actually required a confirmation
        // step; NONE keeps sending confirmed=false as before.
        gate.request(confirmPolicy) { onCommand(value, confirmPolicy != "NONE") }
    }

    when (widget.type) {
        WidgetType.TEXT_VALUE -> TextValueWidget(title, widget.config["unit"], state, modifier)
        WidgetType.TEMPERATURE -> TemperatureWidget(title, state, widget.config["unit"], modifier)
        WidgetType.HUMIDITY -> HumidityWidget(title, state, widget.config["unit"], modifier)
        WidgetType.BOOLEAN_STATUS -> BooleanStatusWidget(title, state, modifier)
        WidgetType.SWITCH -> SwitchWidget(
            title = title,
            state = state,
            enabled = writable,
            modifier = modifier,
            onToggle = { on -> sendGated(on) },
        )
        WidgetType.HISTORY -> HistoryWidget(title = title, objectId = widget.objectId, unit = widget.config["unit"], modifier = modifier)
        WidgetType.ALARM -> AlarmWidget(title = title, objectId = widget.objectId, state = state, modifier = modifier)
        WidgetType.CAMERA -> CameraWidget(title = title, objectId = widget.objectId, modifier = modifier)
        WidgetType.URL_IMAGE -> UrlImageWidget(title = title, urlEmbedId = widget.config["urlEmbedId"], modifier = modifier)
        WidgetType.WEB_VIEW -> WebPageWidget(
            title = title,
            urlEmbedId = widget.config["urlEmbedId"],
            showLivePreview = widget.config["previewMode"] != "button",
            useTunnel = widget.config["tunnel"] == "on",
            modifier = modifier,
        )
        WidgetType.LABEL -> LabelWidget(title = title, modifier = modifier)
        WidgetType.MOMENTARY_BUTTON -> MomentaryButtonWidget(
            title = title,
            state = state,
            enabled = writable,
            modifier = modifier,
            onPress = { sendGated(true) },
        )
        WidgetType.SLIDER -> SliderWidget(
            title = title,
            state = state,
            min = min,
            max = max,
            step = step,
            enabled = writable,
            modifier = modifier,
            onValueChangeFinished = { value -> sendGated(value) },
        )
        WidgetType.ROLLER_SHUTTER -> RollerShutterWidget(
            title = title,
            state = state,
            min = min,
            max = max,
            enabled = writable,
            modifier = modifier,
            onSetPosition = { value -> sendGated(value) },
        )
        WidgetType.THERMOSTAT -> ThermostatWidget(
            title = title,
            state = state,
            min = min,
            max = max,
            step = step ?: DEFAULT_THERMOSTAT_STEP,
            enabled = writable,
            modifier = modifier,
            onSetTarget = { value -> sendGated(value) },
        )
    }
}
