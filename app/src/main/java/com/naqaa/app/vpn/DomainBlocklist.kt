package com.naqaa.app.vpn

import android.content.res.AssetManager
import java.io.BufferedInputStream
import java.io.FileNotFoundException
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

        /**
         * Reads the catalogue from wherever the build put it. A packaging tool expands a
         * gzip asset and drops the suffix from the name it is stored under, so the stream is
         * recognised by its magic bytes instead of by the name it arrived under: a filter
         * that starts without its list blocks nothing, and it would do so silently.
         */
        fun fromAsset(assets: AssetManager): DomainBlocklist {
            ASSET_NAMES.forEach { name ->
                val stream = runCatching { assets.open(name) }.getOrNull() ?: return@forEach
                return stream.use { fromStream(it) }
            }
            throw FileNotFoundException(ASSET_NAMES.joinToString(prefix = "none of ", separator = ", "))
        }

        /** Accepts the compressed catalogue and the plain text one alike. */
        fun fromStream(stream: InputStream): DomainBlocklist {
            val buffered = BufferedInputStream(stream)
            val body = if (isCompressed(buffered)) GZIPInputStream(buffered) else buffered
            // The lines are consumed one at a time: the catalogue holds tens of thousands
            // of names, and the reader keeps only what it needs before the byte table is built.
            return body.bufferedReader().use { reader -> fromLines(reader.lineSequence()) }
        }

        private fun isCompressed(stream: BufferedInputStream): Boolean {
            stream.mark(MAGIC.size)
            val head = ByteArray(MAGIC.size)
            val read = stream.read(head)
            stream.reset()
            return read == MAGIC.size && head.contentEquals(MAGIC)
        }

        private val MAGIC = byteArrayOf(0x1F, 0x8B.toByte())
        private val ASSET_NAMES = listOf("blocked-domains.txt", "blocked-domains.txt.gz")

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
