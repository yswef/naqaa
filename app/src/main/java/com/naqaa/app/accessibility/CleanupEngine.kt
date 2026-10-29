package com.naqaa.app.accessibility

import android.accessibilityservice.AccessibilityService
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityNodeInfo
import com.naqaa.app.R
import com.naqaa.app.util.CrashLog
import org.json.JSONObject
import java.io.File
import java.time.LocalDate
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.random.Random

/**
 * What the account cleanup is doing, shared between the screen that controls it and the
 * accessibility service that performs it. Everything is in memory except the daily counter
 * and the pacing settings, which live in a small private preferences file.
 */
object CleanupState {

    enum class Mode { OFF, UNFOLLOW, UNLIKE }

    enum class Phase { IDLE, WAITING_SCREEN, WORKING, PAUSED, DONE, STOPPED }

    private val listeners = CopyOnWriteArrayList<() -> Unit>()
    private val controlListeners = CopyOnWriteArrayList<() -> Unit>()

    @Volatile var mode: Mode = Mode.OFF
    @Volatile var phase: Phase = Phase.IDLE
    @Volatile var sessionDone: Int = 0
    @Volatile var pausedUntil: Long = 0L
    @Volatile var messageRes: Int = R.string.cleanup_status_idle
    @Volatile var dumpAt: Long = 0L

    fun addListener(listener: () -> Unit): () -> Unit {
        listeners.add(listener)
        return { listeners.remove(listener) }
    }

    /** The service listens here: a start, a stop or a dump request. */
    fun addControlListener(listener: () -> Unit): () -> Unit {
        controlListeners.add(listener)
        return { controlListeners.remove(listener) }
    }

    /** Something the engine must react to; the screens are told as well. */
    fun changed() {
        controlListeners.forEach { runCatching { it() } }
        listeners.forEach { runCatching { it() } }
    }

    /** Only the status line moved: the screens are told, the engine is left alone. */
    fun statusChanged() = listeners.forEach { runCatching { it() } }

    fun start(newMode: Mode) {
        mode = newMode
        phase = Phase.WAITING_SCREEN
        sessionDone = 0
        pausedUntil = 0L
        messageRes = R.string.cleanup_status_waiting
        changed()
    }

    fun stop(finalPhase: Phase = Phase.STOPPED, message: Int = R.string.cleanup_status_stopped) {
        mode = Mode.OFF
        phase = finalPhase
        messageRes = message
        changed()
    }

    /** Asks the service to write what the screen shows a few seconds from now. */
    fun requestDump(delaySeconds: Int = 8) {
        dumpAt = System.currentTimeMillis() + delaySeconds * 1000L
        changed()
    }

    // ---- settings and the daily counter ----

    private const val FILE = "cleanup"

    private fun prefs(context: Context) = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun batchSize(context: Context): Int = prefs(context).getInt("batch", 6)
    fun pauseMinutes(context: Context): Int = prefs(context).getInt("pause", 15)
    fun dailyCap(context: Context): Int = prefs(context).getInt("cap", 40)

    fun saveSettings(context: Context, batch: Int, pause: Int, cap: Int) {
        prefs(context).edit()
            .putInt("batch", batch.coerceIn(1, 20))
            .putInt("pause", pause.coerceIn(5, 120))
            .putInt("cap", cap.coerceIn(1, 200))
            .apply()
    }

    fun doneToday(context: Context): Int {
        val saved = prefs(context)
        return if (saved.getLong("day", 0L) == LocalDate.now().toEpochDay()) saved.getInt("count", 0) else 0
    }

    fun addDone(context: Context, amount: Int) {
        val today = LocalDate.now().toEpochDay()
        val saved = prefs(context)
        val current = if (saved.getLong("day", 0L) == today) saved.getInt("count", 0) else 0
        saved.edit().putLong("day", today).putInt("count", current + amount).apply()
        sessionDone += amount
    }
}

/**
 * Removes follows or likes from the user's own Instagram account, slowly, by pressing the
 * same buttons the user would press.
 *
 * It does not use any private interface: it reads the labels on the screen through the
 * accessibility service and acts only while the Instagram window is in front and the user
 * has switched the cleanup on. The pace is deliberately human: a random pause of several
 * seconds between actions, a longer pause after every batch, a daily cap, and a stop as soon
 * as the screen stops changing. The labels are data (`accessibility-rules.json`, section
 * "cleanup") because Instagram changes its texts; when a label does not match, the screen
 * dump (see [dump]) shows what is really there.
 */
