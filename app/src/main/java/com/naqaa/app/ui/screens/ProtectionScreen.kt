package com.naqaa.app.ui.screens

import android.content.Intent
import android.net.VpnService
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.naqaa.app.R
import com.naqaa.app.data.LockRule
import com.naqaa.app.guard.DelayGate
import com.naqaa.app.ui.AppViewModel
import com.naqaa.app.ui.DelayActivity
import com.naqaa.app.ui.UiState
import com.naqaa.app.util.LocaleX
import com.naqaa.app.util.SystemGate
import com.naqaa.app.util.TimeX
import com.naqaa.app.vpn.DnsVpnService
import com.naqaa.app.vpn.VpnState

/**
 * Everything that stands between the user and the content: the local filter, the short
 * video rules, the locks, the night window and the permissions without which none of it
 * works. State is shown as it is, including the honest note that an application cannot
 * forbid its own removal.
 */
@Composable
fun ProtectionScreen(viewModel: AppViewModel, state: UiState) {
    val context = LocalContext.current
    val language = state.preferences.language
    val display = LocaleX.displayLocale(language)
    var showPicker by remember { mutableStateOf(false) }

    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        ScreenTitle(title = stringResource(R.string.protection_title), subtitle = stringResource(R.string.protection_subtitle))

        if (state.encouragement != null) {
            EncouragementCard(state.encouragement, language, viewModel::dismissEncouragement)
        }

        SectionCard(title = stringResource(R.string.protection_filter)) {
            StatLine(
                stringResource(R.string.protection_state),
                stringResource(if (state.vpnRunning) R.string.protection_on else R.string.protection_off)
            )
            StatLine(stringResource(R.string.protection_blocked), VpnState.blockedCount().toString())
            Spacer(Modifier.height(8.dp))
            if (state.vpnRunning) {
                OutlinedButton(
                    onClick = {
                        val intent = Intent(context, DelayActivity::class.java)
                            .putExtra(DelayActivity.EXTRA_ACTION, DelayGate.Action.VPN_OFF.name)
                        runCatching { context.startActivity(intent) }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(text = stringResource(R.string.protection_stop))
                }
            } else {
                Button(
                    onClick = {
                        val consent = VpnService.prepare(context)
                        if (consent != null) {
                            runCatching { context.startActivity(consent) }
                        } else {
                            runCatching {
                                androidx.core.content.ContextCompat.startForegroundService(
                                    context,
                                    Intent(context, DnsVpnService::class.java)
                                )
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(text = stringResource(R.string.protection_start))
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.protection_always_on_steps),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (SystemGate.privateDnsIsStrict(context)) {
            SectionCard(title = stringResource(R.string.protection_private_dns)) {
                Text(
                    text = stringResource(R.string.protection_private_dns_body, VpnState.privateDnsHost(context).orEmpty()),
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { runCatching { context.startActivity(SystemGate.privateDnsSettings()) } },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(text = stringResource(R.string.protection_open_settings))
                }
            }
        }

        SectionCard(title = stringResource(R.string.protection_rules)) {
            SettingSwitch(
                title = stringResource(R.string.protection_block_shorts),
                subtitle = stringResource(R.string.protection_block_shorts_hint),
                checked = state.preferences.blockShorts,
                onCheckedChange = { value -> viewModel.update { it.copy(blockShorts = value) } }
            )
            SettingSwitch(
                title = stringResource(R.string.protection_block_tiktok),
                subtitle = stringResource(R.string.protection_block_tiktok_hint),
                checked = state.preferences.blockTikTok,
                onCheckedChange = { value -> viewModel.update { it.copy(blockTikTok = value) } }
            )
            SettingSwitch(
                title = stringResource(R.string.protection_browser_keywords),
                subtitle = stringResource(R.string.protection_browser_keywords_hint),
                checked = state.preferences.blockBrowserKeywords,
                onCheckedChange = { value -> viewModel.update { it.copy(blockBrowserKeywords = value) } }
            )
            SettingSwitch(
                title = stringResource(R.string.protection_night),
                subtitle = stringResource(R.string.protection_night_hint),
                checked = state.preferences.nightMode,
                onCheckedChange = { value -> viewModel.update { it.copy(nightMode = value) } }
            )
            SettingSwitch(
                title = stringResource(R.string.protection_auto_start),
                subtitle = stringResource(R.string.protection_auto_start_hint),
                checked = state.preferences.vpnAutoStart,
                onCheckedChange = { value -> viewModel.update { it.copy(vpnAutoStart = value) } }
            )
        }

        SectionCard(title = stringResource(R.string.protection_locked_apps)) {
            if (state.preferences.lockedApps.isEmpty()) {
                Text(text = stringResource(R.string.protection_no_locks), style = MaterialTheme.typography.bodyMedium)
            }
            state.preferences.lockedApps.forEach { rule ->
                Column(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                    Text(text = rule.packageName, style = MaterialTheme.typography.bodyLarge)
                    val selected = LockPreset.of(rule)
                    Text(
                        text = selected?.let { stringResource(it.label) }
                            ?: stringResource(R.string.preset_custom, TimeX.clock(rule.startMinute, display), TimeX.clock(rule.endMinute, display)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Column {
                        LockPreset.entries.chunked(3).forEach { row ->
                            Row {
                                row.forEach { preset ->
                                    FilterChip(
                                        selected = selected == preset,
                                        onClick = {
                                            viewModel.update { preferences ->
                                                preferences.copy(
                                                    lockedApps = preferences.lockedApps.map { existing ->
                                                        if (existing.packageName == rule.packageName) {
                                                            LockRule(rule.packageName, preset.startMinute, preset.endMinute)
                                                        } else {
                                                            existing
                                                        }
                                                    }
                                                )
                                            }
                                        },
                                        label = { Text(text = stringResource(preset.label)) },
                                        modifier = Modifier.padding(end = 6.dp)
                                    )
                                }
                            }
                        }
                    }
                    TextButton(onClick = {
                        viewModel.update { preferences ->
                            preferences.copy(lockedApps = preferences.lockedApps.filterNot { it.packageName == rule.packageName })
                        }
                    }) {
                        Text(text = stringResource(R.string.protection_remove_lock))
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Button(onClick = { showPicker = true }, modifier = Modifier.fillMaxWidth()) {
                Text(text = stringResource(R.string.protection_add_lock))
            }
        }

        SectionCard(title = stringResource(R.string.protection_permissions)) {
            PermissionRow(
                title = stringResource(R.string.permission_accessibility),
                granted = SystemGate.accessibilityEnabled(context),
                grantedText = stringResource(R.string.permission_enabled),
                missingText = stringResource(R.string.permission_needed),
                onEnable = { runCatching { context.startActivity(SystemGate.accessibilitySettings()) } }
            )
            PermissionRow(
                title = stringResource(R.string.permission_admin),
                granted = SystemGate.isDeviceAdmin(context),
                grantedText = stringResource(R.string.permission_enabled),
                missingText = stringResource(R.string.permission_needed),
                onEnable = { runCatching { context.startActivity(SystemGate.deviceAdminRequest(context)) } }
            )
            PermissionRow(
                title = stringResource(R.string.permission_notifications),
                granted = SystemGate.notificationsEnabled(context),
                grantedText = stringResource(R.string.permission_enabled),
                missingText = stringResource(R.string.permission_needed),
                onEnable = { runCatching { context.startActivity(SystemGate.notificationSettings(context)) } }
            )
            PermissionRow(
                title = stringResource(R.string.permission_alarms),
                granted = SystemGate.canScheduleExactAlarms(context),
                grantedText = stringResource(R.string.permission_enabled),
                missingText = stringResource(R.string.permission_needed),
                onEnable = { runCatching { context.startActivity(SystemGate.exactAlarmSettings()) } }
            )
            PermissionRow(
                title = stringResource(R.string.permission_battery),
                granted = SystemGate.ignoringBatteryOptimizations(context),
                grantedText = stringResource(R.string.permission_enabled),
                missingText = stringResource(R.string.permission_needed),
                onEnable = { runCatching { context.startActivity(SystemGate.batterySettings()) } }
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.admin_explanation),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.height(20.dp))
    }

    if (showPicker) {
        AppPicker(
            excluded = state.preferences.lockedApps.map { it.packageName }.toSet(),
            own = context.packageName,
            onDismiss = { showPicker = false },
            onPick = { packageName ->
                viewModel.update { preferences ->
                    preferences.copy(lockedApps = preferences.lockedApps + LockRule(packageName, 0, 0))
                }
                showPicker = false
            }
        )
    }
}

/** The time windows offered for a locked application. */
private enum class LockPreset(val label: Int, val startMinute: Int, val endMinute: Int) {
    ALL_DAY(R.string.preset_all_day, 0, 0),
    NIGHT(R.string.preset_night, 22 * 60, 6 * 60),
    EVENING(R.string.preset_evening, 18 * 60, 23 * 60),
    MORNING(R.string.preset_morning, 5 * 60, 12 * 60),
    WORK(R.string.preset_work, 8 * 60, 17 * 60);

    companion object {
        fun of(rule: LockRule): LockPreset? = entries.firstOrNull {
            it.startMinute == rule.startMinute && it.endMinute == rule.endMinute
        }
    }
}

@Composable
internal fun AppPicker(
    excluded: Set<String>,
    own: String,
    onDismiss: () -> Unit,
    onPick: (String) -> Unit
) {
    val context = LocalContext.current
    val applications = remember {
        val manager = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        manager.queryIntentActivities(intent, 0)
            .mapNotNull { it.activityInfo?.applicationInfo }
            .distinctBy { it.packageName }
            .filter { it.packageName != own && it.packageName !in excluded }
            .map { it.packageName to manager.getApplicationLabel(it).toString() }
            .sortedBy { it.second }
    }
    var selected by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.protection_pick_app)) },
        text = {
            Column {
                if (applications.isEmpty()) {
                    Text(text = stringResource(R.string.protection_pick_app_empty))
                }
                LazyColumn(modifier = Modifier.fillMaxWidth().height(360.dp)) {
                    items(applications, key = { it.first }) { entry ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = selected == entry.first,
                                onCheckedChange = { selected = if (selected == entry.first) null else entry.first }
                            )
                            Text(text = entry.second, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { selected?.let(onPick) },
                enabled = selected != null
            ) { Text(text = stringResource(R.string.confirm)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(text = stringResource(R.string.cancel)) }
        }
    )
}
