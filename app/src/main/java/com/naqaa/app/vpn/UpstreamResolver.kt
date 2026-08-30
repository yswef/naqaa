package com.naqaa.app.vpn

import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

/**
 * Sends allowed queries to the resolver the device already uses.
 *
 * Upstream sockets are handed to [VpnService.protect], otherwise the tunnel would route
 * them back into this process. Answers are validated against the question before they are
 * handed to the client so a mismatched or spoofed reply is never forwarded.
 *
 * Only the datagram socket is excluded. A stream socket cannot be excluded through the
 * public interface, and the tunnel carries datagrams only, so a truncated answer is
 * forwarded as it arrived and the client decides what to do next; queries over TCP are
 * blocked at the tunnel instead of being answered wrongly.
 */
class UpstreamResolver(
    private val protect: (DatagramSocket) -> Boolean,
    private val servers: () -> List<InetAddress>,
    private val timeoutMillis: Int = 1500
) {

    fun query(request: ByteArray): ByteArray? {
        val candidates = servers()
        for (server in candidates) {
            val answer = overUdp(server, request)
            if (answer != null && matches(request, answer)) return answer
        }
        return null
    }

    private fun overUdp(server: InetAddress, request: ByteArray): ByteArray? = try {
        DatagramSocket().use { socket ->
            if (!protect(socket)) return null
            socket.soTimeout = timeoutMillis
            socket.send(DatagramPacket(request, request.size, server, PORT))
            val buffer = DatagramPacket(ByteArray(4096), 4096)
            socket.receive(buffer)
            buffer.data.copyOf(buffer.length)
        }
    } catch (_: Exception) {
        null
    }

    companion object {

        const val PORT = 53

        fun matches(request: ByteArray, answer: ByteArray): Boolean {
            if (answer.size < 17) return false
            if (DnsMessage.u16(answer, 0) != DnsMessage.u16(request, 0)) return false
            if (DnsMessage.u16(answer, 2) and 0x8000 == 0) return false
            if (DnsMessage.u16(answer, 4) != 1) return false
            val asked = DnsMessage.readQuestion(request) ?: return false
            val replied = DnsMessage.readQuestion(answer) ?: return false
            return asked.name == replied.name && asked.type == replied.type
        }
    }
}
