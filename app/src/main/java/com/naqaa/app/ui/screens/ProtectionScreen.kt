package com.naqaa.app.ui.screens

import android.Manifest
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.naqaa.app.data.Session
import com.naqaa.app.guard.UsagePass
import com.naqaa.app.ui.theme.NaqaaOutlinedTextFieldColors
import com.naqaa.app.ui.theme.Neon
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.core.content.ContextCompat
import com.naqaa.app.R
import com.naqaa.app.data.LockRule
import com.naqaa.app.guard.DelayGate
import com.naqaa.app.ui.AppViewModel
import com.naqaa.app.ui.DelayActivity
import com.naqaa.app.ui.UiState
import com.naqaa.app.ui.rememberVpnStartAction
import com.naqaa.app.util.LocaleX
import com.naqaa.app.util.SystemGate
import com.naqaa.app.util.TimeX
import com.naqaa.app.vpn.VpnState

/** Local filtering, app locks, schedules, and permission status. */
@Composable
fun ProtectionScreen(viewModel: AppViewModel, state: UiState, onOpenCleanup: () -> Unit = {}) {
    val context = LocalContext.current
    val language = state.preferences.language
    val display = LocaleX.displayLocale(language)
    var showPicker by remember { mutableStateOf(false) }
    val resumeToken = rememberResumeToken()
    val startVpn = rememberVpnStartAction()
    var notificationDenied by remember { mutableStateOf(false) }
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        notificationDenied = !granted || !SystemGate.notificationsEnabled(context)
    }
    val notificationPermissionGranted = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    val showNotificationPrompt = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        !notificationDenied && !notificationPermissionGranted
    val notificationsGranted = remember(resumeToken) { SystemGate.notificationsEnabled(context) }
    val adminEnabled = remember(resumeToken) { SystemGate.isDeviceAdmin(context) }
    val alarmsEnabled = remember(resumeToken) { SystemGate.canScheduleExactAlarms(context) }

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
                    onClick = startVpn,
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
                    onClick = { SystemGate.openSettings(context, SystemGate.privateDnsSettings()) },
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
                title = stringResource(R.string.protection_block_vpn),
                subtitle = stringResource(R.string.protection_block_vpn_hint),
                checked = state.preferences.blockOtherVpn,
                onCheckedChange = { value -> viewModel.update { it.copy(blockOtherVpn = value) } }
            )
            SettingSwitch(
                title = stringResource(R.string.protection_auto_start),
                subtitle = stringResource(R.string.protection_auto_start_hint),
                checked = state.preferences.vpnAutoStart,
                onCheckedChange = { value -> viewModel.update { it.copy(vpnAutoStart = value) } }
            )
        }

        ShortsPassCard(state)

        SectionCard(title = stringResource(R.string.protection_vpn_report)) {
            Text(
                text = stringResource(R.string.protection_vpn_report_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = { copyText(context, com.naqaa.app.util.Diagnostics.vpnReport(context)) },
                modifier = Modifier.fillMaxWidth()
            ) { Text(text = stringResource(R.string.protection_vpn_report_copy)) }
        }

        SectionCard(title = stringResource(R.string.cleanup_title)) {
            Text(
                text = stringResource(R.string.cleanup_card_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = onOpenCleanup, modifier = Modifier.fillMaxWidth()) {
                Text(text = stringResource(R.string.cleanup_open))
            }
        }

        SectionCard(title = stringResource(R.string.protection_locked_apps)) {
            if (state.preferences.lockedApps.isEmpty()) {
                Text(text = stringResource(R.string.protection_no_locks), style = MaterialTheme.typography.bodyMedium)
            }
            state.preferences.lockedApps.forEach { rule ->
                Column(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val icon = rememberAppIcon(rule.packageName)
                        if (icon != null) {
                            Image(bitmap = icon, contentDescription = null, modifier = Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)))
                            Spacer(Modifier.width(10.dp))
                        }
                        Text(text = appLabel(rule.packageName), style = MaterialTheme.typography.bodyLarge)
                    }
                    val selected = LockPreset.of(rule)
                    Text(
                        text = selected?.let { stringResource(it.label) }
                            ?: stringResource(R.string.preset_custom, TimeX.clock(rule.startMinute, display), TimeX.clock(rule.endMinute, display)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    ChipFlow {
                        LockPreset.entries.forEach { preset ->
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
                                label = { Text(text = stringResource(preset.label)) }
                            )
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
            AccessibilityPermissionRow()
            PermissionRow(
                title = stringResource(R.string.permission_admin),
                granted = adminEnabled,
                grantedText = stringResource(R.string.permission_enabled),
                missingText = stringResource(R.string.permission_needed),
                onEnable = { SystemGate.openSettings(context, SystemGate.deviceAdminRequest(context)) }
            )
            PermissionRow(
                title = stringResource(R.string.permission_notifications),
                granted = notificationsGranted,
                grantedText = stringResource(R.string.permission_enabled),
                missingText = stringResource(R.string.permission_needed),
                actionText = stringResource(
                    if (showNotificationPrompt) R.string.permission_enable else R.string.permission_settings
                ),
                onEnable = {
                    if (showNotificationPrompt) {
                        notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        SystemGate.openSettings(context, SystemGate.notificationSettings(context))
                    }
                }
            )
            PermissionRow(
                title = stringResource(R.string.permission_alarms),
                granted = alarmsEnabled,
                grantedText = stringResource(R.string.permission_enabled),
                missingText = stringResource(R.string.permission_needed),
                onEnable = { SystemGate.openSettings(context, SystemGate.exactAlarmSettings(context)) }
            )
            BatteryPermissionRow()
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
            onPick = { packages, preset ->
                viewModel.update { preferences ->
                    preferences.copy(
                        lockedApps = preferences.lockedApps +
                            packages.map { LockRule(it, preset.startMinute, preset.endMinute) }
                    )
                }
                showPicker = false
            }
        )
    }
}

/** The time windows offered for a locked application. */
internal enum class LockPreset(val label: Int, val startMinute: Int, val endMinute: Int) {
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

private data class AppEntry(val packageName: String, val label: String)

/**
 * Full screen picker for the applications to lock: a grid of launcher icons with a search
 * field. Any number can be selected at once; the chosen window applies to all of them.
 */
@Composable
internal fun AppPicker(
    excluded: Set<String>,
    own: String,
    onDismiss: () -> Unit,
    onPick: (Set<String>, LockPreset) -> Unit
) {
    val context = LocalContext.current
    var apps by remember { mutableStateOf<List<AppEntry>?>(null) }
    var query by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf(setOf<String>()) }
    var preset by remember { mutableStateOf(LockPreset.ALL_DAY) }

    LaunchedEffect(Unit) {
        apps = withContext(Dispatchers.Default) {
            val manager = context.packageManager
            val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            manager.queryIntentActivities(intent, 0)
                .mapNotNull { it.activityInfo?.applicationInfo }
                .distinctBy { it.packageName }
                .filter { it.packageName != own && it.packageName !in excluded }
                .map { AppEntry(it.packageName, manager.getApplicationLabel(it).toString()) }
                .sortedBy { it.label.lowercase() }
        }
    }
    val visible = remember(apps, query) {
        apps.orEmpty().filter { query.isBlank() || it.label.contains(query.trim(), ignoreCase = true) }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = Modifier.fillMaxSize().padding(8.dp),
            shape = RoundedCornerShape(26.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.98f),
            border = BorderStroke(1.5.dp, Brush.linearGradient(listOf(Neon.Cyan, Neon.Violet, Neon.Magenta)))
        ) {
            Column(modifier = Modifier.safeDrawingPadding().padding(16.dp)) {
                Text(
                    text = stringResource(R.string.protection_pick_app),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    colors = NaqaaOutlinedTextFieldColors(),
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    label = { Text(text = stringResource(R.string.search)) },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(10.dp))
                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    when {
                        apps == null -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                        visible.isEmpty() -> Text(
                            text = stringResource(R.string.protection_pick_app_empty),
                            modifier = Modifier.align(Alignment.Center)
                        )
                        else -> LazyVerticalGrid(
                            columns = GridCells.Adaptive(84.dp),
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            items(visible, key = { it.packageName }) { entry ->
                                val on = entry.packageName in selected
                                AppTile(entry, on) {
                                    selected = if (on) selected - entry.packageName else selected + entry.packageName
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                ChipFlow {
                    LockPreset.entries.forEach { option ->
                        FilterChip(
                            selected = preset == option,
                            onClick = { preset = option },
                            label = { Text(text = stringResource(option.label)) },
                            modifier = Modifier.padding(top = 3.dp, bottom = 3.dp)
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) {
                        Text(text = stringResource(R.string.cancel))
                    }
                    Button(
                        onClick = { onPick(selected, preset) },
                        enabled = selected.isNotEmpty(),
                        modifier = Modifier.weight(2f)
                    ) {
                        Text(text = stringResource(R.string.protection_lock_selected, selected.size))
                    }
                }
            }
        }
    }
}

/** One application: its icon in a rounded tile that lights up and grows when it is selected. */
@Composable
private fun AppTile(entry: AppEntry, selected: Boolean, onToggle: () -> Unit) {
    val icon = rememberAppIcon(entry.packageName)
    val scale by animateFloatAsState(if (selected) 1.08f else 1f, label = "tile")
    val ring = if (selected) Neon.Cyan else Color.Transparent
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onToggle)
            .padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(contentAlignment = Alignment.Center) {
            Box(
                modifier = Modifier
                    .size(62.dp)
                    .scale(scale)
                    .clip(RoundedCornerShape(18.dp))
                    .background(
                        if (selected) Neon.Cyan.copy(alpha = 0.18f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                    )
                    .border(2.dp, ring, RoundedCornerShape(18.dp)),
                contentAlignment = Alignment.Center
            ) {
                if (icon != null) {
                    Image(bitmap = icon, contentDescription = null, modifier = Modifier.size(46.dp))
                }
            }
            if (selected) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(Neon.Cyan),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = "✓", color = Color(0xFF001A20), style = MaterialTheme.typography.labelMedium)
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = entry.label,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.width(78.dp),
            textAlign = TextAlign.Center
        )
    }
}

/**
 * The time the user gives himself for short videos. While a pass runs the short video rules
 * stand aside; when it ends the next block arrives as a screen saying the time is over. The
 * pass is kept in memory only, and starting one asks for the PIN (the developer session
 * does not).
 */
@Composable
private fun ShortsPassCard(state: UiState) {
    var remaining by remember { mutableLongStateOf(UsagePass.shortsRemainingSeconds()) }
    var askingFor by remember { mutableStateOf<Int?>(null) }
    var custom by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        while (true) {
            remaining = UsagePass.shortsRemainingSeconds()
            delay(1_000)
        }
    }

    fun begin(minutes: Int) {
        if (Session.developer || !state.preferences.hasPin) {
            UsagePass.allowShorts(minutes)
            remaining = UsagePass.shortsRemainingSeconds()
        } else {
            askingFor = minutes
        }
    }

    SectionCard(title = stringResource(R.string.pass_title)) {
        Text(
            text = stringResource(R.string.pass_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(8.dp))
        if (remaining > 0) {
            StatLine(stringResource(R.string.pass_remaining), TimeX.countdown(remaining))
            OutlinedButton(
                onClick = {
                    UsagePass.cancelShorts()
                    remaining = 0
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text(text = stringResource(R.string.pass_end_now)) }
        } else {
            ChipFlow {
                listOf(15, 30, 60, 120).forEach { minutes ->
                    FilterChip(
                        selected = false,
                        onClick = { begin(minutes) },
                        label = { Text(text = stringResource(R.string.minutes_short, minutes)) },
                        modifier = Modifier.padding(top = 3.dp, bottom = 3.dp)
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    colors = NaqaaOutlinedTextFieldColors(),
                    value = custom,
                    onValueChange = { custom = it.filter(Char::isDigit).take(3) },
                    singleLine = true,
                    label = { Text(text = stringResource(R.string.pass_custom)) },
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = { custom.toIntOrNull()?.takeIf { it in 1..600 }?.let(::begin) },
                    enabled = custom.toIntOrNull()?.let { it in 1..600 } == true
                ) { Text(text = stringResource(R.string.start)) }
            }
        }
    }
    askingFor?.let { minutes ->
        PinPrompt(
            preferences = state.preferences,
            onCancel = { askingFor = null },
            onSuccess = {
                askingFor = null
                UsagePass.allowShorts(minutes)
                remaining = UsagePass.shortsRemainingSeconds()
            }
        )
    }
}
