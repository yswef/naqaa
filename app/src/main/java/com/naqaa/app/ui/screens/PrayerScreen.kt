package com.naqaa.app.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.location.LocationManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.naqaa.app.R
import com.naqaa.app.content.Content
import com.naqaa.app.data.EventKind
import com.naqaa.app.prayer.CalculationMethod
import com.naqaa.app.prayer.Cities
import com.naqaa.app.prayer.City
import com.naqaa.app.prayer.PrayerCalculator
import com.naqaa.app.ui.AppViewModel
import com.naqaa.app.ui.UiState
import com.naqaa.app.util.LocaleX
import com.naqaa.app.util.TimeX
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Prayer times, the city, and the daily remembrance.
 *
 * Times are computed on the device; the city picker exists so no location permission is
 * needed unless the user asks for one fix to pre-select the nearest city. The daily wird
 * is a single verse, and reading it is logged like any other good habit.
 */
@Composable
fun PrayerScreen(viewModel: AppViewModel, state: UiState) {
    val context = LocalContext.current
    val language = state.preferences.language
    val display = LocaleX.displayLocale(language)
    val zone = ZoneId.systemDefault()
    var showCities by remember { mutableStateOf(false) }
    var period by remember { mutableStateOf(Content.PERIOD_MORNING) }
    var expanded by remember { mutableStateOf<String?>(null) }

    val locationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            val manager = context.getSystemService(LocationManager::class.java)
            val last = runCatching {
                manager.getProviders(true).firstNotNullOfOrNull { provider -> manager.getLastKnownLocation(provider) }
            }.getOrNull()
            if (last != null) {
                val city = Cities.nearest(last.latitude, last.longitude)
                viewModel.update {
                    it.copy(cityId = city.id, latitude = last.latitude, longitude = last.longitude, prayerMethod = city.method)
                }
            }
        }
    }

    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        ScreenTitle(title = stringResource(R.string.prayer_title), subtitle = stringResource(R.string.prayer_subtitle))

        SectionCard(title = stringResource(R.string.prayer_times)) {
            val times = state.todayTimes
            if (times == null) {
                Text(text = stringResource(R.string.prayer_none_today), style = MaterialTheme.typography.bodyMedium)
            } else {
                val next = state.nextPrayer
                times.ordered().forEach { (name, at) ->
                    StatLine(
                        label = prayerLabel(name) + if (next?.first == name) "  •" else "",
                        value = TimeX.clock(at, zone, display)
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            val city = Cities.byId(state.preferences.cityId)
            Text(
                text = city?.let {
                    if (language == "en") "${it.nameEn}, ${it.countryEn}" else "${it.nameAr}، ${it.countryAr}"
                } ?: stringResource(R.string.prayer_city_custom),
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(Modifier.height(8.dp))
            Row {
                OutlinedButton(onClick = { showCities = true }, modifier = Modifier.weight(1f)) {
                    Text(text = stringResource(R.string.prayer_choose_city))
                }
                Spacer(Modifier.height(4.dp))
                OutlinedButton(
                    onClick = { locationPermission.launch(Manifest.permission.ACCESS_COARSE_LOCATION) },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(text = stringResource(R.string.prayer_use_location))
                }
            }
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) !=
                PackageManager.PERMISSION_GRANTED
            ) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.prayer_location_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        SectionCard(title = stringResource(R.string.prayer_method)) {
            CalculationMethod.entries.forEach { method ->
                FilterChip(
                    selected = state.preferences.prayerMethod == method,
                    onClick = { viewModel.update { it.copy(prayerMethod = method) } },
                    label = { Text(text = methodLabel(method)) },
                    modifier = Modifier.padding(top = 3.dp, end = 6.dp, bottom = 3.dp)
                )
            }
            SettingSwitch(
                title = stringResource(R.string.prayer_hanafi),
                subtitle = stringResource(R.string.prayer_hanafi_hint),
                checked = state.preferences.hanafiAsr,
                onCheckedChange = { value -> viewModel.update { it.copy(hanafiAsr = value) } }
            )
            SettingSwitch(
                title = stringResource(R.string.prayer_reminders),
                checked = state.preferences.prayerReminders,
                onCheckedChange = { value -> viewModel.update { it.copy(prayerReminders = value) } }
            )
            SettingSwitch(
                title = stringResource(R.string.prayer_adhkar_reminders),
                checked = state.preferences.adhkarReminders,
                onCheckedChange = { value -> viewModel.update { it.copy(adhkarReminders = value) } }
            )
        }

        SectionCard(title = stringResource(R.string.prayer_wird)) {
            val wird = remember { Content.wirdOfDay(LocalDate.now()) }
            if (wird == null) {
                Text(text = stringResource(R.string.content_unavailable), style = MaterialTheme.typography.bodyMedium)
            } else {
                Text(text = wird.textAr, style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(6.dp))
                Text(text = if (language == "en") wird.referenceEn else wird.referenceAr, style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.height(10.dp))
                Button(onClick = { viewModel.log(EventKind.QURAN) }, modifier = Modifier.fillMaxWidth()) {
                    Text(text = stringResource(R.string.prayer_wird_read))
                }
            }
        }

        SectionCard(title = stringResource(R.string.prayer_adhkar)) {
            Row {
                FilterChip(
                    selected = period == Content.PERIOD_MORNING,
                    onClick = { period = Content.PERIOD_MORNING },
                    label = { Text(text = stringResource(R.string.prayer_morning)) },
                    modifier = Modifier.padding(end = 6.dp)
                )
                FilterChip(
                    selected = period == Content.PERIOD_EVENING,
                    onClick = { period = Content.PERIOD_EVENING },
                    label = { Text(text = stringResource(R.string.prayer_evening)) }
                )
            }
            Spacer(Modifier.height(8.dp))
            val adhkar = remember(period) { Content.adhkar(period) }
            adhkar.forEach { dhikr ->
                Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Row(modifier = Modifier.fillMaxWidth()) {
                        TextButton(onClick = { expanded = if (expanded == dhikr.id) null else dhikr.id }) {
                            Text(
                                text = stringResource(
                                    if (expanded == dhikr.id) R.string.collapse else R.string.expand
                                )
                            )
                        }
                        Text(
                            text = stringResource(R.string.prayer_repeat, dhikr.repeat),
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 14.dp)
                        )
                    }
                    if (expanded == dhikr.id) {
                        Text(text = dhikr.textAr, style = MaterialTheme.typography.bodyLarge)
                        Spacer(Modifier.height(4.dp))
                        Text(text = dhikr.sourceAr, style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = { viewModel.log(EventKind.ADHKAR) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = stringResource(R.string.prayer_adhkar_done))
            }
        }
        Spacer(Modifier.height(20.dp))
    }

    if (showCities) {
        CityPicker(
            onDismiss = { showCities = false },
            onPick = { city ->
                viewModel.update {
                    it.copy(cityId = city.id, latitude = city.latitude, longitude = city.longitude, prayerMethod = city.method)
                }
                showCities = false
            }
        )
    }
}

@Composable
private fun CityPicker(onDismiss: () -> Unit, onPick: (City) -> Unit) {
    val language = LocaleX.language(androidx.compose.ui.platform.LocalContext.current)
    var query by remember { mutableStateOf("") }
    val results = remember(query) {
        val needle = query.trim()
        if (needle.isEmpty()) {
            Cities.all
        } else {
            Cities.all.filter { it.nameAr.contains(needle) || it.nameEn.contains(needle, ignoreCase = true) }
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.prayer_choose_city)) },
        text = {
            Column {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    label = { Text(text = stringResource(R.string.search)) },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                LazyColumn(modifier = Modifier.fillMaxWidth().height(360.dp)) {
                    items(results, key = { it.id }) { city ->
                        TextButton(onClick = { onPick(city) }, modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = if (language == "en") "${city.nameEn}, ${city.countryEn}" else "${city.nameAr}، ${city.countryAr}"
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(text = stringResource(R.string.cancel)) }
        }
    )
}

@Composable
private fun methodLabel(method: CalculationMethod): String = stringResource(
    when (method) {
        CalculationMethod.UMM_AL_QURA -> R.string.method_umm_al_qura
        CalculationMethod.MUSLIM_WORLD_LEAGUE -> R.string.method_mwl
        CalculationMethod.EGYPTIAN -> R.string.method_egyptian
        CalculationMethod.KARACHI -> R.string.method_karachi
        CalculationMethod.ISNA -> R.string.method_isna
        CalculationMethod.DUBAI -> R.string.method_dubai
    }
)
