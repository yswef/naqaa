package com.naqaa.app.vpn

import java.util.Locale

/**
 * Safe search enforcement.
 *
 * The resolver rewrites search host names to the vendors' restricted entry points before
 * the query leaves the device, so the answer that comes back belongs to a filtering
 * service. Only search entry points are rewritten: other services of the same vendors,
 * such as mail, keep working, which matters because an over-broad rule pushes people to
 * disable the protection entirely.
 */
object SafeSearch {

    enum class Target(val host: String) {
        GOOGLE("forcesafesearch.google.com"),
        YOUTUBE("restrict.youtube.com"),
        BING("strict.bing.com"),
        DUCKDUCKGO("safe.duckduckgo.com")
    }

    fun target(name: String): Target? {
        val host = name.trimEnd('.').lowercase(Locale.ROOT)
        val bare = if (host.startsWith("www.")) host.removePrefix("www.") else host
        return when {
            isApexOf(bare, "google") -> Target.GOOGLE
            bare == "youtube.com" || bare == "m.youtube.com" || bare == "youtube-nocookie.com" -> Target.YOUTUBE
            bare == "bing.com" -> Target.BING
            bare == "duckduckgo.com" -> Target.DUCKDUCKGO
            else -> null
        }
    }

    fun rewrite(name: String): String? = target(name)?.host

    /** Matches `google.com` and country variants such as `google.com.sa` or `google.co.uk`. */
    private fun isApexOf(host: String, label: String): Boolean {
        val labels = host.split('.')
        return labels.size in 2..3 && labels[0] == label
    }
}
