package com.kaan.play.ui.player

import android.content.Context
import android.content.Intent
import com.kaan.play.data.db.DownloadEntity
import com.kaan.play.data.download.DownloadCodec
import com.kaan.play.data.download.toTarget
import com.kaan.play.data.model.MediaType
import com.kaan.play.data.net.PlayJson
import com.kaan.play.data.stream.PlayTarget
import com.kaan.play.data.stream.PlayableStream
import com.kaan.play.data.stream.StreamRequest
import com.kaan.play.data.stream.StreamSource
import com.kaan.play.data.stream.StreamSubtitle
import kotlinx.serialization.Serializable

/**
 * İndirilmiş (çevrimdışı) bir oynatma. [downloadId]: Media3 indirme kimliği (oynatıcı öğeyi buradan kurar);
 * [subtitleFiles]: indirme sırasında saklanan altyazılar, özgün adres -> yerel UTF-8 dosyası.
 */
@Serializable
data class OfflineSession(val downloadId: Long, val subtitleFiles: Map<String, String>)

/** Oynatıcı etkinliğine giden her şey: ne oynanıyor, hangi kaynakla, nereden başlanacak. */
@Serializable
data class PlaySession(
    val target: PlayTarget,
    /** Oynatılacak kaynak; null: [autoSource] (oynatıcı kaynakları kendisi bulup seçer). */
    val stream: PlayableStream?,
    val startPositionMs: Long,
    /** Kayıtlı konumdan sormadan devam ediliyorsa true: oynatıcı kısa bir "Baştan başla" bildirimi gösterir. */
    val autoResumed: Boolean = false,
    /** Doluysa video internetten değil, indirilmiş kopyadan oynar ve kaynak değiştirilemez. */
    val offline: OfflineSession? = null,
    /**
     * Otomatik sonraki bölümde [stream] açılmazsa sırayla denenecek yedek kaynaklar (kullanıcının seçtiği kaynak değil, bulunan
     * sıra). Hepsi tükenirse oynatıcı her zamanki hata ekranını ("Başka kaynak seç") gösterir.
     */
    val fallbacks: List<PlayableStream> = emptyList(),
    /** Önceki bölümden otomatik geçişle açıldı: etkinlik geçişi animasyonsuz olur (kesintisiz görünsün). */
    val fromAutoNext: Boolean = false,
    /** Kaynak listesi beklenmeden açıldı: [stream] yok, oynatıcı uygun ilk kaynağı arka planda bulur. */
    val autoSource: Boolean = false,
    /** Yalnızca debug/duman testi oynatması ([testVideo]): izleme geçmişine ("İzlemeye devam et") yazılmaz. */
    val noHistory: Boolean = false,
) {
    fun toIntent(context: Context): Intent =
        Intent(context, PlayerActivity::class.java).putExtra(EXTRA, PlayJson.encodeToString(serializer(), this))

    companion object {
        private const val EXTRA = "session"

        /**
         * İndirilmiş kopyadan oturum. Hedef çevrimiçi oynatmayla birebir aynıdır (aynı `watch_history` satırı: kaldığı yerden
         * devam ve geçmiş ortak); akışın altyazıları indirilenler olur, böylece altyazı menüsü ağsız dolar.
         */
        fun offline(
            download: DownloadEntity,
            startPositionMs: Long,
            autoResumed: Boolean,
            fromAutoNext: Boolean = false,
        ): PlaySession {
            val subtitles = DownloadCodec.decodeSubtitles(download.subtitlesJson)
            return PlaySession(
                target = download.toTarget(),
                stream = PlayableStream(
                    source = StreamSource(addonId = 0, addonName = download.sourceName),
                    url = download.sourceUrl,
                    resolution = null,
                    sizeBytes = download.totalBytes.takeIf { it > 0 },
                    headers = emptyMap(),
                    subtitles = subtitles.map { StreamSubtitle(it.url, it.language) },
                ),
                startPositionMs = startPositionMs,
                autoResumed = autoResumed,
                offline = OfflineSession(download.id, subtitles.associate { it.url to it.file }),
                fromAutoNext = fromAutoNext,
            )
        }

        /**
         * Debug derlemede duman testinin oynatıcıyı doğrudan açması için (`--es play_test_video <adres>`): tek kaynaklı,
         * geçmişe yazılmayan bir film oturumu, [startPositionMs]'ten başlar.
         */
        fun testVideo(url: String, startPositionMs: Long = 60_000L): PlaySession = PlaySession(
            target = PlayTarget(
                request = StreamRequest(tmdbId = 0, imdbId = null, type = MediaType.Movie, title = TEST_TITLE),
                title = TEST_TITLE,
                posterPath = null,
            ),
            stream = PlayableStream(
                source = StreamSource(addonId = 0, addonName = TEST_TITLE),
                url = url,
                resolution = null,
                sizeBytes = null,
                headers = emptyMap(),
                subtitles = emptyList(),
            ),
            startPositionMs = startPositionMs,
            noHistory = true,
        )

        private const val TEST_TITLE = "Play test"

        /** Bozuk/eksik veri null döner (etkinlik kendini kapatır). */
        fun from(intent: Intent): PlaySession? {
            val text = intent.getStringExtra(EXTRA) ?: return null
            return try {
                PlayJson.decodeFromString(serializer(), text)
            } catch (t: Throwable) {
                android.util.Log.e("PlaySession", "PlaySession deserialization failed: ${t.message}", t)
                null
            }
        }
    }
}
