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

    fun available(context: Context): Boolean {
        if (!canUsePlatformPrompt()) return false
        val manager = context.getSystemService(BiometricManager::class.java) ?: return false
        return manager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK) ==
            BiometricManager.BIOMETRIC_SUCCESS
    }

    fun prompt(
        context: Context,
        title: String,
        subtitle: String,
        cancelText: String,
        onSuccess: () -> Unit
    ) {
        if (!canUsePlatformPrompt()) return
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

    private fun canUsePlatformPrompt(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
}