class CleanupEngine(private val service: AccessibilityService) {

    private enum class Step { FIND, CONFIRM, SELECT, PICK, ACT }

    private val handler = Handler(Looper.getMainLooper())
    private var labels = Labels()
    private var step = Step.FIND
    private var batchCount = 0
    private var emptyTicks = 0
    private var sameClicks = 0
    private var lastKey: String? = null
    private var picked = 0
    private val pickedKeys = HashSet<String>()
    private var scheduledDump = 0L

    private data class Labels(
        val packageName: String = "com.instagram.android",
        val following: List<String> = listOf("Following", "متابَع", "متابع", "يتابَع"),
        val unfollow: List<String> = listOf("Unfollow", "إلغاء المتابعة", "إلغاء متابعة"),
        val select: List<String> = listOf("Select", "تحديد", "اختيار"),
        val unlike: List<String> = listOf("Unlike", "إلغاء الإعجاب", "إلغاء الاعجاب"),
        val confirm: List<String> = listOf("Unlike", "إلغاء الإعجاب", "إلغاء الاعجاب", "Yes", "نعم", "OK", "موافق")
    )

    init {
        runCatching {
            val text = service.assets.open("accessibility-rules.json").bufferedReader().use { it.readText() }
            JSONObject(text).optJSONObject("cleanup")?.let { json ->
                fun list(key: String, fallback: List<String>): List<String> {
                    val array = json.optJSONArray(key) ?: return fallback
                    return (0 until array.length()).mapNotNull { array.optString(it).takeIf(String::isNotBlank) }
                        .ifEmpty { fallback }
                }
                val base = Labels()
                labels = Labels(
                    packageName = json.optString("package", base.packageName),
                    following = list("following", base.following),
                    unfollow = list("unfollow", base.unfollow),
                    select = list("select", base.select),
                    unlike = list("unlike", base.unlike),
                    confirm = list("confirm", base.confirm)
                )
            }
        }
    }

    /** Package the service must watch for the cleanup to see the screen. */
    val packageName: String get() = labels.packageName

    /** Called whenever [CleanupState] changes: starts or stops the loop and arms a requested dump. */
    fun sync() {
        handler.removeCallbacks(loop)
        val active = CleanupState.mode != CleanupState.Mode.OFF &&
            CleanupState.phase in setOf(CleanupState.Phase.WAITING_SCREEN, CleanupState.Phase.WORKING, CleanupState.Phase.PAUSED)
        if (active) {
            step = if (CleanupState.mode == CleanupState.Mode.UNLIKE) Step.SELECT else Step.FIND
            batchCount = 0
            emptyTicks = 0
            sameClicks = 0
            lastKey = null
            picked = 0
            pickedKeys.clear()
            handler.postDelayed(loop, 1_500)
        }
        val wanted = CleanupState.dumpAt
        if (wanted != 0L && wanted != scheduledDump) {
            scheduledDump = wanted
            handler.postDelayed({ runCatching { dump() } }, (wanted - System.currentTimeMillis()).coerceAtLeast(0))
        }
    }

    fun shutdown() {
        handler.removeCallbacksAndMessages(null)
    }

    private val loop = Runnable {
        runCatching { tick() }.onFailure {
            CrashLog.note(service, "cleanup", it)
            CleanupState.stop(CleanupState.Phase.STOPPED, R.string.cleanup_status_error)
        }
    }

    private fun schedule(milliseconds: Long) {
        handler.removeCallbacks(loop)
        handler.postDelayed(loop, milliseconds)
    }

    private fun humanPause(): Long = Random.nextLong(7_000, 16_000)

