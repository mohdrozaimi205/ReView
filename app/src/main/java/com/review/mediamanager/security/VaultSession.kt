package com.review.mediamanager.security

import android.content.Context
import android.util.LruCache
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** In-memory unlock state of the Private Vault. Nothing here is persisted. */
object VaultSession {
    private val _unlocked = MutableStateFlow(false)
    val unlocked: StateFlow<Boolean> = _unlocked.asStateFlow()

    /** Small cache of decrypted vault thumbnails, wiped on lock. */
    val thumbCache: LruCache<Long, ByteArray> = object : LruCache<Long, ByteArray>(8 * 1024 * 1024) {
        override fun sizeOf(key: Long, value: ByteArray) = value.size
    }

    /** Set before opening a system picker/share sheet so leaving the app briefly doesn't lock the vault. */
    @Volatile var keepUnlockedOnce = false

    fun unlock() { _unlocked.value = true }

    fun lock() {
        _unlocked.value = false
        thumbCache.evictAll()
    }

    fun onAppBackgrounded(autoLock: Boolean) {
        if (keepUnlockedOnce) { keepUnlockedOnce = false; return }
        if (autoLock) lock()
    }
}

object BiometricHelper {
    private const val AUTH = BiometricManager.Authenticators.BIOMETRIC_STRONG or
        BiometricManager.Authenticators.BIOMETRIC_WEAK

    fun isAvailable(context: Context): Boolean =
        BiometricManager.from(context).canAuthenticate(AUTH) == BiometricManager.BIOMETRIC_SUCCESS

    fun authenticate(
        activity: FragmentActivity,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        val prompt = BiometricPrompt(
            activity,
            ContextCompat.getMainExecutor(activity),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = onSuccess()
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    if (errorCode != BiometricPrompt.ERROR_USER_CANCELED &&
                        errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON &&
                        errorCode != BiometricPrompt.ERROR_CANCELED
                    ) onError(errString.toString())
                }
            }
        )
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Unlock Private Vault")
            .setSubtitle("Use your fingerprint or face")
            .setNegativeButtonText("Use PIN")
            .setAllowedAuthenticators(AUTH)
            .build()
        prompt.authenticate(info)
    }
}
