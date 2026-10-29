package com.naqaa.app.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.res.Configuration
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.widget.Toast
import com.naqaa.app.NaqaaApplication
import com.naqaa.app.R
import com.naqaa.app.data.Preferences
import com.naqaa.app.guard.GuardGrace
import com.naqaa.app.guard.UsagePass
import com.naqaa.app.guard.VpnApps
import com.naqaa.app.vpn.VpnState
import com.naqaa.app.guard.GuardSignals
import com.naqaa.app.guard.LockWindow
import com.naqaa.app.guard.NightMode
import com.naqaa.app.prayer.PrayerCalculator
import com.naqaa.app.ui.Overlays
import com.naqaa.app.util.CrashLog
import com.naqaa.app.util.TimeX
import java.time.Instant
import java.time.ZoneId

/**
 * Watches only what it must, and only while a rule can act.
 *
 * Three jobs: close the short video surfaces of the configured applications, keep the
 * address field of the configured browsers away from a list of words, and put the waiting
 * screen in front of a settings page that lowers protection. Applications outside the rules
 * never reach this code because the service subscribes to their packages only.
 *
 * The back action is used for short video, which leaves the user inside the application
 * they opened instead of sending them home. Address text is compared and discarded; it is
 * never stored, and the journal records only that a page was filtered.
 */
class GuardAccessibilityService : AccessibilityService() {

    private var rules: Rules = Rules.EMPTY
    private var rulesLoaded = false
    private var watched: Set<String> = emptySet()
    private var lastActionAt = 0L
    private var lastActionPackage: String? = null

    private var unsubscribe: (() -> Unit)? = null
    private var unsubscribeCleanup: (() -> Unit)? = null
    private var unsubscribeVpn: (() -> Unit)? = null
    private val cleanup by lazy { CleanupEngine(this) }
    private var lastWatchRefresh = 0L
    private var lastVpnResume = 0L

    override fun onServiceConnected() {
        super.onServiceConnected()
        runCatching {
            loadRules()
            val store = (application as NaqaaApplication).graph.preferences
            refreshWatched(store.load())
            // A lock added later must start working at once: events of an application that
            // is not on the list are never delivered, so the list follows the settings.
            unsubscribe = store.addListener { updated ->
                Handler(Looper.getMainLooper()).post { runCatching { refreshWatched(updated) } }
            }
            // The account cleanup is controlled from a screen of the application; the engine
            // lives here because only this service can read and press another application.
            unsubscribeCleanup = CleanupState.addControlListener {
                Handler(Looper.getMainLooper()).post { runCatching { cleanup.sync() } }
            }
            // Another VPN replacing the local filter: bring the consent screen back.
            unsubscribeVpn = VpnState.addListener {
                Handler(Looper.getMainLooper()).post { runCatching { onVpnChanged() } }
            }
        }.onFailure { CrashLog.note(this, "accessibility connect", it) }
    }

    override fun onDestroy() {
        unsubscribe?.invoke()
        unsubscribe = null
        unsubscribeCleanup?.invoke()
        unsubscribeCleanup = null
        unsubscribeVpn?.invoke()
        unsubscribeVpn = null
        runCatching { cleanup.shutdown() }
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val value = event ?: return
        // An uncaught failure in this callback makes the system restart the service a few
        // times and then switch it off, so the failure is recorded and the event is dropped.
        runCatching { handle(value) }.onFailure { CrashLog.note(this, "accessibility event", it) }
    }

    private fun handle(event: AccessibilityEvent) {
        if (event.eventType !in WATCHED_EVENTS) return
        val packageName = event.packageName?.toString() ?: return
        if (packageName == this.packageName) return
        loadRules()
        val preferences = preferences()
        if (!preferences.onboarded) return
        val nowMillis = System.currentTimeMillis()
        // Newly installed VPN clients are picked up without waiting for a settings change.
        if (watched.isEmpty() || nowMillis - lastWatchRefresh > WATCH_REFRESH_MILLIS) {
            lastWatchRefresh = nowMillis
            refreshWatched(preferences)
        }
        if (!canAct(preferences, packageName)) return

        // Another VPN service would end the local filter, so its application is covered by
        // the same lock screen as a scheduled lock, with its own message.
        val passActive = UsagePass.appAllowed(packageName)
        if (!passActive && isOtherVpn(preferences, packageName)) {
            openLock(R.string.block_vpn_message, packageName)
            return
        }

        val minute = TimeX.minuteOfDay(Instant.now(), ZoneId.systemDefault())
        if (!passActive && lockedNow(preferences, packageName, minute)) {
            openLock(R.string.lock_message_schedule, packageName)
            return
        }
        if (!passActive && nightNow(preferences, packageName)) {
            openLock(R.string.lock_message_night, packageName)
            return
        }
        if (GuardGrace.isActive()) return

        val addressIds = rules.addressIds(packageName)
        val facts = NodeScan.facts(rootInActiveWindow, packageName, addressIds)

        if (preferences.blockShorts) {
            val rule = rules.appRule(packageName)
            // While the user's own time pass is running the short video rules stand aside.
            if (rule != null && !UsagePass.shortsAllowed()) {
                if (rule.wholeApp) {
                    if (preferences.blockTikTok) closeEverything(R.string.block_short_message, packageName, GuardSignals.Reason.REELS)
                } else if (ScreenRules.showsShortVideo(rule, facts)) {
                    closeSurface(R.string.block_short_message, packageName, GuardSignals.Reason.SHORTS)
                }
            }
        }

        if (preferences.blockBrowserKeywords && rules.browserRule(packageName) != null &&
            ScreenRules.blockedAddress(rules, facts)
        ) {
            closeSurface(R.string.block_browser_message, packageName, GuardSignals.Reason.BROWSER_KEYWORD)
        }

        ScreenRules.riskyAction(rules, facts)?.let { action ->
            if (throttlePassed(packageName)) {
                startActivity(Overlays.delay(this, action))
            }
        }
    }

