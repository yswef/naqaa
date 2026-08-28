package com.naqaa.app.vpn

import java.util.Locale

/**
 * Minimal but complete handling of the DNS wire format for a filtering resolver.
 *
 * Only the pieces a stub resolver needs are implemented: question extraction, error and
 * address answers, building a query for a rewritten name, and reading addresses back out
 * of a real upstream answer. Name compression is understood in both directions.
 */
object DnsMessage {

    const val TYPE_A = 1
    const val TYPE_CNAME = 5
    const val TYPE_AAAA = 28

    const val RCODE_FORMERR = 1
    const val RCODE_SERVFAIL = 2
    const val RCODE_NXDOMAIN = 3

    const val FLAG_RESPONSE = 0x8000
    const val FLAG_RECURSION_DESIRED = 0x0100
    const val FLAG_RECURSION_AVAILABLE = 0x0080
    const val POINTER = 0xC00C

    const val MAX_NAME_LENGTH = 255
    const val MAX_LABEL_LENGTH = 63

    data class Question(val name: String, val type: Int, val u16Class: Int, val end: Int)

    /** A name read from a packet together with the offset right after its encoding. */
    data class Name(val text: String, val next: Int)

    fun u16(packet: ByteArray, at: Int): Int =
        ((packet[at].toInt() and 0xFF) shl 8) or (packet[at + 1].toInt() and 0xFF)

    fun put16(packet: ByteArray, at: Int, value: Int) {
        packet[at] = (value ushr 8).toByte()
        packet[at + 1] = value.toByte()
    }

    fun put32(packet: ByteArray, at: Int, value: Long) {
        packet[at] = (value ushr 24).toByte()
        packet[at + 1] = (value ushr 16).toByte()
        packet[at + 2] = (value ushr 8).toByte()
        packet[at + 3] = value.toByte()
    }

    fun readName(packet: ByteArray, offset: Int): Name? {
        val labels = StringBuilder()
        var cursor = offset
        var next = -1
        var jumps = 0
        var length = 0
        while (true) {
            if (cursor >= packet.size) return null
            val marker = packet[cursor].toInt() and 0xFF
            when {
                marker == 0 -> {
                    cursor += 1
                    if (next < 0) next = cursor
                    break
                }
                marker and 0xC0 == 0xC0 -> {
                    if (cursor + 1 >= packet.size) return null
                    val target = ((marker and 0x3F) shl 8) or (packet[cursor + 1].toInt() and 0xFF)
                    if (next < 0) next = cursor + 2
                    if (++jumps > MAX_JUMPS || target >= packet.size) return null
                    cursor = target
                }
                marker and 0xC0 != 0 -> return null
                else -> {
                    val start = cursor + 1
                    if (start + marker > packet.size) return null
                    if (labels.isNotEmpty()) labels.append('.')
                    for (index in 0 until marker) {
                        val value = packet[start + index].toInt() and 0xFF
                        if (value !in 0x21..0x7E) return null
                        labels.append(value.toChar())
                    }
                    length += marker + 1
                    if (length > MAX_NAME_LENGTH) return null
                    cursor = start + marker
                }
            }
        }
        if (labels.isEmpty()) return null
        return Name(labels.toString().lowercase(Locale.ROOT), next)
    }

    /** Question of a packet regardless of its flags, used to inspect upstream answers. */
    fun readQuestion(packet: ByteArray): Question? {
        if (packet.size < 17) return null
        if (u16(packet, 4) != 1) return null
        val name = readName(packet, 12) ?: return null
        val end = name.next
        if (end + 4 > packet.size) return null
        return Question(name.text, u16(packet, end), u16(packet, end + 2), end + 4)
    }

    /** Question of an incoming query; responses are rejected. */
    fun question(packet: ByteArray): Question? =
        if (u16(packet, 2) and FLAG_RESPONSE != 0) null else readQuestion(packet)

    /** Builds a response that echoes the question and carries the given error code. */
    fun error(query: ByteArray, rcode: Int): ByteArray? {
        val asked = readQuestion(query) ?: return null
        val flags = FLAG_RESPONSE or (u16(query, 2) and FLAG_RECURSION_DESIRED) or FLAG_RECURSION_AVAILABLE or (rcode and 0xF)
        return query.copyOf(asked.end).also {
            put16(it, 2, flags)
            put16(it, 6, 0)
            put16(it, 8, 0)
            put16(it, 10, 0)
        }
    }

