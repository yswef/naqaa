package com.naqaa.app.data

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Symmetric encryption for journal payloads and preferences.
 *
 * The AES-256-GCM key never leaves the Android Keystore, so a copy of the application
 * data directory is useless on another device. Each record binds its ciphertext to an
 * identifier through additional authenticated data, which prevents an attacker who can
 * write the database from moving a payload to a different row.
 */
class Vault(private val alias: String = KEY_ALIAS) {

    fun seal(plain: ByteArray, context: String): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        cipher.updateAAD(context.toByteArray(Charsets.UTF_8))
        return cipher.iv + cipher.doFinal(plain)
    }

    fun open(encrypted: ByteArray, context: String): ByteArray {
        require(encrypted.size >= IV_LENGTH + TAG_LENGTH) { "ciphertext too short" }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(TAG_LENGTH * 8, encrypted.copyOfRange(0, IV_LENGTH)))
        cipher.updateAAD(context.toByteArray(Charsets.UTF_8))
        return cipher.doFinal(encrypted.copyOfRange(IV_LENGTH, encrypted.size))
    }

    fun destroy() {
        val store = KeyStore.getInstance(PROVIDER).apply { load(null) }
        store.deleteEntry(alias)
    }

    fun exists(): Boolean = KeyStore.getInstance(PROVIDER).apply { load(null) }.containsAlias(alias)

    private fun key(): SecretKey {
        val store = KeyStore.getInstance(PROVIDER).apply { load(null) }
        (store.getKey(alias, null) as? SecretKey)?.let { return it }
        synchronized(this) {
            (store.getKey(alias, null) as? SecretKey)?.let { return it }
            val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, PROVIDER)
            generator.init(
                KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setKeySize(256)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setRandomizedEncryptionRequired(true)
                    .build()
            )
            return generator.generateKey()
        }
    }

    private companion object {
        const val PROVIDER = "AndroidKeyStore"
        const val KEY_ALIAS = "naqaa.vault.v1"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_LENGTH = 12
        const val TAG_LENGTH = 16
    }
}
