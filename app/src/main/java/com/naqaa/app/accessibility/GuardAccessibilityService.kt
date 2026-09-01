package com.naqaa.app.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.res.Configuration
import android.view.accessibility.AccessibilityEvent
import android.widget.Toast
import com.naqaa.app.NaqaaApplication
import com.naqaa.app.R
import com.naqaa.app.data.Preferences
import com.naqaa.app.guard.GuardGrace
import com.naqaa.app.guard.GuardSignals
import com.naqaa.app.guard.LockWindow
import com.naqaa.app.guard.NightMode
import com.naqaa.app.prayer.PrayerCalculator
import com.naqaa.app.ui.Overlays
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

    override fun onServiceConnected() {
        super.onServiceConnected()
        loadRules()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val event = event ?: return
        if (event.eventType !in WATCHED_EVENTS) return
        val packageName = event.packageName?.toString() ?: return
        if (packageName == this.packageName) return
        loadRules()
        val preferences = preferences()
        if (!preferences.onboarded) return
        updateWatchedPackages(preferences, packageName)
        if (!canAct(packageName)) return

        val minute = TimeX.minuteOfDay(Instant.now(), ZoneId.systemDefault())
        if (lockedNow(preferences, packageName, minute)) {
            openLock(R.string.lock_message_schedule, packageName)
            return
        }
        if (nightNow(preferences, packageName)) {
            openLock(R.string.lock_message_night, packageName)
            return
        }
        if (GuardGrace.isActive()) return

        val addressIds = rules.addressIds(packageName)
        val facts = NodeScan.facts(rootInActiveWindow, packageName, addressIds)

        if (preferences.blockShorts) {
            val rule = rules.appRule(packageName)
            if (rule != null) {
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

    /** Launcher applications can appear without an accessibility event, so the list is refreshed as needed. */
    private fun updateWatchedPackages(preferences: Preferences, packageName: String) {
        if (packageName in watched) return
        val wanted = buildSet {
            add(packageName)
            rules.shortVideo.forEach { add(it.packageName) }
            rules.browsers.forEach { add(it.packageName) }
            addAll(rules.riskyPackages)
            preferences.lockedApps.forEach { add(it.packageName) }
        }
        if (wanted == watched) return
        watched = wanted
        runCatching {
            serviceInfo = (serviceInfo ?: AccessibilityServiceInfo()).apply {
                packageNames = wanted.toTypedArray()
                notificationTimeout = NOTIFICATION_TIMEOUT
            }
        }
    }

    private fun canAct(packageName: String): Boolean =
        rules.watches(packageName) || preferences().lockedApps.any { it.packageName == packageName }

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
        ramadan = TimeX.isRamadan(date)
    )

    private fun closeSurface(messageId: Int, packageName: String, reason: GuardSignals.Reason) {
        if (!throttlePassed(packageName)) return
        performGlobalAction(GLOBAL_ACTION_BACK)
        message(messageId)
        GuardSignals.raise(reason)
    }

    private fun closeEverything(messageId: Int, packageName: String, reason: GuardSignals.Reason) {
        if (!throttlePassed(packageName)) return
        performGlobalAction(GLOBAL_ACTION_HOME)
        message(messageId)
        GuardSignals.raise(reason)
    }

    private fun openLock(messageId: Int, packageName: String) {
        if (!throttlePassed(packageName)) return
        performGlobalAction(GLOBAL_ACTION_HOME)
        startActivity(Overlays.lock(this, messageId, packageName))
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

        val WATCHED_EVENTS = setOf(
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED,
            AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED
        )
    }
}