    override fun onInterrupt() = Unit

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        loadRules()
    }

    /** Subscribes the service to the packages a rule or a lock can act on, and to nothing else. */
    private fun refreshWatched(preferences: Preferences) {
        loadRules()
        val wanted = buildSet {
            rules.shortVideo.forEach { add(it.packageName) }
            rules.browsers.forEach { add(it.packageName) }
            addAll(rules.riskyPackages)
            preferences.lockedApps.forEach { add(it.packageName) }
            if (preferences.blockOtherVpn) addAll(VpnApps.all(this@GuardAccessibilityService))
            add(cleanup.packageName)
        }
        if (wanted.isEmpty() || wanted == watched) return
        watched = wanted
        runCatching {
            serviceInfo = (serviceInfo ?: AccessibilityServiceInfo()).apply {
                packageNames = wanted.toTypedArray()
                notificationTimeout = NOTIFICATION_TIMEOUT
            }
        }
    }

    private fun canAct(preferences: Preferences, packageName: String): Boolean =
        rules.watches(packageName) || preferences.lockedApps.any { it.packageName == packageName } ||
            isOtherVpn(preferences, packageName)

    private fun isOtherVpn(preferences: Preferences, packageName: String): Boolean =
        preferences.blockOtherVpn && packageName in VpnApps.all(this)

    /** After another VPN replaced the filter, ask for consent again (at most once a minute). */
    private fun onVpnChanged() {
        if (!VpnState.replacedByOther || !preferences().blockOtherVpn) return
        val now = System.currentTimeMillis()
        if (now - lastVpnResume < VPN_RESUME_MILLIS) return
        lastVpnResume = now
        startActivity(Overlays.vpnResume(this))
    }

    /** True while the current minute falls inside a window the user scheduled for the app. */
    private fun lockedNow(preferences: Preferences, packageName: String, minute: Int): Boolean =
        LockWindow.coversAny(preferences.lockedApps, packageName, minute)

    private fun nightNow(preferences: Preferences, packageName: String): Boolean {
        if (!preferences.nightMode) return false
        if (packageName in preferences.nightAllowlist) return false
        if (!rules.watches(packageName) || rules.isRiskyPackage(packageName)) return false
        val zone = ZoneId.systemDefault()
        val now = Instant.now()
        return NightMode.activeAt(now, ::times, zone)
    }

    private fun times(date: java.time.LocalDate) = PrayerCalculator.calculate(
        date = date,
        latitude = preferences().latitude,
        longitude = preferences().longitude,
        method = preferences().prayerMethod,
        hanafiAsr = preferences().hanafiAsr,
        ramadan = TimeX.isRamadan(date),
        offsets = preferences().prayerOffsetMap
    )

    private fun closeSurface(messageId: Int, packageName: String, reason: GuardSignals.Reason) {
        if (!throttlePassed(packageName)) return
        if (UsagePass.consumeShortsExpired()) {
            endedScreen(packageName)
            return
        }
        performGlobalAction(GLOBAL_ACTION_BACK)
        message(messageId)
        GuardSignals.raise(reason)
    }

    private fun closeEverything(messageId: Int, packageName: String, reason: GuardSignals.Reason) {
        if (!throttlePassed(packageName)) return
        if (UsagePass.consumeShortsExpired()) {
            endedScreen(packageName)
            return
        }
        performGlobalAction(GLOBAL_ACTION_HOME)
        message(messageId)
        GuardSignals.raise(reason)
    }

    private fun openLock(messageId: Int, packageName: String) {
        if (!throttlePassed(packageName)) return
        // A pass that just ran out is announced as such instead of as a plain lock.
        if (UsagePass.consumeAppExpired(packageName)) {
            endedScreen(packageName)
            return
        }
        performGlobalAction(GLOBAL_ACTION_HOME)
        startActivity(Overlays.lock(this, messageId, packageName))
        GuardSignals.raise(GuardSignals.Reason.LOCKED_APP)
    }

    /** The screen that says the time the user allowed himself is over. */
    private fun endedScreen(packageName: String) {
        performGlobalAction(GLOBAL_ACTION_HOME)
        startActivity(Overlays.lock(this, R.string.usage_ended_message, packageName, ended = true))
        GuardSignals.raise(GuardSignals.Reason.LOCKED_APP)
    }

    private fun message(messageId: Int) {
        runCatching { Toast.makeText(this, messageId, Toast.LENGTH_SHORT).show() }
    }

    private fun throttlePassed(packageName: String): Boolean {
        val now = System.currentTimeMillis()
        if (now - lastActionAt < THROTTLE_MILLIS && lastActionPackage == packageName) return false
        lastActionAt = now
        lastActionPackage = packageName
        return true
    }

    private fun preferences(): Preferences =
        runCatching { (application as NaqaaApplication).graph.current() }.getOrDefault(Preferences())

    private fun loadRules() {
        if (rulesLoaded) return
        rules = runCatching {
            assets.open(RULES_ASSET).bufferedReader().use { reader -> Rules.parse(reader.readText()) }
        }.getOrDefault(Rules.EMPTY)
        rulesLoaded = true
    }

    private companion object {
        const val RULES_ASSET = "accessibility-rules.json"
        const val THROTTLE_MILLIS = 1_200L
        const val NOTIFICATION_TIMEOUT = 200L
        const val WATCH_REFRESH_MILLIS = 5 * 60_000L
        const val VPN_RESUME_MILLIS = 60_000L

        val WATCHED_EVENTS = setOf(
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED,
            AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED
        )
    }
}
