package com.naqaa.app.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import com.naqaa.app.R
import com.naqaa.app.ui.AppViewModel
import com.naqaa.app.ui.UiState

/** Every screen the application can show. */
enum class Destination(val label: Int, val icon: ImageVector?, val inBar: Boolean) {
    HOME(R.string.nav_home, Icons.Filled.Home, true),
    JOURNAL(R.string.nav_journal, Icons.Filled.Edit, true),
    PROTECTION(R.string.nav_protection, Icons.Filled.Lock, true),
    PRAYER(R.string.nav_prayer, Icons.Filled.Notifications, true),
    SETTINGS(R.string.nav_settings, Icons.Filled.Settings, true),
    ADHKAR(R.string.prayer_adhkar, null, false),
    EMERGENCY(R.string.nav_emergency, null, false),
    PROGRESS(R.string.nav_progress, null, false),
    PLAN(R.string.nav_plan, null, false),
    REPORT(R.string.nav_report, null, false)
}

/** Main tabs and secondary screens. */
@Composable
fun AppShell(viewModel: AppViewModel, state: UiState, requested: Destination? = null) {
    var destinationName by rememberSaveable { mutableStateOf((requested ?: Destination.HOME).name) }
    val destination = Destination.entries.firstOrNull { it.name == destinationName } ?: Destination.HOME

    LaunchedEffect(requested) {
        requested?.let { destinationName = it.name }
    }

    fun navigate(to: Destination) {
        destinationName = to.name
    }

    BackHandler(enabled = destination != Destination.HOME) {
        navigate(if (destination == Destination.ADHKAR) Destination.PRAYER else Destination.HOME)
    }

    Scaffold(
        bottomBar = {
            NavigationBar {
                Destination.entries.filter { it.inBar }.forEach { entry ->
                    NavigationBarItem(
                        selected = destination == entry || (destination == Destination.ADHKAR && entry == Destination.PRAYER),
                        onClick = { navigate(entry) },
                        icon = { entry.icon?.let { Icon(imageVector = it, contentDescription = null) } },
                        label = { Text(text = stringResource(entry.label)) }
                    )
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {
            when (destination) {
                Destination.HOME -> HomeScreen(viewModel, state, ::navigate)
                Destination.EMERGENCY -> EmergencyScreen(viewModel, state, ::navigate)
                Destination.JOURNAL -> JournalScreen(viewModel, state)
                Destination.PROGRESS -> ProgressScreen(state)
                Destination.PROTECTION -> ProtectionScreen(viewModel, state)
                Destination.PRAYER -> PrayerScreen(viewModel, state) { navigate(Destination.ADHKAR) }
                Destination.ADHKAR -> AdhkarScreen(viewModel, onBack = { navigate(Destination.PRAYER) })
                Destination.PLAN -> PlanScreen(viewModel, state)
                Destination.REPORT -> ReportScreen(viewModel, state)
                Destination.SETTINGS -> SettingsScreen(viewModel, state)
            }
        }
    }
}
