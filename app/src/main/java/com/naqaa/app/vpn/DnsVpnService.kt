package com.naqaa.app.vpn

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import com.naqaa.app.AppGraph
import com.naqaa.app.NaqaaApplication
import com.naqaa.app.R
import com.naqaa.app.content.Content
import com.naqaa.app.data.EventKind
import com.naqaa.app.notify.Notices
import com.naqaa.app.util.CrashLog
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.InetAddress
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * Local resolver.
 *
 * The tunnel carries name resolution only: the interface address is used for replies and
 * the routes cover this resolver plus the name servers of the underlying network, so
 * nothing else is diverted. Every answer that is not filtered leaves the device over a
 * protected socket, which is the sole use of the INTERNET permission in this application.
 */
class DnsVpnService : VpnService() {

    private val graph: AppGraph get() = (application as NaqaaApplication).graph
    private val running = AtomicBoolean(false)
    private val blockedNoticeAt = AtomicLong(0L)

    private var tunnel: ParcelFileDescriptor? = null
    private var tunnelRoutes: List<String> = emptyList()
    private var worker: Thread? = null
    private var blocklist: DomainBlocklist? = null
    private var callback: ConnectivityManager.NetworkCallback? = null

    override fun onCreate() {
        super.onCreate()
        Notices.channels(this)
        Content.load(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForegroundCompat()
        if (running.compareAndSet(false, true)) {
            VpnState.markStarted()
            announceStart()
            watchNetworks()
            startWorker()
        }
        return Service.START_STICKY
    }

    /** Restarts the tunnel when the name servers of the underlying network change. */
    private fun watchNetworks() {
        if (callback != null) return
        val observer = object : ConnectivityManager.NetworkCallback() {
            override fun onLinkPropertiesChanged(network: Network, properties: LinkProperties) {
                if (underlyingServers() != tunnelRoutes) restart()
            }
        }
        callback = observer
        runCatching { connectivity().registerDefaultNetworkCallback(observer) }
    }

    private fun restart() {
        val previous = worker
        running.set(false)
        closeTunnel()
        previous?.interrupt()
        // Closing the descriptor unblocks the read in the loop that is ending; waiting for
        // its cleanup to finish keeps a dying session from stopping the one that starts
        // here, because the old finally block would otherwise close the new tunnel and
        // stop the service from under the replacement worker.
        runCatching { previous?.join(RESTART_JOIN_MILLIS) }
        if (running.compareAndSet(false, true)) {
            VpnState.markStarted()
            startForegroundCompat()
            startWorker()
        }
    }

    private fun startWorker() {
        val thread = Thread({ serve() }, "naqaa-resolver")
        worker = thread
        thread.start()
    }

    private fun serve() {
        try {
            val filter = blocklist ?: loadBlocklist().also { blocklist = it }
            val descriptor = establish() ?: throw IllegalStateException("tunnel unavailable")
            tunnel = descriptor
            val resolver = UpstreamResolver({ socket -> protect(socket) }, { resolverAddresses() })
            val cache = DnsCache()
            FileInputStream(descriptor.fileDescriptor).use { input ->
                FileOutputStream(descriptor.fileDescriptor).use { output ->
                    val buffer = ByteArray(MAX_PACKET)
                    while (running.get()) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        if (read == 0) continue
                        val reply = handle(buffer, read, filter, resolver, cache) ?: continue
                        output.write(reply)
                    }
                }
            }
        } catch (error: Exception) {
            // A revoked tunnel or a closed descriptor ends the loop; onRevoke and the next
            // start command decide whether protection comes back. A catalogue that cannot be
            // read is different, and is recorded: a filter without its list does nothing.
            if (blocklist == null) CrashLog.note(this, "blocklist", error)
        } finally {
            // Only the active worker ends the session: after a network change the loop
            // that replaced this one owns the state, and a late cleanup from a stale
            // worker must not tear down the tunnel that took its place.
            if (Thread.currentThread() == worker && running.getAndSet(false)) {
                closeTunnel()
                VpnState.markStopped()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
    }

    private fun handle(
        packet: ByteArray,
        length: Int,
        filter: DomainBlocklist,
        resolver: UpstreamResolver,
        cache: DnsCache
    ): ByteArray? {
        val datagram = Ipv4Udp.parse(packet, length) ?: return null
        if (datagram.destinationPort != UpstreamResolver.PORT) return null
        val query = datagram.payload
        val asked = DnsMessage.question(query) ?: return null
        val response = when {
            filter.blocks(asked.name) -> {
                reportBlock()
                DnsMessage.error(query, DnsMessage.RCODE_NXDOMAIN)
            }
            else -> {
                val restricted = SafeSearch.rewrite(asked.name)
                if (restricted == null) forward(query, resolver, cache) else restricted(query, asked, restricted, resolver)
            }
        } ?: return null
        return Ipv4Udp.reply(packet, response)
    }

    private fun forward(query: ByteArray, resolver: UpstreamResolver, cache: DnsCache): ByteArray? {
        val key = DnsCache.key(query)
        if (key != null) cache.get(key, System.currentTimeMillis())?.let { return it }
        val answer = resolver.query(query) ?: return DnsMessage.error(query, DnsMessage.RCODE_SERVFAIL)
        if (key != null) {
            val ttl = DnsMessage.records(answer, NO_TYPE).minimumTtl ?: DEFAULT_TTL
            cache.put(key, answer, System.currentTimeMillis(), ttl)
        }
        return answer
    }

    /**
     * Rewrites a search name to the vendor's restricted entry point and answers the client
     * with the address of that entry point, under the name the client asked for.
     */
    private fun restricted(
        query: ByteArray,
        asked: DnsMessage.Question,
        target: String,
        resolver: UpstreamResolver
    ): ByteArray? {
        val rewritten = DnsMessage.withQuestionName(query, target) ?: return null
        val answer = resolver.query(rewritten) ?: return DnsMessage.error(query, DnsMessage.RCODE_SERVFAIL)
        val records = DnsMessage.records(answer, asked.type)
        return DnsMessage.addressAnswer(query, asked.type, records.addresses, records.minimumTtl ?: DEFAULT_TTL)
    }

    private fun reportBlock() {
        VpnState.noteBlock()
        val now = System.currentTimeMillis()
        val previous = blockedNoticeAt.get()
        if (now - previous < BLOCK_NOTICE_INTERVAL) return
        if (!blockedNoticeAt.compareAndSet(previous, now)) return
        val card = Content.protectionCard()
        Notices.blocked(this, card.verseAr, card.motivation(graph.current().language))
    }

    private fun announceStart() {
        val card = Content.protectionCard()
        Notices.protectionStarted(this, card.verseAr, card.motivation(graph.current().language))
    }

    private fun loadBlocklist(): DomainBlocklist = DomainBlocklist.fromAsset(assets)

    private fun establish(): ParcelFileDescriptor? {
        val routes = underlyingServers()
        val builder = Builder()
            .setSession(getString(R.string.app_name))
            .addAddress(INTERFACE_ADDRESS, PREFIX)
            .addDnsServer(RESOLVER_ADDRESS)
            .addRoute(RESOLVER_ADDRESS, PREFIX)
            .setMtu(MTU)
            .setBlocking(true)
        routes.forEach { builder.addRoute(it, PREFIX) }
        tunnelRoutes = routes
        return runCatching { builder.establish() }.getOrNull()
    }

    /** Name servers of the networks this tunnel sits on top of, without the tunnel itself. */
    private fun underlyingServers(): List<String> {
        val manager = connectivity()
        val servers = linkedSetOf<String>()
        manager.allNetworks.forEach { network ->
            val capabilities = manager.getNetworkCapabilities(network) ?: return@forEach
            if (!capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) return@forEach
            if (!capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)) return@forEach
            manager.getLinkProperties(network)?.dnsServers?.forEach { address ->
                address.hostAddress?.takeIf { it.count { character -> character == '.' } == 3 }?.let(servers::add)
            }
        }
        return servers.take(MAX_ROUTES)
    }

    private fun resolverAddresses(): List<InetAddress> {
        val manager = connectivity()
        val addresses = mutableListOf<InetAddress>()
        manager.allNetworks.forEach { network ->
            val capabilities = manager.getNetworkCapabilities(network) ?: return@forEach
            if (!capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)) return@forEach
            manager.getLinkProperties(network)?.dnsServers?.let(addresses::addAll)
        }
        return addresses.distinct().sortedBy { it.hostAddress ?: "" }
    }

