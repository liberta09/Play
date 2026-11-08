package com.kaan.play

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class StartupGuardRulesTest {

    private class InMemoryStore : StartupGuardRules.Store {
        private val map = mutableMapOf<String, String>()
        override fun getString(key: String): String? = map[key]
        override fun putString(key: String, value: String) {
            map[key] = value
        }
        override fun remove(key: String) {
            map.remove(key)
        }
    }

    private lateinit var store: InMemoryStore

    @Before
    fun setup() {
        store = InMemoryStore()
    }

    @Test
    fun `ilk basarisizlikta yeniden dene karari uretilir`() {
        val now = 1_000_000L
        val decision = StartupGuardRules.recordFailureAndDecide(store, "MainActivity", now)
        assertEquals(StartupGuardRules.Decision.RETRY, decision)
        assertEquals(1, StartupGuardRules.getRecentFailureCount(store, "MainActivity", now))
    }

    @Test
    fun `ikinci basarisizlikta 10 dakika icinde vazgec karari uretilir`() {
        val t0 = 1_000_000L
        val t1 = t0 + 2 * 60 * 1000L // 2 dakika sonra

        val d1 = StartupGuardRules.recordFailureAndDecide(store, "MainActivity", t0)
        assertEquals(StartupGuardRules.Decision.RETRY, d1)

        val d2 = StartupGuardRules.recordFailureAndDecide(store, "MainActivity", t1)
        assertEquals(StartupGuardRules.Decision.GIVE_UP, d2)
        assertEquals(2, StartupGuardRules.getRecentFailureCount(store, "MainActivity", t1))
    }

    @Test
    fun `ucuncu ve sonraki basarisizliklarda vazgec kalir`() {
        val t0 = 1_000_000L
        StartupGuardRules.recordFailureAndDecide(store, "PlayerActivity", t0)
        StartupGuardRules.recordFailureAndDecide(store, "PlayerActivity", t0 + 1000)
        val d3 = StartupGuardRules.recordFailureAndDecide(store, "PlayerActivity", t0 + 2000)
        assertEquals(StartupGuardRules.Decision.GIVE_UP, d3)
        assertEquals(3, StartupGuardRules.getRecentFailureCount(store, "PlayerActivity", t0 + 2000))
    }

    @Test
    fun `10 dakikadan eski basarisizliklar elenir ve yeni basarisizlik ilk sayilir`() {
        val t0 = 1_000_000L
        val tOld = t0 - (10 * 60 * 1000L + 1) // 10 dakika 1 ms önce

        // Eski başarısızlığı store'a elle yazalım
        store.putString(StartupGuardRules.keyFor("MainActivity"), tOld.toString())

        // Şimdi başarısızlık olsun: eski elenmeli, toplam sayı 1 olmalı -> RETRY
        val decision = StartupGuardRules.recordFailureAndDecide(store, "MainActivity", t0)
        assertEquals(StartupGuardRules.Decision.RETRY, decision)
        assertEquals(1, StartupGuardRules.getRecentFailureCount(store, "MainActivity", t0))
    }

    @Test
    fun `etkinlikler arasinda yalitim saglanir`() {
        val now = 1_000_000L
        // MainActivity 2 kez başarısız olsun
        StartupGuardRules.recordFailureAndDecide(store, "MainActivity", now)
        val dMain2 = StartupGuardRules.recordFailureAndDecide(store, "MainActivity", now + 1000)
        assertEquals(StartupGuardRules.Decision.GIVE_UP, dMain2)

        // PlayerActivity ilk kez başarısız olsun -> RETRY almalı
        val dPlayer1 = StartupGuardRules.recordFailureAndDecide(store, "PlayerActivity", now + 2000)
        assertEquals(StartupGuardRules.Decision.RETRY, dPlayer1)
        assertEquals(1, StartupGuardRules.getRecentFailureCount(store, "PlayerActivity", now + 2000))
    }

    @Test
    fun `basarili acilista gecmis temizlenir`() {
        val now = 1_000_000L
        StartupGuardRules.recordFailureAndDecide(store, "MainActivity", now)
        assertEquals(1, StartupGuardRules.getRecentFailureCount(store, "MainActivity", now))

        StartupGuardRules.recordSuccess(store, "MainActivity")
        assertEquals(0, StartupGuardRules.getRecentFailureCount(store, "MainActivity", now))

        // Temizlendikten sonraki ilk hata tekrar RETRY olmalı
        val decision = StartupGuardRules.recordFailureAndDecide(store, "MainActivity", now + 5000)
        assertEquals(StartupGuardRules.Decision.RETRY, decision)
    }

    @Test
    fun `zaman damgasi ayrirma ve birlestirme dayaniklidir`() {
        assertTrue(StartupGuardRules.parseTimestamps(null).isEmpty())
        assertTrue(StartupGuardRules.parseTimestamps("").isEmpty())
        assertTrue(StartupGuardRules.parseTimestamps("   ").isEmpty())
        assertTrue(StartupGuardRules.parseTimestamps("invalid,abc").isEmpty())

        val parsed = StartupGuardRules.parseTimestamps("100, 200, invalid, 300")
        assertEquals(listOf(100L, 200L, 300L), parsed)

        val serialized = StartupGuardRules.serializeTimestamps(listOf(100L, 200L, 300L))
        assertEquals("100,200,300", serialized)
    }

    @Test
    fun `shouldUseSafeTheme intent ve kayit durumlarini dogru degerlendirir`() {
        assertFalse(StartupGuardRules.shouldUseSafeTheme(intentFlag = false, storedFlag = false))
        assertTrue(StartupGuardRules.shouldUseSafeTheme(intentFlag = true, storedFlag = false))
        assertTrue(StartupGuardRules.shouldUseSafeTheme(intentFlag = false, storedFlag = true))
        assertTrue(StartupGuardRules.shouldUseSafeTheme(intentFlag = true, storedFlag = true))
    }

    @Test
    fun `safe_window kayit ve sorgulama calisir ve recordSuccess ile silinmez`() {
        assertFalse(StartupGuardRules.isSafeWindow(store))

        StartupGuardRules.setSafeWindow(store, true)
        assertTrue(StartupGuardRules.isSafeWindow(store))

        // recordSuccess yalnizca etkinlik gecmisini siler, safe_window kalmalidir
        StartupGuardRules.recordSuccess(store, "MainActivity")
        assertTrue("recordSuccess sonrasinda safe_window korunmali", StartupGuardRules.isSafeWindow(store))

        StartupGuardRules.clearSafeWindow(store)
        assertFalse(StartupGuardRules.isSafeWindow(store))
    }

    @Test
    fun `GIVE_UP kararinda safe_window temizlenir`() {
        StartupGuardRules.setSafeWindow(store, true)
        assertTrue(StartupGuardRules.isSafeWindow(store))

        val now = 1_000_000L
        StartupGuardRules.recordFailureAndDecide(store, "MainActivity", now)
        val d2 = StartupGuardRules.recordFailureAndDecide(store, "MainActivity", now + 1000)
        assertEquals(StartupGuardRules.Decision.GIVE_UP, d2)

        // StartupWindowGuard give_up dalinda clearSafeWindow cagirir
        StartupGuardRules.clearSafeWindow(store)
        assertFalse("GIVE_UP sonrasinda safe_window temizlenmis olmali", StartupGuardRules.isSafeWindow(store))
    }
}
