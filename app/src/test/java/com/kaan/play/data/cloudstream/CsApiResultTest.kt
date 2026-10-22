package com.kaan.play.data.cloudstream

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.InvocationTargetException

class CsApiResultTest {

    @Test
    fun `noSuchMethodError uyumsuz kutuphane olarak siniflandirilir`() {
        val error = NoSuchMethodError("com.lagradost.cloudstream3.MainAPI.getMainPage")
        val result = classifyDexError(error, "TrProvider")
        assertTrue(result is CsApiResult.IncompatibleLibrary)
        val incompatible = result as CsApiResult.IncompatibleLibrary
        assertEquals("NoSuchMethodError", incompatible.causeClass)
        assertEquals("TrProvider", incompatible.pluginId)
    }

    @Test
    fun `noClassDefFoundError uyumsuz kutuphane olarak siniflandirilir`() {
        val error = NoClassDefFoundError("com.lagradost.cloudstream3.NiceHttp")
        val result = classifyDexError(error, "TrProvider")
        assertTrue(result is CsApiResult.IncompatibleLibrary)
        val incompatible = result as CsApiResult.IncompatibleLibrary
        assertEquals("NoClassDefFoundError", incompatible.causeClass)
        assertEquals("TrProvider", incompatible.pluginId)
    }

    @Test
    fun `abstractMethodError uyumsuz kutuphane olarak siniflandirilir`() {
        val error = object : AbstractMethodError() {}
        val result = classifyDexError(error, "TrProvider")
        assertTrue(result is CsApiResult.IncompatibleLibrary)
        assertEquals("TrProvider", (result as CsApiResult.IncompatibleLibrary).pluginId)
    }

    @Test
    fun `linkageError turevleri uyumsuz kutuphane olarak siniflandirilir`() {
        val error = VerifyError("Bad type")
        val result = classifyDexError(error, "TrProvider")
        assertTrue(result is CsApiResult.IncompatibleLibrary)
        assertEquals("VerifyError", (result as CsApiResult.IncompatibleLibrary).causeClass)
    }

    @Test
    fun `reflection ile sarilmis hata kok nedene gore siniflandirilir`() {
        val target = NoSuchMethodError("missing method")
        val wrapper = InvocationTargetException(target)
        val result = classifyDexError(wrapper, "TrProvider")
        assertTrue(result is CsApiResult.IncompatibleLibrary)
        assertEquals("NoSuchMethodError", (result as CsApiResult.IncompatibleLibrary).causeClass)
    }

    @Test
    fun `diger istisnalar loadFailed olarak siniflandirilir ve hassas veri icermez`() {
        val secretUrl = "https://private.repo.com/token=secret123"
        val error = RuntimeException("Ağ hatası: $secretUrl")
        val result = classifyDexError(error, "TrProvider")

        assertTrue(result is CsApiResult.LoadFailed)
        val loadFailed = result as CsApiResult.LoadFailed
        assertEquals("RuntimeException", loadFailed.causeClass)
        assertEquals("TrProvider", loadFailed.pluginId)

        val log = formatCsLog(loadFailed)
        assertEquals("TrProvider: LoadFailed(RuntimeException)", log)
        assertFalse("Günlük adresi/belirteci içermemeli", log.contains(secretUrl))
        assertFalse("Günlük token içermemeli", log.contains("secret123"))
    }

    @Test
    fun `formatCsLog tum sebep turlerini tek satirda formatlar`() {
        assertEquals("row#42: PluginRowMissing", formatCsLog(CsApiResult.PluginRowMissing(42L)))
        assertEquals("TrProvider: DexFileMissing", formatCsLog(CsApiResult.DexFileMissing("TrProvider")))
        assertEquals(
            "TrProvider: IncompatibleLibrary(NoSuchMethodError)",
            formatCsLog(CsApiResult.IncompatibleLibrary("NoSuchMethodError", "TrProvider")),
        )
        assertEquals("TrProvider: NoApiRegistered", formatCsLog(CsApiResult.NoApiRegistered("TrProvider")))
    }

    @Test
    fun `apisOrEmpty basarida listeyi basarisizlikta bos listeyi doner`() {
        val success = CsApiResult.Success(emptyList())
        assertEquals(emptyList<Any>(), success.apisOrEmpty())

        val missing = CsApiResult.DexFileMissing("TrProvider")
        assertEquals(emptyList<Any>(), missing.apisOrEmpty())
    }
}
