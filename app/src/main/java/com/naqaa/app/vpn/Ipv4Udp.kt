package com.naqaa.app.vpn

/**
 * IPv4 and UDP framing for the tunnel. Only the subset used by DNS traffic is handled:
 * unfragmented IPv4 datagrams carrying UDP. Everything else is dropped by the caller,
 * which keeps the tunnel limited to name resolution.
 */
object Ipv4Udp {

    data class Datagram(
        val clientAddress: ByteArray,
        val clientPort: Int,
        val destinationPort: Int,
        val payload: ByteArray
    )

    fun parse(packet: ByteArray, length: Int): Datagram? {
        if (length < 28 || length > packet.size) return null
        val versionAndHeader = packet[0].toInt() and 0xFF
        if (versionAndHeader ushr 4 != 4) return null
        val headerLength = (versionAndHeader and 0x0F) * 4
        if (headerLength < 20 || headerLength + 8 > length) return null
        if (DnsMessage.u16(packet, 2) != length) return null
        if (DnsMessage.u16(packet, 6) and 0x3FFF != 0) return null
        if (packet[9].toInt() and 0xFF != 17) return null
        val udpLength = DnsMessage.u16(packet, headerLength + 4)
        if (udpLength < 8 || headerLength + udpLength != length) return null
        val payload = packet.copyOfRange(headerLength + 8, length)
        if (payload.size < 12) return null
        return Datagram(
            clientAddress = packet.copyOfRange(12, 16),
            clientPort = DnsMessage.u16(packet, headerLength),
            destinationPort = DnsMessage.u16(packet, headerLength + 2),
            payload = payload
        )
    }

    /** Wraps a DNS response in a UDP datagram addressed back to the asking client. */
    fun reply(request: ByteArray, dns: ByteArray): ByteArray {
        val headerLength = (request[0].toInt() and 0x0F) * 4
        val clientAddress = request.copyOfRange(12, 16)
        val serverAddress = request.copyOfRange(16, 20)
        val clientPort = DnsMessage.u16(request, headerLength)
        val serverPort = DnsMessage.u16(request, headerLength + 2)
        val response = ByteArray(20 + 8 + dns.size)
        response[0] = 0x45
        DnsMessage.put16(response, 2, response.size)
        DnsMessage.put16(response, 4, 0)
        DnsMessage.put16(response, 6, 0x4000)
        response[8] = 64
        response[9] = 17
        serverAddress.copyInto(response, 12)
        clientAddress.copyInto(response, 16)
        DnsMessage.put16(response, 10, checksum(response, 0, 20, 0))
        DnsMessage.put16(response, 20, serverPort)
        DnsMessage.put16(response, 22, clientPort)
        DnsMessage.put16(response, 24, 8 + dns.size)
        DnsMessage.put16(response, 26, 0)
        dns.copyInto(response, 28)
        val pseudo = pseudoHeader(serverAddress, clientAddress, 17, 8 + dns.size)
        DnsMessage.put16(response, 26, checksum(response, 20, 8 + dns.size, pseudo))
        return response
    }

    /** One's complement checksum; a zero result is transmitted as all ones. */
    private fun checksum(packet: ByteArray, offset: Int, length: Int, initial: Int): Int {
        var sum = initial
        var index = offset
        val end = offset + length
        while (index + 1 < end) {
            sum += DnsMessage.u16(packet, index)
            index += 2
        }
        if (index < end) sum += (packet[index].toInt() and 0xFF) shl 8
        while (sum ushr 16 != 0) sum = (sum and 0xFFFF) + (sum ushr 16)
        val result = sum.inv() and 0xFFFF
        return if (result == 0) 0xFFFF else result
    }

    private fun pseudoHeader(source: ByteArray, destination: ByteArray, protocol: Int, length: Int): Int {
        var sum = 0
        var index = 0
        while (index + 1 < source.size) {
            sum += DnsMessage.u16(source, index)
            index += 2
        }
        index = 0
        while (index + 1 < destination.size) {
            sum += DnsMessage.u16(destination, index)
            index += 2
        }
        sum += protocol
        sum += length
        return sum
    }
}
