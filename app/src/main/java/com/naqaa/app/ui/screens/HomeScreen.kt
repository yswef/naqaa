package com.naqaa.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.TextButton
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.naqaa.app.R
import com.naqaa.app.content.Content
import com.naqaa.app.data.EventKind
import com.naqaa.app.prayer.PrayerName
import com.naqaa.app.ui.AppViewModel
import com.naqaa.app.ui.UiState
import com.naqaa.app.util.LocaleX
import com.naqaa.app.util.TimeX
import com.naqaa.app.vpn.VpnState
import java.time.Instant
import java.time.ZoneId

/**
 * The first screen: the number the user came for, the next prayer, today's plan step, and
 * one tap logging. Protection state is stated plainly instead of a badge, because a filter
 * that silently stopped working is worse than no filter.
 */
@Composable
fun HomeScreen(viewModel: AppViewModel, state: UiState, onNavigate: (Destination) -> Unit) {
    val language = state.preferences.language
    val display = LocaleX.displayLocale(language)
    val zone = ZoneId.systemDefault()

    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        ScreenTitle(
            title = stringResource(R.string.home_greeting),
            subtitle = stringResource(R.string.home_subtitle)
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = state.streak.current.toString(),
                    style = MaterialTheme.typography.displayMedium
                )
                Text(text = stringResource(R.string.home_clean_days), style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.home_best_and_points, state.streak.best, state.report.points),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        // The urgent action sits right under the counter, where a thumb finds it first.
        Button(
            onClick = { onNavigate(Destination.EMERGENCY) },
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 4.dp).heightIn(min = 52.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.error,
                contentColor = MaterialTheme.colorScheme.onError
            )
        ) {
            Text(text = stringResource(R.string.home_emergency), style = MaterialTheme.typography.titleMedium)
        }

        state.encouragement?.let { card ->
            EncouragementCard(card = card, language = language, onDismiss = viewModel::dismissEncouragement)
        }

        SectionCard(title = stringResource(R.string.home_protection)) {
            StatLine(
                label = stringResource(R.string.home_protection_state),
                value = if (state.vpnRunning) stringResource(R.string.protection_on) else stringResource(R.string.protection_off)
            )
            StatLine(
                label = stringResource(R.string.home_blocked_today),
                value = blockedTodayLabel(state)
            )
            Spacer(Modifier.height(8.dp))
            Button(onClick = { onNavigate(Destination.PROTECTION) }, modifier = Modifier.fillMaxWidth()) {
                Text(text = stringResource(R.string.home_open_protection))
            }
        }

        SectionCard(title = stringResource(R.string.home_next_prayer)) {
            val next = state.nextPrayer
            if (next == null) {
                Text(text = stringResource(R.string.prayer_none_today), style = MaterialTheme.typography.bodyMedium)
            } else {
                val (name, at) = next
                val remaining = (at.epochSecond - Instant.now().epochSecond).coerceAtLeast(0)
                StatLine(
                    label = prayerLabel(name),
                    value = TimeX.clock(at, zone, display) + "  (" + TimeX.countdown(remaining) + ")"
                )
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = { onNavigate(Destination.PRAYER) }, modifier = Modifier.fillMaxWidth()) {
                Text(text = stringResource(R.string.home_open_prayer))
            }
        }

        HabitsCard(viewModel, state, onNavigate)

        SectionCard(title = stringResource(R.string.home_quick_log)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = { viewModel.quickLog(EventKind.RESISTED) },
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                ) {
                    Text(text = stringResource(R.string.log_resisted), maxLines = 2)
                }
                OutlinedButton(
                    onClick = { viewModel.quickLog(EventKind.LAPSE) },
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                ) {
                    Text(text = stringResource(R.string.log_lapse), maxLines = 2)
                }
            }
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.home_logged_count, state.events.size),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = { onNavigate(Destination.JOURNAL) }) {
                    Text(text = stringResource(R.string.home_add_details))
                }
            }
        }

        SectionCard(title = stringResource(R.string.home_plan_today)) {
            val plan = remember(state.planDay) {
                state.planDay?.let { day -> Content.plan().firstOrNull { it.day == day } }
            }
            if (plan == null) {
                Text(text = stringResource(R.string.plan_not_started), style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(8.dp))
                Button(onClick = viewModel::startPlanToday, modifier = Modifier.fillMaxWidth()) {
                    Text(text = stringResource(R.string.plan_start))
                }
            } else {
                Text(text = stringResource(R.string.plan_day_of, plan.day), style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(6.dp))
                Text(text = plan.behavior(language), style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(4.dp))
                Text(text = plan.spiritual(language), style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(4.dp))
                Text(
                    text = plan.challenge(language),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(10.dp))
                Button(
                    onClick = { viewModel.log(EventKind.PLAN) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(text = stringResource(R.string.plan_done))
                }
                Spacer(Modifier.height(6.dp))
                OutlinedButton(onClick = { onNavigate(Destination.PLAN) }, modifier = Modifier.fillMaxWidth()) {
                    Text(text = stringResource(R.string.plan_open))
                }
            }
        }

        OutlinedButton(onClick = { onNavigate(Destination.PROGRESS) }, modifier = Modifier.fillMaxWidth()) {
            Text(text = stringResource(R.string.nav_progress))
        }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = { onNavigate(Destination.REPORT) }, modifier = Modifier.fillMaxWidth()) {
            Text(text = stringResource(R.string.nav_report))
        }
        Spacer(Modifier.height(20.dp))
    }
}

private fun blockedTodayLabel(state: UiState): String =
    if (state.vpnRunning) VpnState.blockedCount().toString() else "0"

/** The four things worth doing every day, each marked once with its state shown at a glance. */
@Composable
private fun HabitsCard(viewModel: AppViewModel, state: UiState, onNavigate: (Destination) -> Unit) {
    val zone = ZoneId.systemDefault()
    val today = java.time.LocalDate.now(zone)
    val counts = remember(state.events) {
        state.events
            .filter { java.time.LocalDate.ofInstant(it.at, zone) == today }
            .groupingBy { it.kind }
            .eachCount()
    }
    val prayers = counts[EventKind.PRAYER] ?: 0
    SectionCard(title = stringResource(R.string.home_habits)) {
        HabitRow(
            title = stringResource(R.string.habit_prayer),
            status = stringResource(R.string.habit_count, prayers, PRAYERS_PER_DAY),
            done = prayers >= PRAYERS_PER_DAY,
            action = stringResource(R.string.habit_mark),
            onAction = { onNavigate(Destination.PRAYER) }
        )
        HabitRow(
            title = stringResource(R.string.habit_adhkar),
            status = null,
            done = (counts[EventKind.ADHKAR] ?: 0) > 0,
            action = stringResource(R.string.start),
            onAction = { onNavigate(Destination.ADHKAR) }
        )
        HabitRow(
            title = stringResource(R.string.habit_quran),
            status = null,
            done = (counts[EventKind.QURAN] ?: 0) > 0,
            action = stringResource(R.string.habit_mark),
            onAction = { viewModel.log(EventKind.QURAN) }
        )
    }
}

@Composable
private fun HabitRow(title: String, status: String?, done: Boolean, action: String, onAction: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            if (status != null) {
                Text(
                    text = status,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (done) {
            Text(
                text = stringResource(R.string.habit_done),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
        } else {
            FilledTonalButton(onClick = onAction) { Text(text = action) }
        }
    }
}

private const val PRAYERS_PER_DAY = 5
