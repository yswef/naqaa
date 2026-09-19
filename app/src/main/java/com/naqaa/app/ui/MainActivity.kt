package com.naqaa.app.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModelProvider
import com.naqaa.app.NaqaaApplication
import com.naqaa.app.ui.screens.AppShell
import com.naqaa.app.ui.screens.Destination
import com.naqaa.app.ui.screens.LockScreen
import com.naqaa.app.ui.screens.OnboardingScreen
import com.naqaa.app.ui.theme.NaqaaTheme
import com.naqaa.app.util.LocaleX

/**
 * The single interface of the application.
 *
 * The screens are one composable tree with a small navigation state; a navigation library
 * would add a dependency for a fixed set of destinations that never need deep links. The
 * window is marked secure so the PIN pad and the journal never appear in the recent tasks
 * preview.
 */
class MainActivity : ComponentActivity() {

    private val viewModel: AppViewModel by lazy { ViewModelProvider(this)[AppViewModel::class.java] }

    private var requestedScreen by mutableStateOf<Destination?>(null)

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleX.localized(newBase, languageOf(newBase)))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        val preferences = (application as NaqaaApplication).graph.current()
        PersonaSwitch.apply(this, preferences.persona)
        requestedScreen = requested(intent)
        setContent {
            NaqaaTheme {
                val state by viewModel.state.collectAsState()
                LaunchedEffect(state.preferences.persona) {
                    PersonaSwitch.apply(this@MainActivity, state.preferences.persona)
                }
                Root(state, requestedScreen)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refresh()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        requestedScreen = requested(intent)
    }

    @Composable
    private fun Root(state: UiState, requested: Destination?) {
        var unlocked by rememberSaveable { mutableStateOf(!state.preferences.hasPin) }
        if (!state.preferences.onboarded) {
            OnboardingScreen(viewModel = viewModel)
            return
        }
        if (state.preferences.hasPin && !unlocked && !UnlockState.unlocked) {
            LockScreen(
                preferences = state.preferences,
                onUnlocked = {
                    UnlockState.unlocked = true
                    unlocked = true
                    viewModel.refresh()
                },
                onUpdate = { transform -> viewModel.update(transform) }
            )
            return
        }
        AppShell(viewModel = viewModel, state = state, requested = requested)
    }

    private companion object {
        private fun requested(intent: Intent?): Destination? = when (intent?.getStringExtra(Screens.EXTRA)) {
            Screens.HOME -> Destination.HOME
            Screens.EMERGENCY -> Destination.EMERGENCY
            Screens.PROTECTION -> Destination.PROTECTION
            Screens.PRAYER -> Destination.PRAYER
            else -> null
        }

        fun languageOf(context: Context): String = runCatching {
            LocaleX.sanitize((context.applicationContext as NaqaaApplication).graph.current().language)
        }.getOrDefault(LocaleX.DEFAULT_LANGUAGE)
    }
}

/** Session flag: the application locks itself again when the process is recreated. */
object UnlockState {
    @Volatile var unlocked: Boolean = false
}
