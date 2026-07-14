package com.mobilecontrol.app.data.crypto

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.Signature
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages the device's identity key pair inside the Android Keystore.
 *
 * We deliberately use EC P-256 (secp256r1) with ECDSA/SHA256 instead of Ed25519: as of API 34,
 * Ed25519 is not reliably hardware-backed across Android Keystore implementations (support varies
 * by OEM/StrongBox), whereas P-256 has broad, well-tested hardware-backed support. The server
 * contract accepts any EC-P256 SPKI public key, so this is purely a client-side choice.
 *
 * The private key is generated with setIsStrongBoxBacked-agnostic defaults (falls back gracefully
 * if StrongBox is unavailable) and is never exportable - only sign operations are exposed.
 */
@Singleton
class KeystoreManager @Inject constructor() {

    private val keyStore: KeyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

    fun hasKeyPair(): Boolean = keyStore.containsAlias(KEY_ALIAS)

    /**
     * Generates a fresh non-exportable EC P-256 key pair, replacing any previous one (e.g. on re-pairing).
     * Returns the public key encoded as base64 SPKI (X.509), matching what the claim endpoint expects.
     */
    suspend fun generateKeyPair(): String = withContext(Dispatchers.Default) {
        if (hasKeyPair()) {
            keyStore.deleteEntry(KEY_ALIAS)
        }
        val generator = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, ANDROID_KEYSTORE)
        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY,
        )
            .setAlgorithmParameterSpec(java.security.spec.ECGenParameterSpec("secp256r1"))
            .setDigests(KeyProperties.DIGEST_SHA256)
            .setUserAuthenticationRequired(false)
            .build()
        generator.initialize(spec)
        val keyPair = generator.generateKeyPair()
        Base64.encodeToString(keyPair.public.encoded, Base64.NO_WRAP)
    }

    fun publicKeyBase64(): String? {
        val cert = keyStore.getCertificate(KEY_ALIAS) ?: return null
        return Base64.encodeToString(cert.publicKey.encoded, Base64.NO_WRAP)
    }

    /** Signs raw bytes (e.g. the decoded auth-challenge nonce) with SHA256withECDSA using the Keystore-held private key. */
    suspend fun sign(data: ByteArray): ByteArray = withContext(Dispatchers.Default) {
        val privateKey = keyStore.getKey(KEY_ALIAS, null) as? java.security.PrivateKey
            ?: error("No key pair present - device is not paired")
        val signature = Signature.getInstance("SHA256withECDSA")
        signature.initSign(privateKey)
        signature.update(data)
        signature.sign()
    }

    fun deleteKeyPair() {
        if (hasKeyPair()) {
            keyStore.deleteEntry(KEY_ALIAS)
        }
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "mobile_control_device_identity"
    }
}
