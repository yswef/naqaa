package com.naqaa.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.naqaa.app.R
import com.naqaa.app.content.Content
import com.naqaa.app.data.EventKind
import com.naqaa.app.ui.AppViewModel
import java.time.LocalTime

/**
 * One remembrance after another: the text, a large counter to tap for each repetition, and
 * the next one appears by itself. Opens on the morning set before mid-afternoon and on the
 * evening set after it.
 */
@Composable
fun AdhkarScreen(viewModel: AppViewModel, onBack: () -> Unit) {
    var evening by rememberSaveable { mutableIntStateOf(if (LocalTime.now().hour >= 15) 1 else 0) }
    val period = if (evening == 1) Content.PERIOD_EVENING else Content.PERIOD_MORNING
    val adhkar = remember(period) { Content.adhkar(period) }
    var index by rememberSaveable(period) { mutableIntStateOf(0) }
    var count by rememberSaveable(period) { mutableIntStateOf(0) }
    val current = adhkar.getOrNull(index)
    val required = current?.repeat?.coerceAtLeast(1) ?: 1
    val progress = when {
        adhkar.isEmpty() -> 0f
        current == null -> 1f
        else -> ((index + count.toFloat() / required) / adhkar.size).coerceIn(0f, 1f)
    }

    fun advance() {
        count = 0
        if (index + 1 < adhkar.size) {
            index += 1
        } else {
            index = adhkar.size
            viewModel.log(EventKind.ADHKAR)
        }
    }

    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text(text = stringResource(R.string.back)) }
            Spacer(Modifier.weight(1f))
            FilterChip(
                selected = evening == 0,
                onClick = { evening = 0 },
                label = { Text(text = stringResource(R.string.prayer_morning)) },
                modifier = Modifier.padding(end = 8.dp)
            )
            FilterChip(
                selected = evening == 1,
                onClick = { evening = 1 },
                label = { Text(text = stringResource(R.string.prayer_evening)) }
            )
        }
        ScreenTitle(title = stringResource(R.string.prayer_adhkar))
        LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(14.dp))

        if (adhkar.isEmpty()) {
            Text(text = stringResource(R.string.content_unavailable), style = MaterialTheme.typography.bodyMedium)
        } else if (current == null) {
            SectionCard {
                Text(text = stringResource(R.string.adhkar_complete), style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = {
                        index = 0
                        count = 0
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(text = stringResource(R.string.adhkar_restart))
                }
            }
        } else {
            Text(
                text = stringResource(R.string.adhkar_item_progress, index + 1, adhkar.size),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(10.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(22.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = current.textAr,
                        style = MaterialTheme.typography.headlineSmall,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = current.sourceAr,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
            Spacer(Modifier.height(24.dp))
            Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier
                        .size(132.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                        .clickable {
                            if (count + 1 >= required) advance() else count += 1
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = (required - count).toString(),
                        style = MaterialTheme.typography.displayMedium,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.adhkar_tap_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                TextButton(
                    onClick = {
                        count = 0
                        if (index > 0) index -= 1
                    },
                    enabled = index > 0
                ) { Text(text = stringResource(R.string.back)) }
                TextButton(onClick = { advance() }) { Text(text = stringResource(R.string.next)) }
            }
        }
        Spacer(Modifier.height(20.dp))
    }
}
