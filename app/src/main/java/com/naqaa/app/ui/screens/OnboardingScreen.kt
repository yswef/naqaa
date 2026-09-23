package com.naqaa.app.ui.screens

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.VpnService
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.naqaa.app.R
import com.naqaa.app.data.LockRule
import com.naqaa.app.data.PinHasher
import com.naqaa.app.prayer.CalculationMethod
import com.naqaa.app.prayer.Cities
import com.naqaa.app.prayer.City
import com.naqaa.app.ui.AppViewModel
import com.naqaa.app.ui.BiometricUnlock
import com.naqaa.app.ui.PinGate
import com.naqaa.app.util.LocaleX
import com.naqaa.app.util.SystemGate
import com.naqaa.app.vpn.DnsVpnService

/**
 * The setup walk, one decision per screen.
 *
 * Permissions are asked for one by one with a sentence explaining what each is for, and the
 * walk can be finished even when something is refused: the application works with less, and
 * the remaining switches stay reachable from the protection screen.
 */
@Composable
fun OnboardingScreen(viewModel: AppViewModel) {
    val context = LocalContext.current
    val preferences = viewModel.state.value.preferences
    var step by remember { mutableIntStateOf(0) }
    var reason by remember { mutableStateOf(preferences.reason) }
    var pin by remember { mutableStateOf("") }
    var pinRepeat by remember { mutableStateOf("") }
    var trustedName by remember { mutableStateOf(preferences.trustedName) }
    var trustedPhone by remember { mutableStateOf(preferences.trustedPhone) }
    var pendingCity by remember { mutableStateOf<City?>(Cities.byId(preferences.cityId)) }

    Column(
        modifier = Modifier.fillMaxSize().padding(20.dp)
    ) {
        LinearProgressIndicator(
            progress = { (step + 1).toFloat() / STEPS },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(18.dp))
        Column(modifier = Modifier.weight(1f)) {
            when (step) {
                0 -> StepLanguage(viewModel)
                1 -> StepReason(reason, { reason = it })
                2 -> StepCity(pendingCity) { pendingCity = it }
                3 -> StepLocation { pendingCity = it }
                4 -> StepPermissions()
                5 -> StepApps(viewModel)
                6 -> StepPin(
                    pin = pin,
                    pinRepeat = pinRepeat,
                    onPin = { pin = it },
                    onRepeat = { pinRepeat = it },
                    onBiometric = { value -> viewModel.update { it.copy(biometric = value) } },
                    biometricAvailable = BiometricUnlock.available(context),
                    biometricEnabled = preferences.biometric
                )
                else -> StepContact(trustedName, trustedPhone, { trustedName = it }, { trustedPhone = it })
            }
        }
        Row(modifier = Modifier.fillMaxWidth()) {
            if (step > 0) {
                TextButton(onClick = { step -= 1 }) { Text(text = stringResource(R.string.back)) }
            }
            Spacer(Modifier.weight(1f))
            Button(onClick = {
                if (step < STEPS - 1) {
                    step += 1
                } else {
                    finish(
                        viewModel = viewModel,
                        reason = reason,
                        city = pendingCity,
                        pin = pin,
                        trustedName = trustedName,
                        trustedPhone = trustedPhone
                    )
                }
            }) {
                Text(text = stringResource(if (step < STEPS - 1) R.string.next else R.string.onboarding_finish))
            }
        }
    }
}

