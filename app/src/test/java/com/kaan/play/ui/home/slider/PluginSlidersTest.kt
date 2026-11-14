package com.kaan.play.ui.home.slider

import com.kaan.play.data.provider.CatalogItem
import com.kaan.play.data.provider.CatalogRow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PluginSlidersTest {
    // Site adlarıyla eklenti adları (görünen ad ve iç ad biçimleri).
    private val published = mapOf(
        "anyshort_film_strip" to listOf("AnyShort", "anyshort"),
        "directdrama_cinema_screen" to listOf("DirectDrama", "Direct Drama"),
        "dramadizilerim_bookshelf" to listOf("DramaDizilerim", "Drama Dizilerim"),
        "dramadunyam_globe" to listOf("DramaDünyam", "DramaDunyam"),
        "dramacix_card_deck" to listOf("DramaCix", "Dramacix"),
        "fulldrama_cover_flow" to listOf("FullDrama", "Full Drama"),
        "izlegec_snapshots" to listOf("İzleGeç", "IzleGec"),
        "liderdrama_spotlight" to listOf("LiderDrama", "Lider Drama"),
        "lidershort_vertical_feed" to listOf("LiderShort", "Lider Short"),
        "narto_accordion" to listOf("Narto", "NARTO"),
        "verticalshorts_cube" to listOf("VerticalShorts", "Vertical Shorts"),
        "minidiziizle_open_matchbox" to listOf("MiniDiziİzle", "MiniDiziIzle"),
        "dramavakti_matchbox" to listOf("DramaVakti", "Drama Vakti"),
        "dramatr_warp" to listOf("DramaTR", "Drama TR"),
        "shortizle_fan" to listOf("Shortİzle", "Shortizle"),
    )

    private fun items(n: Int, p: String) = List(n) { CatalogItem(id = "$p$it", title = "T$it", providerId = p) }
    private fun row(provider: String, n: Int) = CatalogRow(id = provider, title = "R", items = items(n, provider), providerId = provider, providerName = provider)

    @Test fun everyPublishedPluginHasExactlyOneEntry() {
        assertEquals(15, PluginSliders.entries.size)
        assertEquals(15, PluginSliders.entries.map { it.key }.toSet().size)
        for ((key, names) in published) for (name in names) {
            val hits = PluginSliders.entries.filter { it.matches(name) }.map { it.key }
            assertEquals("$name → $hits", listOf(key), hits)
        }
    }

    @Test fun lookalikeNamesDoNotMatch() {
        val mini = PluginSliders.entries.first { it.key == "minidiziizle_open_matchbox" }
        // Benzer ama başka adlar (ek, sonek, eksik ad) site stilini almaz.
        for (n in listOf("DramaVaktiMp4", "MiniDiziİzle Plus", "MiniDizi", "LiderShorts2", "Cix", "Dizilerim", "TR")) {
            assertTrue(n, PluginSliders.entries.none { it.matches(n) && it.key != "shortizle_fan" })
        }
        assertFalse(mini.matches("MiniDiziİzleTV"))
        assertEquals("minidiziizle", PluginSliders.key("MiniDiziİzle"))
        assertEquals("izlegec", PluginSliders.key("İzleGeç"))
        assertEquals("dramadunyam", PluginSliders.key("DramaDünyam"))
    }

    @Test fun everyStyleHasCode() {
        // Yer tutucu kalmadı: her stilin kodu var.
        assertEquals(emptyList<String>(), PluginSliders.entries.filter { it.content == null }.map { it.key })
        val narto = "Narto"
        assertEquals("narto_accordion", PluginSliders.pick(listOf(narto), listOf(row(narto, 10)))?.slider?.key)
    }

    @Test fun firstRegistryEntryWinsLikeBefore() {
        val fan = "Shortİzle"
        val tr = "DramaTR"
        val vakti = "DramaVakti"
        val mini = "MiniDiziİzle"
        val rows = listOf(row(mini, 12), row(vakti, 12), row(tr, 12), row(fan, 12))
        assertEquals("shortizle_fan", PluginSliders.pick(listOf(mini, vakti, tr, fan), rows)?.slider?.key)
        assertEquals("dramatr_warp", PluginSliders.pick(listOf(mini, vakti, tr), rows)?.slider?.key)
        assertEquals("dramavakti_matchbox", PluginSliders.pick(listOf(mini, vakti), rows)?.slider?.key)
        assertEquals("minidiziizle_open_matchbox", PluginSliders.pick(listOf(mini), rows)?.slider?.key)
    }

    @Test fun cardsAreFirstFilledRowCappedAtTenAndAtLeastThree() {
        val mini = "MiniDiziİzle"
        val rows = listOf(row("Başka", 12), row(mini, 0), row(mini, 14), row(mini, 5))
        val pick = assertNotNullPick(PluginSliders.pick(listOf(mini), rows))
        assertEquals(10, pick.items.size)
        assertEquals("${mini}0", pick.items.first().id)
        assertNull(PluginSliders.pick(listOf(mini), listOf(row(mini, 2))))
        assertEquals(3, PluginSliders.pick(listOf(mini), listOf(row(mini, 3)))?.items?.size)
        // Seçili değilse satırı olsa bile gösterilmez.
        assertNull(PluginSliders.pick(listOf("Narto"), listOf(row(mini, 10))))
    }

    @Test fun styledPluginWithoutRowsFallsThroughButShortRowBlocksAsBefore() {
        val fan = "Shortİzle"
        val mini = "MiniDiziİzle"
        // Yelpazenin dolu satırı yoksa sıradaki stil.
        assertEquals("minidiziizle_open_matchbox", PluginSliders.pick(listOf(fan, mini), listOf(row(fan, 0), row(mini, 6)))?.slider?.key)
        // Yelpazenin satırı 3'ten azsa (eski davranış) hiçbir sürgü yok.
        assertNull(PluginSliders.pick(listOf(fan, mini), listOf(row(fan, 2), row(mini, 6))))
    }

    private fun assertNotNullPick(p: SliderPick?): SliderPick { assertNotNull(p); return p!! }
}
