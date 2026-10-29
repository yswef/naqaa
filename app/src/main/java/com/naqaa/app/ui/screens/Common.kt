package com.naqaa.app.ui.screens

import android.os.Build
import androidx.compose.foundation.BorderStroke
import androidx.compose.runtime.produceState
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.naqaa.app.R
import com.naqaa.app.content.Card as ContentCard
import com.naqaa.app.prayer.PrayerName
import com.naqaa.app.util.SystemGate

/** Shared UI components. */

@Composable
fun SectionCard(
    modifier: Modifier = Modifier,
    title: String? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = MaterialTheme.shapes.large
    val glow = MaterialTheme.colorScheme.primary
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .shadow(10.dp, shape, ambientColor = glow.copy(alpha = 0.5f), spotColor = glow.copy(alpha = 0.5f)),
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.82f)),
        border = BorderStroke(
            1.dp,
            Brush.linearGradient(
                listOf(
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.85f),
                    MaterialTheme.colorScheme.secondary.copy(alpha = 0.55f),
                    MaterialTheme.colorScheme.tertiary.copy(alpha = 0.7f)
                )
            )
        )
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            if (title != null) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.height(10.dp))
            }
            content()
        }
    }
}

/**
 * The launcher icon of an installed package, loaded off the main thread. It is null until
 * the image is ready (or when the package has no icon), so a grid can draw a placeholder.
 */
@Composable
fun rememberAppIcon(packageName: String, sizePx: Int = 96): ImageBitmap? {
    val context = LocalContext.current
    val icon by produceState<ImageBitmap?>(initialValue = null, packageName) {
        value = withContext(Dispatchers.Default) {
            runCatching {
                context.packageManager.getApplicationIcon(packageName).toBitmap(sizePx, sizePx).asImageBitmap()
            }.getOrNull()
        }
    }
    return icon
}

/**
 * A line whose text already carries its value. Used where the sentence is written with a
 * placeholder in the string resources, so the number stays inside the sentence instead of
 * being placed in a second column.
 */
@Composable
fun ValueLine(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
    )
}

@Composable
fun StatLine(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium)
        Text(text = value, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
fun SettingSwitch(title: String, subtitle: String? = null, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
fun rememberResumeToken(): Int {
    val lifecycleOwner = LocalLifecycleOwner.current
    var token by remember { mutableIntStateOf(0) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) token += 1
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    return token
}

@Composable
fun PermissionRow(
    title: String,
    granted: Boolean,
    grantedText: String,
    missingText: String,
    actionText: String? = null,
    onEnable: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = if (granted) grantedText else missingText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (!granted) {
            TextButton(onClick = onEnable) {
                Text(text = actionText ?: stringResource(R.string.permission_enable))
            }
        }
    }
}

@Composable
fun AccessibilityPermissionRow() {
    val context = LocalContext.current
    var attempted by remember { mutableStateOf(false) }
    var showSteps by remember { mutableStateOf(false) }
    val resumeToken = rememberResumeToken()
    var tick by remember { mutableStateOf(0) }
    val enabled = remember(resumeToken, tick) { SystemGate.accessibilityEnabled(context) }
    if (showSteps) {
        // From Android 13 the system refuses this service for a sideloaded app until the user
        // allows restricted settings on its info page, which cannot be done from inside the app.
        AlertDialog(
            onDismissRequest = { showSteps = false },
            title = { Text(text = stringResource(R.string.accessibility_steps_title)) },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Text(text = stringResource(R.string.accessibility_steps_body), style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(10.dp))
                    Text(text = stringResource(R.string.accessibility_adb_body), style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(6.dp))
                    Text(text = SystemGate.adbGrantCommand(context), style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    showSteps = false
                    attempted = true
                    SystemGate.openSettings(context, SystemGate.accessibilitySettings())
                }) { Text(text = stringResource(R.string.accessibility_open_settings)) }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = {
                        copyText(context, SystemGate.accessibilityReport(context))
                    }) { Text(text = stringResource(R.string.accessibility_copy_report)) }
                    TextButton(onClick = {
                        showSteps = false
                        SystemGate.openSettings(context, SystemGate.appDetailsSettings(context))
                    }) { Text(text = stringResource(R.string.permission_app_info)) }
                }
            }
        )
    }
    Column {
        PermissionRow(
            title = stringResource(R.string.permission_accessibility),
            granted = enabled,
            grantedText = stringResource(R.string.permission_enabled),
            missingText = stringResource(R.string.permission_needed),
            onEnable = {
                if (SystemGate.enableAccessibilityDirect(context)) {
                    // Granted over adb earlier: no blocked screen involved.
                    tick++
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    showSteps = true
                } else {
                    attempted = true
                    SystemGate.openSettings(context, SystemGate.accessibilitySettings())
                }
            }
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && attempted && !enabled) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.accessibility_restricted_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = {
                    SystemGate.openSettings(context, SystemGate.appDetailsSettings(context))
                }) {
                    Text(text = stringResource(R.string.permission_app_info))
                }
            }
        }
    }
}