@Composable
private fun StepLanguage(viewModel: AppViewModel) {
    val context = LocalContext.current
    val language = viewModel.state.value.preferences.language
    Column {
        Text(text = stringResource(R.string.onboarding_welcome), style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        Text(text = stringResource(R.string.onboarding_intro), style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(16.dp))
        Text(text = stringResource(R.string.settings_language), style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        Row {
            FilterChip(
                selected = language == LocaleX.ARABIC,
                onClick = {
                    viewModel.update { it.copy(language = LocaleX.ARABIC) }
                    (context as? Activity)?.recreate()
                },
                label = { Text(text = stringResource(R.string.language_arabic)) },
                modifier = Modifier.padding(end = 8.dp)
            )
            FilterChip(
                selected = language == LocaleX.ENGLISH,
                onClick = {
                    viewModel.update { it.copy(language = LocaleX.ENGLISH) }
                    (context as? Activity)?.recreate()
                },
                label = { Text(text = stringResource(R.string.language_english)) }
            )
        }
    }
}

@Composable
private fun StepReason(reason: String, onReason: (String) -> Unit) {
    Column {
        Text(text = stringResource(R.string.onboarding_reason_title), style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        Text(text = stringResource(R.string.onboarding_reason_body), style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = reason,
            onValueChange = { value -> if (value.length <= 400) onReason(value) },
            minLines = 4,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun StepCity(city: City?, onCity: (City?) -> Unit) {
    var query by remember { mutableStateOf("") }
    val results = remember(query) {
        if (query.isBlank()) Cities.all.take(12)
        else Cities.all.filter { it.nameAr.contains(query) || it.nameEn.contains(query, true) }
    }
    Column {
        Text(text = stringResource(R.string.onboarding_city_title), style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        Text(text = stringResource(R.string.onboarding_city_body), style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            singleLine = true,
            label = { Text(text = stringResource(R.string.search)) },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = city?.let { stringResource(R.string.onboarding_city_selected, it.nameAr) }
                ?: stringResource(R.string.onboarding_city_none),
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(Modifier.height(10.dp))
        LazyColumn(modifier = Modifier.fillMaxWidth().height(320.dp)) {
            items(results, key = { it.id }) { option ->
                TextButton(onClick = { onCity(option) }, modifier = Modifier.fillMaxWidth()) {
                    Text(text = "${option.nameAr} - ${option.countryAr}")
                }
            }
        }
    }
}

@Composable
private fun StepLocation(onCity: (City) -> Unit) {
    val context = LocalContext.current
    var refused by remember { mutableStateOf(false) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        refused = !granted
        if (granted) {
            val manager = context.getSystemService(LocationManager::class.java)
            val last = runCatching {
                manager.getProviders(true).firstNotNullOfOrNull { provider -> manager.getLastKnownLocation(provider) }
            }.getOrNull()
            if (last != null) onCity(Cities.nearest(last.latitude, last.longitude)) else refused = true
        }
    }
    Column {
        Text(text = stringResource(R.string.onboarding_location_title), style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        Text(text = stringResource(R.string.onboarding_location_body), style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(14.dp))
        OutlinedButton(
            onClick = { permission.launch(Manifest.permission.ACCESS_COARSE_LOCATION) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(text = stringResource(R.string.onboarding_location_action))
        }
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.onboarding_location_again),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (refused) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.onboarding_location_refused),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun StepPermissions() {
    val context = LocalContext.current
    Column {
        Text(text = stringResource(R.string.onboarding_permissions_title), style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        Text(text = stringResource(R.string.onboarding_permissions_body), style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(12.dp))
        val vpnMissing = remember { VpnService.prepare(context) != null }
        ActionButton(
            title = stringResource(R.string.protection_filter),
            subtitle = stringResource(R.string.onboarding_permission_vpn),
            onClick = {
                val consent = VpnService.prepare(context)
                if (consent != null) {
                    runCatching { context.startActivity(consent) }
                } else {
                    runCatching {
                        androidx.core.content.ContextCompat.startForegroundService(
                            context,
                            Intent(context, DnsVpnService::class.java)
                        )
                    }
                }
            }
        )
        ActionButton(
            title = stringResource(R.string.permission_accessibility),
            subtitle = stringResource(R.string.onboarding_permission_accessibility),
            onClick = { runCatching { context.startActivity(SystemGate.accessibilitySettings()) } }
        )
        ActionButton(
            title = stringResource(R.string.permission_admin),
            subtitle = stringResource(R.string.onboarding_permission_admin),
            onClick = { runCatching { context.startActivity(SystemGate.deviceAdminRequest(context)) } }
        )
        ActionButton(
            title = stringResource(R.string.permission_notifications),
            subtitle = stringResource(R.string.onboarding_permission_notifications),
            onClick = { runCatching { context.startActivity(SystemGate.notificationSettings(context)) } }
        )
        ActionButton(
            title = stringResource(R.string.permission_alarms),
            subtitle = stringResource(R.string.onboarding_permission_alarms),
            onClick = { runCatching { context.startActivity(SystemGate.exactAlarmSettings()) } }
        )
        ActionButton(
            title = stringResource(R.string.permission_battery),
            subtitle = stringResource(R.string.onboarding_permission_battery),
            onClick = { runCatching { context.startActivity(SystemGate.batterySettings()) } }
        )
    }
}

@Composable
private fun StepApps(viewModel: AppViewModel) {
    val preferences = viewModel.state.value.preferences
    var showPicker by remember { mutableStateOf(false) }
    Column {
        Text(text = stringResource(R.string.onboarding_apps_title), style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        Text(text = stringResource(R.string.onboarding_apps_body), style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(12.dp))
        preferences.lockedApps.forEach { rule ->
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Text(text = rule.packageName, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                TextButton(onClick = {
                    viewModel.update { current ->
                        current.copy(lockedApps = current.lockedApps.filterNot { it.packageName == rule.packageName })
                    }
                }) { Text(text = stringResource(R.string.cancel)) }
            }
        }
        Button(onClick = { showPicker = true }, modifier = Modifier.fillMaxWidth()) {
            Text(text = stringResource(R.string.protection_add_lock))
        }
    }
    if (showPicker) {
        AppPicker(
            excluded = preferences.lockedApps.map { it.packageName }.toSet(),
            own = LocalContext.current.packageName,
            onDismiss = { showPicker = false },
            onPick = { packageName ->
                viewModel.update { current ->
                    current.copy(lockedApps = current.lockedApps + LockRule(packageName, 0, 0))
                }
                showPicker = false
            }
        )
    }
}

@Composable
private fun StepPin(
    pin: String,
    pinRepeat: String,
    onPin: (String) -> Unit,
    onRepeat: (String) -> Unit,
    onBiometric: (Boolean) -> Unit,
    biometricAvailable: Boolean,
    biometricEnabled: Boolean
) {
    Column {
        Text(text = stringResource(R.string.onboarding_pin_title), style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        Text(text = stringResource(R.string.onboarding_pin_body), style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = pin,
            onValueChange = { value -> onPin(value.filter(Char::isDigit).take(12)) },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            label = { Text(text = stringResource(R.string.settings_pin_new)) },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = pinRepeat,
            onValueChange = { value -> onRepeat(value.filter(Char::isDigit).take(12)) },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            label = { Text(text = stringResource(R.string.settings_pin_repeat)) },
            modifier = Modifier.fillMaxWidth()
        )
        if (pin.isNotEmpty() && pinRepeat.isNotEmpty() && pin != pinRepeat) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.settings_pin_mismatch),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall
            )
        }
        if (biometricAvailable) {
            Spacer(Modifier.height(8.dp))
            SettingSwitch(
                title = stringResource(R.string.settings_biometric),
                subtitle = stringResource(R.string.settings_biometric_hint),
                checked = biometricEnabled,
                onCheckedChange = onBiometric
            )
        }
    }
}

@Composable
private fun StepContact(
    name: String,
    phone: String,
    onName: (String) -> Unit,
    onPhone: (String) -> Unit
) {
    Column {
        Text(text = stringResource(R.string.onboarding_contact_title), style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        Text(text = stringResource(R.string.onboarding_contact_body), style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = name,
            onValueChange = onName,
            singleLine = true,
            label = { Text(text = stringResource(R.string.settings_trusted_name)) },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = phone,
            onValueChange = { value -> onPhone(value.filter { it.isDigit() || it == '+' || it == ' ' }) },
            singleLine = true,
            label = { Text(text = stringResource(R.string.settings_trusted_phone)) },
            modifier = Modifier.fillMaxWidth()
        )
    }
}

private fun finish(
    viewModel: AppViewModel,
    reason: String,
    city: City?,
    pin: String,
    trustedName: String,
    trustedPhone: String
) {
    val salt = if (pin.length >= 6) PinHasher.salt() else null
    val hash = if (salt != null) PinHasher.hash(pin.toCharArray(), salt) else null
    viewModel.update { preferences ->
        preferences.copy(
            onboarded = true,
            reason = reason,
            cityId = city?.id ?: preferences.cityId,
            latitude = city?.latitude ?: preferences.latitude,
            longitude = city?.longitude ?: preferences.longitude,
            prayerMethod = city?.method ?: preferences.prayerMethod,
            trustedName = trustedName,
            trustedPhone = trustedPhone,
            pinSalt = salt?.let(PinGate::encode) ?: preferences.pinSalt,
            pinHash = hash?.let(PinGate::encode) ?: preferences.pinHash
        )
    }
    viewModel.refresh()
}

private const val STEPS = 8