    private fun tick() {
        val state = CleanupState
        if (state.mode == CleanupState.Mode.OFF) return
        if (state.doneToday(service) >= state.dailyCap(service)) {
            state.stop(CleanupState.Phase.DONE, R.string.cleanup_status_cap)
            return
        }
        val now = System.currentTimeMillis()
        if (now < state.pausedUntil) {
            state.phase = CleanupState.Phase.PAUSED
            state.messageRes = R.string.cleanup_status_paused
            state.statusChanged()
            schedule(20_000)
            return
        }
        val root = service.rootInActiveWindow
        if (root == null || root.packageName?.toString() != labels.packageName) {
            state.phase = CleanupState.Phase.WAITING_SCREEN
            state.messageRes = R.string.cleanup_status_waiting
            state.statusChanged()
            schedule(3_000)
            return
        }
        state.phase = CleanupState.Phase.WORKING
        state.messageRes = R.string.cleanup_status_working
        state.statusChanged()
        when (state.mode) {
            CleanupState.Mode.UNFOLLOW -> stepUnfollow(root)
            CleanupState.Mode.UNLIKE -> stepUnlike(root)
            CleanupState.Mode.OFF -> Unit
        }
    }

    // ---- unfollow: press "Following" on a row, then "Unfollow" in the confirmation ----

    private fun stepUnfollow(root: AccessibilityNodeInfo) {
        when (step) {
            Step.FIND -> {
                val button = find(root, labels.following, preferButton = true)
                if (button == null) {
                    scrollOrFinish(root)
                    return
                }
                emptyTicks = 0
                if (repeated(button)) return
                click(button)
                step = Step.CONFIRM
                schedule(Random.nextLong(1_200, 2_400))
            }
            else -> {
                val confirm = find(root, labels.unfollow, preferButton = false)
                if (confirm != null) {
                    click(confirm)
                    counted(1)
                }
                step = Step.FIND
                afterAction()
            }
        }
    }

    // ---- unlike (experimental): select mode, pick a few posts, press "Unlike", confirm ----

    private fun stepUnlike(root: AccessibilityNodeInfo) {
        when (step) {
            Step.SELECT, Step.FIND -> {
                val select = find(root, labels.select, preferButton = false)
                if (select == null) {
                    scrollOrFinish(root)
                    return
                }
                emptyTicks = 0
                click(select)
                picked = 0
                step = Step.PICK
                schedule(Random.nextLong(1_500, 2_500))
            }
            Step.PICK -> {
                val items = collect(root) { node ->
                    node.className?.toString().orEmpty().contains("Image") && node.isClickable
                }.filter { key(it) !in pickedKeys }
                val limit = minOf(CleanupState.batchSize(service), 5)
                if (picked >= limit || items.isEmpty()) {
                    step = if (picked > 0) Step.ACT else Step.SELECT
                    if (picked == 0) {
                        scrollOrFinish(root)
                    } else {
                        schedule(Random.nextLong(900, 1_600))
                    }
                    return
                }
                val item = items.first()
                pickedKeys.add(key(item))
                click(item)
                picked++
                schedule(Random.nextLong(700, 1_400))
            }
            Step.ACT -> {
                val unlike = find(root, labels.unlike, preferButton = true)
                if (unlike == null) {
                    CleanupState.stop(CleanupState.Phase.STOPPED, R.string.cleanup_status_nomatch)
                    return
                }
                click(unlike)
                step = Step.CONFIRM
                schedule(Random.nextLong(1_200, 2_000))
            }
            Step.CONFIRM -> {
                val confirm = find(root, labels.confirm, preferButton = true)
                if (confirm != null) click(confirm)
                counted(picked)
                picked = 0
                step = Step.SELECT
                afterAction()
            }
        }
    }

    // ---- pacing ----

    private fun counted(amount: Int) {
        if (amount > 0) {
            CleanupState.addDone(service, amount)
            batchCount += amount
            CleanupState.statusChanged()
        }
    }

    private fun afterAction() {
        val batch = CleanupState.batchSize(service)
        if (batchCount >= batch) {
            val minutes = CleanupState.pauseMinutes(service)
            val jitter = Random.nextLong(0, minutes * 12_000L)
            CleanupState.pausedUntil = System.currentTimeMillis() + minutes * 60_000L + jitter
            batchCount = 0
        }
        schedule(humanPause())
    }

    /** True (and the run stops) when the same node keeps being pressed without the screen changing. */
    private fun repeated(node: AccessibilityNodeInfo): Boolean {
        val current = key(node)
        sameClicks = if (current == lastKey) sameClicks + 1 else 0
        lastKey = current
        if (sameClicks >= 2) {
            CleanupState.stop(CleanupState.Phase.STOPPED, R.string.cleanup_status_nochange)
            return true
        }
        return false
    }

