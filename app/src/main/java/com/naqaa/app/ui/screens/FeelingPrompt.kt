package com.naqaa.app.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.naqaa.app.R
import com.naqaa.app.data.Feeling
import com.naqaa.app.data.Trigger
import com.naqaa.app.ui.AppViewModel

/**
 * Appears right after a lapse was saved with one tap: first how it felt and what led to it,
 * then the few steps worth taking for the rest of the day. Skipping keeps the record as it is.
 */
@Composable
fun FeelingPrompt(viewModel: AppViewModel) {
    val target by viewModel.feelingTarget.collectAsState()
    val event = target ?: return
    var feeling by remember(event.id) { mutableStateOf<String?>(null) }
    var trigger by remember(event.id) { mutableStateOf<String?>(null) }
    var saved by remember(event.id) { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = { viewModel.feelingTarget.value = null },
        title = { Text(text = stringResource(if (saved) R.string.lapse_title else R.string.feeling_prompt_title)) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                if (!saved) {
                    ChipFlow {
                        Feeling.entries.forEach { option ->
                            FilterChip(
                                selected = feeling == option.key,
                                onClick = { feeling = if (feeling == option.key) null else option.key },
                                label = { Text(text = feelingLabel(option)) }
                            )
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    Text(text = stringResource(R.string.lapse_trigger), style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(6.dp))
                    ChipFlow {
                        Trigger.entries.forEach { option ->
                            FilterChip(
                                selected = trigger == option.key,
                                onClick = { trigger = if (trigger == option.key) null else option.key },
                                label = { Text(text = triggerLabel(option)) }
                            )
                        }
                    }
                } else {
                    listOf(
                        R.string.lapse_return_body,
                        R.string.lapse_ghusl_body,
                        R.string.lapse_prayer_body,
                        R.string.lapse_plan_body
                    ).forEach { line ->
                        Text(
                            text = stringResource(line),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(bottom = 10.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (!saved) {
                    viewModel.annotate(event, trigger.orEmpty(), feeling.orEmpty())
                    saved = true
                } else {
                    viewModel.feelingTarget.value = null
                }
            }) {
                Text(text = stringResource(if (saved) R.string.dismiss else R.string.save))
            }
        },
        dismissButton = {
            if (!saved) {
                TextButton(onClick = { viewModel.feelingTarget.value = null }) {
                    Text(text = stringResource(R.string.feeling_prompt_skip))
                }
            }
        }
    )
}
