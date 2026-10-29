package com.naqaa.app.ui

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.naqaa.app.util.CrashLog
import com.naqaa.app.util.SystemGate
import com.naqaa.app.vpn.DnsVpnService

/**
 * A transparent screen that asks Android for VPN consent again. It is opened by the guard
 * when another VPN took the place of the local filter; accepting the system dialog starts the
 * filter again and ends the other VPN, declining leaves things as they are.
 */
class VpnResumeActivity : ComponentActivity() {

    private val launcher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) start()
        finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val consent = runCatching { SystemGate.vpnConsentMissing(this) }
            .onFailure { CrashLog.note(this, "vpn resume", it) }
            .getOrNull()
        if (consent == null) {
            start()
            finish()
        } else {
            runCatching { launcher.launch(consent) }.onFailure {
                CrashLog.note(this, "vpn resume", it)
                finish()
            }
        }
    }

    private fun start() {
        runCatching { ContextCompat.startForegroundService(this, Intent(this, DnsVpnService::class.java)) }
            .onFailure { CrashLog.note(this, "vpn resume start", it) }
    }
}
