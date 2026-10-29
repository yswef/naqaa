package com.naqaa.app.ui.screens

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.naqaa.app.R
import com.naqaa.app.ui.theme.NaqaaOutlinedTextFieldColors
import com.naqaa.app.data.LockRule
import com.naqaa.app.data.PinHasher
import com.naqaa.app.prayer.CalculationMethod
import com.naqaa.app.prayer.Cities
import com.naqaa.app.prayer.City
import com.naqaa.app.ui.AppViewModel
import com.naqaa.app.ui.BiometricUnlock
import com.naqaa.app.ui.PinGate
import com.naqaa.app.ui.rememberVpnStartAction
import com.naqaa.app.util.LocaleX
import com.naqaa.app.util.OneTimeLocation
import com.naqaa.app.util.SystemGate

/** First-run setup for the app's main preferences. */
@Composable
fun OnboardingScreen(viewModel: AppViewModel) {
    val context = LocalContext.current
    val uiState by viewModel.state.collectAsState()
    val preferences = uiState.preferences
    // Saved across a rotation or a change of theme, which recreate the screen: without this
    // the setup went back to its first step and lost what had been typed.
    var step by rememberSaveable { mutableIntStateOf(0) }
    var reason by rememberSaveable { mutableStateOf(preferences.reason) }
    var pin by rememberSaveable { mutableStateOf("") }
    var pinRepeat by rememberSaveable { mutableStateOf("") }
    var trustedName by rememberSaveable { mutableStateOf(preferences.trustedName) }
    var trustedPhone by rememberSaveable { mutableStateOf(preferences.trustedPhone) }
    var pendingCityId by rememberSaveable { mutableStateOf(preferences.cityId) }
    val pendingCity: City? = Cities.byId(pendingCityId)
    val stepScrollState = remember(step) { ScrollState(0) }
    val pinProblem = pin.isNotEmpty() && (pin.length != PinGate.PIN_LENGTH || pin != pinRepeat)

    Column(
        modifier = Modifier.fillMaxSize().safeDrawingPadding().padding(20.dp)
    ) {
        LinearProgressIndicator(
            progress = { (step + 1).toFloat() / STEPS },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(18.dp))
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(stepScrollState)
        ) {
            when (step) {
                0 -> StepLanguage(viewModel)
                1 -> StepReason(reason, { reason = it })
                2 -> StepCity(
                    city = pendingCity,
                    language = preferences.language,
                    onCity = { pendingCityId = it?.id.orEmpty() }
                )
                3 -> StepPermissions(viewModel)
                4 -> StepApps(viewModel)
                5 -> StepPin(
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
            Button(enabled = !(step == PIN_STEP && pinProblem), onClick = {
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
    val uiState by viewModel.state.collectAsState()
    val language = uiState.preferences.language
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
            colors = NaqaaOutlinedTextFieldColors(),
            value = reason,
            onValueChange = { value -> if (value.length <= 400) onReason(value) },
            minLines = 4,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

private enum class LocationPickerStatus { IDLE, LOADING, FOUND, UNAVAILABLE }

@Composable
private fun StepCity(city: City?, language: String, onCity: (City?) -> Unit) {
    val context = LocalContext.current
    var query by remember { mutableStateOf("") }
    var status by remember { mutableStateOf(LocationPickerStatus.IDLE) }
    var manualRequested by rememberSaveable { mutableStateOf(false) }
    val results = remember(query) { Cities.search(query) }
    // The list is the fallback: it stays out of the way while location can do the job, and
    // disappears once a city was found from the location.
    val showManual = status != LocationPickerStatus.FOUND &&
        (manualRequested || status == LocationPickerStatus.UNAVAILABLE)
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) {
            status = LocationPickerStatus.UNAVAILABLE
        } else {
            status = LocationPickerStatus.LOADING
            OneTimeLocation.request(context) { location ->
                if (location == null) {
                    status = LocationPickerStatus.UNAVAILABLE
                } else {
                    val nearest = Cities.nearest(location.latitude, location.longitude)
                    onCity(nearest)
                    status = LocationPickerStatus.FOUND
                }
            }
        }
    }

    Column {
        Text(text = stringResource(R.string.onboarding_city_title), style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        Text(text = stringResource(R.string.onboarding_city_body), style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = { permission.launch(Manifest.permission.ACCESS_COARSE_LOCATION) },
            enabled = status != LocationPickerStatus.LOADING,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (status == LocationPickerStatus.LOADING) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                    strokeWidth = 2.dp
                )
                Spacer(Modifier.width(8.dp))
            }
            Text(
                text = stringResource(
                    if (status == LocationPickerStatus.LOADING) R.string.onboarding_location_loading
                    else R.string.onboarding_location_action
                )
            )
        }
        when (status) {
            LocationPickerStatus.LOADING -> Unit
            LocationPickerStatus.FOUND -> Text(
                text = stringResource(R.string.onboarding_location_found),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 6.dp)
            )
            LocationPickerStatus.UNAVAILABLE -> Text(
                text = stringResource(R.string.onboarding_location_unavailable),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 6.dp)
            )
            LocationPickerStatus.IDLE -> if (ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                Text(
                    text = stringResource(R.string.onboarding_location_again),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
        }
        if (status != LocationPickerStatus.FOUND) {
            TextButton(
                onClick = { manualRequested = !manualRequested },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = stringResource(R.string.onboarding_manual))
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = city?.let { stringResource(R.string.onboarding_city_selected, it.displayLabel(language)) }
                ?: stringResource(R.string.onboarding_city_none),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (!showManual) return@Column
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            colors = NaqaaOutlinedTextFieldColors(),
            value = query,
            onValueChange = { query = it },
            singleLine = true,
            label = { Text(text = stringResource(R.string.search)) },
            placeholder = { Text(text = stringResource(R.string.city_search_hint)) },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(6.dp))
        if (results.isEmpty()) {
            Text(
                text = stringResource(R.string.city_no_results),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 12.dp)
            )
        } else {
            LazyColumn(modifier = Modifier.fillMaxWidth().height(220.dp)) {
                items(results, key = { it.id }) { option ->
                    TextButton(
                        onClick = {
                            onCity(option)
                            status = LocationPickerStatus.IDLE
                        },
                        enabled = status != LocationPickerStatus.LOADING,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(text = option.displayLabel(language), maxLines = 2)
                    }
                }
            }
        }
    }
}

@Composable
private fun StepPermissions(viewModel: AppViewModel) {
    val context = LocalContext.current
    val resumeToken = rememberResumeToken()
    val notificationsGranted = remember(resumeToken) { SystemGate.notificationsEnabled(context) }
    val adminEnabled = remember(resumeToken) { SystemGate.isDeviceAdmin(context) }
    val alarmsEnabled = remember(resumeToken) { SystemGate.canScheduleExactAlarms(context) }
    var notificationDenied by remember { mutableStateOf(false) }
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        notificationDenied = !granted || !SystemGate.notificationsEnabled(context)
    }
    val notificationPermissionGranted = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    val showNotificationPrompt = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        !notificationDenied && !notificationPermissionGranted
    val startVpn = rememberVpnStartAction()
    val uiState by viewModel.state.collectAsState()
    val vpnRunning = uiState.vpnRunning

    Column {
        Text(text = stringResource(R.string.onboarding_permissions_title), style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(12.dp))
        PermissionRow(
            title = stringResource(R.string.protection_filter),
            granted = vpnRunning,
            grantedText = stringResource(R.string.permission_enabled),
            missingText = stringResource(R.string.permission_needed),
            onEnable = startVpn
        )
        AccessibilityPermissionRow()
        PermissionRow(
            title = stringResource(R.string.permission_admin),
            granted = adminEnabled,
            grantedText = stringResource(R.string.permission_enabled),
            missingText = stringResource(R.string.permission_needed),
            onEnable = { SystemGate.openSettings(context, SystemGate.deviceAdminRequest(context)) }
        )
        PermissionRow(
            title = stringResource(R.string.permission_notifications),
            granted = notificationsGranted,
            grantedText = stringResource(R.string.permission_enabled),
            missingText = stringResource(R.string.permission_needed),
            actionText = stringResource(
                if (showNotificationPrompt) R.string.permission_enable else R.string.permission_settings
            ),
            onEnable = {
                if (showNotificationPrompt) {
                    notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    SystemGate.openSettings(context, SystemGate.notificationSettings(context))
                }
            }
        )
        PermissionRow(
            title = stringResource(R.string.permission_alarms),
            granted = alarmsEnabled,
            grantedText = stringResource(R.string.permission_enabled),
            missingText = stringResource(R.string.permission_needed),
            onEnable = { SystemGate.openSettings(context, SystemGate.exactAlarmSettings(context)) }
        )
        BatteryPermissionRow()
    }
}

@Composable
private fun StepApps(viewModel: AppViewModel) {
    val uiState by viewModel.state.collectAsState()
    val preferences = uiState.preferences
    var showPicker by remember { mutableStateOf(false) }
    Column {
        Text(text = stringResource(R.string.onboarding_apps_title), style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        Text(text = stringResource(R.string.onboarding_apps_body), style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(12.dp))
        preferences.lockedApps.forEach { rule ->
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Text(text = appLabel(rule.packageName), modifier = Modifier.weight(1f).align(Alignment.CenterVertically), style = MaterialTheme.typography.bodyMedium)
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
            onPick = { packages, preset ->
                viewModel.update { current ->
                    current.copy(
                        lockedApps = current.lockedApps + packages.map { LockRule(it, preset.startMinute, preset.endMinute) }
                    )
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
            colors = NaqaaOutlinedTextFieldColors(),
            value = pin,
            onValueChange = { value -> onPin(value.filter(Char::isDigit).take(PinGate.PIN_LENGTH)) },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            label = { Text(text = stringResource(R.string.settings_pin_new)) },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            colors = NaqaaOutlinedTextFieldColors(),
            value = pinRepeat,
            onValueChange = { value -> onRepeat(value.filter(Char::isDigit).take(PinGate.PIN_LENGTH)) },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            label = { Text(text = stringResource(R.string.settings_pin_repeat)) },
            modifier = Modifier.fillMaxWidth()
        )
        if (pin.isNotEmpty() && pin.length != PinGate.PIN_LENGTH) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.settings_pin_rule),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall
            )
        } else if (pin.isNotEmpty() && pin != pinRepeat) {
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
            colors = NaqaaOutlinedTextFieldColors(),
            value = name,
            onValueChange = onName,
            singleLine = true,
            label = { Text(text = stringResource(R.string.settings_trusted_name)) },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            colors = NaqaaOutlinedTextFieldColors(),
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
    val salt = if (pin.length == PinGate.PIN_LENGTH) PinHasher.salt() else null
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

private const val STEPS = 7
private const val PIN_STEP = 5
