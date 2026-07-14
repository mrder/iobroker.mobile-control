package com.mobilecontrol.app.data.remote

import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject

class DynamicBaseUrlInterceptor @Inject constructor(
    private val serverConfigHolder: ServerConfigHolder,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        val realBase = serverConfigHolder.baseUrl
            ?: return chain.proceed(original) // not paired yet / no server known: let it fail naturally

        val originalUrlString = original.url.toString()
        if (!originalUrlString.startsWith(ServerConfigHolder.PLACEHOLDER_BASE_URL)) {
            return chain.proceed(original) // already pointing somewhere else (shouldn't happen)
        }
        val relative = originalUrlString.removePrefix(ServerConfigHolder.PLACEHOLDER_BASE_URL)
        val resolved = realBase.resolve(relative) ?: return chain.proceed(original)

        val newRequest = original.newBuilder().url(resolved).build()
        return chain.proceed(newRequest)
    }
}
