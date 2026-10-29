package com.naqaa.app.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.naqaa.app.R
import com.naqaa.app.accessibility.CleanupState
import com.naqaa.app.ui.theme.NaqaaOutlinedTextFieldColors
import com.naqaa.app.util.SystemGate

/**
 * Starts and stops the slow clean-up of the user's own Instagram account (follows and
 * likes). The work is done by the accessibility service while Instagram is in front; this
 * screen only sets the pace and shows the state. The pace defaults are small on purpose.
 */
@Composable
fun CleanupScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var tick by remember { mutableIntStateOf(0) }
    DisposableEffect(Unit) {
        val remove = CleanupState.addListener { tick += 1 }
        onDispose { remove() }
    }
    val resume = rememberResumeToken()
    // Reading the ticks makes the values below refresh when the engine reports progress.
    val refreshed = tick + resume
    val accessibility = remember(refreshed) { SystemGate.accessibilityEnabled(context) }
    val doneToday = remember(refreshed) { CleanupState.doneToday(context) }

    var batch by remember { mutableStateOf(CleanupState.batchSize(context).toString()) }
    var pause by remember { mutableStateOf(CleanupState.pauseMinutes(context).toString()) }
    var cap by remember { mutableStateOf(CleanupState.dailyCap(context).toString()) }
    var mode by remember { mutableStateOf(CleanupState.Mode.UNFOLLOW) }
    val running = CleanupState.mode != CleanupState.Mode.OFF

    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        ScreenTitle(title = stringResource(R.string.cleanup_title), subtitle = stringResource(R.string.cleanup_subtitle))

        SectionCard(title = stringResource(R.string.cleanup_warning_title)) {
            Text(text = stringResource(R.string.cleanup_warning), style = MaterialTheme.typography.bodyMedium)
        }

        SectionCard(title = stringResource(R.string.cleanup_what)) {
            ChipFlow {
                FilterChip(
                    selected = mode == CleanupState.Mode.UNFOLLOW,
                    onClick = { mode = CleanupState.Mode.UNFOLLOW },
                    label = { Text(text = stringResource(R.string.cleanup_mode_unfollow)) },
                    enabled = !running
                )
                FilterChip(
                    selected = mode == CleanupState.Mode.UNLIKE,
                    onClick = { mode = CleanupState.Mode.UNLIKE },
                    label = { Text(text = stringResource(R.string.cleanup_mode_unlike)) },
                    enabled = !running
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = stringResource(
                    if (mode == CleanupState.Mode.UNFOLLOW) R.string.cleanup_steps_unfollow else R.string.cleanup_steps_unlike
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        SectionCard(title = stringResource(R.string.cleanup_pace)) {
            Row {
                NumberField(batch, stringResource(R.string.cleanup_batch), Modifier.weight(1f)) { batch = it }
                Spacer(Modifier.width(8.dp))
                NumberField(pause, stringResource(R.string.cleanup_pause), Modifier.weight(1f)) { pause = it }
                Spacer(Modifier.width(8.dp))
                NumberField(cap, stringResource(R.string.cleanup_cap), Modifier.weight(1f)) { cap = it }
            }
            Spacer(Modifier.height(6.dp))
            OutlinedButton(
                onClick = {
                    CleanupState.saveSettings(
                        context,
                        batch.toIntOrNull() ?: 6,
                        pause.toIntOrNull() ?: 15,
                        cap.toIntOrNull() ?: 40
                    )
                    batch = CleanupState.batchSize(context).toString()
                    pause = CleanupState.pauseMinutes(context).toString()
                    cap = CleanupState.dailyCap(context).toString()
                },
                enabled = !running,
                modifier = Modifier.fillMaxWidth()
            ) { Text(text = stringResource(R.string.save)) }
        }

        SectionCard(title = stringResource(R.string.cleanup_state)) {
            Text(text = stringResource(CleanupState.messageRes), style = MaterialTheme.typography.titleMedium)
            StatLine(stringResource(R.string.cleanup_done_today), doneToday.toString() + " / " + CleanupState.dailyCap(context))
            StatLine(stringResource(R.string.cleanup_done_session), CleanupState.sessionDone.toString())
            if (!accessibility) {
                Text(
                    text = stringResource(R.string.cleanup_needs_accessibility),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
            Spacer(Modifier.height(8.dp))
            if (running) {
                Button(onClick = { CleanupState.stop() }, modifier = Modifier.fillMaxWidth()) {
                    Text(text = stringResource(R.string.cleanup_stop))
                }
            } else {
                Button(
                    onClick = {
                        CleanupState.saveSettings(context, batch.toIntOrNull() ?: 6, pause.toIntOrNull() ?: 15, cap.toIntOrNull() ?: 40)
                        CleanupState.start(mode)
                        context.packageManager.getLaunchIntentForPackage("com.instagram.android")?.let {
                            runCatching { context.startActivity(it) }
                        }
                    },
                    enabled = accessibility,
                    modifier = Modifier.fillMaxWidth()
                ) { Text(text = stringResource(R.string.cleanup_start)) }
            }
        }

        SectionCard(title = stringResource(R.string.cleanup_dump_title)) {
            Text(
                text = stringResource(R.string.cleanup_dump_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(6.dp))
            OutlinedButton(
                onClick = {
                    CleanupState.requestDump()
                    context.packageManager.getLaunchIntentForPackage("com.instagram.android")?.let {
                        runCatching { context.startActivity(it) }
                    }
                },
                enabled = accessibility,
                modifier = Modifier.fillMaxWidth()
            ) { Text(text = stringResource(R.string.cleanup_dump_button)) }
        }

        TextButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text(text = stringResource(R.string.back)) }
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun NumberField(value: String, label: String, modifier: Modifier, onChange: (String) -> Unit) {
    OutlinedTextField(
        colors = NaqaaOutlinedTextFieldColors(),
        value = value,
        onValueChange = { onChange(it.filter(Char::isDigit).take(3)) },
        singleLine = true,
        label = { Text(text = label, maxLines = 1) },
        modifier = modifier
    )
}
