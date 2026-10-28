package com.kaan.play.data.sync.local

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.SocketException
import java.net.SocketTimeoutException
import java.util.concurrent.atomic.AtomicBoolean

/**
 * UDP bulma (Android'den bağımsız). Sunucu tarafı [Responder]: [LocalSyncProtocol.DISCOVERY_PORT]'ta dinler,
 * özel ağdan gelen "PLAY_SYNC_DISCOVER"a "PLAY_SYNC_HERE" ile cevap verir. İstemci tarafı [discover]: yayın adreslerine
 * "PLAY_SYNC_DISCOVER" gönderir, cevap veren adresleri toplar. (Android'de Wi-Fi yayın paketleri için
 * `WifiManager.MulticastLock` ayrıca alınır; NSD yedeği `LocalSyncNsd`.)
 */
object LocalSyncDiscovery {

    class Responder(
        private val port: Int = LocalSyncProtocol.DISCOVERY_PORT,
        private val addressAllowed: (InetAddress) -> Boolean = LocalNetwork::isPrivateLan,
    ) {
        private val running = AtomicBoolean(false)
        private var socket: DatagramSocket? = null

        /** Dinlenen port döner (testte 0 → rastgele). */
        @Synchronized
        fun start(): Int {
            check(!running.get()) { "already running" }
            val s = DatagramSocket(null)
            s.reuseAddress = true
            s.broadcast = true
            s.bind(InetSocketAddress(port))
            socket = s
            running.set(true)
            Thread({ loop(s) }, "PlaySyncResponder").apply { isDaemon = true }.start()
            return s.localPort
        }

        fun stop() {
            if (!running.compareAndSet(true, false)) return
            socket?.close()
        }

        private fun loop(s: DatagramSocket) {
            val buf = ByteArray(256)
            while (running.get()) {
                val packet = DatagramPacket(buf, buf.size)
                try {
                    s.receive(packet)
                } catch (_: SocketException) {
                    break
                } catch (_: Exception) {
                    continue
                }
                val text = String(packet.data, packet.offset, packet.length, Charsets.UTF_8).trim()
                if (text != LocalSyncProtocol.DISCOVER || !addressAllowed(packet.address)) continue
                val reply = LocalSyncProtocol.HERE.toByteArray(Charsets.UTF_8)
                runCatching { s.send(DatagramPacket(reply, reply.size, packet.address, packet.port)) }
            }
        }
    }

    /**
     * [targets] adreslerine (yayın adresleri) sorar, [timeoutMs] boyunca gelen "PLAY_SYNC_HERE" cevaplarının kaynak
     * adreslerini döner. [ownAddresses] (bu cihazın kendi sunucusu) ve özel ağ dışı adresler atlanır.
     */
    suspend fun discover(
        targets: List<InetAddress>,
        port: Int = LocalSyncProtocol.DISCOVERY_PORT,
        timeoutMs: Int = 2_500,
        ownAddresses: Set<InetAddress> = emptySet(),
        addressAllowed: (InetAddress) -> Boolean = LocalNetwork::isPrivateLan,
    ): List<InetAddress> = withContext(Dispatchers.IO) {
        val found = LinkedHashSet<InetAddress>()
        DatagramSocket().use { s ->
            s.broadcast = true
            val ask = LocalSyncProtocol.DISCOVER.toByteArray(Charsets.UTF_8)
            for (target in targets) runCatching { s.send(DatagramPacket(ask, ask.size, target, port)) }
            val deadline = System.currentTimeMillis() + timeoutMs
            val buf = ByteArray(256)
            while (true) {
                val left = deadline - System.currentTimeMillis()
                if (left <= 0) break
                s.soTimeout = left.toInt().coerceAtLeast(1)
                val packet = DatagramPacket(buf, buf.size)
                try {
                    s.receive(packet)
                } catch (_: SocketTimeoutException) {
                    break
                }
                val text = String(packet.data, packet.offset, packet.length, Charsets.UTF_8).trim()
                if (text == LocalSyncProtocol.HERE && packet.address !in ownAddresses && addressAllowed(packet.address)) {
                    found += packet.address
                }
            }
        }
        found.toList()
    }

    /** Bu cihazın özel IPv4 adresleri ve bunların yayın adresleri (+ 255.255.255.255). */
    fun localInterfaces(): Pair<Set<InetAddress>, List<InetAddress>> {
        val own = LinkedHashSet<InetAddress>()
        val broadcasts = LinkedHashSet<InetAddress>()
        try {
            for (nif in NetworkInterface.getNetworkInterfaces()?.toList().orEmpty()) {
                if (!nif.isUp || nif.isLoopback) continue
                for (ia in nif.interfaceAddresses) {
                    val addr = ia.address as? Inet4Address ?: continue
                    if (!LocalNetwork.isPrivateLan(addr)) continue
                    own += addr
                    ia.broadcast?.let { broadcasts += it }
                }
            }
        } catch (_: SocketException) {
            // arayüz listesi alınamadı: yalnızca genel yayın
        }
        broadcasts += InetAddress.getByAddress(byteArrayOf(-1, -1, -1, -1))
        return own to broadcasts.toList()
    }
}