    private fun scrollOrFinish(root: AccessibilityNodeInfo) {
        emptyTicks++
        val scroller = collect(root) { it.isScrollable }.firstOrNull()
        if (scroller != null && emptyTicks <= 3) {
            scroller.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
            schedule(Random.nextLong(2_000, 3_500))
        } else {
            CleanupState.stop(CleanupState.Phase.DONE, R.string.cleanup_status_finished)
        }
    }

    // ---- node helpers ----

    private fun key(node: AccessibilityNodeInfo): String {
        val bounds = Rect()
        node.getBoundsInScreen(bounds)
        return "${node.text ?: node.contentDescription}@${bounds.flattenToString()}"
    }

    private fun collect(root: AccessibilityNodeInfo, accept: (AccessibilityNodeInfo) -> Boolean): List<AccessibilityNodeInfo> {
        val found = ArrayList<AccessibilityNodeInfo>()
        val pending = ArrayDeque<AccessibilityNodeInfo>()
        pending.add(root)
        var visited = 0
        while (pending.isNotEmpty() && visited < MAX_NODES) {
            val node = pending.removeFirst()
            visited++
            if (accept(node)) found.add(node)
            for (index in 0 until node.childCount) node.getChild(index)?.let { pending.add(it) }
        }
        return found
    }

    /** First node whose text or description equals one of the labels; buttons first when asked. */
    private fun find(root: AccessibilityNodeInfo, wanted: List<String>, preferButton: Boolean): AccessibilityNodeInfo? {
        val matches = collect(root) { node ->
            val text = node.text?.toString()?.trim()
            val description = node.contentDescription?.toString()?.trim()
            wanted.any { label -> label.equals(text, ignoreCase = true) || label.equals(description, ignoreCase = true) }
        }
        if (matches.isEmpty()) return null
        if (preferButton) {
            matches.firstOrNull { it.className?.toString().orEmpty().endsWith("Button") }?.let { return it }
        }
        return matches.first()
    }

    private fun click(node: AccessibilityNodeInfo) {
        var target: AccessibilityNodeInfo? = node
        var levels = 0
        while (target != null && !target.isClickable && levels < 4) {
            target = target.parent
            levels++
        }
        (target ?: node).performAction(AccessibilityNodeInfo.ACTION_CLICK)
    }

    // ---- diagnostics ----

    /**
     * Writes one line per node of the window in front (class, identifier, text, description,
     * flags, bounds) to a file and to the clipboard. It is what to send back when a label
     * does not match: the list shows the real texts of the screen.
     */
    fun dump() {
        val root = service.rootInActiveWindow ?: return
        val lines = StringBuilder()
        lines.append("package=").append(root.packageName).append('\n')
        val pending = ArrayDeque<Pair<AccessibilityNodeInfo, Int>>()
        pending.add(root to 0)
        var visited = 0
        val bounds = Rect()
        while (pending.isNotEmpty() && visited < MAX_NODES) {
            val (node, depth) = pending.removeFirst()
            visited++
            node.getBoundsInScreen(bounds)
            lines.append("  ".repeat(depth.coerceAtMost(12)))
                .append(node.className?.toString()?.substringAfterLast('.'))
                .append(" id=").append(node.viewIdResourceName?.substringAfterLast('/'))
                .append(" text=").append(node.text?.toString()?.take(60))
                .append(" desc=").append(node.contentDescription?.toString()?.take(60))
                .append(if (node.isClickable) " [click]" else "")
                .append(if (node.isScrollable) " [scroll]" else "")
                .append(" ").append(bounds.flattenToString())
                .append('\n')
            for (index in 0 until node.childCount) node.getChild(index)?.let { pending.add(it to depth + 1) }
        }
        val text = lines.toString()
        File(service.filesDir, DUMP_FILE).writeText(text)
        runCatching {
            val clipboard = service.getSystemService(ClipboardManager::class.java)
            clipboard.setPrimaryClip(ClipData.newPlainText("naqaa-dump", text))
        }
        CleanupState.dumpAt = 0L
        scheduledDump = 0L
        runCatching { android.widget.Toast.makeText(service, R.string.cleanup_dump_done, android.widget.Toast.LENGTH_LONG).show() }
    }

    private companion object {
        const val MAX_NODES = 600
        const val DUMP_FILE = "cleanup-dump.txt"
    }
}
