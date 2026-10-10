package com.naqaa.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.material3.OutlinedTextField
import com.naqaa.app.R
import com.naqaa.app.ui.theme.NaqaaOutlinedTextFieldColors
import com.naqaa.app.data.Preferences
import com.naqaa.app.ui.BiometricUnlock
import com.naqaa.app.ui.PinGate
import com.naqaa.app.util.TimeX
import java.time.Instant

/**
 * The lock in front of the journal.
 *
 * A six digit keypad is used instead of a text field, and the wait after repeated failures
 * is shown rather than hidden, because the person in front of the screen is the owner and
 * a silent refusal would look like a defect. Biometrics are offered only when the device
 * supports them and the owner turned them on.
 */
@Composable
fun LockScreen(
    preferences: Preferences,
    onUnlocked: () -> Unit,
    onUpdate: ((Preferences) -> Preferences) -> Unit
) {
    var pin by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    var now by remember { mutableStateOf(Instant.now()) }
    var showReset by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val waiting = PinGate.isWaiting(preferences, now)

    LaunchedEffect(pin, waiting) {
        if (pin.length == PinGate.PIN_LENGTH && !waiting) {
            if (PinGate.verify(preferences, pin)) {
                onUpdate { PinGate.onSuccess(it) }
                onUnlocked()
            } else {
                onUpdate { PinGate.onFailure(it, Instant.now()) }
                message = context.getString(R.string.pin_wrong)
            }
            pin = ""
        }
    }
    LaunchedEffect(preferences.pinLockedUntil) {
        while (PinGate.isWaiting(preferences, Instant.now())) {
            kotlinx.coroutines.delay(1_000)
            now = Instant.now()
        }
        now = Instant.now()
    }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = stringResource(R.string.app_name), style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(8.dp))
            Text(
                text = if (waiting) {
                    stringResource(R.string.pin_wait, TimeX.countdown(PinGate.waitingSeconds(preferences, now)))
                } else {
                    stringResource(R.string.pin_enter)
                },
                style = MaterialTheme.typography.bodyLarge
            )
            Spacer(Modifier.height(18.dp))
            Dots(filled = pin.length, total = PinGate.PIN_LENGTH)
            Spacer(Modifier.height(10.dp))
            message?.let {
                Text(text = it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(8.dp))
            }
            Keypad(
                enabled = !waiting,
                onDigit = { if (pin.length < PinGate.PIN_LENGTH) pin += it },
                onBackspace = { pin = pin.dropLast(1) }
            )
            if (preferences.biometric && BiometricUnlock.available(context)) {
                Spacer(Modifier.height(12.dp))
                OutlinedButton(
                    onClick = {
                        BiometricUnlock.prompt(
                            context = context,
                            title = context.getString(R.string.app_name),
                            subtitle = context.getString(R.string.pin_enter),
                            cancelText = context.getString(R.string.cancel),
                            onSuccess = {
                                onUpdate { PinGate.onSuccess(it) }
                                onUnlocked()
                            }
                        )
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(text = stringResource(R.string.unlock_biometric))
                }
            }
            Spacer(Modifier.height(4.dp))
            TextButton(onClick = { showReset = true }) {
                Text(text = stringResource(R.string.pin_forgot))
            }
        }
    }

    if (showReset) {
        AlertDialog(
            onDismissRequest = { showReset = false },
            title = { Text(text = stringResource(R.string.erase_title)) },
            text = { Text(text = stringResource(R.string.pin_forgot_body)) },
            confirmButton = {
                TextButton(onClick = { showReset = false }) { Text(text = stringResource(R.string.dismiss)) }
            }
        )
    }
}

/** PIN entry used by the screens that need a confirmation rather than a session unlock. */
@Composable
fun PinPrompt(preferences: Preferences, onCancel: () -> Unit, onSuccess: () -> Unit) {
    var pin by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(text = stringResource(R.string.pin_confirm_title)) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                OutlinedTextField(
                colors = NaqaaOutlinedTextFieldColors(),
                    value = pin,
                    onValueChange = { value -> if (value.length <= PinGate.PIN_LENGTH) pin = value.filter(Char::isDigit) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    label = { Text(text = stringResource(R.string.pin_label)) }
                )
                message?.let {
                    Spacer(Modifier.height(6.dp))
                    Text(text = it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                if (PinGate.verify(preferences, pin)) onSuccess() else message = context.getString(R.string.pin_wrong)
            }) { Text(text = stringResource(R.string.confirm)) }
        },
        dismissButton = {
            TextButton(onClick = onCancel) { Text(text = stringResource(R.string.cancel)) }
        }
    )
}

@Composable
private fun Dots(filled: Int, total: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        repeat(total) { index ->
            Box(
                modifier = Modifier
                    .size(14.dp)
                    .background(
                        color = if (index < filled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                        shape = CircleShape
                    )
            )
        }
    }
}

@Composable
private fun Keypad(enabled: Boolean, onDigit: (String) -> Unit, onBackspace: () -> Unit) {
    val rows = listOf(
        listOf("1", "2", "3"),
        listOf("4", "5", "6"),
        listOf("7", "8", "9"),
        listOf("", "0", "<")
    )
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        rows.forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                row.forEach { key ->
                    when (key) {
                        "" -> Spacer(Modifier.weight(1f))
                        "<" -> OutlinedButton(
                            onClick = onBackspace,
                            enabled = enabled,
                            modifier = Modifier.weight(1f)
                        ) { Text(text = stringResource(R.string.pin_backspace)) }
                        else -> Button(
                            onClick = { onDigit(key) },
                            enabled = enabled,
                            modifier = Modifier.weight(1f)
                        ) { Text(text = key) }
                    }
                }
            }
        }
    }
}
