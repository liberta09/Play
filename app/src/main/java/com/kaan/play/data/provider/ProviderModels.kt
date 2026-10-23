package com.kaan.play.data.provider

import com.kaan.play.data.model.MediaType
import kotlinx.coroutines.flow.Flow

enum class ProviderTab { All, CloudStream, Nuvio }

enum class ProviderKind { CloudStream, NuvioAddon, NuvioPlugin }

/**
 * Ana sayfa seçiminin çözümü ([ProviderRules.resolveSelection]). [valid]: listede bulunan seçili sağlayıcılar;
 * [showAll]: varsayılan akış gösterilir; [stale]: listede artık olmayan (silinmiş/kapatılmış) seçili kimlikler.
 */
data class ResolvedSelection(val valid: List<ProviderItem>, val showAll: Boolean, val stale: Set<String>)

/** Seçicide bölüm: önce Nuvio, sonra CloudStream (kullanıcı kararı 2026-10-21). */
enum class ProviderSectionKind { Nuvio, CloudStream }

data class ProviderSection(val kind: ProviderSectionKind, val items: List<ProviderItem>)

/**
 * Nuvio kaynağı (addon ya da plugin deposu) satırı; [manifest] Stremio addon manifesti olarak okunamazsa null.
 * [rowId]: `addons` tablosundaki satır kimliği (katalog yolu `nuvio_addon:<rowId>` ile bunu bulur).
 */
data class NuvioSource(
    val rowId: Long,
    val name: String,
    val addonId: String?,
    val enabled: Boolean,
    val manifest: com.kaan.play.data.addon.AddonManifest?,
)

/**
 * Sağlayıcı seçicide listelenen tek bir eklenti / sağlayıcı öğesi.
 * [id]: Benzersiz kimlik (örn. "cs:12", "nuvio_addon:3", "nuvio_plugin:5").
 * [name]: Eklentinin adı (örn. "DiziBox", "VidMoly").
 * [subtitle]: "CloudStream · repo adı" veya "Nuvio · addon adı".
 */
data class ProviderItem(
    val id: String,
    val name: String,
    val subtitle: String,
    val kind: ProviderKind,
    val isPinned: Boolean = false,
    /** Nuvio addon'larında manifest kimliği (ör. `com.play.custom.catalogs`); diğerlerinde null. */
    val addonId: String? = null,
)

/**
 * Sağlayıcı kataloğundaki tek bir içerik (kart).
 */
data class CatalogItem(
    val id: String,
    val title: String,
    val subtitle: String? = null,
    val posterUrl: String? = null,
    val posterHeaders: Map<String, String>? = null,
    val mediaType: MediaType = MediaType.Movie,
    val providerId: String,
    val rawUrl: String? = null,
    val addonType: String? = null,
)

/**
 * Sağlayıcı ana sayfasındaki tek bir içerik satırı (başlık + afiş şeridi).
 */
data class CatalogRow(
    val id: String,
    val title: String,
    val items: List<CatalogItem>,
    val hasNext: Boolean = false,
    val page: Int = 1,
    val isHorizontalImages: Boolean = false,
    val providerId: String,
    val providerName: String,
)

/**
 * Eklentinin kendi ana sayfa verilerini sağlaması için katalog arayüzü.
 * CloudStream getMainPage veya Nuvio Stremio katalog akışı bu arayüzü uygular.
 */
interface ProviderCatalog {
    val providerId: String
    val name: String
    fun rows(): Flow<List<CatalogRow>>
    suspend fun loadMore(rowId: String, page: Int): List<CatalogItem>
}

object ProviderConstants {
    /** "Tüm sağlayıcılar" (varsayılan TMDB akışı) özel kimliği. */
    const val ALL_PROVIDERS_ID = "__all__"
}
