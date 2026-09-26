package com.naqaa.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.naqaa.app.R
import com.naqaa.app.content.Content
import com.naqaa.app.data.EventKind
import com.naqaa.app.data.Feeling
import com.naqaa.app.data.JournalEvent
import com.naqaa.app.data.Place
import com.naqaa.app.data.Trigger
import com.naqaa.app.ui.AppViewModel
import com.naqaa.app.ui.UiState
import com.naqaa.app.util.LocaleX
import com.naqaa.app.util.TimeX
import java.time.ZoneId
import kotlin.random.Random

/**
 * One tap logging first, details later or never.
 *
 * The quick row is the path the user will take most days; the detailed form exists for the
 * evenings when naming the trigger helps. A lapse opens a short guided flow instead of
 * plain entry, because that is the moment when the next step matters more than the record.
 */
@Composable
fun JournalScreen(viewModel: AppViewModel, state: UiState) {
    var mode by remember { mutableStateOf(Mode.QUICK) }
    when (mode) {
        Mode.QUICK -> Quick(viewModel, state, onDetails = { mode = Mode.DETAILS }, onLapse = { mode = Mode.LAPSE })
        Mode.DETAILS -> Details(viewModel, onBack = { mode = Mode.QUICK })
        Mode.LAPSE -> Lapse(viewModel, onDone = { mode = Mode.QUICK })
    }
}

private enum class Mode { QUICK, DETAILS, LAPSE }

