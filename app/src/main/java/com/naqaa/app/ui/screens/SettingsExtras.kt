package com.naqaa.app.ui.screens

import android.app.Activity
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import com.naqaa.app.data.Backup
import com.naqaa.app.data.Session
import com.naqaa.app.sound.Sounds
import com.naqaa.app.ui.AppViewModel
import com.naqaa.app.ui.UiState
import com.naqaa.app.ui.theme.NaqaaOutlinedTextFieldColors
import java.security.MessageDigest
import java.time.LocalDate

/** Who is signed in, and a way to sign out. */
@Composable
fun AccountCard() {
    val context = LocalContext.current
    SectionCard(title = stringResource(R.string.account_title)) {
        // The developer session is shown to its owner only, in its own card below.
        StatLine(stringResource(R.string.auth_username), Session.username.orEmpty())
        Spacer(Modifier.height(8.dp))
        OutlinedButton(
            onClick = {
                Session.close()
                (context as? Activity)?.recreate()
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text(text = stringResource(R.string.account_sign_out)) }
    }
}

/**
 * Export and import of everything, protected by a password chosen at the moment of export.
 * The file opens in any later version, on any phone, and survives a reinstall.
 */
@Composable
fun BackupCard(viewModel: AppViewModel) {
    var exportPassword by remember { mutableStateOf<String?>(null) }
    var askExport by remember { mutableStateOf(false) }
    var importUri by remember { mutableStateOf<android.net.Uri?>(null) }
    var message by remember { mutableStateOf<Int?>(null) }
    var detail by remember { mutableStateOf("") }

    val createLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        val password = exportPassword
        exportPassword = null
        if (uri != null && password != null) {
            message = R.string.backup_working
            viewModel.exportBackup(uri, password.toCharArray()) { ok ->
                message = if (ok) R.string.backup_export_done else R.string.backup_export_failed
            }
        }
    }
    val openLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) importUri = uri
    }

    SectionCard(title = stringResource(R.string.backup_title)) {
        Text(text = stringResource(R.string.backup_hint), style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(8.dp))
        Button(onClick = { askExport = true }, modifier = Modifier.fillMaxWidth()) {
            Text(text = stringResource(R.string.backup_export))
        }
        Spacer(Modifier.height(6.dp))
        OutlinedButton(onClick = { openLauncher.launch(arrayOf("*/*")) }, modifier = Modifier.fillMaxWidth()) {
            Text(text = stringResource(R.string.backup_import))
        }
        message?.let {
            Spacer(Modifier.height(6.dp))
            Text(text = stringResource(it) + detail, style = MaterialTheme.typography.bodyMedium)
        }
    }

    if (askExport) {
        PasswordDialog(
            title = stringResource(R.string.backup_export),
            body = stringResource(R.string.backup_password_new),
            confirmRepeat = true,
            onCancel = { askExport = false },
            onSubmit = { password ->
                askExport = false
                exportPassword = password
                createLauncher.launch("naqaa-backup-" + LocalDate.now() + ".nqbk")
            }
        )
    }
    importUri?.let { uri ->
        PasswordDialog(
            title = stringResource(R.string.backup_import),
            body = stringResource(R.string.backup_password_existing),
            confirmRepeat = false,
            onCancel = { importUri = null },
            onSubmit = { password ->
                importUri = null
                message = R.string.backup_working
                detail = ""
                viewModel.importBackup(uri, password.toCharArray()) { outcome ->
                    detail = ""
                    message = when (outcome) {
                        is Backup.Outcome.Imported -> {
                            detail = " (" + outcome.events + ")"
                            R.string.backup_import_done
                        }
                        Backup.Outcome.WrongPasswordOrDamaged -> R.string.backup_wrong_password
                        Backup.Outcome.NotABackup -> R.string.backup_not_a_backup
                        Backup.Outcome.TooNew -> R.string.backup_too_new
                        is Backup.Outcome.Failed -> {
                            detail = " " + outcome.reason
                            R.string.backup_import_failed
                        }
                    }
                }
            }
        )
    }
}

@Composable
private fun PasswordDialog(
    title: String,
    body: String,
    confirmRepeat: Boolean,
    onCancel: () -> Unit,
    onSubmit: (String) -> Unit
) {
    var password by remember { mutableStateOf("") }
    var repeat by remember { mutableStateOf("") }
    val valid = password.length >= Backup.MIN_PASSWORD && (!confirmRepeat || password == repeat)
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(text = title) },
        text = {
            androidx.compose.foundation.layout.Column {
                Text(text = body, style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    colors = NaqaaOutlinedTextFieldColors(),
                    value = password,
                    onValueChange = { password = it.take(64) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    label = { Text(text = stringResource(R.string.auth_password)) }
                )
                if (confirmRepeat) {
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        colors = NaqaaOutlinedTextFieldColors(),
                        value = repeat,
                        onValueChange = { repeat = it.take(64) },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        label = { Text(text = stringResource(R.string.auth_password_repeat)) }
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSubmit(password) }, enabled = valid) { Text(text = stringResource(R.string.confirm)) }
        },
        dismissButton = {
            TextButton(onClick = onCancel) { Text(text = stringResource(R.string.cancel)) }
        }
    )
}

