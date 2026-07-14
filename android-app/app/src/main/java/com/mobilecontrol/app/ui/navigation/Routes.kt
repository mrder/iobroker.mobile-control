package com.mobilecontrol.app.ui.navigation

object Routes {
    const val ONBOARDING_GRAPH = "onboarding"
    const val WELCOME = "onboarding/welcome"
    const val QR_SCAN = "onboarding/qr_scan"
    const val SERVER_CHECK = "onboarding/server_check"
    const val KEY_GENERATION = "onboarding/key_generation"
    const val PAIRING_WAIT = "onboarding/pairing_wait"
    const val PIN_SETUP = "onboarding/pin_setup"

    const val LOCK = "lock"
    const val REVOKED = "revoked"

    const val START = "start"
    const val DASHBOARDS = "start/dashboards"
    const val OBJECTS = "start/objects"
    const val NOTIFICATIONS = "start/notifications"
    const val SETTINGS = "start/settings"

    // The editor screen doubles as the live view (toggle via its edit-mode action) - a separate
    // read-only route was dropped as unnecessary duplication for MVP.
    const val DASHBOARD_EDITOR = "dashboard_editor/{dashboardId}"
    fun dashboardEditor(id: String) = "dashboard_editor/$id"
    const val DASHBOARD_EDITOR_ARG = "dashboardId"
}