@Composable
private fun Quick(viewModel: AppViewModel, state: UiState, onDetails: () -> Unit, onLapse: () -> Unit) {
    val language = state.preferences.language
    val display = LocaleX.displayLocale(language)
    val zone = ZoneId.systemDefault()
    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        ScreenTitle(title = stringResource(R.string.journal_title), subtitle = stringResource(R.string.journal_subtitle))
        SectionCard(title = stringResource(R.string.journal_quick)) {
            AppViewModel.logLabels.forEach { (kind, label) ->
                OutlinedButton(
                    onClick = { if (kind == EventKind.LAPSE) onLapse() else viewModel.quickLog(kind) },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)
                ) {
                    Text(text = stringResource(label))
                }
            }
            Spacer(Modifier.height(8.dp))
            Button(onClick = onDetails, modifier = Modifier.fillMaxWidth()) {
                Text(text = stringResource(R.string.journal_add_details))
            }
        }
        SectionCard(title = stringResource(R.string.journal_recent)) {
            val recent = state.events.sortedByDescending(JournalEvent::at).take(30)
            if (recent.isEmpty()) {
                Text(text = stringResource(R.string.journal_empty), style = MaterialTheme.typography.bodyMedium)
            } else {
                LazyColumn(modifier = Modifier.fillMaxWidth().height(320.dp)) {
                    items(recent, key = { it.id }) { event ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(text = kindLabel(event.kind), style = MaterialTheme.typography.bodyLarge)
                                val detail = detailOf(event, language)
                                if (detail.isNotBlank()) {
                                    Text(
                                        text = detail,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Text(
                                text = TimeX.timestamp(event.at, zone, display),
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun Details(viewModel: AppViewModel, onBack: () -> Unit) {
    var kind by remember { mutableStateOf(EventKind.LAPSE) }
    var trigger by remember { mutableStateOf<String?>(null) }
    var place by remember { mutableStateOf<String?>(null) }
    var feeling by remember { mutableStateOf<String?>(null) }
    var note by remember { mutableStateOf("") }

    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        ScreenTitle(title = stringResource(R.string.journal_details_title))
        SectionCard(title = stringResource(R.string.journal_kind)) {
            AppViewModel.logLabels.forEach { (option, label) ->
                FilterChip(
                    selected = kind == option,
                    onClick = { kind = option },
                    label = { Text(text = stringResource(label)) },
                    modifier = Modifier.padding(vertical = 3.dp)
                )
            }
        }
        SectionCard(title = stringResource(R.string.journal_trigger)) {
            Trigger.entries.forEach { option ->
                FilterChip(
                    selected = trigger == option.key,
                    onClick = { trigger = if (trigger == option.key) null else option.key },
                    label = { Text(text = triggerLabel(option)) },
                    modifier = Modifier.padding(vertical = 3.dp)
                )
            }
        }
        SectionCard(title = stringResource(R.string.journal_place)) {
            Place.entries.forEach { option ->
                FilterChip(
                    selected = place == option.key,
                    onClick = { place = if (place == option.key) null else option.key },
                    label = { Text(text = placeLabel(option)) },
                    modifier = Modifier.padding(vertical = 3.dp)
                )
            }
        }
        SectionCard(title = stringResource(R.string.journal_feeling)) {
            Feeling.entries.forEach { option ->
                FilterChip(
                    selected = feeling == option.key,
                    onClick = { feeling = if (feeling == option.key) null else option.key },
                    label = { Text(text = feelingLabel(option)) },
                    modifier = Modifier.padding(vertical = 3.dp)
                )
            }
        }
        SectionCard(title = stringResource(R.string.journal_note)) {
            OutlinedTextField(
                value = note,
                onValueChange = { value -> if (value.length <= 500) note = value },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3
            )
        }
        Button(
            onClick = {
                viewModel.log(kind, trigger.orEmpty(), place.orEmpty(), feeling.orEmpty(), note)
                onBack()
            },
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
        ) {
            Text(text = stringResource(R.string.save))
        }
        TextButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
            Text(text = stringResource(R.string.cancel))
        }
        Spacer(Modifier.height(20.dp))
    }
}

/**
 * The flow after a lapse: return, wash, pray, name the trigger, and leave with one small
 * plan for the rest of the day. Nothing here scolds, and the day is not treated as lost.
 */
@Composable
private fun Lapse(viewModel: AppViewModel, onDone: () -> Unit) {
    var step by remember { mutableIntStateOf(0) }
    var trigger by remember { mutableStateOf<String?>(null) }
    val verse = remember { Content.verseOfDay(java.time.LocalDate.now().minusDays(1)) }
    val hadith = remember { Content.hadithFor(listOf(Content.TOPIC_REPENTANCE, Content.TOPIC_MERCY), Random.Default) }

    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        ScreenTitle(
            title = stringResource(R.string.lapse_title),
            subtitle = stringResource(R.string.lapse_step, step + 1, LAPSE_STEPS)
        )
        when (step) {
            0 -> SectionCard(title = stringResource(R.string.lapse_return)) {
                Text(text = stringResource(R.string.lapse_return_body), style = MaterialTheme.typography.bodyLarge)
                verse?.let {
                    Spacer(Modifier.height(10.dp))
                    Text(text = it.textAr, style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(4.dp))
                    Text(text = it.referenceAr, style = MaterialTheme.typography.labelMedium)
                }
                hadith?.let {
                    Spacer(Modifier.height(10.dp))
                    Text(text = it.textAr, style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(4.dp))
                    Text(text = it.referenceAr, style = MaterialTheme.typography.labelMedium)
                }
            }
            1 -> SectionCard(title = stringResource(R.string.lapse_ghusl)) {
                Text(text = stringResource(R.string.lapse_ghusl_body), style = MaterialTheme.typography.bodyLarge)
            }
            2 -> SectionCard(title = stringResource(R.string.lapse_prayer)) {
                Text(text = stringResource(R.string.lapse_prayer_body), style = MaterialTheme.typography.bodyLarge)
            }
            3 -> SectionCard(title = stringResource(R.string.lapse_trigger)) {
                Trigger.entries.forEach { option ->
                    FilterChip(
                        selected = trigger == option.key,
                        onClick = { trigger = option.key },
                        label = { Text(text = triggerLabel(option)) },
                        modifier = Modifier.padding(vertical = 3.dp)
                    )
                }
            }
            else -> SectionCard(title = stringResource(R.string.lapse_plan)) {
                Text(text = stringResource(R.string.lapse_plan_body), style = MaterialTheme.typography.bodyLarge)
            }
        }
        Button(
            onClick = {
                if (step < LAPSE_STEPS - 1) {
                    step += 1
                } else {
                    viewModel.log(EventKind.LAPSE, trigger = trigger.orEmpty())
                    onDone()
                }
            },
            modifier = Modifier.fillMaxWidth().padding(top = 10.dp)
        ) {
            Text(text = stringResource(if (step < LAPSE_STEPS - 1) R.string.next else R.string.save))
        }
        TextButton(onClick = onDone, modifier = Modifier.fillMaxWidth()) {
            Text(text = stringResource(R.string.cancel))
        }
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
internal fun kindLabel(kind: EventKind): String = stringResource(
    when (kind) {
        EventKind.LAPSE -> R.string.log_lapse
        EventKind.RESISTED -> R.string.log_resisted
        EventKind.PRAYER -> R.string.log_prayer
        EventKind.ADHKAR -> R.string.log_adhkar
        EventKind.QURAN -> R.string.log_quran
        EventKind.PLAN -> R.string.log_plan
        EventKind.GUARD_OFF -> R.string.log_guard_off
    }
)

@Composable
internal fun triggerLabel(trigger: Trigger): String = stringResource(
    when (trigger) {
        Trigger.BOREDOM -> R.string.trigger_boredom
        Trigger.LONELINESS -> R.string.trigger_loneliness
        Trigger.STRESS -> R.string.trigger_stress
        Trigger.ANGER -> R.string.trigger_anger
        Trigger.FATIGUE -> R.string.trigger_fatigue
        Trigger.NIGHT -> R.string.trigger_night
        Trigger.SOCIAL -> R.string.trigger_social
        Trigger.BED -> R.string.trigger_bed
        Trigger.PHONE -> R.string.trigger_phone
        Trigger.OTHER -> R.string.trigger_other
    }
)

@Composable
internal fun placeLabel(place: Place): String = stringResource(
    when (place) {
        Place.ROOM -> R.string.place_room
        Place.BATHROOM -> R.string.place_bathroom
        Place.HOME -> R.string.place_home
        Place.WORK -> R.string.place_work
        Place.OUTSIDE -> R.string.place_outside
        Place.OTHER -> R.string.place_other
    }
)

@Composable
internal fun feelingLabel(feeling: Feeling): String = stringResource(
    when (feeling) {
        Feeling.ANXIOUS -> R.string.feeling_anxious
        Feeling.SAD -> R.string.feeling_sad
        Feeling.LONELY -> R.string.feeling_lonely
        Feeling.ANGRY -> R.string.feeling_angry
        Feeling.TIRED -> R.string.feeling_tired
        Feeling.BORED -> R.string.feeling_bored
        Feeling.NUMB -> R.string.feeling_numb
        Feeling.CALM -> R.string.feeling_calm
        Feeling.OTHER -> R.string.feeling_other
    }
)

@Composable
private fun detailOf(event: JournalEvent, language: String): String {
    val parts = mutableListOf<String>()
    Trigger.from(event.trigger)?.let { parts.add(triggerLabel(it)) }
    Place.from(event.place)?.let { parts.add(placeLabel(it)) }
    Feeling.from(event.feeling)?.let { parts.add(feelingLabel(it)) }
    if (event.note.isNotBlank()) parts.add(event.note)
    return parts.joinToString(" · ")
}

private const val LAPSE_STEPS = 5
