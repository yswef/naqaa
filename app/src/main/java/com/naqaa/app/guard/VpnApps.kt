package com.naqaa.app.guard

import android.content.Context
import android.content.Intent

/**
 * Applications that open a VPN of their own.
 *
 * Android lets one VPN run at a time, so starting another one silently ends the local
 * filter. The names below are well known services; on top of them every installed launcher
 * application whose package name or label contains "vpn" is treated the same way, which
 * covers the long tail without a list that would go out of date.
 */
object VpnApps {

    val KNOWN: Set<String> = setOf(
        "com.windscribe.vpn",
        "ch.protonvpn.android",
        "com.nordvpn.android",
        "com.expressvpn.vpn",
        "com.surfshark.vpnclient.android",
        "de.mobileconcepts.cyberghost",
        "com.psiphon3",
        "com.psiphon3.subscription",
        "com.tunnelbear.android",
        "hotspotshield.android.vpn",
        "free.vpn.unblock.proxy.turbovpn",
        "com.cloudflare.onedotonedotonedotone",
        "com.wireguard.android",
        "net.openvpn.openvpn",
        "de.blinkt.openvpn",
        "net.mullvad.mullvadvpn",
        "com.privateinternetaccess.android",
        "com.ixolit.ipvanish",
        "org.outline.android.client",
        "org.torproject.android"
    )

    @Volatile
    private var cache: Set<String> = emptySet()

    @Volatile
    private var cachedAt = 0L

    /** Known names plus the installed applications that look like VPN clients. Cached for five minutes. */
    fun all(context: Context, own: String = context.packageName): Set<String> {
        val now = System.currentTimeMillis()
        if (now - cachedAt < CACHE_MILLIS && cache.isNotEmpty()) return cache
        val manager = context.packageManager
        val found = runCatching {
            val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            manager.queryIntentActivities(intent, 0).mapNotNull { resolved ->
                val info = resolved.activityInfo?.applicationInfo ?: return@mapNotNull null
                val label = runCatching { manager.getApplicationLabel(info).toString() }.getOrDefault("")
                val name = info.packageName
                if (name.contains("vpn", ignoreCase = true) || label.contains("vpn", ignoreCase = true)) name else null
            }
        }.getOrDefault(emptyList())
        cache = (KNOWN + found).filterNot { it == own }.toSet()
        cachedAt = now
        return cache
    }

    private const val CACHE_MILLIS = 5 * 60_000L
}
