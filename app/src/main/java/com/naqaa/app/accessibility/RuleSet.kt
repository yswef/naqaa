package com.naqaa.app.accessibility

import com.naqaa.app.guard.DelayGate
import org.json.JSONArray
import org.json.JSONObject

/**
 * The rules that decide when the service interferes, kept as data.
 *
 * View identifiers change with application updates, and a list stored in the assets can be
 * corrected without touching logic. Matching is deliberately conservative: a rule fires
 * only when a known container identifier and a known label appear together, or when the
 * address field of a known browser contains a word from the list. Nothing else on the
 * screen is read.
 */
data class AppRule(
    val packageName: String,
    val ids: List<String>,
    val labels: List<String>,
    val wholeApp: Boolean
)

data class BrowserRule(
    val packageName: String,
    val addressIds: List<String>
)

data class RiskyGroup(
    val action: DelayGate.Action,
    val keywords: List<String>
)

data class Rules(
    val shortVideo: List<AppRule>,
    val browsers: List<BrowserRule>,
    val keywords: List<String>,
    val riskyPackages: Set<String>,
    val riskyGroups: List<RiskyGroup>
) {

    fun appRule(packageName: String): AppRule? = shortVideo.firstOrNull { it.packageName == packageName }

    fun browserRule(packageName: String): BrowserRule? = browsers.firstOrNull { it.packageName == packageName }

    fun isRiskyPackage(packageName: String): Boolean = packageName in riskyPackages

    fun addressIds(packageName: String): List<String> = browserRule(packageName)?.addressIds.orEmpty()

    /** True when any rule or lock can act on this package, so the service can filter events. */
    fun watches(packageName: String): Boolean =
        appRule(packageName) != null || browserRule(packageName) != null || isRiskyPackage(packageName)

    companion object {

        val EMPTY = Rules(emptyList(), emptyList(), emptyList(), emptySet(), emptyList())

        fun parse(json: String): Rules {
            val root = JSONObject(json)
            return Rules(
                shortVideo = root.optJSONArray("shortVideo").objects().mapNotNull { item ->
                    val packageName = item.optString("package").takeIf { it.isNotBlank() } ?: return@mapNotNull null
                    AppRule(
                        packageName = packageName,
                        ids = item.optJSONArray("ids").strings(),
                        labels = item.optJSONArray("labels").strings(),
                        wholeApp = item.optBoolean("wholeApp", false)
                    )
                },
                browsers = root.optJSONArray("browsers").objects().mapNotNull { item ->
                    val packageName = item.optString("package").takeIf { it.isNotBlank() } ?: return@mapNotNull null
                    BrowserRule(packageName = packageName, addressIds = item.optJSONArray("addressIds").strings())
                },
                keywords = root.optJSONArray("keywords").strings().map { it.lowercase() },
                riskyPackages = root.optJSONObject("riskySettings")?.optJSONArray("packages").strings().orEmpty().toSet(),
                riskyGroups = root.optJSONObject("riskySettings")?.optJSONArray("groups").objects().orEmpty().mapNotNull { item ->
                    val action = DelayGate.Action.entries.firstOrNull { it.name == item.optString("action") }
                        ?: return@mapNotNull null
                    RiskyGroup(action = action, keywords = item.optJSONArray("keywords").strings().map { it.lowercase() })
                }
            )
        }

        private fun JSONArray?.objects(): List<JSONObject> {
            val array = this ?: return emptyList()
            return (0 until array.length()).mapNotNull { array.optJSONObject(it) }
        }

        private fun JSONArray?.strings(): List<String> {
            val array = this ?: return emptyList()
            return (0 until array.length()).mapNotNull { array.optString(it).takeIf(String::isNotBlank) }
        }
    }
}

/** What the service observed, already reduced to the smallest useful shape. */
data class ScreenFacts(
    val packageName: String,
    val viewIds: Set<String>,
    val texts: List<String>,
    val addressText: String?,
    val containsEditText: Boolean
)

/** Pure decisions, so the behaviour can be tested without a running service. */
object ScreenRules {

    fun showsShortVideo(rule: AppRule, facts: ScreenFacts): Boolean {
        if (rule.ids.isEmpty()) return false
        val idSeen = rule.ids.any { wanted -> facts.viewIds.any { it.endsWith(wanted) } }
        if (!idSeen) return false
        if (rule.labels.isEmpty()) return true
        return rule.labels.any { label -> facts.texts.any { it.equals(label, ignoreCase = true) } }
    }

    fun blockedAddress(rules: Rules, facts: ScreenFacts): Boolean {
        val text = facts.addressText?.lowercase() ?: return false
        if (text.isBlank()) return false
        return rules.keywords.any { keyword -> keyword.isNotBlank() && text.contains(keyword) }
    }

    fun riskyAction(rules: Rules, facts: ScreenFacts): DelayGate.Action? {
        if (!rules.isRiskyPackage(facts.packageName)) return null
        val lowered = facts.texts.map { it.lowercase() }
        return rules.riskyGroups.firstOrNull { group ->
            group.keywords.any { keyword -> lowered.any { it.contains(keyword) } }
        }?.action
    }
}
