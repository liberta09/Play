package com.kaan.play.data.plugin

import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.time.Duration.Companion.seconds

class PluginRunBudgetTest {
    @Test
    fun `kisa kaynak ayari eklentiye en az 25 sn tanir`() {
        assertEquals(25.seconds, PluginStreamProvider.runBudget(10.seconds))
        assertEquals(25.seconds, PluginStreamProvider.runBudget(15.seconds))
        assertEquals(30.seconds, PluginStreamProvider.runBudget(30.seconds))
    }

    @Test
    fun `daha uzun kullanici ayari korunur`() {
        assertEquals(60.seconds, PluginStreamProvider.runBudget(60.seconds))
    }
}