/** Switches for the recorded call to prayer and hadith, and a button to try them now. */
@Composable
fun SoundCard(viewModel: AppViewModel, state: UiState) {
    val context = LocalContext.current
    val files = remember { Sounds.files(context) }
    val adhan = remember { Sounds.adhan(context, null) }
    val hadith = remember { Sounds.hadithFiles(context) }
    SectionCard(title = stringResource(R.string.sound_title)) {
        Text(
            text = stringResource(R.string.sound_found, if (adhan != null) 1 else 0, hadith.size),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (files.isEmpty()) {
            Spacer(Modifier.height(4.dp))
            Text(text = stringResource(R.string.sound_missing_hint), style = MaterialTheme.typography.bodySmall)
        }
        SettingSwitch(
            title = stringResource(R.string.sound_adhan),
            checked = state.preferences.adhanSound,
            onCheckedChange = { value -> viewModel.update { it.copy(adhanSound = value) } }
        )
        SettingSwitch(
            title = stringResource(R.string.sound_hadith),
            subtitle = stringResource(R.string.sound_hadith_hint),
            checked = state.preferences.hadithSound,
            onCheckedChange = { value -> viewModel.update { it.copy(hadithSound = value) } }
        )
        Row2(
            enabled = adhan != null,
            onAdhan = { adhan?.let { Sounds.play(context, it, loud = true) } },
            onHadith = { Sounds.hadithOfDay(context, LocalDate.now())?.let { Sounds.play(context, it, loud = false) } },
            hadithEnabled = hadith.isNotEmpty(),
            onStop = { Sounds.stop(context) }
        )
    }
}

@Composable
private fun Row2(enabled: Boolean, hadithEnabled: Boolean, onAdhan: () -> Unit, onHadith: () -> Unit, onStop: () -> Unit) {
    OutlinedButton(onClick = onAdhan, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
        Text(text = stringResource(R.string.sound_try_adhan))
    }
    Spacer(Modifier.height(4.dp))
    OutlinedButton(onClick = onHadith, enabled = hadithEnabled, modifier = Modifier.fillMaxWidth()) {
        Text(text = stringResource(R.string.sound_try_hadith))
    }
    Spacer(Modifier.height(4.dp))
    TextButton(onClick = onStop, modifier = Modifier.fillMaxWidth()) { Text(text = stringResource(R.string.sound_stop)) }
}

/**
 * The SHA-256 of the certificate that signed the installed copy. Android refuses to replace
 * an application with one signed by a different certificate ("conflicts with an existing
 * package"), so two builds that show different values here cannot update one another.
 */
@Composable
fun SigningCard() {
    val context = LocalContext.current
    val fingerprint = remember { signingFingerprint(context) }
    SectionCard(title = stringResource(R.string.signing_title)) {
        Text(text = stringResource(R.string.signing_hint), style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(6.dp))
        Text(text = fingerprint, style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(6.dp))
        OutlinedButton(onClick = { copyText(context, fingerprint) }, modifier = Modifier.fillMaxWidth()) {
            Text(text = stringResource(R.string.signing_copy))
        }
    }
}

private fun signingFingerprint(context: android.content.Context): String = runCatching {
    val manager = context.packageManager
    val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        manager.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
            .signingInfo?.apkContentsSigners
    } else {
        @Suppress("DEPRECATION")
        manager.getPackageInfo(context.packageName, PackageManager.GET_SIGNATURES).signatures
    }
    val first = signatures?.firstOrNull() ?: return@runCatching "unknown"
    MessageDigest.getInstance("SHA-256").digest(first.toByteArray()).joinToString(":") { "%02X".format(it) }
}.getOrDefault("unknown")

/** Shown only in the hidden developer session: what it changes, and shortcuts to test screens. */
@Composable
fun DeveloperCard() {
    if (!Session.developer) return
    val context = LocalContext.current
    SectionCard(title = stringResource(R.string.dev_title)) {
        Text(text = stringResource(R.string.dev_body), style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(8.dp))
        OutlinedButton(
            onClick = {
                runCatching {
                    context.startActivity(
                        com.naqaa.app.ui.Overlays.lock(context, R.string.usage_ended_message, context.packageName, ended = true)
                    )
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text(text = stringResource(R.string.dev_test_ended)) }
        Spacer(Modifier.height(4.dp))
        OutlinedButton(
            onClick = {
                runCatching {
                    context.startActivity(
                        com.naqaa.app.ui.Overlays.delay(context, com.naqaa.app.guard.DelayGate.Action.OTHER)
                    )
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text(text = stringResource(R.string.dev_test_delay)) }
    }
}
