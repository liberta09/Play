package com.kaan.play.ui.home.slider

import androidx.compose.runtime.Composable
import com.kaan.play.data.provider.CatalogItem
import com.kaan.play.data.provider.CatalogRow
import com.kaan.play.ui.home.FanCarousel
import com.kaan.play.ui.home.MatchboxCarousel
import com.kaan.play.ui.home.WarpCarousel
import com.kaan.play.ui.home.isDramaTrProvider
import com.kaan.play.ui.home.isDramaVaktiProvider
import com.kaan.play.ui.home.isShortizleProvider

/** Sürgü çizen bileşen: kartlar + ortadakine dokununca detay. */
internal typealias SliderContent = @Composable (items: List<CatalogItem>, onOpen: (CatalogItem) -> Unit) -> Unit

/**
 * Kayıttaki tek satır. [key]: LazyColumn öğe anahtarı (değişmez). [matches]: eklentinin uygulamadaki adı (ya da iç adı) bu
 * eklenti mi. [content] null ise yer tutucudur: stil henüz yok, hiçbir şey gösterilmez.
 */
internal class PluginSlider(
    val key: String,
    val plugin: String,
    val style: String,
    val matches: (String) -> Boolean,
    val content: SliderContent? = null,
)

/** Seçilen sürgü ve kartları. */
internal class SliderPick(val slider: PluginSlider, val items: List<CatalogItem>)

/**
 * Eklentiye özgü üst kayan alanların TEK kaydı. Ana sayfada seçili eklentinin adı bir sitenin adıyla eşleşirse o sitenin
 * stili gösterilir; eşleşme yoksa hiçbir şey çıkmaz. Sıra öncelik sırasıdır: birden fazla stilli eklenti seçiliyse listede
 * ilk gelen gösterilir. Yeni stil: kendi dosyanı yaz, aşağıya bir satır ekle. Ayrıntı: `docs/eklenti-kayan-alanlari.md`.
 */
internal object PluginSliders {
    const val MAX_ITEMS = 10
    const val MIN_ITEMS = 3

    val entries: List<PluginSlider> = listOf(
        // Mevcut üç stil (görünüş ve davranış aynen; eşleşme işlevleri kendi dosyalarında).
        PluginSlider("shortizle_fan", "Shortİzle", "yelpaze", ::isShortizleProvider) { i, o -> FanCarousel(items = i, onOpen = o) },
        PluginSlider("dramatr_warp", "DramaTR", "3B halka", ::isDramaTrProvider) { i, o -> WarpCarousel(items = i, onOpen = o) },
        PluginSlider("dramavakti_matchbox", "DramaVakti", "koyu kibrit kutusu", ::isDramaVaktiProvider) { i, o ->
            MatchboxCarousel(items = i, onOpen = o)
        },
        PluginSlider("minidiziizle_open_matchbox", "MiniDiziİzle", "açık kibrit kutusu", named("minidiziizle")) { i, o ->
            OpenMatchboxCarousel(items = i, onOpen = o)
        },
        // Site adıyla eşleşen diğer stiller.
        PluginSlider("anyshort_film_strip", "AnyShort", "film şeridi", named("anyshort")) { i, o -> FilmStripCarousel(i, o) },
        PluginSlider("directdrama_cinema_screen", "DirectDrama", "sinema perdesi", named("directdrama")) { i, o -> CinemaScreenCarousel(i, o) },
        PluginSlider("dramadizilerim_bookshelf", "DramaDizilerim", "kitap rafı", named("dramadizilerim")) { i, o ->
            BookshelfCarousel(i, o)
        },
        PluginSlider("dramadunyam_globe", "DramaDünyam", "dünya küresi", named("dramadunyam")) { i, o -> DramaDunyamGlobeCarousel(i, o) },
        PluginSlider("dramacix_card_deck", "DramaCix", "kart destesi", named("dramacix")) { i, o -> CardDeckCarousel(i, o) },
        PluginSlider("fulldrama_cover_flow", "FullDrama", "kapak akışı", named("fulldrama")) { i, o -> CoverFlowCarousel(i, o) },
        PluginSlider("izlegec_snapshots", "İzleGeç", "saçılmış anlık fotoğraflar", named("izlegec")) { i, o -> SnapshotsCarousel(i, o) },
        PluginSlider("liderdrama_spotlight", "LiderDrama", "spot ışığı", named("liderdrama")) { i, o -> SpotlightCarousel(i, o) },
        PluginSlider("lidershort_vertical_feed", "LiderShort", "dikey kaydırma", named("lidershort")) { i, o -> VerticalFeedCarousel(i, o) },
        PluginSlider("narto_accordion", "Narto", "akordeon şeritler", named("narto")) { i, o -> NartoAccordionCarousel(i, o) },
        PluginSlider("verticalshorts_cube", "VerticalShorts", "3B küp", named("verticalshorts")) { i, o -> VerticalShortsCubeCarousel(i, o) },
    )

    /**
     * Eklenti adını karşılaştırma anahtarına çevirir: Türkçe harfler sadeleşir, yalnız a–z kalır.
     * "MiniDiziİzle" → "minidiziizle", "İzleGeç" → "izlegec".
     */
    fun key(name: String): String = buildString {
        for (c in name) {
            when (c) {
                'İ', 'I', 'ı', 'i' -> append('i')
                'Ü', 'ü' -> append('u')
                'Ö', 'ö' -> append('o')
                'Ç', 'ç' -> append('c')
                'Ş', 'ş' -> append('s')
                'Ğ', 'ğ' -> append('g')
                else -> c.lowercaseChar().takeIf { it in 'a'..'z' }?.let(::append)
            }
        }
    }

    /**
     * Site adı eşleşmesi: eklenti adının anahtarı ([key]) verilen site adlarından biriyle birebir aynı olmalı. Benzer adlar
     * (ör. "DramaVaktiMp4", "MiniDiziİzle Plus") eşleşmez.
     */
    fun named(vararg sites: String): (String) -> Boolean {
        val keys = sites.toSet()
        return { name -> key(name) in keys }
    }

    /**
     * Seçili eklentiler [selectedNames] ve yüklü satırlar [rows] için gösterilecek sürgü: kayıt sırasında ilk, kodu olan ve
     * seçili bir eklentiyle eşleşen giriş; kartlar o eklentinin ilk dolu satırının ilk [MAX_ITEMS] öğesi. Eşleşen eklentinin
     * dolu satırı yoksa sıradaki girişe geçilir; kart [MIN_ITEMS]'ten azsa hiçbir sürgü gösterilmez (eski davranış).
     */
    fun pick(
        selectedNames: List<String>,
        rows: List<CatalogRow>,
        registry: List<PluginSlider> = entries,
    ): SliderPick? {
        for (slider in registry) {
            if (slider.content == null) continue
            if (selectedNames.none(slider.matches)) continue
            val items = rows.firstOrNull { slider.matches(it.providerName) && it.items.isNotEmpty() }?.items ?: continue
            return SliderPick(slider, items.take(MAX_ITEMS)).takeIf { it.items.size >= MIN_ITEMS }
        }
        return null
    }
}
