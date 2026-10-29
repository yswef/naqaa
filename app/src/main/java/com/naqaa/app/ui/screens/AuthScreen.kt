package com.naqaa.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.naqaa.app.R
import com.naqaa.app.data.DevAccount
import com.naqaa.app.data.Session
import com.naqaa.app.data.UserStore
import com.naqaa.app.ui.theme.NaqaaOutlinedTextFieldColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Sign in or create an account. With no account yet the screen offers to create the first
 * one. The name entered here is the name printed on the reports. The hidden developer
 * account is recognised by [DevAccount] and is indistinguishable from a wrong password on
 * the screen, so nothing hints that it exists.
 */
@Composable
fun AuthScreen(users: UserStore, onSignedIn: () -> Unit) {
    var creating by remember { mutableStateOf(!users.hasUsers()) }
    var name by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var repeat by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<Int?>(null) }
    var busy by remember { mutableStateOf(false) }
    var failures by remember { mutableIntStateOf(0) }
    var waitSeconds by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()

    // Five wrong attempts in a row cost a short wait that grows, so guessing is slow.
    LaunchedEffect(waitSeconds) {
        if (waitSeconds > 0) {
            delay(1_000)
            waitSeconds -= 1
        }
    }

    fun submit() {
        if (busy || waitSeconds > 0) return
        busy = true
        error = null
        val typedName = name
        val typedPassword = password.toCharArray()
        val typedRepeat = repeat
        scope.launch {
            if (creating) {
                if (typedPassword.concatToString() != typedRepeat) {
                    error = R.string.auth_mismatch
                } else {
                    val result = withContext(Dispatchers.Default) { users.create(typedName, typedPassword.copyOf()) }
                    when (result) {
                        UserStore.Result.Ok -> {
                            Session.open(typedName.trim())
                            busy = false
                            onSignedIn()
                            return@launch
                        }
                        UserStore.Result.NameTaken -> error = R.string.auth_name_taken
                        UserStore.Result.NameInvalid -> error = R.string.auth_name_invalid
                        UserStore.Result.PasswordShort -> error = R.string.auth_password_short
                    }
                }
            } else {
                val developer = withContext(Dispatchers.Default) { DevAccount.matches(typedName, typedPassword.copyOf()) }
                val stored = if (developer) null else withContext(Dispatchers.Default) { users.verify(typedName, typedPassword.copyOf()) }
                if (developer) {
                    Session.open(typedName.trim(), isDeveloper = true)
                    busy = false
                    onSignedIn()
                    return@launch
                }
                if (stored != null) {
                    Session.open(stored)
                    busy = false
                    onSignedIn()
                    return@launch
                }
                failures += 1
                error = R.string.auth_wrong
                if (failures % 5 == 0) waitSeconds = 30 * (failures / 5)
            }
            typedPassword.fill('\u0000')
            busy = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = stringResource(R.string.app_name), style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(6.dp))
        Text(
            text = stringResource(if (creating) R.string.auth_create_title else R.string.auth_login_title),
            style = MaterialTheme.typography.titleMedium
        )
        Spacer(Modifier.height(20.dp))
        OutlinedTextField(
            colors = NaqaaOutlinedTextFieldColors(),
            value = name,
            onValueChange = { name = it.take(24) },
            singleLine = true,
            label = { Text(text = stringResource(R.string.auth_username)) },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            colors = NaqaaOutlinedTextFieldColors(),
            value = password,
            onValueChange = { password = it.take(64) },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            label = { Text(text = stringResource(R.string.auth_password)) },
            modifier = Modifier.fillMaxWidth()
        )
        if (creating) {
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                colors = NaqaaOutlinedTextFieldColors(),
                value = repeat,
                onValueChange = { repeat = it.take(64) },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                label = { Text(text = stringResource(R.string.auth_password_repeat)) },
                modifier = Modifier.fillMaxWidth()
            )
        }
        error?.let {
            Spacer(Modifier.height(8.dp))
            Text(text = stringResource(it), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        }
        if (waitSeconds > 0) {
            Spacer(Modifier.height(8.dp))
            Text(text = stringResource(R.string.auth_wait, waitSeconds), style = MaterialTheme.typography.bodyMedium)
        }
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = ::submit,
            enabled = !busy && waitSeconds == 0 && name.isNotBlank() && password.isNotEmpty(),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(text = stringResource(if (creating) R.string.auth_create else R.string.auth_login))
        }
        // New accounts can be added from the sign-in screen once a first account exists.
        TextButton(onClick = { creating = !creating; error = null }) {
            Text(text = stringResource(if (creating) R.string.auth_have_account else R.string.auth_new_account))
        }
    }
}
