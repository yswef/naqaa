package com.naqaa.app.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.naqaa.app.R
import com.naqaa.app.content.Content
import com.naqaa.app.data.EventKind
import com.naqaa.app.ui.AppViewModel
import com.naqaa.app.ui.UiState
import com.naqaa.app.util.SystemGate
import com.naqaa.app.util.TimeX
import kotlinx.coroutines.delay
import kotlin.random.Random

/**
 * The screen for a moment of urge, built around one idea: the urge is a wave, and the
 * screen keeps the hands and the breath busy until it drops. It offers a guided breath, a
 * fifteen minute timer, five concrete alternatives, the reason written by the user at
 * setup, and one way to reach another person.
 */
@Composable
fun EmergencyScreen(viewModel: AppViewModel, state: UiState, onNavigate: (Destination) -> Unit) {
    val context = LocalContext.current
    val language = state.preferences.language
    val verse = remember { Content.verseFor(listOf(Content.TOPIC_URGE, Content.TOPIC_MERCY), Random.Default) }
    val hadith = remember { Content.hadithFor(listOf(Content.TOPIC_URGE, Content.TOPIC_GAZE), Random.Default) }
    val dua = remember { Content.adhkar("any").firstOrNull { it.id == "ham-wahzan" } }

    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        ScreenTitle(
            title = stringResource(R.string.emergency_title),
            subtitle = stringResource(R.string.emergency_subtitle)
        )

        if (state.preferences.reason.isNotBlank()) {
            SectionCard(title = stringResource(R.string.emergency_reason)) {
                Text(text = state.preferences.reason, style = MaterialTheme.typography.bodyLarge)
            }
        }

        SectionCard(title = stringResource(R.string.emergency_breathe)) {
            BreathExercise()
        }

        SectionCard(title = stringResource(R.string.emergency_timer)) {
            UrgeTimer(onResisted = { viewModel.log(EventKind.RESISTED) }, onNavigate = onNavigate)
        }

        SectionCard(title = stringResource(R.string.emergency_alternatives)) {
            listOf(
                R.string.alternative_wudu,
                R.string.alternative_walk,
                R.string.alternative_water,
                R.string.alternative_prayer,
                R.string.alternative_call
            ).forEach { label ->
                Text(
                    text = "• " + stringResource(label),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(vertical = 3.dp)
                )
            }
        }

        if (verse != null) {
            SectionCard(title = stringResource(R.string.emergency_verse)) {
                Text(text = verse.textAr, style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(6.dp))
                Text(
                    text = if (language == "en") verse.referenceEn else verse.referenceAr,
                    style = MaterialTheme.typography.labelMedium
                )
            }
        }

        if (hadith != null) {
            SectionCard(title = stringResource(R.string.emergency_hadith)) {
                Text(text = hadith.textAr, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(6.dp))
                Text(
                    text = if (language == "en") hadith.referenceEn else hadith.referenceAr,
                    style = MaterialTheme.typography.labelMedium
                )
            }
        }

        if (dua != null) {
            SectionCard(title = stringResource(R.string.emergency_dua)) {
                Text(text = dua.textAr, style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(6.dp))
                Text(text = dua.sourceAr, style = MaterialTheme.typography.labelMedium)
            }
        }

        if (state.preferences.hasTrustedContact) {
            Button(
                onClick = {
                    runCatching { context.startActivity(SystemGate.dial(state.preferences.trustedPhone)) }
                },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            ) {
                Text(
                    text = state.preferences.trustedName.ifBlank {
                        stringResource(R.string.emergency_call_contact)
                    }
                )
            }
        }

        Spacer(Modifier.height(8.dp))
        Button(
            onClick = { viewModel.log(EventKind.RESISTED) },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
        ) {
            Text(text = stringResource(R.string.emergency_resisted))
        }
        Spacer(Modifier.height(20.dp))
    }
}

/**
 * Four counts in, seven held, eight out, twice a second at most so the pace stays calm.
 * The circle grows with the in breath and shrinks with the out breath.
 */
@Composable
private fun BreathExercise() {
    var running by remember { mutableStateOf(false) }
    var phase by remember { mutableIntStateOf(0) }
    var secondsLeft by remember { mutableIntStateOf(INHALE_SECONDS) }
    var cycles by remember { mutableIntStateOf(0) }
    val target = when (phase) {
        0 -> 1f
        1 -> 1f
        else -> 0.62f
    }
    val scale by animateFloatAsState(
        targetValue = target,
        animationSpec = tween(durationMillis = secondsLeft.coerceAtLeast(1) * 1_000),
        label = "breath"
    )

    LaunchedEffect(running) {
        while (running) {
            delay(1_000)
            secondsLeft -= 1
            if (secondsLeft <= 0) {
                phase = (phase + 1) % 3
                secondsLeft = when (phase) {
                    0 -> INHALE_SECONDS
                    1 -> HOLD_SECONDS
                    else -> EXHALE_SECONDS
                }
                if (phase == 0) cycles += 1
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(140.dp)
                .scale(scale)
                .background(MaterialTheme.colorScheme.primaryContainer, CircleShape)
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = stringResource(
                when (phase) {
                    0 -> R.string.breath_in
                    1 -> R.string.breath_hold
                    else -> R.string.breath_out
                }
            ) + "  " + secondsLeft,
            style = MaterialTheme.typography.titleLarge
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.breath_cycles, cycles),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { running = !running }) {
                Text(text = stringResource(if (running) R.string.pause else R.string.start))
            }
            OutlinedButton(
                onClick = {
                    running = false
                    phase = 0
                    secondsLeft = INHALE_SECONDS
                    cycles = 0
                }
            ) {
                Text(text = stringResource(R.string.reset))
            }
        }
    }
}

/** Fifteen minutes of waiting, started by hand, with the achievement logged at the end. */
@Composable
private fun UrgeTimer(onResisted: () -> Unit, onNavigate: (Destination) -> Unit) {
    var remaining by remember { mutableIntStateOf(URGE_SECONDS) }
    var running by remember { mutableStateOf(false) }
    var finished by remember { mutableStateOf(false) }

    LaunchedEffect(running) {
        while (running && remaining > 0) {
            delay(1_000)
            remaining -= 1
        }
        if (remaining <= 0) {
            running = false
            finished = true
        }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = TimeX.countdown(remaining.toLong()),
            style = MaterialTheme.typography.displaySmall,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )
        Spacer(Modifier.height(10.dp))
        if (finished) {
            Text(
                text = stringResource(R.string.timer_finished),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
            Spacer(Modifier.height(10.dp))
            Button(
                onClick = {
                    onResisted()
                    onNavigate(Destination.HOME)
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = stringResource(R.string.timer_log_resisted))
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { running = !running },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(text = stringResource(if (running) R.string.pause else R.string.timer_start))
                }
                OutlinedButton(
                    onClick = {
                        running = false
                        remaining = URGE_SECONDS
                        finished = false
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(text = stringResource(R.string.reset))
                }
            }
        }
    }
}

private const val INHALE_SECONDS = 4
private const val HOLD_SECONDS = 7
private const val EXHALE_SECONDS = 8
private const val URGE_SECONDS = 15 * 60
