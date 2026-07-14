package com.mobilecontrol.app.data.remote

import com.mobilecontrol.app.BuildConfig
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject

/** Adds the app/API/platform/device identification headers required on every request. */
class AppInfoInterceptor @Inject constructor(
    private val serverConfigHolder: ServerConfigHolder,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val builder = chain.request().newBuilder()
            .header("X-App-Version", BuildConfig.VERSION_NAME)
            .header("X-Api-Version", BuildConfig.API_VERSION)
            .header("X-Platform", "android")

        serverConfigHolder.deviceId?.let { builder.header("X-Device-Id", it) }
        serverConfigHolder.instanceId?.let { builder.header("X-Instance-Id", it) }

        return chain.proceed(builder.build())
    }
}
