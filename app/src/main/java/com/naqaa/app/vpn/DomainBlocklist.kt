package com.naqaa.app.vpn

import java.io.InputStream
import java.util.Locale
import java.util.zip.GZIPInputStream

/**
 * Domain suffix set used by the resolver.
 *
 * The compressed catalogue is decoded once and kept as a single byte table of names in
 * sorted order plus a table of offsets. Lookups binary search the table, trying every
 * suffix of the queried name, so a rule for `example.com` also covers `cdn.example.com`.
 * A byte table keeps the resident cost close to the raw text instead of one string object
 * per rule, which matters at seventy thousand entries.
 */
class DomainBlocklist private constructor(
    private val table: ByteArray,
    private val offsets: IntArray
) {

    val size: Int get() = offsets.size - 1

    fun blocks(domain: String): Boolean {
        var suffix = domain.trim().trimEnd('.').lowercase(Locale.ROOT)
        while (suffix.isNotEmpty()) {
            if (contains(suffix)) return true
            val dot = suffix.indexOf('.')
            if (dot < 0) return false
            suffix = suffix.substring(dot + 1)
        }
        return false
    }

    private fun contains(name: String): Boolean {
        var low = 0
        var high = size - 1
        while (low <= high) {
            val middle = (low + high) ushr 1
            val comparison = compare(middle, name)
            when {
                comparison == 0 -> return true
                comparison < 0 -> low = middle + 1
                else -> high = middle - 1
            }
        }
        return false
    }

    /** Compares a stored name with the queried one; both are read in the same direction. */
    private fun compare(index: Int, name: String): Int {
        val start = offsets[index]
        val end = offsets[index + 1]
        var position = start
        var queried = 0
        while (position < end && queried < name.length) {
            val stored = table[position].toInt() and 0xFF
            val wanted = name[queried].code
            if (stored != wanted) return stored - wanted
            position++
            queried++
        }
        return (end - position) - (name.length - queried)
    }

    companion object {

        fun fromCompressed(stream: InputStream): DomainBlocklist =
            GZIPInputStream(stream).bufferedReader().use { reader -> fromLines(reader.readLines().asSequence()) }

        fun fromLines(lines: Sequence<String>): DomainBlocklist {
            val ordered = lines.asSequence()
                .map { it.substringBefore('#').trim().trimEnd('.').lowercase(Locale.ROOT) }
                .filter { it.isNotEmpty() && it.length <= 253 && it.contains('.') }
                .filter { entry -> entry.all { it.code in 0x21..0x7E } }
                .distinct()
                .sorted()
                .toList()
            var bytes = 0
            ordered.forEach { bytes += it.length }
            val table = ByteArray(bytes)
            val offsets = IntArray(ordered.size + 1)
            var cursor = 0
            ordered.forEachIndexed { index, entry ->
                offsets[index] = cursor
                entry.forEach { table[cursor++] = it.code.toByte() }
            }
            offsets[ordered.size] = cursor
            return DomainBlocklist(table, offsets)
        }
    }
}
