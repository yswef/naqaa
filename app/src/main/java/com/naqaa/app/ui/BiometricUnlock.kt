package com.naqaa.app.ui

import android.content.Context
import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricPrompt
import android.os.Build
import androidx.core.content.ContextCompat
import android.os.CancellationSignal

/**
 * Fingerprint or face unlock, using the platform prompt directly.
 *
 * The androidx biometric library is not used: the platform class is enough for a single
 * prompt and it keeps the dependency list short. On devices below Android 10 the prompt is
 * attempted only when the platform reports usable hardware, otherwise the PIN pad stays the
 * only way in.
 */
object BiometricUnlock {

    /**
     * The overload that takes an authenticator mask was added in API 30; Android 10 and 11
     * only have the no-argument form, and calling the newer one there ends the process with
     * a missing-method error, so each level uses the call it actually has.
     */
    @Suppress("DEPRECATION")
    fun available(context: Context): Boolean {
        if (!canUsePlatformPrompt()) return false
        return runCatching {
            val manager = context.getSystemService(BiometricManager::class.java) ?: return@runCatching false
            val result = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                manager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK)
            } else {
                manager.canAuthenticate()
            }
            result == BiometricManager.BIOMETRIC_SUCCESS
        }.getOrDefault(false)
    }

    fun prompt(
        context: Context,
        title: String,
        subtitle: String,
        cancelText: String,
        onSuccess: () -> Unit
    ) {
        if (!canUsePlatformPrompt()) return
        // A device that reports hardware but refuses the prompt must leave the PIN pad
        // usable instead of ending the process.
        runCatching {
            val signal = CancellationSignal()
            val prompt = BiometricPrompt.Builder(context)
                .setTitle(title)
                .setSubtitle(subtitle)
                .setNegativeButton(cancelText, ContextCompat.getMainExecutor(context)) { _, _ -> }
                .build()
            prompt.authenticate(
                signal,
                ContextCompat.getMainExecutor(context),
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult?) {
                        onSuccess()
                    }
                }
            )
        }
    }

    private fun canUsePlatformPrompt(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
}
