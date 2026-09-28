package com.naqaa.app.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.naqaa.app.R
import com.naqaa.app.content.Content
import com.naqaa.app.data.EventKind
import com.naqaa.app.data.Progress
import com.naqaa.app.prayer.PrayerCalculator
import com.naqaa.app.ui.AppViewModel
import com.naqaa.app.ui.UiState
import java.time.LocalDate
import java.time.ZoneId

/**
 * The thirty day plan: one behavioural step, one act of worship and one small challenge a
 * day. Days already passed stay readable so the plan can be resumed after a gap without
 * pretending nothing happened.
 */
@Composable
fun PlanScreen(viewModel: AppViewModel, state: UiState) {
    val language = state.preferences.language
    val plan = remember { Content.plan() }
    val doneDays = remember(state.events) {
        state.events.filter { it.kind == EventKind.PLAN }
            .map { java.time.LocalDate.ofInstant(it.at, ZoneId.systemDefault()) }
            .toSet()
    }
    val today = LocalDate.now()
    val start = state.preferences.planStart

    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        ScreenTitle(title = stringResource(R.string.plan_title), subtitle = stringResource(R.string.plan_subtitle))

        if (start == null) {
            SectionCard(title = stringResource(R.string.plan_not_started)) {
                Text(text = stringResource(R.string.plan_intro), style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(10.dp))
                Button(onClick = viewModel::startPlanToday, modifier = Modifier.fillMaxWidth()) {
                    Text(text = stringResource(R.string.plan_start))
                }
            }
            Spacer(Modifier.height(20.dp))
            return
        }

        SectionCard(title = stringResource(R.string.plan_progress)) {
            val day = state.planDay ?: plan.size
            StatLine(stringResource(R.string.plan_current_day), day.toString())
            StatLine(stringResource(R.string.plan_done_days), doneDays.size.toString())
        }

        plan.forEach { entry ->
            val isToday = entry.day == state.planDay
            val done = doneDays.contains(start.plusDays((entry.day - 1).toLong()))
            Card(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isToday) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    }
                )
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = stringResource(R.string.plan_day_of, entry.day),
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.weight(1f)
                        )
                        if (done) {
                            Text(
                                text = stringResource(R.string.plan_marked),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(text = entry.behavior(language), style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(4.dp))
                    Text(text = entry.spiritual(language), style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = entry.challenge(language),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (isToday && !done) {
                        Spacer(Modifier.height(10.dp))
                        Button(
                            onClick = { viewModel.log(EventKind.PLAN) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(text = stringResource(R.string.plan_done))
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(20.dp))
    }
}
