package com.naqaa.app.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.naqaa.app.NaqaaApplication
import com.naqaa.app.R
import com.naqaa.app.content.Content
import com.naqaa.app.guard.DelayGate
import com.naqaa.app.guard.GuardGrace
import com.naqaa.app.util.LocaleX
import com.naqaa.app.util.SystemGate
import com.naqaa.app.util.TimeX
import kotlinx.coroutines.delay

/**
 * The wait before protection can be switched off.
 *
 * Android does not let one application forbid the user from disabling another, so this
 * screen does what the platform allows: it blocks the settings page for ten minutes, names
 * what is about to be lost, shows one verse, and only then offers the way forward. The
 * countdown is not persisted, so closing the screen restarts it.
 */
class DelayActivity : ComponentActivity() {

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleX.localized(newBase, languageOf(newBase)))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        val action = DelayGate.actionOf(intent.getStringExtra(EXTRA_ACTION))
        setContent {
            com.naqaa.app.ui.theme.NaqaaTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    Screen(action = action)
                }
            }
        }
    }

    @Composable
    private fun Screen(action: DelayGate.Action) {
        var remaining by remember { mutableIntStateOf(DelayGate.DURATION.seconds.toInt()) }
        var decided by remember { mutableStateOf(false) }
        val card = remember { Content.protectionCard() }
        val preferences = remember { (application as NaqaaApplication).graph.current() }

        LaunchedEffect(Unit) {
            while (remaining > 0) {
                delay(1_000)
                remaining -= 1
            }
            decided = true
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = stringResource(R.string.delay_title), style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(10.dp))
            Text(
                text = stringResource(
                    when (action) {
                        DelayGate.Action.VPN_OFF -> R.string.delay_vpn_off
                        DelayGate.Action.ACCESSIBILITY_OFF -> R.string.delay_accessibility_off
                        DelayGate.Action.ADMIN_OFF -> R.string.delay_admin_off
                        DelayGate.Action.UNINSTALL -> R.string.delay_uninstall
                        DelayGate.Action.LOCK_DISABLE -> R.string.delay_lock_off
                        DelayGate.Action.LOCK_OFF -> R.string.delay_lock_off
                        DelayGate.Action.SHORTS_OFF -> R.string.delay_shorts_off
                        DelayGate.Action.OTHER -> R.string.delay_other
                    }
                ),
                style = MaterialTheme.typography.bodyLarge
            )
            Spacer(Modifier.height(18.dp))
            Text(text = card.verseAr, style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(6.dp))
            Text(text = card.referenceAr, style = MaterialTheme.typography.labelMedium)
            Spacer(Modifier.height(18.dp))
            LinearProgressIndicator(
                progress = { 1f - remaining.toFloat() / DelayGate.DURATION.seconds.toFloat() },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.delay_remaining, TimeX.countdown(remaining.toLong())),
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(Modifier.height(20.dp))
            Button(
                onClick = {
                    GuardGrace.suppress(minutes = GRACE_MINUTES)
                    openSettings(action)
                    finish()
                },
                enabled = decided,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = stringResource(R.string.delay_continue))
            }
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = { finish() }, modifier = Modifier.fillMaxWidth()) {
                Text(text = stringResource(R.string.delay_cancel))
            }
            if (!decided) {
                Spacer(Modifier.height(6.dp))
                Text(text = stringResource(R.string.delay_hint), style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.height(12.dp))
            Text(
                text = card.motivation(preferences.language),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
        }
    }

    private fun openSettings(action: DelayGate.Action) {
        if (action == DelayGate.Action.VPN_OFF) {
            runCatching {
                (application as NaqaaApplication).graph.record(
                    kind = com.naqaa.app.data.EventKind.GUARD_OFF,
                    note = NOTE_USER_DISABLED
                )
                stopService(Intent(this, com.naqaa.app.vpn.DnsVpnService::class.java))
            }
        }
        val intent = when (action) {
            DelayGate.Action.VPN_OFF -> SystemGate.vpnSettings()
            DelayGate.Action.ACCESSIBILITY_OFF -> SystemGate.accessibilitySettings()
            DelayGate.Action.ADMIN_OFF -> SystemGate.deviceAdminSettings()
            else -> null
        }
        intent?.let { SystemGate.openSettings(this, it) }
    }

    companion object {
        const val EXTRA_ACTION = "action"
        private const val GRACE_MINUTES = 10
        private const val NOTE_USER_DISABLED = "user_disabled" 

        private fun languageOf(context: Context): String = runCatching {
            LocaleX.sanitize((context.applicationContext as NaqaaApplication).graph.current().language)
        }.getOrDefault(LocaleX.DEFAULT_LANGUAGE)
    }
}
