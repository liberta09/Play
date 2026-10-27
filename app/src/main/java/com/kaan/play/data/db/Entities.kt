package com.kaan.play.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.kaan.play.data.model.MediaType

/**
 * Eklenti kaynağının türü. Faz 1'de yalnızca [NUVIO_ADDON] çalışır; diğerleri şema olarak hazır
 * (Faz 2: Nuvio plugin deposu, Faz 3: CloudStream deposu, 8. adım: [WEB_SITE]). Tür TEXT olarak
 * (enum adıyla) saklanır; yeni değer eklemek şemayı değiştirmez, bu yüzden göç gerekmez.
 */
enum class AddonType { NUVIO_ADDON, NUVIO_PLUGIN_REPO, CLOUDSTREAM_REPO, WEB_SITE }

/**
 * Kullanıcının eklediği kaynak. [sourceUrl]: addon için manifest.json adresi, depolar için depo adresi.
 * [manifestJson]: doğrulanmış manifest'in kendi modelimizle kodlanmış hali (her zaman çözülebilir).
 */
@Entity(tableName = "addons", indices = [Index(value = ["sourceUrl"], unique = true)])
data class AddonEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: AddonType,
    val sourceUrl: String,
    val addonId: String,
    val name: String,
    val version: String?,
    val description: String?,
    val logoUrl: String?,
    val enabled: Boolean = true,
    val manifestJson: String,
    val addedAt: Long,
)

/**
 * Bir depo ([AddonType.NUVIO_PLUGIN_REPO] / [AddonType.CLOUDSTREAM_REPO]) içindeki tek tek plugin'ler (Faz 2: JS kazıyıcılar).
 * Kod uygulamaya özel dizinde saklanır (`PluginCodeStore`); burada yalnızca üst bilgi, bütünlük özeti ve açık/kapalı durumu
 * vardır. [enabled] varsayılan KAPALIDIR: kullanıcı bilinçle açar. Şema sürüm 2'de genişledi (tablo Faz 1'de boştu).
 */
@Entity(
    tableName = "plugins",
    foreignKeys = [
        ForeignKey(
            entity = AddonEntity::class,
            parentColumns = ["id"],
            childColumns = ["repoId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("repoId"), Index(value = ["repoId", "pluginId"], unique = true)],
)
data class PluginEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val repoId: Long,
    val pluginId: String,
    val name: String,
    val version: String?,
    val description: String? = null,
    /** Manifest'teki ham dosya adı. */
    val filename: String = "",
    /** İndirme adresi (çözülmüş). */
    val scriptUrl: String,
    /** Ham `supportedTypes`, virgülle ayrılmış (`movie,tv`). */
    val supportedTypes: String = "movie,tv",
    /** `contentLanguage`, virgülle ayrılmış. */
    val contentLanguage: String = "",
    val hasSettings: Boolean = false,
    /** İndirilen kodun SHA-256 özeti (hex) ve bayt boyutu: bütünlük ve değişim denetimi için. */
    val sha256: String = "",
    val sizeBytes: Long = 0,
    val enabled: Boolean = false,
    val installedAt: Long,
)

/**
 * İzleme geçmişi: içerik başına tek satır. Filmde [season] ve [episode] 0'dır (NULL, benzersiz
 * indekste ayrı sayılacağı için kullanılmaz). [title]/[posterPath], "devam et" kartı için anlık kopyadır.
 * Davranışı 7. adımda gelir; şema şimdiden sabit.
 */
@Entity(
    tableName = "watch_history",
    indices = [
        Index(value = ["userId", "profileId", "tmdbId", "type", "season", "episode"], unique = true),
        Index(value = ["userId", "profileId", "lastWatchedAt"]),
    ],
)
data class WatchHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val tmdbId: Int,
    val type: MediaType,
    val userId: String = "legacy-user",
    val profileId: String = "legacy-profile",
    val season: Int = 0,
    val episode: Int = 0,
    val positionMs: Long,
    val durationMs: Long,
    val lastWatchedAt: Long,
    val title: String,
    val posterPath: String?,
    val pluginId: String? = null,
    val dataUrl: String? = null,
    val contentUrl: String? = null,
)


/**
 * Faz 4/A: bir film/dizi için seçilen altyazı dili. [contentKey]: film `movie:{tmdbId}`, dizi `tv:{tmdbId}` (bölümler arası
 * ortak: bir dizide seçilen dil sonraki bölümlerde otomatik seçilir). [language]: iki harfli dil ya da `off` (kullanıcı kapattı).
 */
@Entity(tableName = "subtitle_language")
data class SubtitleLanguageEntity(
    @PrimaryKey val contentKey: String,
    val language: String,
    val updatedAt: Long,
)

/**
 * Faz 4/A: elle senkron kaydırması, (içerik, altyazı) başına. [contentKey]: film `movie:{tmdbId}`, bölüm `tv:{tmdbId}:{sezon}:{bölüm}`;
 * [subtitleKey]: altyazı adresinin SHA-256'sı. 0 kaydırma saklanmaz (satır silinir).
 */
@Entity(
    tableName = "subtitle_offset",
    primaryKeys = ["contentKey", "subtitleKey"],
    indices = [Index("updatedAt")],
)
data class SubtitleOffsetEntity(
    val contentKey: String,
    val subtitleKey: String,
    val offsetMs: Long,
    val updatedAt: Long,
)

/** Faz 4/B: indirmenin durumu. Media3'ün durumlarından [com.kaan.play.data.download.DownloadRules.statusOf] ile türetilir. */
enum class DownloadStatus { Queued, Downloading, Paused, Completed, Failed }

/**
 * Faz 4/B: çevrimdışı izleme için indirilen (ya da inen) film/bölüm. Media3 `DownloadManager` kendi dizininde asıl veriyi ve
 * durumu tutar (kimliği bu satırın [id]'sidir); bu tablo arayüz için üst bilgiyi (başlık, afiş, kaynak adı, altyazılar) ve
 * son bilinen durumu/baytları saklar. Aynı film/bölüm için tek satır olur (benzersiz indeks). Filmde [season] ve [episode] 0'dır.
 * [headersJson]: akışın `proxyHeaders`'ı (`{"ad":"değer"}`). [subtitlesJson]: indirme sırasında saklanan altyazılar
 * ([com.kaan.play.data.download.DownloadSubtitle] listesi). [totalBytes]: bilinmiyorsa 0; bölünmüş akışlarda (HLS/DASH) tahmindir.
 */
@Entity(
    tableName = "downloads",
    indices = [
        Index(value = ["tmdbId", "type", "season", "episode"], unique = true),
        Index("createdAt"),
    ],
)
data class DownloadEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val tmdbId: Int,
    val imdbId: String?,
    val type: MediaType,
    val season: Int = 0,
    val episode: Int = 0,
    val title: String,
    val posterPath: String?,
    val sourceName: String,
    val sourceUrl: String,
    val headersJson: String = "{}",
    val status: DownloadStatus = DownloadStatus.Queued,
    val downloadedBytes: Long = 0,
    val totalBytes: Long = 0,
    val createdAt: Long,
    val subtitlesJson: String = "[]",
)

@Entity(
    tableName = "favorites",
    primaryKeys = ["userId", "profileId", "tmdbId", "type"],
)
data class FavoriteEntity(
    val tmdbId: Int,
    val type: MediaType,
    val userId: String = "legacy-user",
    val profileId: String = "legacy-profile",
    val title: String,
    val posterPath: String?,
    val addedAt: Long,
)
