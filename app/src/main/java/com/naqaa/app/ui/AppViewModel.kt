package com.naqaa.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.naqaa.app.NaqaaApplication
import com.naqaa.app.R
import com.naqaa.app.content.Card
import com.naqaa.app.content.Content
import com.naqaa.app.data.EventKind
import com.naqaa.app.data.JournalEvent
import com.naqaa.app.data.Preferences
import com.naqaa.app.data.Progress
import com.naqaa.app.data.StreakSummary
import com.naqaa.app.guard.GuardSignals
import com.naqaa.app.notify.Reminders
import com.naqaa.app.prayer.DayPrayerTimes
import com.naqaa.app.prayer.PrayerAlarms
import com.naqaa.app.prayer.PrayerCalculator
import com.naqaa.app.prayer.PrayerName
import com.naqaa.app.report.Report
import com.naqaa.app.report.ReportAnalyzer
import com.naqaa.app.report.Risk
import com.naqaa.app.report.WeekStats
import com.naqaa.app.util.CrashLog
import com.naqaa.app.util.TimeX
import com.naqaa.app.vpn.VpnState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.random.Random

/** One snapshot of everything the interface draws, rebuilt after every change. */
data class UiState(
    val preferences: Preferences,
    val events: List<JournalEvent>,
    val streak: StreakSummary,
    val report: Report,
    val vpnRunning: Boolean,
    val nextPrayer: Pair<PrayerName, Instant>?,
    val todayTimes: DayPrayerTimes?,
    val planDay: Int?,
    val encouragement: Card?,
    val logCount: Int,
    val cleanDays: Set<LocalDate>
) {

    companion object {
        /** The state before anything could be read: enough to draw the shell and explain. */
        fun empty(today: LocalDate = LocalDate.now()): UiState {
            val week = WeekStats(
                from = today.minusDays(6),
                to = today,
                logged = 0,
                lapses = 0,
                resisted = 0,
                prayerDays = 0,
                adhkarDays = 0,
                quranDays = 0,
                planDays = 0,
                hourHistogram = IntArray(24),
                triggers = emptyList(),
                places = emptyList(),
                feelings = emptyList()
            )
            return UiState(
                preferences = Preferences(),
                events = emptyList(),
                streak = StreakSummary(0, 0, 0, 0, today),
                report = Report(
                    week = week,
                    previous = week,
                    streak = StreakSummary(0, 0, 0, 0, today),
                    points = 0,
                    risk = Risk.LOW,
                    score = 0,
                    recommendations = emptyList(),
                    badges = emptyList()
                ),
                vpnRunning = false,
                nextPrayer = null,
                todayTimes = null,
                planDay = null,
                encouragement = null,
                logCount = 0,
                cleanDays = emptySet()
            )
        }
    }
}

/**
 * Holds the state of the application in a single flow.
 *
 * Writes go through the graph, which owns the encrypted store, and every write is followed
 * by a state rebuild so the screens never read stale numbers. Analysis and asset parsing
 * run off the main thread, which keeps the interface responsive while the journal is read.
 */
class AppViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as NaqaaApplication
    private val graph = app.graph

    private val mutable = MutableStateFlow(carefulBuild(logCount = 0, encouragement = null))
    val state: StateFlow<UiState> = mutable.asStateFlow()

    private var reported = false

    private val stopGuard: () -> Unit = GuardSignals.addListener { reason ->
        if (reason == GuardSignals.Reason.GUARD_OFF) return@addListener
        viewModelScope.launch {
            withContext(Dispatchers.Default) {
                Content.load(app)
                mutable.value = mutable.value.copy(encouragement = Content.protectionCard(Random.Default))
            }
        }
    }

    private val stopVpn: () -> Unit = VpnState.addListener { refresh() }

    init {
        refresh()
    }

    override fun onCleared() {
        stopGuard()
        stopVpn()
        super.onCleared()
    }

    fun refresh() {
        viewModelScope.launch {
            // A read that fails keeps the previous state on screen rather than ending the
            // process: the journal is still on disk and the next refresh can succeed.
            runCatching {
                mutable.value = withContext(Dispatchers.Default) {
                    build(mutable.value.logCount, mutable.value.encouragement)
                }
            }.onFailure { record(it) }
        }
    }

    fun update(transform: (Preferences) -> Preferences) {
        val updated = graph.update(transform)
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.Default) {
                    PrayerAlarms.schedule(app, updated)
                    Reminders.schedule(app, updated)
                    mutable.value = build(mutable.value.logCount, mutable.value.encouragement)
                }
            }.onFailure { record(it) }
        }
    }

    /** One tap logging: the record is written now, details can be added from the journal. */
    fun quickLog(kind: EventKind) = log(kind)

    fun log(kind: EventKind, trigger: String = "", place: String = "", feeling: String = "", note: String = "") {
        graph.record(kind, trigger, place, feeling, note)
        viewModelScope.launch {
            runCatching {
                mutable.value = withContext(Dispatchers.Default) {
                    build(mutable.value.logCount + 1, mutable.value.encouragement)
                }
            }.onFailure { record(it) }
        }
    }

    private fun record(error: Throwable) {
        if (reported) return
        reported = true
        CrashLog.note(app, "state", error)
    }

    fun dismissEncouragement() {
        mutable.value = mutable.value.copy(encouragement = null)
    }

    fun eraseAll() {
        graph.wipe()
        refresh()
    }

    fun startPlanToday() {
        update { it.copy(planStart = LocalDate.now()) }
    }

    /**
     * A state that cannot be built must not end the launch: the shell is drawn with what is
     * known, and the failure is written down so that the next start offers its text instead
     * of dying in silence again. Only the first failure of a session is recorded, so a
     * repeated read does not bury the one that matters.
     */
    private fun carefulBuild(logCount: Int, encouragement: Card?): UiState =
        runCatching { build(logCount, encouragement) }
            .onFailure { error ->
                if (!reported) {
                    reported = true
                    CrashLog.note(app, "state", error)
                }
            }
            .getOrElse { UiState.empty() }

    private fun build(logCount: Int, encouragement: Card?): UiState {
        val preferences = graph.current()
        val events = graph.snapshot().events
        val now = Instant.now()
        val zone = ZoneId.systemDefault()
        val today = LocalDate.ofInstant(now, zone)
        val times = PrayerCalculator.calculate(
            date = today,
            latitude = preferences.latitude,
            longitude = preferences.longitude,
            method = preferences.prayerMethod,
            hanafiAsr = preferences.hanafiAsr,
            ramadan = TimeX.isRamadan(today)
        )
        val nextPrayer = PrayerCalculator.next(times, now) ?: PrayerCalculator.next(
            PrayerCalculator.calculate(
                date = today.plusDays(1),
                latitude = preferences.latitude,
                longitude = preferences.longitude,
                method = preferences.prayerMethod,
                hanafiAsr = preferences.hanafiAsr,
                ramadan = TimeX.isRamadan(today.plusDays(1))
            ),
            now
        )
        val planDay = preferences.planStart?.let { start ->
            (Progress.daysBetween(start, today) + 1).toInt().takeIf { it in 1..Content.plan().size }
        }
        return UiState(
            preferences = preferences,
            events = events,
            streak = Progress.streak(events, preferences.startedAt, now, zone),
            report = ReportAnalyzer.analyse(events, preferences, now, zone),
            vpnRunning = VpnState.running,
            nextPrayer = nextPrayer,
            todayTimes = times,
            planDay = planDay,
            encouragement = encouragement,
            logCount = logCount,
            cleanDays = Progress.cleanDaysBetween(events, today.minusDays(89), today, zone)
        )
    }

    companion object {
        val logLabels = listOf(
            EventKind.RESISTED to R.string.log_resisted,
            EventKind.LAPSE to R.string.log_lapse,
            EventKind.PRAYER to R.string.log_prayer,
            EventKind.ADHKAR to R.string.log_adhkar,
            EventKind.QURAN to R.string.log_quran,
            EventKind.PLAN to R.string.log_plan
        )
    }
}
