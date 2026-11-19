package com.review.mediamanager.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Owns the vault master key inside the Android Keystore (hardware-backed where the phone
 * supports it). The master key never leaves the Keystore; it only wraps/unwraps the random
 * per-file data keys used by [VaultCrypto].
 */
class KeyManager {
    private val keyStore: KeyStore = KeyStore.getInstance(PROVIDER).apply { load(null) }

    @Synchronized
    private fun masterKey(): SecretKey {
        (keyStore.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, PROVIDER)
        gen.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setRandomizedEncryptionRequired(true)
                .build()
        )
        return gen.generateKey()
    }

    /** Returns iv(12) + ciphertext+tag. */
    fun wrap(dataKey: ByteArray): ByteArray {
        val c = Cipher.getInstance(TRANSFORMATION)
        c.init(Cipher.ENCRYPT_MODE, masterKey())
        val iv = c.iv
        val ct = c.doFinal(dataKey)
        return iv + ct
    }

    fun unwrap(wrapped: ByteArray): ByteArray {
        val iv = wrapped.copyOfRange(0, 12)
        val ct = wrapped.copyOfRange(12, wrapped.size)
        val c = Cipher.getInstance(TRANSFORMATION)
        c.init(Cipher.DECRYPT_MODE, masterKey(), GCMParameterSpec(128, iv))
        return c.doFinal(ct)
    }

    companion object {
        private const val PROVIDER = "AndroidKeyStore"
        private const val ALIAS = "review_vault_master_v1"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}
