package com.kaan.play.data.cloudstream

import android.content.Context
import android.content.ContextWrapper
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.plugins.BasePlugin
import com.lagradost.cloudstream3.plugins.Plugin
import org.junit.Assert.*
import org.junit.Test

class CsPluginSettingsTest {
    @Test fun callbackAssignedDuringLoadSurvivesApiResult() {
        var received: Context? = null
        val plugin = object : Plugin() {
            override fun load() { openSettings = { received = it } }
        }
        plugin.load()
        val api = object : MainAPI() {}
        val result = CsApiResult.Success(listOf(api), nativeSettingsCallback(plugin))
        assertEquals(listOf(api), result.apisOrEmpty())
        val activityContext = ContextWrapper(null)
        requireNotNull(result.openSettings)(activityContext)
        assertSame(activityContext, received)
    }

    @Test fun settingsAreNotInventedForPluginsWithoutCallback() {
        assertNull(nativeSettingsCallback(object : Plugin() {}))
        assertNull(nativeSettingsCallback(object : BasePlugin() {}))
        assertNull(CsApiResult.Success(emptyList()).openSettings)
    }

    @Test fun loadedPluginsKeepTheirOwnSettingsCallback() {
        var first = 0; var second = 0
        val a = object : Plugin() {}.apply { openSettings = { first++ } }
        val b = object : Plugin() {}.apply { openSettings = { second++ } }
        val fromA = nativeSettingsCallback(a)
        val fromB = nativeSettingsCallback(b)
        requireNotNull(fromA)(ContextWrapper(null))
        assertEquals(1, first); assertEquals(0, second)
        requireNotNull(fromB)(ContextWrapper(null))
        assertEquals(1, first); assertEquals(1, second)
    }
}
