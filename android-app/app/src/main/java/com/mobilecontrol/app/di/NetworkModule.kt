package com.mobilecontrol.app.di

import com.mobilecontrol.app.BuildConfig
import com.mobilecontrol.app.data.remote.ApiService
import com.mobilecontrol.app.data.remote.AppInfoInterceptor
import com.mobilecontrol.app.data.remote.AuthHeaderInterceptor
import com.mobilecontrol.app.data.remote.DynamicBaseUrlInterceptor
import com.mobilecontrol.app.data.remote.ServerConfigHolder
import com.mobilecontrol.app.data.remote.TokenAuthenticator
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Qualifier
import javax.inject.Singleton

/** Marks the dedicated client/Retrofit/ApiService that [TokenAuthenticator] uses to call
 *  auth/refresh - see that class's provider doc below for why it must not be the shared client. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class AuthRefresh

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }

    // ServerConfigHolder is constructor-injected (@Inject) directly - no @Provides needed here.

    @Provides
    @Singleton
    fun provideOkHttpClient(
        appInfoInterceptor: AppInfoInterceptor,
        authHeaderInterceptor: AuthHeaderInterceptor,
        dynamicBaseUrlInterceptor: DynamicBaseUrlInterceptor,
        tokenAuthenticator: TokenAuthenticator,
    ): OkHttpClient {
        val logging = HttpLoggingInterceptor().apply {
            // BODY logging is intentionally restricted to debug builds - request/response bodies can
            // contain tokens/signatures and must never hit logcat in release.
            level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BASIC else HttpLoggingInterceptor.Level.NONE
        }
        return OkHttpClient.Builder()
            .addInterceptor(dynamicBaseUrlInterceptor)
            .addInterceptor(appInfoInterceptor)
            .addInterceptor(authHeaderInterceptor)
            .addInterceptor(logging)
            .authenticator(tokenAuthenticator)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .pingInterval(20, TimeUnit.SECONDS)
            .build()
    }

    /**
     * A completely separate client/Dispatcher for TokenAuthenticator's own auth/refresh call -
     * confirmed live as a real deadlock otherwise: [provideOkHttpClient]'s Authenticator runs
     * runBlocking { ... } on one of that SAME client's own dispatcher threads on every 401, and
     * OkHttp's Dispatcher caps concurrent requests per host (default 5) across ALL calls on that
     * client, including the app's long-lived realtime WebSocket connection, which occupies one of
     * those slots for as long as it's open. Once the other slots are also busy, the blocked
     * refresh call needs a slot from the very pool its own blocking thread is preventing from
     * freeing up - a real deadlock, not just a slow retry, reproduced live as a Web-Seite widget
     * stuck on "Lädt…" forever the moment its access token happened to be expired. A client with
     * its own independent Dispatcher can never contend with the main client's slots.
     */
    @Provides
    @Singleton
    @AuthRefresh
    fun provideAuthRefreshOkHttpClient(
        appInfoInterceptor: AppInfoInterceptor,
        dynamicBaseUrlInterceptor: DynamicBaseUrlInterceptor,
    ): OkHttpClient {
        val logging = HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BASIC else HttpLoggingInterceptor.Level.NONE
        }
        return OkHttpClient.Builder()
            .addInterceptor(dynamicBaseUrlInterceptor)
            .addInterceptor(appInfoInterceptor)
            .addInterceptor(logging)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    @Provides
    @Singleton
    @AuthRefresh
    fun provideAuthRefreshRetrofit(@AuthRefresh okHttpClient: OkHttpClient, json: Json): Retrofit {
        val contentType = "application/json".toMediaType()
        return Retrofit.Builder()
            .baseUrl(ServerConfigHolder.PLACEHOLDER_BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory(contentType))
            .build()
    }

    @Provides
    @Singleton
    @AuthRefresh
    fun provideAuthRefreshApiService(@AuthRefresh retrofit: Retrofit): ApiService = retrofit.create(ApiService::class.java)

    @Provides
    @Singleton
    fun provideRetrofit(okHttpClient: OkHttpClient, json: Json): Retrofit {
        val contentType = "application/json".toMediaType()
        return Retrofit.Builder()
            .baseUrl(ServerConfigHolder.PLACEHOLDER_BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory(contentType))
            .build()
    }

    @Provides
    @Singleton
    fun provideApiService(retrofit: Retrofit): ApiService = retrofit.create(ApiService::class.java)
}