    private fun connectivity(): ConnectivityManager = getSystemService(ConnectivityManager::class.java)

    private fun startForegroundCompat() {
        val notification = Notices.protection(this)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(Notices.ID_PROTECTION, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(Notices.ID_PROTECTION, notification)
        }
    }

    private fun closeTunnel() {
        runCatching { tunnel?.close() }
        tunnel = null
    }

    override fun onRevoke() {
        // The user removed the tunnel from system settings; the journal keeps a record so
        // the interruption shows up in the weekly report instead of passing unnoticed.
        running.set(false)
        closeTunnel()
        VpnState.markStopped()
        runCatching { (application as NaqaaApplication).graph.record(EventKind.GUARD_OFF, note = NOTE_REVOKED) }
        stopSelf()
        super.onRevoke()
    }

    override fun onDestroy() {
        callback?.let { observer -> runCatching { connectivity().unregisterNetworkCallback(observer) } }
        callback = null
        running.set(false)
        closeTunnel()
        worker?.interrupt()
        VpnState.markStopped()
        super.onDestroy()
    }

    private companion object {
        const val INTERFACE_ADDRESS = "10.111.0.1"
        const val RESOLVER_ADDRESS = "10.111.0.2"
        const val PREFIX = 32
        const val MTU = 1500
        const val MAX_PACKET = 32_767
        const val MAX_ROUTES = 4
        const val BLOCK_NOTICE_INTERVAL = 15_000L
        const val RESTART_JOIN_MILLIS = 2_000L
        const val DEFAULT_TTL = 60
        const val NO_TYPE = -1
        const val NOTE_REVOKED = "vpn_revoked"
    }
}
