package com.example.spikemobilewallet.data

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.spec.ECGenParameterSpec
import java.util.UUID

/**
 * Manages holder keys in Android Keystore.
 * These keys are used for Key Binding JWTs when presenting SD-JWT credentials.
 */
object HolderKeyManager {

    private const val KEYSTORE_PROVIDER = "AndroidKeyStore"

    fun generateHolderKey(): String {
        val alias = "holder_key_${UUID.randomUUID()}"

        val keyGenSpec = KeyGenParameterSpec.Builder(
            alias,
            KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY
        )
            .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
            .setDigests(KeyProperties.DIGEST_SHA256)
            .build()

        KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, KEYSTORE_PROVIDER)
            .apply { initialize(keyGenSpec) }
            .generateKeyPair()

        return alias
    }

    fun getPublicKeyJwk(alias: String): Map<String, String> {
        val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
        val publicKey = keyStore.getCertificate(alias)?.publicKey
            ?: throw IllegalStateException("No key found for alias: $alias")

        val ecPublicKey = publicKey as java.security.interfaces.ECPublicKey
        val point = ecPublicKey.w

        return mapOf(
            "kty" to "EC",
            "crv" to "P-256",
            "x" to android.util.Base64.encodeToString(
                point.affineX.toByteArray(),
                android.util.Base64.URL_SAFE or android.util.Base64.NO_PADDING or android.util.Base64.NO_WRAP
            ),
            "y" to android.util.Base64.encodeToString(
                point.affineY.toByteArray(),
                android.util.Base64.URL_SAFE or android.util.Base64.NO_PADDING or android.util.Base64.NO_WRAP
            )
        )
    }
}
