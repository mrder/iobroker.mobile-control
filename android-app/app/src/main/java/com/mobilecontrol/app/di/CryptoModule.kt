package com.mobilecontrol.app.di

import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * No @Provides needed here: KeystoreManager, TokenStore and TokenAuthenticator are all
 * constructor-injected (@Inject) directly and Hilt resolves them without an explicit binding.
 * This module is kept as the documented seam for crypto-related bindings (e.g. if BiometricManager
 * or a KeyStore alias strategy ever needs to become configurable/testable).
 */
@Module
@InstallIn(SingletonComponent::class)
object CryptoModule
