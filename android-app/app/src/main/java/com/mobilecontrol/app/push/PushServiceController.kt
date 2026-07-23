package com.mobilecontrol.app.push

/** Thin, testable wrapper around starting/stopping [PushConnectionService] - kept separate from
 *  the raw Android Context call so ViewModels that need to trigger it (AppRootViewModel,
 *  SettingsViewModel) stay unit-testable with a fake, same reasoning as [SystemNotifier]. */
interface PushServiceController {
    fun start()
    fun stop()
}
