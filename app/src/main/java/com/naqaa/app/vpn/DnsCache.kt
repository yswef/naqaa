package com.naqaa.app.vpn

import java.util.LinkedHashMap

/**
 * Small in-memory answer cache. DNS filtering generates a query for every name a page
 * needs; caching the upstream answer for its advertised lifetime keeps latency and mobile
 * radio wake-ups low. The cache dies with the process and nothing is written to disk.
 */
class DnsCache(private val limit: Int = 256) {

    private data class Entry(val response: ByteArray, val expiresAt: Long)

    private val entries = object : LinkedHashMap<String, Entry>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Entry>): Boolean = size > limit
    }

    @Synchronized
    fun get(key: String, nowMillis: Long): ByteArray? {
        val entry = entries[key] ?: return null
        if (entry.expiresAt <= nowMillis) {
            entries.remove(key)
            return null
        }
        return entry.response
    }

    @Synchronized
    fun put(key: String, response: ByteArray, nowMillis: Long, ttlSeconds: Int) {
        val ttl = ttlSeconds.coerceIn(MIN_TTL_SECONDS, MAX_TTL_SECONDS)
        entries[key] = Entry(response, nowMillis + ttl * 1_000L)
    }

    @Synchronized
    fun clear() = entries.clear()

    @Synchronized
    fun size(): Int = entries.size

    companion object {
        fun key(query: ByteArray): String? {
            val asked = DnsMessage.question(query) ?: return null
            val dnssec = DnsMessage.u16(query, 10) > 0
            return "${asked.name}/${asked.type}/${asked.u16Class}/$dnssec"
        }

        private const val MIN_TTL_SECONDS = 30
        private const val MAX_TTL_SECONDS = 900
    }
}
