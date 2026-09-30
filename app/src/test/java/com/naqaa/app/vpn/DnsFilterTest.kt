package com.naqaa.app.vpn

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The pieces the local filter is built from: name parsing and building, the two allowed
 * safe search hosts, the domain list, and the answer cache.
 *
 * The blocklist is checked with subdomains because a list that only matches exact hosts
 * would let the mobile variants through.
 */
class DnsFilterTest {

    private fun query(name: String, type: Int = DnsMessage.TYPE_A): ByteArray {
        val header = ByteArray(12)
        DnsMessage.put16(header, 0, 0x1234)
        DnsMessage.put16(header, 2, DnsMessage.FLAG_RECURSION_DESIRED)
        DnsMessage.put16(header, 4, 1)
        val encoded = DnsMessage.encodeName(name) ?: error("name was refused")
        val packet = header + encoded + ByteArray(4)
        DnsMessage.put16(packet, 12 + encoded.size, type)
        DnsMessage.put16(packet, 14 + encoded.size, 1)
        return packet
    }

    @Test
    fun `a name survives a round trip`() {
        val packet = query("www.example.com")
        val question = DnsMessage.question(packet)
        assertNotNull(question)
        assertEquals("www.example.com", question!!.name)
        assertEquals(DnsMessage.TYPE_A, question.type)
    }

    @Test
    fun `an overlong label is refused`() {
        val long = "a".repeat(64)
        assertNull(DnsMessage.encodeName("$long.example.com"))
    }

    @Test
    fun `an empty name is refused`() {
        assertNull(DnsMessage.encodeName(""))
    }

    @Test
    fun `a response answer carries the address back`() {
        val packet = query("example.com")
        val address = byteArrayOf(93.toByte(), 184.toByte(), 216.toByte(), 34.toByte())
        val answer = DnsMessage.addressAnswer(packet, DnsMessage.TYPE_A, listOf(address), 60)
        assertNotNull(answer)
        assertTrue(DnsMessage.u16(answer!!, 2) and DnsMessage.FLAG_RESPONSE != 0)
        val records = DnsMessage.records(answer, DnsMessage.TYPE_A)
        assertEquals(1, records.addresses.size)
        assertArrayEquals(address, records.addresses[0])
        assertEquals(60, records.minimumTtl)
    }

    @Test
    fun `an error response keeps the question and sets the code`() {
        val packet = query("blocked.example")
        val answer = DnsMessage.error(packet, DnsMessage.RCODE_NXDOMAIN)
        assertNotNull(answer)
        assertEquals(DnsMessage.RCODE_NXDOMAIN, DnsMessage.u16(answer!!, 2) and 0x000F)
        assertTrue(DnsMessage.question(answer) == null)
        assertEquals("blocked.example", DnsMessage.readQuestion(answer)?.name)
    }

    @Test
    fun `a rewrite keeps the question name and the transaction`() {
        val packet = query("www.google.com")
        val rewritten = DnsMessage.withQuestionName(packet, "forcesafesearch.google.com")
        assertNotNull(rewritten)
        assertEquals(DnsMessage.u16(packet, 0), DnsMessage.u16(rewritten!!, 0))
        assertEquals("forcesafesearch.google.com", DnsMessage.question(rewritten)?.name)
    }

    @Test
    fun `safe search rewrites only the search hosts`() {
        assertEquals("forcesafesearch.google.com", SafeSearch.rewrite("www.google.com"))
        assertEquals("forcesafesearch.google.com", SafeSearch.rewrite("google.co.uk"))
        assertEquals("safe.duckduckgo.com", SafeSearch.rewrite("duckduckgo.com"))
        assertNull(SafeSearch.rewrite("example.com"))
        assertNull(SafeSearch.rewrite("notgoogle.com"))
    }

    @Test
    fun `the blocklist matches a domain and its subdomains`() {
        val list = DomainBlocklist.fromLines(
            sequenceOf(
                "# comment",
                "example.com",
                "tracker.example.net",
                "",
                "ads.test"
            )
        )
        assertTrue(list.blocks("example.com"))
        assertTrue(list.blocks("www.example.com"))
        assertTrue(list.blocks("a.b.example.com"))
        assertTrue(list.blocks("tracker.example.net"))
        assertTrue(list.blocks("ads.test"))
        assertTrue(!list.blocks("notexample.com"))
        assertTrue(!list.blocks("example.com.evil.net"))
    }

    @Test
    fun `the cache honours the ttl and forgets expired answers`() {
        val cache = DnsCache(limit = 2)
        val packet = query("cached.example")
        val key = DnsCache.key(packet)
        assertNotNull(key)
        cache.put(key!!, packet, nowMillis = 1_000, ttlSeconds = 60)
        assertNotNull(cache.get(key, nowMillis = 30_000))
        assertNull(cache.get(key, nowMillis = 62_000))
    }

    @Test
    fun `the cache clamps an extreme ttl`() {
        val cache = DnsCache(limit = 4)
        val packet = query("ttl.example")
        val key = DnsCache.key(packet)!!
        cache.put(key, packet, nowMillis = 0, ttlSeconds = 10)
        assertNotNull("a short ttl is raised to the floor", cache.get(key, nowMillis = 29_000))
        assertNull(cache.get(key, nowMillis = 31_000))
    }

    @Test
    fun `a packet without a question is not parsed`() {
        val empty = ByteArray(12)
        assertNull(DnsMessage.question(empty))
    }
}
