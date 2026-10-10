package com.naqaa.app.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.naqaa.app.util.CrashLog
import com.naqaa.app.util.SystemGate
import com.naqaa.app.vpn.DnsVpnService

/** Requests VPN consent and starts filtering as soon as Android returns approval. */
@Composable
fun rememberVpnStartAction(): () -> Unit {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) startVpn(context)
    }
    return {
        val preparation = runCatching { SystemGate.vpnConsentMissing(context) }
        if (preparation.isFailure) {
            preparation.exceptionOrNull()?.let { CrashLog.note(context, "vpn consent", it) }
        } else {
            val consent = preparation.getOrNull()
            if (consent == null) {
                startVpn(context)
            } else {
                runCatching { launcher.launch(consent) }
                    .onFailure { CrashLog.note(context, "vpn consent", it) }
            }
        }
    }
}

private fun startVpn(context: Context) {
    runCatching {
        ContextCompat.startForegroundService(context, Intent(context, DnsVpnService::class.java))
    }.onFailure { CrashLog.note(context, "vpn start", it) }
}