    /** Builds a response answering the original question with the supplied addresses. */
    fun addressAnswer(query: ByteArray, type: Int, addresses: List<ByteArray>, ttl: Int): ByteArray? {
        val asked = readQuestion(query) ?: return null
        val header = 12 + (asked.end - 12)
        val size = header + addresses.sumOf { 12 + it.size }
        val answer = ByteArray(size)
        query.copyInto(answer, 0, 0, header)
        val flags = FLAG_RESPONSE or (u16(query, 2) and FLAG_RECURSION_DESIRED) or FLAG_RECURSION_AVAILABLE
        put16(answer, 2, flags)
        put16(answer, 4, 1)
        put16(answer, 6, addresses.size)
        put16(answer, 8, 0)
        put16(answer, 10, 0)
        var cursor = header
        addresses.forEach { address ->
            put16(answer, cursor, POINTER)
            put16(answer, cursor + 2, type)
            put16(answer, cursor + 4, 1)
            put32(answer, cursor + 6, ttl.toLong())
            put16(answer, cursor + 10, address.size)
            address.copyInto(answer, cursor + 12)
            cursor += 12 + address.size
        }
        return answer
    }

    /** Returns a copy of the query whose question name is replaced. */
    fun withQuestionName(query: ByteArray, name: String): ByteArray? {
        val asked = readQuestion(query) ?: return null
        val encoded = encodeName(name) ?: return null
        val tail = query.copyOfRange(asked.end, query.size)
        val answer = ByteArray(12 + encoded.size + 4 + tail.size)
        query.copyInto(answer, 0, 0, 12)
        encoded.copyInto(answer, 12)
        put16(answer, 12 + encoded.size, asked.type)
        put16(answer, 12 + encoded.size + 2, asked.u16Class)
        tail.copyInto(answer, 12 + encoded.size + 4)
        put16(answer, 4, 1)
        put16(answer, 6, 0)
        put16(answer, 8, 0)
        put16(answer, 10, u16(query, 10))
        return answer
    }

    fun encodeName(name: String): ByteArray? {
        val normalized = name.trimEnd('.')
        if (normalized.isEmpty() || normalized.length > MAX_NAME_LENGTH) return null
        val labels = normalized.split('.')
        val size = labels.sumOf { it.length + 1 } + 1
        if (size > MAX_NAME_LENGTH) return null
        val encoded = ByteArray(size)
        var cursor = 0
        labels.forEach { label ->
            if (label.isEmpty() || label.length > MAX_LABEL_LENGTH) return null
            encoded[cursor++] = label.length.toByte()
            label.forEach { character ->
                val value = character.code
                if (value !in 0x21..0x7E) return null
                encoded[cursor++] = value.toByte()
            }
        }
        encoded[cursor] = 0
        return encoded
    }

    data class Records(val addresses: List<ByteArray>, val minimumTtl: Int?)

    /** Collects records of the requested type from every answer section. */
    fun records(packet: ByteArray, type: Int): Records {
        val questions = u16(packet, 4)
        val counts = intArrayOf(u16(packet, 6), u16(packet, 8), u16(packet, 10))
        var cursor = 12
        repeat(questions) {
            val name = readName(packet, cursor) ?: return Records(emptyList(), null)
            cursor = name.next + 4
        }
        val addresses = mutableListOf<ByteArray>()
        var minimum: Int? = null
        counts.forEach { count ->
            repeat(count) {
                if (cursor + 10 > packet.size) return Records(addresses, minimum)
                val name = readName(packet, cursor) ?: return Records(addresses, minimum)
                val recordType = u16(packet, name.next)
                val ttl = (u16(packet, name.next + 4).toLong() shl 16) or u16(packet, name.next + 6).toLong()
                val length = u16(packet, name.next + 8)
                val data = name.next + 10
                if (data + length > packet.size) return Records(addresses, minimum)
                if (recordType != 41) {
                    val value = ttl.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
                    val current = minimum
                    minimum = if (current == null || value < current) value else current
                }
                if (recordType == type && (length == 4 || length == 16)) {
                    addresses += packet.copyOfRange(data, data + length)
                }
                cursor = data + length
            }
        }
        return Records(addresses, minimum)
    }

    private const val MAX_JUMPS = 24
}
