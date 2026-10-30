package com.kaan.play.data.net

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress

class Ipv4FirstDnsTest {
    private val v6a = InetAddress.getByAddress(ByteArray(16) { if (it == 15) 1 else 0x20 })
    private val v6b = InetAddress.getByAddress(ByteArray(16) { if (it == 15) 2 else 0x20 })
    private val v4a = InetAddress.getByAddress(byteArrayOf(93.toByte(), 184.toByte(), 216.toByte(), 34))
    private val v4b = InetAddress.getByAddress(byteArrayOf(93.toByte(), 184.toByte(), 216.toByte(), 35))

    @Test
    fun `IPv4 onde, IPv6 yedek olarak kalir`() {
        val ordered = Ipv4FirstDns.order(listOf(v6a, v4a, v6b, v4b))
        assertEquals(listOf(v4a, v4b, v6a, v6b), ordered)
        assertTrue(ordered[0] is Inet4Address)
        assertTrue(ordered.last() is Inet6Address)
    }

    @Test
    fun `adres silinmez, bos liste bos kalir`() {
        assertEquals(4, Ipv4FirstDns.order(listOf(v6a, v4a, v6b, v4b)).size)
        assertEquals(emptyList<InetAddress>(), Ipv4FirstDns.order(emptyList()))
        assertEquals(listOf(v6a), Ipv4FirstDns.order(listOf(v6a)))
    }
}
