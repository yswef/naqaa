package com.naqaa.app.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
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
import com.naqaa.app.ui.theme.NaqaaOutlinedTextFieldColors
import com.naqaa.app.content.Content
import com.naqaa.app.data.EventKind
import com.naqaa.app.prayer.CalculationMethod
import com.naqaa.app.prayer.Cities
import com.naqaa.app.prayer.City
import com.naqaa.app.prayer.PrayerCalculator
import com.naqaa.app.ui.AppViewModel
import com.naqaa.app.ui.UiState
import com.naqaa.app.util.LocaleX
import com.naqaa.app.util.OneTimeLocation
import com.naqaa.app.util.TimeX
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Prayer times, city selection, and daily remembrance. */
@Composable
fun PrayerScreen(viewModel: AppViewModel, state: UiState, onOpenAdhkar: () -> Unit) {
    val context = LocalContext.current
    val language = state.preferences.language
    val display = LocaleX.displayLocale(language)
    val zone = ZoneId.systemDefault()
    var showCities by remember { mutableStateOf(false) }
    var locationLoading by remember { mutableStateOf(false) }
    var locationFailed by remember { mutableStateOf(false) }
    var editingTimes by remember { mutableStateOf(false) }

    val locationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) {
            locationLoading = false
            locationFailed = true
        } else {
            locationLoading = true
            locationFailed = false
            OneTimeLocation.request(context) { location ->
                locationLoading = false
                if (location == null) {
                    locationFailed = true
                } else {
                    val city = Cities.nearest(location.latitude, location.longitude)
                    viewModel.update {
                        it.copy(cityId = city.id, latitude = location.latitude, longitude = location.longitude, prayerMethod = city.method)
                    }
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
                    if (editingTimes) {
                        val offset = state.preferences.prayerOffsets[name.name] ?: 0
                        OffsetRow(
                            label = prayerLabel(name),
                            time = TimeX.clock(at, zone, display),
                            offset = offset,
                            onChange = { minutes ->
                                viewModel.update {
                                    it.copy(
                                        prayerOffsets = if (minutes == 0) it.prayerOffsets - name.name
                                        else it.prayerOffsets + (name.name to minutes.coerceIn(-MAX_OFFSET, MAX_OFFSET))
                                    )
                                }
                            }
                        )
                    } else {
                        StatLine(
                            label = prayerLabel(name) + if (next?.first == name) "  •" else "",
                            value = TimeX.clock(at, zone, display)
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
                Row {
                    OutlinedButton(onClick = { editingTimes = !editingTimes }, modifier = Modifier.weight(1f)) {
                        Text(text = stringResource(if (editingTimes) R.string.prayer_edit_done else R.string.prayer_edit))
                    }
                    if (editingTimes && state.preferences.prayerOffsets.isNotEmpty()) {
                        Spacer(Modifier.width(8.dp))
                        TextButton(onClick = { viewModel.update { it.copy(prayerOffsets = emptyMap()) } }) {
                            Text(text = stringResource(R.string.prayer_edit_reset))
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            val city = Cities.byId(state.preferences.cityId)
            Text(
                text = city?.displayLabel(language) ?: stringResource(R.string.prayer_city_custom),
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(Modifier.height(10.dp))
            Button(
                onClick = { locationPermission.launch(Manifest.permission.ACCESS_COARSE_LOCATION) },
                enabled = !locationLoading,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (locationLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Text(
                    text = stringResource(
                        if (locationLoading) R.string.prayer_location_loading else R.string.prayer_use_location
                    )
                )
            }
            Spacer(Modifier.height(6.dp))
            OutlinedButton(
                onClick = { showCities = true },
                enabled = !locationLoading,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = stringResource(R.string.prayer_choose_city))
            }
            if (locationFailed) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = stringResource(R.string.prayer_location_failed),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            } else if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) !=
                PackageManager.PERMISSION_GRANTED
            ) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = stringResource(R.string.prayer_location_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        PrayerLogCard(viewModel, state)

        SectionCard(title = stringResource(R.string.prayer_method)) {
            ChipFlow {
                CalculationMethod.entries.forEach { method ->
                    FilterChip(
                        selected = state.preferences.prayerMethod == method,
                        onClick = { viewModel.update { it.copy(prayerMethod = method) } },
                        label = { Text(text = methodLabel(method)) },
                        modifier = Modifier.padding(top = 3.dp, bottom = 3.dp)
                    )
                }
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
            Button(onClick = onOpenAdhkar, modifier = Modifier.fillMaxWidth()) {
                Text(text = stringResource(R.string.prayer_adhkar_start))
            }
        }
        Spacer(Modifier.height(20.dp))
    }

    if (showCities) {
        CityPicker(
            language = language,
            onDismiss = { showCities = false },
            onPick = { city ->
                locationFailed = false
                viewModel.update {
                    it.copy(cityId = city.id, latitude = city.latitude, longitude = city.longitude, prayerMethod = city.method)
                }
                showCities = false
            }
        )
    }
}

@Composable
private fun CityPicker(language: String, onDismiss: () -> Unit, onPick: (City) -> Unit) {
    var query by remember { mutableStateOf("") }
    val results = remember(query) { Cities.search(query) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.prayer_choose_city)) },
        text = {
            Column {
                OutlinedTextField(
                    colors = NaqaaOutlinedTextFieldColors(),
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    label = { Text(text = stringResource(R.string.search)) },
                    placeholder = { Text(text = stringResource(R.string.city_search_hint)) },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                if (results.isEmpty()) {
                    Text(
                        text = stringResource(R.string.city_no_results),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 320.dp)) {
                        items(results, key = { it.id }) { city ->
                            TextButton(onClick = { onPick(city) }, modifier = Modifier.fillMaxWidth()) {
                                Text(text = city.displayLabel(language), maxLines = 2)
                            }
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

private const val MAX_OFFSET = 60

/** One prayer in edit mode: the resulting time and buttons that move it a minute or five. */
@Composable
private fun OffsetRow(label: String, time: String, offset: Int, onChange: (Int) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = label, style = MaterialTheme.typography.bodyMedium)
            Text(
                text = time + if (offset != 0) "  (" + (if (offset > 0) "+$offset" else "$offset") + ")" else "",
                style = MaterialTheme.typography.titleMedium
            )
        }
        OutlinedButton(onClick = { onChange(offset - 5) }) { Text(text = "-5") }
        Spacer(Modifier.width(4.dp))
        OutlinedButton(onClick = { onChange(offset - 1) }) { Text(text = "-1") }
        Spacer(Modifier.width(4.dp))
        OutlinedButton(onClick = { onChange(offset + 1) }) { Text(text = "+1") }
        Spacer(Modifier.width(4.dp))
        OutlinedButton(onClick = { onChange(offset + 5) }) { Text(text = "+5") }
    }
}

/**
 * Which prayers were prayed today, and whether each was inside its time. A prayer can be
 * marked once its time has come; marking it again changes the status. The window of a
 * prayer ends where the next entry of the day starts (sunrise ends Fajr, for instance), and
 * the button matching the current window is the one drawn filled.
 */
@Composable
private fun PrayerLogCard(viewModel: AppViewModel, state: UiState) {
    val times = state.todayTimes ?: return
    val zone = ZoneId.systemDefault()
    val today = LocalDate.now(zone)
    val now = java.time.Instant.now()
    val logged = remember(state.events) {
        state.events
            .filter { it.kind == EventKind.PRAYER && it.trigger.isNotEmpty() && LocalDate.ofInstant(it.at, zone) == today }
            .associateBy { it.trigger }
    }
    val all = times.ordered()
    val onTimeCount = logged.values.count { it.note == AppViewModel.PRAYER_ONTIME }
    val lateCount = logged.values.count { it.note == AppViewModel.PRAYER_LATE }

    SectionCard(title = stringResource(R.string.prayer_log_title)) {
        Text(
            text = stringResource(R.string.prayer_log_summary, onTimeCount, lateCount),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(6.dp))
        all.forEachIndexed { index, (name, at) ->
            if (!name.isPrayer) return@forEachIndexed
            val entry = logged[name.name.lowercase()]
            val reached = !at.isAfter(now) || com.naqaa.app.data.Session.developer
            val end = all.getOrNull(index + 1)?.second
            val inWindow = end == null || now.isBefore(end)
            Column(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                Row(modifier = Modifier.fillMaxWidth()) {
                    Text(text = prayerLabel(name), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    Text(
                        text = stringResource(
                            when {
                                entry?.note == AppViewModel.PRAYER_ONTIME -> R.string.prayer_status_ontime
                                entry?.note == AppViewModel.PRAYER_LATE -> R.string.prayer_status_late
                                !reached -> R.string.prayer_not_yet
                                else -> R.string.prayer_not_logged
                            }
                        ),
                        style = MaterialTheme.typography.labelLarge,
                        color = when {
                            entry?.note == AppViewModel.PRAYER_ONTIME -> com.naqaa.app.ui.theme.Neon.Lime
                            entry?.note == AppViewModel.PRAYER_LATE -> com.naqaa.app.ui.theme.Neon.Amber
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
                Spacer(Modifier.height(4.dp))
                Row(modifier = Modifier.fillMaxWidth()) {
                    val onTimeSelected = entry?.note == AppViewModel.PRAYER_ONTIME
                    val lateSelected = entry?.note == AppViewModel.PRAYER_LATE
                    val onTimeFilled = onTimeSelected || (entry == null && inWindow)
                    val lateFilled = lateSelected || (entry == null && !inWindow)
                    if (onTimeFilled) {
                        Button(onClick = { viewModel.logPrayer(name, true) }, enabled = reached, modifier = Modifier.weight(1f)) {
                            Text(text = stringResource(R.string.prayer_on_time))
                        }
                    } else {
                        OutlinedButton(onClick = { viewModel.logPrayer(name, true) }, enabled = reached, modifier = Modifier.weight(1f)) {
                            Text(text = stringResource(R.string.prayer_on_time))
                        }
                    }
                    Spacer(Modifier.width(8.dp))
                    if (lateFilled) {
                        Button(onClick = { viewModel.logPrayer(name, false) }, enabled = reached, modifier = Modifier.weight(1f)) {
                            Text(text = stringResource(R.string.prayer_late))
                        }
                    } else {
                        OutlinedButton(onClick = { viewModel.logPrayer(name, false) }, enabled = reached, modifier = Modifier.weight(1f)) {
                            Text(text = stringResource(R.string.prayer_late))
                        }
                    }
                }
            }
        }
    }
}
