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
import javax.inject.Singleton

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

    @Provides
    @Singleton
    fun provideServerConfigHolder(): ServerConfigHolder = ServerConfigHolder()

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
