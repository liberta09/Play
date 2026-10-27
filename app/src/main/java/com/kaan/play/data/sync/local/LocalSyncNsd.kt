package com.kaan.play.data.sync.local

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.net.InetAddress
import kotlin.coroutines.resume

/**
 * Android'e özgü bulma parçaları: Wi-Fi yayın paketleri için `WifiManager.MulticastLock` ve UDP bulunamazsa yedek
 * olarak NsdManager (`_playsync._tcp`) kaydı/araması. Adresler günlüğe yazılmaz.
 */
class LocalSyncNsd(context: Context) {

    private val appContext = context.applicationContext
    private val nsd: NsdManager? = appContext.getSystemService(NsdManager::class.java)
    private val wifi: WifiManager? = appContext.getSystemService(WifiManager::class.java)
    private var lock: WifiManager.MulticastLock? = null
    private var registration: NsdManager.RegistrationListener? = null

    @Synchronized
    fun acquireMulticast() {
        if (lock?.isHeld == true) return
        lock = wifi?.createMulticastLock("PlaySync")?.apply {
            setReferenceCounted(false)
            runCatching { acquire() }
        }
    }

    @Synchronized
    fun releaseMulticast() {
        lock?.let { if (it.isHeld) runCatching { it.release() } }
        lock = null
    }

    /** Sunucu açıkken hizmeti duyurur. */
    @Synchronized
    fun register(port: Int) {
        val manager = nsd ?: return
        if (registration != null) return
        val info = NsdServiceInfo().apply {
            serviceName = "Play-" + Build.MODEL.filter { it.isLetterOrDigit() }.take(20)
            serviceType = LocalSyncProtocol.NSD_TYPE
            setPort(port)
        }
        val listener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(serviceInfo: NsdServiceInfo) = Unit
            override fun onRegistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) = Unit
            override fun onServiceUnregistered(serviceInfo: NsdServiceInfo) = Unit
            override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) = Unit
        }
        registration = listener
        runCatching { manager.registerService(info, NsdManager.PROTOCOL_DNS_SD, listener) }
            .onFailure { registration = null }
    }

    @Synchronized
    fun unregister() {
        val listener = registration ?: return
        registration = null
        runCatching { nsd?.unregisterService(listener) }
    }

    /** [timeoutMs] boyunca `_playsync._tcp` hizmetlerini arar ve çözer; özel ağdaki adresleri döner. */
    suspend fun discover(timeoutMs: Long, ownAddresses: Set<InetAddress>): List<InetAddress> {
        val manager = nsd ?: return emptyList()
        val found = mutableListOf<NsdServiceInfo>()
        val listener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) = Unit
            override fun onDiscoveryStopped(serviceType: String) = Unit
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) = Unit
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) = Unit
            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                synchronized(found) { found += serviceInfo }
            }
            override fun onServiceLost(serviceInfo: NsdServiceInfo) = Unit
        }
        if (runCatching { manager.discoverServices(LocalSyncProtocol.NSD_TYPE, NsdManager.PROTOCOL_DNS_SD, listener) }.isFailure) {
            return emptyList()
        }
        try {
            delay(timeoutMs)
        } finally {
            runCatching { manager.stopServiceDiscovery(listener) }
        }
        val services = synchronized(found) { found.toList() }
        val addresses = LinkedHashSet<InetAddress>()
        for (service in services) {
            val host = withTimeoutOrNull(RESOLVE_TIMEOUT_MS) { resolve(manager, service) } ?: continue
            if (host !in ownAddresses && LocalNetwork.isPrivateLan(host)) addresses += host
        }
        return addresses.toList()
    }

    @Suppress("DEPRECATION") // resolveService/host: API 34 öncesiyle uyumlu tek yol.
    private suspend fun resolve(manager: NsdManager, service: NsdServiceInfo): InetAddress? =
        suspendCancellableCoroutine { cont ->
            val ok = runCatching {
                manager.resolveService(service, object : NsdManager.ResolveListener {
                    override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                        if (cont.isActive) cont.resume(null)
                    }

                    override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                        if (cont.isActive) cont.resume(serviceInfo.host)
                    }
                })
            }.isSuccess
            if (!ok && cont.isActive) cont.resume(null)
        }

    private companion object {
        const val RESOLVE_TIMEOUT_MS = 3_000L
    }
}
