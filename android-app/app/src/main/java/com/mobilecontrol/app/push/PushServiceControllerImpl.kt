package com.mobilecontrol.app.push

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PushServiceControllerImpl @Inject constructor(
    @ApplicationContext private val context: Context,
) : PushServiceController {
    override fun start() = PushConnectionService.start(context)
    override fun stop() = PushConnectionService.stop(context)
}
