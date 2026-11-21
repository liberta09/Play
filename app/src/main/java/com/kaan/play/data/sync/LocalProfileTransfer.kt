package com.kaan.play.data.sync

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class PluginStateTransfer(
    val pluginId: String,
    val enabled: Boolean,
    /** Eklentinin `hasSettings` değerleri (Wi-Fi senkronizasyonu). */
    val settings: Map<String, JsonElement> = emptyMap(),
)

@Serializable
data class SourceTransferItem(
    val type: String,
    val sourceUrl: String,
    val enabled: Boolean = true,
    val plugins: List<PluginStateTransfer> = emptyList(),
    /** Görünen ad ("Şunlar eklensin mi?" listesi ve web siteleri için). */
    val name: String = "",
    /** Addon'un kapalı katalogları: `tür:katalogKimliği` (satır kimliği cihaza özel olduğu için yazılmaz). */
    val disabledCatalogs: List<String> = emptyList(),
)

@Serializable
data class DeletedHistoryItem(
    val userId: String = "",
    val profileId: String = "",
    val tmdbId: Int,
    val type: String, // "movie" veya "tv"
    val deletedAt: Long,
)

/**
 * Yalnız Wi-Fi eşitlemesinin silinenler kaydı: bulut biçiminde karşılığı yoktur (sunucu ve
 * yayındaki sürümler yalnız [DeletedHistoryItem]'ı tanır), bu yüzden buluta hiç yazılmaz; bulut eşitlemesinde yalnız bu
 * cihazda süzgeç olarak çalışır. [LocalProfileTransfer.deletedHistory]'den AYRI listededir: eski sürüm bilinmeyen alanı
 * atar; aynı listede olsaydı bölüm/favori silmesini bütün dizinin silinmesi sanırdı.
 *
 * [kind]: [KIND_EPISODE] tek bölüm/film satırı (tmdbId+tür+sezon+bölüm), [KIND_CLEAR] profilin bütün geçmişi
 * (tmdbId/sezon/bölüm 0), [KIND_FAVORITE] favori (tmdbId+tür). Silme anından sonra izlenen/eklenen kayıt etkilenmez.
 */
@Serializable
data class DeletedLibraryItem(
    val profileId: String = "",
    val kind: String,
    val tmdbId: Int = 0,
    val type: String = "",
    val season: Int = 0,
    val episode: Int = 0,
    val deletedAt: Long,
) {
    companion object {
        const val KIND_EPISODE = "episode"
        const val KIND_CLEAR = "clear"
        const val KIND_FAVORITE = "favorite"
    }
}

/**
 * Yalnız Wi-Fi: hiç izlenmemiş eklenti (drama) favorisinin açılış bilgisi. Eklenti numarası
 * (`cs:N`) cihaza özel olduğundan eklentinin iç adı ([pluginName], CS `internalName`) gider; karşı cihaz kendi eklentisine
 * çevirir. Favori kimliği ([tmdbId], negatif) eklenti türü + adresten türetildiği için iki cihazda aynıdır.
 */
@Serializable
data class FavoriteSourceTransfer(
    val tmdbId: Int,
    val type: String,
    val pluginName: String,
    val contentUrl: String,
)

@Serializable
data class DeletedFavoriteItem(
    val userId: String = "",
    val profileId: String = "",
    val tmdbId: Int,
    val type: String, // "movie" veya "tv"
    val deletedAt: Long,
)

@Serializable
data class LocalProfileTransfer(
    val profiles: List<LocalProfileSummary> = emptyList(),
    val backups: Map<String, String> = emptyMap(), // profileId -> JSON of LocalProfileBackup
    val sources: List<SourceTransferItem> = emptyList(),
    val warnings: List<String> = emptyList(),
    /** Gönderen cihazın adı (Wi-Fi senkronizasyonu). */
    val deviceName: String = "",
    val deletedHistory: List<DeletedHistoryItem> = emptyList(),
    /** Yalnız Wi-Fi (bulut kodlayıcısı yazmaz/okumaz; eski sürüm bilinmeyen alan olarak atar). */
    val deletedItems: List<DeletedLibraryItem> = emptyList(),
    /** Yalnız Wi-Fi: eklenti favorilerinin açılış bilgisi. */
    val favoriteSources: List<FavoriteSourceTransfer> = emptyList(),
    val deletedFavorites: List<DeletedFavoriteItem> = emptyList(),
)
