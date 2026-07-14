package com.mobilecontrol.app.data.local

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Tokens are held in EncryptedSharedPreferences (AES256-GCM, key wrapped by an Android Keystore
 * master key) rather than plain SharedPreferences or a plain DataStore file - they must never sit
 * on disk unencrypted.
 */
@Singleton
class TokenStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs = EncryptedSharedPreferences.create(
        context,
        "mobile_control_tokens",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    suspend fun saveTokens(accessToken: String, refreshToken: String, expiresAtEpochMillis: Long) =
        withContext(Dispatchers.IO) {
            prefs.edit()
                .putString(KEY_ACCESS, accessToken)
                .putString(KEY_REFRESH, refreshToken)
                .putLong(KEY_EXPIRES_AT, expiresAtEpochMillis)
                .apply()
        }

    suspend fun getAccessToken(): String? = withContext(Dispatchers.IO) { prefs.getString(KEY_ACCESS, null) }

    suspend fun getRefreshToken(): String? = withContext(Dispatchers.IO) { prefs.getString(KEY_REFRESH, null) }

    suspend fun getAccessTokenExpiresAt(): Long = withContext(Dispatchers.IO) { prefs.getLong(KEY_EXPIRES_AT, 0L) }

    suspend fun savePinHash(hash: String) = withContext(Dispatchers.IO) {
        prefs.edit().putString(KEY_PIN_HASH, hash).apply()
    }

    suspend fun getPinHash(): String? = withContext(Dispatchers.IO) { prefs.getString(KEY_PIN_HASH, null) }

    suspend fun clear() = withContext(Dispatchers.IO) {
        prefs.edit().clear().apply()
    }

    private companion object {
        const val KEY_ACCESS = "access_token"
        const val KEY_REFRESH = "refresh_token"
        const val KEY_EXPIRES_AT = "access_token_expires_at"
        const val KEY_PIN_HASH = "pin_hash"
    }
}
