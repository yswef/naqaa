package com.naqaa.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.naqaa.app.R
import com.naqaa.app.data.EventKind
import com.naqaa.app.data.Progress
import com.naqaa.app.ui.UiState
import java.time.LocalDate

/**
 * The numbers behind the streak, including the ninety day view.
 *
 * A lapse is drawn in the same grid as a clean day; hiding it would make the picture
 * useless. Badges are earned locally and never leave the device.
 */
@Composable
fun ProgressScreen(state: UiState) {
    val today = LocalDate.now()
    val start = remember(state.cleanDays) { today.minusDays(89) }
    val resisted = remember(state.events) { Progress.resisted(state.events) }
    val adhkarDays = remember(state.events) {
        Progress.daysWith(state.events, EventKind.ADHKAR, start, today, java.time.ZoneId.systemDefault())
    }

    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        ScreenTitle(title = stringResource(R.string.progress_title), subtitle = stringResource(R.string.progress_subtitle))
        SectionCard(title = stringResource(R.string.progress_streak)) {
            StatLine(stringResource(R.string.progress_current), state.streak.current.toString())
            StatLine(stringResource(R.string.progress_best), state.streak.best.toString())
            StatLine(stringResource(R.string.progress_clean_days), state.streak.cleanDays.toString())
            StatLine(stringResource(R.string.progress_lapse_days), state.streak.lapseDays.toString())
            StatLine(stringResource(R.string.progress_resisted), resisted.toString())
            StatLine(stringResource(R.string.progress_points), state.report.points.toString())
        }
        SectionCard(title = stringResource(R.string.progress_ninety_days)) {
            NinetyDays(start = start, today = today, cleanDays = state.cleanDays)
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.progress_legend),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        SectionCard(title = stringResource(R.string.progress_badges)) {
            Progress.Badge.entries.forEach { badge ->
                val earned = Progress.earned(badge, state.streak, adhkarDays, resisted)
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = badgeLabel(badge), style = MaterialTheme.typography.bodyLarge)
                    Text(
                        text = stringResource(if (earned) R.string.badge_earned else R.string.badge_locked),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (earned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun NinetyDays(start: LocalDate, today: LocalDate, cleanDays: Set<LocalDate>) {
    val days = remember(cleanDays) { (0 until 90).map { start.plusDays(it.toLong()) } }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        days.chunked(15).forEach { week ->
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                week.forEach { day ->
                    val future = day.isAfter(today)
                    val color = when {
                        future -> MaterialTheme.colorScheme.surfaceVariant
                        day in cleanDays -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.error
                    }
                    Box(
                        modifier = Modifier
                            .size(18.dp)
                            .background(color, RoundedCornerShape(4.dp))
                    )
                }
            }
        }
    }
}

@Composable
private fun badgeLabel(badge: Progress.Badge): String = stringResource(
    when (badge) {
        Progress.Badge.THREE_DAYS -> R.string.badge_three_days
        Progress.Badge.WEEK -> R.string.badge_week
        Progress.Badge.FORTNIGHT -> R.string.badge_fortnight
        Progress.Badge.MONTH -> R.string.badge_month
        Progress.Badge.RESISTED_TEN -> R.string.badge_resisted_ten
        Progress.Badge.ADHKAR_WEEK -> R.string.badge_adhkar_week
    }
)
