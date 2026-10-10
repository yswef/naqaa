package com.naqaa.app.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.naqaa.app.R
import com.naqaa.app.content.Content
import com.naqaa.app.data.EventKind
import com.naqaa.app.ui.AppViewModel

@Composable
fun AdhkarScreen(viewModel: AppViewModel, onBack: () -> Unit) {
    var period by rememberSaveable { mutableStateOf(Content.PERIOD_MORNING) }
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

    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        TextButton(onClick = onBack) { Text(text = stringResource(R.string.back)) }
        ScreenTitle(title = stringResource(R.string.prayer_adhkar))

        SectionCard {
            Row {
                FilterChip(
                    selected = period == Content.PERIOD_MORNING,
                    onClick = { period = Content.PERIOD_MORNING },
                    label = { Text(text = stringResource(R.string.prayer_morning)) },
                    modifier = Modifier.padding(end = 6.dp)
                )
                FilterChip(
                    selected = period == Content.PERIOD_EVENING,
                    onClick = { period = Content.PERIOD_EVENING },
                    label = { Text(text = stringResource(R.string.prayer_evening)) }
                )
            }
            Spacer(Modifier.height(14.dp))
            LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(14.dp))

            if (adhkar.isEmpty()) {
                Text(text = stringResource(R.string.content_unavailable), style = MaterialTheme.typography.bodyMedium)
            } else if (current == null) {
                Text(text = stringResource(R.string.adhkar_complete), style = MaterialTheme.typography.titleMedium)
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
            } else {
                Text(
                    text = stringResource(R.string.adhkar_item_progress, index + 1, adhkar.size),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(14.dp))
                Text(text = current.textAr, style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(8.dp))
                Text(
                    text = current.sourceAr,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(20.dp))
                Text(
                    text = stringResource(R.string.adhkar_repeat_progress, count, required),
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = {
                        val active = adhkar.getOrNull(index) ?: return@Button
                        val target = active.repeat.coerceAtLeast(1)
                        val nextCount = count + 1
                        if (nextCount >= target) {
                            count = 0
                            if (index + 1 < adhkar.size) {
                                index += 1
                            } else {
                                index = adhkar.size
                                viewModel.log(EventKind.ADHKAR)
                            }
                        } else {
                            count = nextCount
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(text = stringResource(R.string.adhkar_count_tap))
                }
            }
        }
    }
}
