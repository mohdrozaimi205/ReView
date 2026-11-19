package com.review.mediamanager.security

import android.content.Context
import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/** Stores only a salted PBKDF2 hash of the vault PIN, with a brute-force lockout. */
class PinManager(context: Context) {
    private val prefs = context.getSharedPreferences("review_vault_security", Context.MODE_PRIVATE)

    val hasPin: Boolean get() = prefs.contains(KEY_HASH)

    fun setPin(pin: String) {
        require(pin.length in 4..8 && pin.all { it.isDigit() }) { "PIN must be 4–8 digits" }
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        prefs.edit()
            .putString(KEY_SALT, b64(salt))
            .putString(KEY_HASH, b64(hash(pin, salt)))
            .putInt(KEY_FAILS, 0)
            .putLong(KEY_LOCK_UNTIL, 0)
            .apply()
    }

    /** Milliseconds the user must still wait before trying again (0 = may try). */
    fun lockoutRemaining(): Long =
        (prefs.getLong(KEY_LOCK_UNTIL, 0) - System.currentTimeMillis()).coerceAtLeast(0)

    sealed class Result {
        data object Ok : Result()
        data class Wrong(val attemptsLeft: Int) : Result()
        data class LockedOut(val millis: Long) : Result()
    }

    fun verify(pin: String): Result {
        val wait = lockoutRemaining()
        if (wait > 0) return Result.LockedOut(wait)
        val salt = prefs.getString(KEY_SALT, null)?.let { unb64(it) } ?: return Result.Wrong(0)
        val expected = prefs.getString(KEY_HASH, null)?.let { unb64(it) } ?: return Result.Wrong(0)
        return if (MessageDigest.isEqual(expected, hash(pin, salt))) {
            prefs.edit().putInt(KEY_FAILS, 0).putLong(KEY_LOCK_UNTIL, 0).apply()
            Result.Ok
        } else {
            val fails = prefs.getInt(KEY_FAILS, 0) + 1
            if (fails >= MAX_ATTEMPTS) {
                val lockMs = 30_000L * (1 shl ((fails - MAX_ATTEMPTS).coerceAtMost(5)))
                prefs.edit().putInt(KEY_FAILS, fails).putLong(KEY_LOCK_UNTIL, System.currentTimeMillis() + lockMs).apply()
                Result.LockedOut(lockMs)
            } else {
                prefs.edit().putInt(KEY_FAILS, fails).apply()
                Result.Wrong(MAX_ATTEMPTS - fails)
            }
        }
    }

    private fun hash(pin: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(pin.toCharArray(), salt, 120_000, 256)
        return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
    }

    private fun b64(b: ByteArray) = Base64.encodeToString(b, Base64.NO_WRAP)
    private fun unb64(s: String) = Base64.decode(s, Base64.NO_WRAP)

    companion object {
        private const val KEY_HASH = "pin_hash"
        private const val KEY_SALT = "pin_salt"
        private const val KEY_FAILS = "pin_fails"
        private const val KEY_LOCK_UNTIL = "pin_lock_until"
        const val MAX_ATTEMPTS = 5
    }
}
