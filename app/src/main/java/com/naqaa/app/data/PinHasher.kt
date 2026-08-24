package com.naqaa.app.data

import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Derives the unlock PIN hash. The iteration count is deliberately high because a
 * six digit PIN has little entropy; it only slows an offline attack on a copied file.
 */
object PinHasher {

    const val MIN_LENGTH = 6
    const val MAX_LENGTH = 32

    fun salt(): ByteArray = ByteArray(SALT_LENGTH).also { SecureRandom().nextBytes(it) }

    fun hash(pin: CharArray, salt: ByteArray): ByteArray {
        require(isValid(pin)) { "PIN length or shape is outside the accepted range" }
        val spec = PBEKeySpec(pin, salt, ITERATIONS, KEY_BITS)
        return try {
            SecretKeyFactory.getInstance(ALGORITHM).generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
            pin.fill('\u0000')
        }
    }

    fun verify(pin: CharArray, salt: ByteArray, expected: ByteArray): Boolean {
        if (!isValid(pin)) {
            pin.fill('\u0000')
            return false
        }
        return MessageDigest.isEqual(hash(pin, salt), expected)
    }

    fun isValid(pin: CharArray): Boolean = pin.size in MIN_LENGTH..MAX_LENGTH && pin.all { it in '0'..'9' }

    private const val ALGORITHM = "PBKDF2WithHmacSHA256"
    private const val SALT_LENGTH = 16
    private const val ITERATIONS = 310_000
    private const val KEY_BITS = 256
}
