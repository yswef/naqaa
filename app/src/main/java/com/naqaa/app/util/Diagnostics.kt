package com.naqaa.app.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.naqaa.app.NaqaaApplication
import com.naqaa.app.guard.VpnApps
import com.naqaa.app.vpn.VpnState
import java.time.Instant

/**
 * Plain-text reports for the problems that cannot be found by reading code. The user copies
 * one and sends it back; nothing in it is private (no addresses, no page names, no journal).
 */
object Diagnostics {

    fun vpnReport(context: Context): String {
        val lines = ArrayList<String>()
        lines += "Naqaa VPN report " + Instant.now()
        lines += "filter running: ${VpnState.running}"
        lines += "replaced by another vpn: ${VpnState.replacedByOther} (revokedAt=${VpnState.revokedAt})"
        lines += "private dns: ${VpnState.privateDnsMode(context)} ${VpnState.privateDnsHost(context).orEmpty()}"
        lines += "accessibility enabled: ${runCatching { SystemGate.accessibilityEnabled(context) }.getOrNull()}"
        val blockOther = runCatching { (context.applicationContext as NaqaaApplication).graph.current().blockOtherVpn }.getOrNull()
        lines += "block other vpn setting: $blockOther"
        runCatching {
            val manager = context.getSystemService(ConnectivityManager::class.java)
            @Suppress("DEPRECATION")
            val networks = manager.allNetworks
            val vpnNetworks = networks.count { manager.getNetworkCapabilities(it)?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true }
            lines += "networks: ${networks.size}, with vpn transport: $vpnNetworks"
        }.onFailure { lines += "networks: unreadable (${it.javaClass.simpleName})" }
        val pm = context.packageManager
        val installed = VpnApps.all(context).filter { pm.getLaunchIntentForPackage(it) != null }
        lines += "installed vpn-like apps covered by the block (${installed.size}): ${installed.sorted().joinToString()}"
        return lines.joinToString("\n")
    }
}
