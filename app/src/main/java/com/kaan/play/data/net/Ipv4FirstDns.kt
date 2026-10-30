package com.kaan.play.data.net

import okhttp3.Dns
import java.net.Inet4Address
import java.net.InetAddress

/**
 * Oynatma isteklerinde IPv4 adreslerini IPv6'dan önce dener; IPv6 yedek olarak listede KALIR (hiçbir adres silinmez).
 *
 * Neden: bazı CDN'lerde IPv6 adresi yanıt vermeyip bağlantı zaman aşımına kadar bekletiyor; açılış saniyelerce gecikiyor ya da
 * kaynak "açılmadı" sayılıyor. IPv4 önce denenince sorun yaşayan ağlarda ilk bağlantı hızlanır; çift yığınlı sağlıklı ağlarda
 * fark yoktur (davranış Nuvio'dan örnek alındı, kod kopyalanmadı).
 * Yalnızca sıralama değişir; adres denetimi (PlayNetworkGuard) ayrıca aynen çalışır.
 */
object Ipv4FirstDns : Dns {
    override fun lookup(hostname: String): List<InetAddress> = order(Dns.SYSTEM.lookup(hostname))

    /** IPv4'ler önce; kendi aralarında ve IPv6'lar arasında özgün sıra korunur. */
    fun order(addresses: List<InetAddress>): List<InetAddress> {
        val (v4, other) = addresses.partition { it is Inet4Address }
        return v4 + other
    }
}
