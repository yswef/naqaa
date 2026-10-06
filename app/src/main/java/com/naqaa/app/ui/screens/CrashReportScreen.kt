package com.naqaa.app.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.naqaa.app.R
import com.naqaa.app.util.CrashLog

/**
 * Shown when the previous start did not finish: the text is on the device and nowhere else,
 * so the user decides whether to copy it, send it, or throw it away.
 */
@Composable
fun CrashReportScreen(report: CrashLog.Report, onContinue: () -> Unit) {
    val context = LocalContext.current
    var copied by remember { mutableStateOf(false) }
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text(text = stringResource(R.string.crash_title), style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(8.dp))
            Text(text = stringResource(R.string.crash_body), style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(12.dp))
            Text(
                text = stringResource(
                    if (report.kind == CrashLog.Kind.EXCEPTION) R.string.crash_exception else R.string.crash_interrupted
                ),
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(Modifier.height(8.dp))
            SectionCard {
                Text(text = report.text, style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = {
                    copy(context, report.text)
                    copied = true
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = stringResource(if (copied) R.string.crash_copied else R.string.crash_copy))
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = { share(context, report.text) }, modifier = Modifier.fillMaxWidth()) {
                Text(text = stringResource(R.string.crash_share))
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = onContinue, modifier = Modifier.fillMaxWidth()) {
                Text(text = stringResource(R.string.crash_continue))
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}

private fun copy(context: Context, text: String) {
    val manager = context.getSystemService(ClipboardManager::class.java) ?: return
    runCatching { manager.setPrimaryClip(ClipData.newPlainText(CLIP_LABEL, text)) }
}

private fun share(context: Context, text: String) {
    val intent = Intent(Intent.ACTION_SEND)
        .setType("text/plain")
        .putExtra(Intent.EXTRA_TEXT, text)
    runCatching {
        context.startActivity(
            Intent.createChooser(intent, context.getString(R.string.crash_share)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}

private const val CLIP_LABEL = "naqaa"
