package com.kaan.play.data.provider

import com.kaan.play.data.addon.AddonManifest
import com.kaan.play.data.addon.ManifestCatalog
import com.kaan.play.data.addon.ManifestResource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderRulesTest {

    private val sampleItems = listOf(
        ProviderItem(id = "cs:1", name = "DiziBox", subtitle = "CloudStream · Turkish", kind = ProviderKind.CloudStream),
        ProviderItem(id = "cs:2", name = "HdFilmCehennemi", subtitle = "CloudStream · Turkish", kind = ProviderKind.CloudStream),
        ProviderItem(id = "nuvio_addon:10", name = "Torrentio", subtitle = "Nuvio · Torrentio", kind = ProviderKind.NuvioAddon),
        ProviderItem(id = "nuvio_plugin:20", name = "VidMoly", subtitle = "Nuvio · Scrapers", kind = ProviderKind.NuvioPlugin),
    )

    @Test
    fun filterByTabWorksCorrectly() {
        val all = ProviderRules.filterByTab(sampleItems, ProviderTab.All)
        assertEquals(4, all.size)

        val cs = ProviderRules.filterByTab(sampleItems, ProviderTab.CloudStream)
        assertEquals(2, cs.size)
        assertTrue(cs.all { it.kind == ProviderKind.CloudStream })

        val nuvio = ProviderRules.filterByTab(sampleItems, ProviderTab.Nuvio)
        assertEquals(2, nuvio.size)
        assertTrue(nuvio.all { it.kind == ProviderKind.NuvioAddon || it.kind == ProviderKind.NuvioPlugin })
    }

    @Test
    fun filterByQueryMatchesCaseInsensitive() {
        val matchDizi = ProviderRules.filterByQuery(sampleItems, "dizi")
        assertEquals(1, matchDizi.size)
        assertEquals("DiziBox", matchDizi.first().name)

        val matchAllWithEmpty = ProviderRules.filterByQuery(sampleItems, "   ")
        assertEquals(4, matchAllWithEmpty.size)

        val noMatch = ProviderRules.filterByQuery(sampleItems, "nonexistent")
        assertTrue(noMatch.isEmpty())
    }

    @Test
    fun pinningBringsItemsToTopAndSortsAlphabetically() {
        // Pin "VidMoly" and "DiziBox"
        val pinned = setOf("nuvio_plugin:20", "cs:1")
        val sorted = ProviderRules.applyPinningAndSort(sampleItems, pinned)

        // Pinned items first: DiziBox (pinned), VidMoly (pinned)
        assertTrue(sorted[0].isPinned)
        assertEquals("DiziBox", sorted[0].name)

        assertTrue(sorted[1].isPinned)
        assertEquals("VidMoly", sorted[1].name)

        // Unpinned items next: HdFilmCehennemi, Torrentio
        assertFalse(sorted[2].isPinned)
        assertEquals("HdFilmCehennemi", sorted[2].name)

        assertFalse(sorted[3].isPinned)
        assertEquals("Torrentio", sorted[3].name)
    }

    @Test
    fun singleSelectBehavior() {
        assertEquals(setOf("cs:1"), ProviderRules.singleSelect("cs:1"))
        assertEquals(emptySet<String>(), ProviderRules.singleSelect(ProviderConstants.ALL_PROVIDERS_ID))
    }

    @Test
    fun multiSelectToggleBehavior() {
        var selected = emptySet<String>()

        // Toggle item 1
        selected = ProviderRules.toggleMultiSelect(selected, "cs:1")
        assertEquals(setOf("cs:1"), selected)

        // Toggle item 2
        selected = ProviderRules.toggleMultiSelect(selected, "nuvio_addon:10")
        assertEquals(setOf("cs:1", "nuvio_addon:10"), selected)

        // Toggle item 1 again (removes it)
        selected = ProviderRules.toggleMultiSelect(selected, "cs:1")
        assertEquals(setOf("nuvio_addon:10"), selected)

        // Selecting ALL_PROVIDERS_ID in multi-select clears/resets
        selected = ProviderRules.toggleMultiSelect(selected, ProviderConstants.ALL_PROVIDERS_ID)
        assertTrue(selected.isEmpty())
    }

    @Test
    fun isAllProvidersCheck() {
        assertTrue(ProviderRules.isAllProviders(emptySet()))
        assertTrue(ProviderRules.isAllProviders(setOf(ProviderConstants.ALL_PROVIDERS_ID)))
        assertFalse(ProviderRules.isAllProviders(setOf("cs:1")))
        assertFalse(ProviderRules.isAllProviders(setOf("cs:1", "nuvio_addon:10")))
    }

    private fun row(title: String, items: Int = 1) = CatalogRow(
        id = title,
        title = title,
        items = List(items) { CatalogItem(id = "i$it", title = "F$it", posterUrl = null, providerId = "nuvio_addon:1") },
        providerId = "nuvio_addon:1",
        providerName = "Play Özel Kataloglar",
    )

    @Test
    fun homeSectionRowsKeepsOnlyRowsWithItems() {
        val result = CatalogResult.Success(listOf(row("Popüler Filmler"), row("Boş", items = 0)))
        assertEquals(listOf("Popüler Filmler"), ProviderRules.homeSectionRows(result).map { it.title })
        assertTrue(ProviderRules.homeSectionRows(CatalogResult.NoCatalogProvided).isEmpty())
    }

    @Test
    fun duplicateTmdbRowsHidesSameNamedRowsTurkishCaseInsensitive() {
        val tmdb = mapOf(
            "trend_movies" to "Trend filmler",
            "popular_movies" to "Popüler filmler",
            "popular_series" to "Popüler  diziler ",
        )
        val shown = listOf("Haftanın Filmleri", "Popüler Filmler", "POPÜLER DİZİLER")
        assertEquals(setOf("popular_movies", "popular_series"), ProviderRules.duplicateTmdbRows(tmdb, shown))
    }

    @Test
    fun duplicateTmdbRowsKeepsTmdbWhenAddonRowsNotShown() {
        val tmdb = mapOf("popular_movies" to "Popüler filmler")
        // Addon rafı yüklenemedi (sunucu kapalı): görünen raf yok, TMDB rafı kalır.
        assertTrue(ProviderRules.duplicateTmdbRows(tmdb, emptyList()).isEmpty())
        // İngilizce arayüzde adlar farklı: gizlenmez.
        assertTrue(ProviderRules.duplicateTmdbRows(mapOf("popular_movies" to "Popular movies"), listOf("Popüler Filmler")).isEmpty())
    }

    // --- Seçici bölümleri (kullanıcı bildirimi 2026-10-21: "Hepsi"de Nuvio sağlayıcıları görünmüyordu) ---

    private val sectionItems = listOf(
        ProviderItem(id = "cs:1", name = "Aardvark", subtitle = "CloudStream", kind = ProviderKind.CloudStream),
        ProviderItem(id = "cs:2", name = "DiziBox", subtitle = "CloudStream", kind = ProviderKind.CloudStream),
        ProviderItem(id = "cs:3", name = "Zeta", subtitle = "CloudStream", kind = ProviderKind.CloudStream, isPinned = true),
        ProviderItem(id = "nuvio_addon:10", name = "Play Özel Kataloglar", subtitle = "Nuvio", kind = ProviderKind.NuvioAddon),
        ProviderItem(id = "nuvio_addon:11", name = "İzlemeli.com Katalog", subtitle = "Nuvio", kind = ProviderKind.NuvioAddon, isPinned = true),
        ProviderItem(id = "nuvio_addon:12", name = "Jasoelys", subtitle = "Nuvio", kind = ProviderKind.NuvioAddon),
    )

    @Test
    fun selectorSectionsPutNuvioFirstThenCloudStreamWithPinnedOnTop() {
        val sections = ProviderRules.selectorSections(sectionItems, ProviderTab.All, "")
        assertEquals(listOf(ProviderSectionKind.Nuvio, ProviderSectionKind.CloudStream), sections.map { it.kind })
        // İğnelenen bölümünün en üstünde, sonra alfabetik ("Aardvark" alfabede önde olsa da Nuvio bölümü önce gelir).
        assertEquals(listOf("nuvio_addon:11", "nuvio_addon:12", "nuvio_addon:10"), sections[0].items.map { it.id })
        assertEquals(listOf("cs:3", "cs:1", "cs:2"), sections[1].items.map { it.id })
    }

    @Test
    fun selectorSectionsTabsShowOnlyTheirOwnSection() {
        assertEquals(listOf(ProviderSectionKind.Nuvio), ProviderRules.selectorSections(sectionItems, ProviderTab.Nuvio, "").map { it.kind })
        assertEquals(listOf(ProviderSectionKind.CloudStream), ProviderRules.selectorSections(sectionItems, ProviderTab.CloudStream, "").map { it.kind })
    }

    @Test
    fun selectorSectionsSearchFiltersBothSectionsAndDropsEmptyOnes() {
        // Arama iki bölümü birlikte süzer; eşleşmesi olmayan bölüm düşer.
        val both = ProviderRules.selectorSections(sectionItems, ProviderTab.All, "kata")
        assertEquals(listOf(ProviderSectionKind.Nuvio), both.map { it.kind })
        assertEquals(setOf("nuvio_addon:10", "nuvio_addon:11"), both.single().items.map { it.id }.toSet())
        val cs = ProviderRules.selectorSections(sectionItems, ProviderTab.All, "dizi")
        assertEquals(listOf(ProviderSectionKind.CloudStream), cs.map { it.kind })
        assertTrue(ProviderRules.selectorSections(sectionItems, ProviderTab.All, "yok-böyle").isEmpty())
    }

    private fun manifest(id: String, catalogs: Int, resource: String = "catalog") = AddonManifest(
        id = id,
        name = id,
        types = listOf("movie", "series"),
        resources = listOf(ManifestResource(resource)),
        catalogs = List(catalogs) { ManifestCatalog("movie", "c$it", "Raf $it") },
    )

    @Test
    fun nuvioCatalogProvidersKeepOnlyEnabledSourcesWithCatalogs() {
        val sources = listOf(
            NuvioSource(1, "İzlemeli.com Katalog", "izlemeli", enabled = true, manifest = manifest("izlemeli", 3)),
            NuvioSource(2, "Torrentio", "torrentio", enabled = true, manifest = manifest("torrentio", 0, "stream")),
            NuvioSource(3, "AltyazıDB", "altyazidb", enabled = true, manifest = manifest("altyazidb", 0, "subtitles")),
            NuvioSource(4, "Kapalı Katalog", "kapali", enabled = false, manifest = manifest("kapali", 2)),
            // Yalnızca "scrapers" listeleyen plugin deposu (ör. jasoelys): addon manifesti olarak okunamaz.
            NuvioSource(5, "jasoelys", null, enabled = true, manifest = null),
            // Manifestinde catalogs olan plugin deposu: aynı addon katalog yolundan gösterilir.
            NuvioSource(6, "Katalog Deposu", "repo.catalogs", enabled = true, manifest = manifest("repo.catalogs", 1)),
        )
        val items = ProviderRules.nuvioCatalogProviders(sources)
        assertEquals(listOf("nuvio_addon:1", "nuvio_addon:6"), items.map { it.id })
        assertTrue(items.all { it.kind == ProviderKind.NuvioAddon })
    }

    // --- Seçim uygulanmıyordu (kullanıcı bildirimi 2026-10-21): Nuvio "izlelan" seçiliyken CloudStream eklentisi seçildi ---

    private val switchProviders = listOf(
        ProviderItem(id = "nuvio_addon:10", name = "izlelan", subtitle = "Nuvio · izlelan", kind = ProviderKind.NuvioAddon),
        ProviderItem(id = "nuvio_addon:11", name = "İzlemeli.com Katalog", subtitle = "Nuvio", kind = ProviderKind.NuvioAddon),
        ProviderItem(id = "cs:7", name = "AlbaniaLive", subtitle = "CloudStream", kind = ProviderKind.CloudStream),
        ProviderItem(id = "cs:8", name = "DiziBox", subtitle = "CloudStream", kind = ProviderKind.CloudStream),
    )

    private fun pillName(selected: Set<String>, providers: List<ProviderItem>?): String? =
        ProviderRules.resolveSelection(selected, providers).let { if (it.showAll) null else it.valid.firstOrNull()?.name }

    @Test
    fun switchingBetweenNuvioAndCloudStreamAppliesImmediately() {
        var selected = ProviderRules.singleSelect("nuvio_addon:10")
        assertEquals("izlelan", pillName(selected, switchProviders))
        // Nuvio → CloudStream
        selected = ProviderRules.singleSelect("cs:7")
        assertEquals("AlbaniaLive", pillName(selected, switchProviders))
        assertTrue(ProviderRules.resolveSelection(selected, switchProviders).stale.isEmpty())
        // CloudStream → CloudStream
        selected = ProviderRules.singleSelect("cs:8")
        assertEquals("DiziBox", pillName(selected, switchProviders))
        // CloudStream → Nuvio
        selected = ProviderRules.singleSelect("nuvio_addon:11")
        assertEquals("İzlemeli.com Katalog", pillName(selected, switchProviders))
    }

    @Test
    fun selectionIsKeptWhileProviderListIsStillLoading() {
        // Liste henüz gelmedi (null): yeni seçim düşürülmez, varsayılan akışa da dönülmez.
        val resolved = ProviderRules.resolveSelection(setOf("cs:7"), null)
        assertTrue(resolved.stale.isEmpty())
        assertFalse(resolved.showAll)
        // Liste gelince seçim uygulanır.
        assertEquals("AlbaniaLive", pillName(setOf("cs:7"), switchProviders))
    }

    @Test
    fun removedProviderIsDroppedOnlyAfterListLoaded() {
        val resolved = ProviderRules.resolveSelection(setOf("cs:99", "cs:8"), switchProviders)
        assertEquals(setOf("cs:99"), resolved.stale)
        assertEquals(listOf("DiziBox"), resolved.valid.map { it.name })
        // Hiç sağlayıcı yoksa (hepsi silindi) seçim düşer, varsayılan akış gösterilir.
        val none = ProviderRules.resolveSelection(setOf("cs:7"), emptyList())
        assertTrue(none.showAll)
        assertEquals(setOf("cs:7"), none.stale)
        // "Tüm sağlayıcılar"
        assertTrue(ProviderRules.resolveSelection(emptySet(), switchProviders).showAll)
    }
}
