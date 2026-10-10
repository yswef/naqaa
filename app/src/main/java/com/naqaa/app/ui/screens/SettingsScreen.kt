package com.naqaa.app.ui.screens

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.naqaa.app.R
import com.naqaa.app.ui.theme.NaqaaOutlinedTextFieldColors
import com.naqaa.app.data.Persona
import com.naqaa.app.data.PinHasher
import com.naqaa.app.data.Preferences
import com.naqaa.app.ui.AppViewModel
import com.naqaa.app.ui.BiometricUnlock
import com.naqaa.app.ui.PersonaSwitch
import com.naqaa.app.ui.PinGate
import com.naqaa.app.ui.UiState
import com.naqaa.app.util.LocaleX
import com.naqaa.app.widget.ProgressWidget

/**
 * Settings, including the two decisions that cannot be undone by accident: the PIN and the
 * erase action. The persona row explains what the alias does before it changes the launcher
 * entry, and the erase action asks twice.
 */
@Composable
fun SettingsScreen(viewModel: AppViewModel, state: UiState) {
    val context = LocalContext.current
    val preferences = state.preferences
    val versionName = remember {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull().orEmpty()
    }
    var showPinDialog by remember { mutableStateOf(false) }
    var widgetNotice by remember { mutableStateOf<Int?>(null) }
    var showEraseDialog by remember { mutableStateOf(false) }
    var eraseStep by remember { mutableStateOf(0) }
    var confirmPin by remember { mutableStateOf("") }

    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        ScreenTitle(title = stringResource(R.string.settings_title))

        SectionCard(title = stringResource(R.string.settings_language)) {
            Row {
                FilterChip(
                    selected = preferences.language == LocaleX.ARABIC,
                    onClick = {
                        viewModel.update { it.copy(language = LocaleX.ARABIC) }
                        (context as? Activity)?.recreate()
                    },
                    label = { Text(text = stringResource(R.string.language_arabic)) },
                    modifier = Modifier.padding(end = 8.dp)
                )
                FilterChip(
                    selected = preferences.language == LocaleX.ENGLISH,
                    onClick = {
                        viewModel.update { it.copy(language = LocaleX.ENGLISH) }
                        (context as? Activity)?.recreate()
                    },
                    label = { Text(text = stringResource(R.string.language_english)) }
                )
            }
        }

        SectionCard(title = stringResource(R.string.settings_persona)) {
            Text(
                text = stringResource(R.string.settings_persona_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            Persona.entries.forEach { persona ->
                FilterChip(
                    selected = preferences.persona == persona,
                    onClick = {
                        viewModel.update { it.copy(persona = persona) }
                        PersonaSwitch.apply(context, persona)
                    },
                    label = { Text(text = personaLabel(persona)) },
                    modifier = Modifier.padding(top = 3.dp, end = 6.dp, bottom = 3.dp)
                )
            }
        }

        SectionCard(title = stringResource(R.string.settings_lock)) {
            StatLine(
                stringResource(R.string.settings_pin),
                stringResource(if (preferences.hasPin) R.string.settings_pin_set else R.string.settings_pin_missing)
            )
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = { showPinDialog = true }, modifier = Modifier.fillMaxWidth()) {
                Text(text = stringResource(if (preferences.hasPin) R.string.settings_pin_change else R.string.settings_pin_create))
            }
            if (preferences.hasPin) {
                Spacer(Modifier.height(6.dp))
                SettingSwitch(
                    title = stringResource(R.string.settings_biometric),
                    subtitle = if (BiometricUnlock.available(context)) {
                        stringResource(R.string.settings_biometric_hint)
                    } else {
                        stringResource(R.string.settings_biometric_unavailable)
                    },
                    checked = preferences.biometric,
                    onCheckedChange = { value -> viewModel.update { it.copy(biometric = value) } }
                )
            }
        }

        SectionCard(title = stringResource(R.string.settings_trusted)) {
            var name by remember(preferences.trustedName) { mutableStateOf(preferences.trustedName) }
            var phone by remember(preferences.trustedPhone) { mutableStateOf(preferences.trustedPhone) }
            OutlinedTextField(
                colors = NaqaaOutlinedTextFieldColors(),
                value = name,
                onValueChange = { name = it },
                label = { Text(text = stringResource(R.string.settings_trusted_name)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                colors = NaqaaOutlinedTextFieldColors(),
                value = phone,
                onValueChange = { phone = it.filter { character -> character.isDigit() || character == '+' || character == ' ' } },
                label = { Text(text = stringResource(R.string.settings_trusted_phone)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = { viewModel.update { it.copy(trustedName = name, trustedPhone = phone) } },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = stringResource(R.string.save))
            }
        }

        SectionCard(title = stringResource(R.string.settings_reason)) {
            var reason by remember(preferences.reason) { mutableStateOf(preferences.reason) }
            OutlinedTextField(
                colors = NaqaaOutlinedTextFieldColors(),
                value = reason,
                onValueChange = { value -> if (value.length <= 400) reason = value },
                minLines = 3,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = { viewModel.update { it.copy(reason = reason) } },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = stringResource(R.string.save))
            }
        }

        SectionCard(title = stringResource(R.string.settings_widget_title)) {
            Text(
                text = stringResource(R.string.settings_widget_body),
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = {
                    val manager = AppWidgetManager.getInstance(context)
                    val pinned = runCatching {
                        manager.requestPinAppWidget(ComponentName(context, ProgressWidget::class.java), null, null)
                    }.getOrDefault(false)
                    widgetNotice = if (pinned) null else R.string.settings_widget_unsupported
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = stringResource(R.string.settings_widget_add))
            }
            widgetNotice?.let {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = stringResource(it),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        SectionCard(title = stringResource(R.string.settings_privacy)) {
            Text(text = stringResource(R.string.settings_privacy_body), style = MaterialTheme.typography.bodySmall)
        }

        SectionCard(title = stringResource(R.string.settings_about)) {
            Text(
                text = stringResource(R.string.settings_version, versionName),
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.settings_sources),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        OutlinedButton(
            onClick = {
                eraseStep = 0
                confirmPin = ""
                showEraseDialog = true
            },
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
        ) {
            Text(text = stringResource(R.string.settings_erase), color = MaterialTheme.colorScheme.error)
        }
        Spacer(Modifier.height(20.dp))
    }

    if (showPinDialog) {
        PinSetupDialog(
            preferences = preferences,
            onDismiss = { showPinDialog = false },
            onSave = { pin ->
                val salt = PinHasher.salt()
                val hash = PinHasher.hash(pin.toCharArray(), salt)
                viewModel.update {
                    it.copy(pinSalt = PinGate.encode(salt), pinHash = PinGate.encode(hash), pinFailures = 0, pinLockedUntil = null)
                }
                showPinDialog = false
            },
            onRemove = {
                viewModel.update { it.copy(pinSalt = "", pinHash = "", biometric = false, pinFailures = 0, pinLockedUntil = null) }
                showPinDialog = false
            }
        )
    }

    if (showEraseDialog) {
        AlertDialog(
            onDismissRequest = { showEraseDialog = false },
            title = { Text(text = stringResource(R.string.erase_title)) },
            text = {
                Column {
                    Text(
                        text = if (eraseStep == 0) {
                            stringResource(R.string.erase_step_one)
                        } else {
                            stringResource(R.string.erase_step_two)
                        }
                    )
                    if (eraseStep == 1 && preferences.hasPin) {
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            colors = NaqaaOutlinedTextFieldColors(),
                            value = confirmPin,
                            onValueChange = { confirmPin = it.filter(Char::isDigit) },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            label = { Text(text = stringResource(R.string.pin_label)) },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            },
            confirmButton = {
                if (eraseStep == 0) {
                    TextButton(onClick = { eraseStep = 1 }) { Text(text = stringResource(R.string.erase_continue)) }
                } else {
                    TextButton(
                        enabled = !preferences.hasPin || PinGate.verify(preferences, confirmPin),
                        onClick = {
                            viewModel.eraseAll()
                            showEraseDialog = false
                        }
                    ) { Text(text = stringResource(R.string.erase_confirm)) }
                }
            },
            dismissButton = {
                TextButton(onClick = { showEraseDialog = false }) { Text(text = stringResource(R.string.cancel)) }
            }
        )
    }
}

@Composable
private fun PinSetupDialog(
    preferences: Preferences,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
    onRemove: () -> Unit
) {
    var current by remember { mutableStateOf("") }
    var next by remember { mutableStateOf("") }
    var repeat by remember { mutableStateOf("") }
    var problem by remember { mutableStateOf<Int?>(null) }
    val currentOk = !preferences.hasPin || PinGate.verify(preferences, current)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(if (preferences.hasPin) R.string.settings_pin_change else R.string.settings_pin_create)) },
        text = {
            Column {
                if (preferences.hasPin) {
                    OutlinedTextField(
                        colors = NaqaaOutlinedTextFieldColors(),
                        value = current,
                        onValueChange = { current = it.filter(Char::isDigit) },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        label = { Text(text = stringResource(R.string.settings_pin_current)) },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                }
                OutlinedTextField(
                    colors = NaqaaOutlinedTextFieldColors(),
                    value = next,
                    onValueChange = { next = it.filter(Char::isDigit) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    label = { Text(text = stringResource(R.string.settings_pin_new)) },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    colors = NaqaaOutlinedTextFieldColors(),
                    value = repeat,
                    onValueChange = { repeat = it.filter(Char::isDigit) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    label = { Text(text = stringResource(R.string.settings_pin_repeat)) },
                    modifier = Modifier.fillMaxWidth()
                )
                problem?.let {
                    Spacer(Modifier.height(6.dp))
                    Text(text = stringResource(it), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                when {
                    !currentOk -> problem = R.string.pin_wrong
                    next != repeat -> problem = R.string.settings_pin_mismatch
                    !PinHasher.isValid(next.toCharArray()) -> problem = R.string.settings_pin_rule
                    else -> onSave(next)
                }
            }) { Text(text = stringResource(R.string.save)) }
        },
        dismissButton = {
            if (preferences.hasPin) {
                TextButton(onClick = onRemove) { Text(text = stringResource(R.string.settings_pin_remove)) }
            } else {
                TextButton(onClick = onDismiss) { Text(text = stringResource(R.string.cancel)) }
            }
        }
    )
}

@Composable
private fun personaLabel(persona: Persona): String = stringResource(
    when (persona) {
        Persona.NAQAA -> R.string.persona_naqaa
        Persona.CALCULATOR -> R.string.persona_calculator
        Persona.NOTES -> R.string.persona_notes
        Persona.TASKS -> R.string.persona_tasks
    }
)