@Composable
fun BatteryPermissionRow() {
    val context = LocalContext.current
    var attempted by remember { mutableStateOf(false) }
    val resumeToken = rememberResumeToken()
    val enabled = remember(resumeToken) { SystemGate.ignoringBatteryOptimizations(context) }
    Column {
        PermissionRow(
            title = stringResource(R.string.permission_battery),
            granted = enabled,
            grantedText = stringResource(R.string.permission_enabled),
            missingText = stringResource(R.string.permission_needed),
            onEnable = {
                attempted = true
                SystemGate.openSettings(context, SystemGate.batterySettings(context))
            }
        )
        if (attempted && !enabled) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.battery_exemption_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = {
                    SystemGate.openSettings(context, SystemGate.appDetailsSettings(context))
                }) {
                    Text(text = stringResource(R.string.permission_app_info))
                }
            }
        }
    }
}

@Composable
fun EncouragementCard(card: ContentCard, language: String, onDismiss: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = card.verseAr, style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(6.dp))
            Text(text = card.reference(language), style = MaterialTheme.typography.labelMedium)
            Spacer(Modifier.height(10.dp))
            Text(text = card.motivation(language), style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(6.dp))
            TextButton(onClick = onDismiss) { Text(text = stringResource(R.string.dismiss)) }
        }
    }
}

@Composable
fun prayerLabel(name: PrayerName): String = stringResource(
    when (name) {
        PrayerName.FAJR -> R.string.prayer_fajr
        PrayerName.SUNRISE -> R.string.prayer_sunrise
        PrayerName.DHUHR -> R.string.prayer_dhuhr
        PrayerName.ASR -> R.string.prayer_asr
        PrayerName.MAGHRIB -> R.string.prayer_maghrib
        PrayerName.ISHA -> R.string.prayer_isha
    }
)

/** Chips that wrap onto as many lines as the width needs instead of one long column. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChipFlow(content: @Composable () -> Unit) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        content()
    }
}

/** The name the launcher shows for an installed package, or the package name if unknown. */
@Composable
fun appLabel(packageName: String): String {
    val context = LocalContext.current
    return remember(packageName) {
        runCatching {
            val manager = context.packageManager
            manager.getApplicationLabel(manager.getApplicationInfo(packageName, 0)).toString()
        }.getOrNull()?.takeIf { it.isNotBlank() } ?: packageName
    }
}

@Composable
fun ScreenTitle(title: String, subtitle: String? = null) {
    Column(modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 12.dp)) {
        Text(text = title, style = MaterialTheme.typography.headlineSmall)
        if (subtitle != null) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** Puts text on the clipboard and says so; used for reports the user sends back. */
fun copyText(context: android.content.Context, text: String) {
    runCatching {
        val clipboard = context.getSystemService(android.content.ClipboardManager::class.java)
        clipboard.setPrimaryClip(android.content.ClipData.newPlainText("naqaa", text))
        android.widget.Toast.makeText(context, R.string.report_copied, android.widget.Toast.LENGTH_SHORT).show()
    }
}
