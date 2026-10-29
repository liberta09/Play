package com.kaan.play.data.sync

import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

/** Aynı Wi‑Fi ağındaki Play senkronizasyon sunucularını IP girmeden bulur. */
object LocalSyncDiscovery {
    const val DISCOVERY_PORT = 43822
    private const val REQUEST = "PLAY_SYNC_DISCOVER"
    private const val RESPONSE = "PLAY_SYNC_HERE"

    fun serveOnce(socket: DatagramSocket): Boolean {
        val buffer = ByteArray(64)
        val packet = DatagramPacket(buffer, buffer.size)
        socket.receive(packet)
        val message = String(packet.data, 0, packet.length, Charsets.UTF_8)
        if (message != REQUEST) return false
        val response = RESPONSE.toByteArray(Charsets.UTF_8)
        socket.send(DatagramPacket(response, response.size, packet.address, packet.port))
        return true
    }

    fun discover(timeoutMs: Int = 1_500): List<String> {
        val found = linkedSetOf<String>()
        DatagramSocket().use { socket ->
            socket.broadcast = true
            socket.soTimeout = 250
            val request = REQUEST.toByteArray(Charsets.UTF_8)
            val broadcast = DatagramPacket(request, request.size, InetAddress.getByName("255.255.255.255"), DISCOVERY_PORT)
            socket.send(broadcast)
            val deadline = System.currentTimeMillis() + timeoutMs
            while (System.currentTimeMillis() < deadline) {
                try {
                    val buffer = ByteArray(64)
                    val packet = DatagramPacket(buffer, buffer.size)
                    socket.receive(packet)
                    if (String(packet.data, 0, packet.length, Charsets.UTF_8) == RESPONSE) {
                        found += packet.address.hostAddress.orEmpty()
                    }
                } catch (_: java.net.SocketTimeoutException) {
                    // Kısa aralıklarla cevap beklemeye devam et.
                }
            }
        }
        return found.filter { it.isNotBlank() }
    }
}
