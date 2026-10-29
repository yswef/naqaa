package com.naqaa.app.ui

import android.content.Context
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.TextButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.naqaa.app.NaqaaApplication
import com.naqaa.app.R
import com.naqaa.app.content.Content
import com.naqaa.app.data.Session
import com.naqaa.app.guard.UsagePass
import com.naqaa.app.ui.screens.ChipFlow
import com.naqaa.app.ui.screens.PinPrompt
import com.naqaa.app.ui.theme.NaqaaTheme
import com.naqaa.app.util.LocaleX

/**
 * Covers an application that the user chose to keep away during a window of the day, or
 * during the night. It is a screen, not a wall: leaving it returns to the home screen and
 * the application stays untouched. Opening the locked application on purpose is possible
 * after the PIN and it is granted for a short while, so the decision leaves a trace rather
 * than a fight.
 */
class AppLockActivity : ComponentActivity() {

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleX.localized(newBase, languageOf(newBase)))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        setContent {
            NaqaaTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = Color.Transparent) {
                    Screen(
                        messageRes = intent.getIntExtra(EXTRA_MESSAGE, R.string.lock_message_schedule),
                        packageName = intent.getStringExtra(EXTRA_PACKAGE),
                        ended = intent.getBooleanExtra(Overlays.EXTRA_ENDED, false),
                        onLeave = { finish() },
                        onContinue = { finish() }
                    )
                }
            }
        }
    }

    @Composable
    private fun Screen(messageRes: Int, packageName: String?, ended: Boolean, onLeave: () -> Unit, onContinue: () -> Unit) {
        var askingPin by remember { mutableStateOf(false) }
        var choosingTime by remember { mutableStateOf(false) }
        val preferences = remember { (application as NaqaaApplication).graph.current() }
        val card = remember { Content.protectionCard() }
        val label = remember(packageName) {
            packageName?.let {
                runCatching {
                    val info = packageManager.getApplicationInfo(it, 0)
                    packageManager.getApplicationLabel(info).toString()
                }.getOrNull()
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
            Text(text = stringResource(R.string.app_name), style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(12.dp))
            Text(
                text = when {
                    ended -> stringResource(R.string.usage_ended_title)
                    label != null -> stringResource(R.string.lock_title_app, label)
                    else -> stringResource(R.string.lock_title_generic)
                },
                style = MaterialTheme.typography.headlineSmall
            )
            Spacer(Modifier.height(8.dp))
            Text(text = stringResource(messageRes), style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(20.dp))
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text(text = card.verseAr, style = MaterialTheme.typography.bodyLarge)
                    Spacer(Modifier.height(6.dp))
                    Text(text = card.referenceAr, style = MaterialTheme.typography.labelMedium)
                    Spacer(Modifier.height(10.dp))
                    Text(text = card.motivation(preferences.language), style = MaterialTheme.typography.bodyMedium)
                }
            }
            Spacer(Modifier.height(20.dp))
            Button(onClick = onLeave, modifier = Modifier.fillMaxWidth()) {
                Text(text = stringResource(R.string.lock_back_home))
            }
            Spacer(Modifier.height(8.dp))
            if (!ended || Session.developer) {
                OutlinedButton(
                    onClick = {
                        // The developer session skips the PIN, like the waits.
                        if (Session.developer || !preferences.hasPin) choosingTime = true else askingPin = true
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(text = stringResource(R.string.lock_open_once))
                }
            }
        }
        if (askingPin) {
            PinPrompt(
                preferences = preferences,
                onCancel = { askingPin = false },
                onSuccess = {
                    askingPin = false
                    choosingTime = true
                }
            )
        }
        if (choosingTime) {
            // How long to open it is the user's own choice; when it runs out this screen
            // comes back and says that the time is over.
            AlertDialog(
                onDismissRequest = { choosingTime = false },
                title = { Text(text = stringResource(R.string.lock_choose_time)) },
                text = {
                    ChipFlow {
                        listOf(5, 15, 30, 60).forEach { minutes ->
                            FilterChip(
                                selected = false,
                                onClick = {
                                    packageName?.let { UsagePass.allowApp(it, minutes) }
                                    onContinue()
                                },
                                label = { Text(text = stringResource(R.string.minutes_short, minutes)) },
                                modifier = Modifier.padding(top = 3.dp, bottom = 3.dp)
                            )
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { choosingTime = false }) { Text(text = stringResource(R.string.cancel)) }
                }
            )
        }
    }

    companion object {
        const val EXTRA_MESSAGE = "message"
        const val EXTRA_PACKAGE = "package"

        private fun languageOf(context: Context): String = runCatching {
            LocaleX.sanitize((context.applicationContext as NaqaaApplication).graph.current().language)
        }.getOrDefault(LocaleX.DEFAULT_LANGUAGE)
    }
}
