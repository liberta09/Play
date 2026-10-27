package com.kaan.play.data.download

import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.scheduler.Requirements
import com.kaan.play.data.db.DownloadEntity
import com.kaan.play.data.db.DownloadStatus

/** İndirmenin durum geçişleri, ağ koşulu, alan denetimi ve açılıştaki uzlaştırma kuralları (Android'den bağımsız, birim testli). */
object DownloadRules {

    /** Kullanıcı duraklatınca Media3 `stopReason` değeri (0 = durdurulmadı). */
    const val STOP_REASON_PAUSED = 1

    /**
     * Media3 durumundan bizim durumumuz. `RESTARTING` (silinip yeniden başlıyor) ve `REMOVING` (siliniyor) arayüzde "sırada"
     * görünür; silinen satırın güncellemesi zaten hiçbir satıra denk gelmez.
     */
    fun statusOf(state: Int): DownloadStatus = when (state) {
        Download.STATE_COMPLETED -> DownloadStatus.Completed
        Download.STATE_FAILED -> DownloadStatus.Failed
        Download.STATE_DOWNLOADING -> DownloadStatus.Downloading
        Download.STATE_STOPPED -> DownloadStatus.Paused
        else -> DownloadStatus.Queued // QUEUED, RESTARTING, REMOVING
    }

    /**
     * Kaynak listesindeki bir satırın indirme simgesi. [entry]: bu film/bölümün indirme satırı (yoksa null). Simge yalnızca
     * **aynı kaynak adresinde** durumu gösterir; aynı içeriğin başka bir kaynağı indirilmişse bu satırda simge normal kalır
     * (dokununca "zaten indirilenlerde" denir). [starting]: dokunuldu, kaynak yoklanıyor.
     */
    fun badge(entry: DownloadEntity?, url: String, starting: Boolean): DownloadBadge = when {
        starting -> DownloadBadge.Active
        entry == null || entry.sourceUrl != url -> DownloadBadge.None
        entry.status == DownloadStatus.Completed -> DownloadBadge.Done
        entry.status == DownloadStatus.Failed -> DownloadBadge.Failed
        else -> DownloadBadge.Active
    }

    /**
     * Detay'daki (film/bölüm) indir düğmesinin durumu: kaynak adresine bakılmaz, içeriğin indirme satırı belirler
     * (aynı film/bölüm tek indirme olur).
     */
    fun contentBadge(entry: DownloadEntity?): DownloadBadge = when (entry?.status) {
        null -> DownloadBadge.None
        DownloadStatus.Completed -> DownloadBadge.Done
        DownloadStatus.Failed -> DownloadBadge.Failed
        else -> DownloadBadge.Active
    }

    /** Kütüphane satırına dokunmanın (ve oynat/duraklat düğmesinin) yaptığı iş. */
    enum class RowAction { Play, Pause, Resume }

    /** Tamam: oynat; sırada/iniyor: duraklat; duraklatılmış/başarısız: sürdür (başarısızda yeniden dener). */
    fun rowAction(status: DownloadStatus): RowAction = when (status) {
        DownloadStatus.Completed -> RowAction.Play
        DownloadStatus.Queued, DownloadStatus.Downloading -> RowAction.Pause
        DownloadStatus.Paused, DownloadStatus.Failed -> RowAction.Resume
    }

    /** İndirme hâlâ sürüyor ya da sürecek mi (bildirim/ilerleme yoklaması için)? */
    fun isActive(status: DownloadStatus): Boolean =
        status == DownloadStatus.Queued || status == DownloadStatus.Downloading

    /** Ağ koşulu: "Sadece Wi-Fi'de indir" açıksa ölçümsüz (Wi-Fi/ethernet) ağ, kapalıysa herhangi bir ağ. */
    fun requirementFlags(wifiOnly: Boolean): Int = if (wifiOnly) Requirements.NETWORK_UNMETERED else Requirements.NETWORK

    /** Media3 "karşılanmayan koşullar" bayraklarında ağ ile ilgili olan var mı? (Sırada bekleyenler için "ağ bekleniyor"). */
    fun waitingForNetwork(notMetRequirements: Int): Boolean =
        notMetRequirements and (Requirements.NETWORK or Requirements.NETWORK_UNMETERED) != 0

    /**
     * Gösterilecek toplam bayt. Bilinen içerik uzunluğu varsa o; yoksa (bölünmüş akışlar) yüzdeden tahmin; o da yoksa
     * (ilk anlar) kaynağın bildirdiği boyut; hiçbiri yoksa 0 (bilinmiyor). Tamamlanınca gerçek indirilen boyut.
     */
    fun totalBytes(
        status: DownloadStatus,
        downloadedBytes: Long,
        contentLength: Long,
        percent: Float,
        hintBytes: Long,
    ): Long {
        if (status == DownloadStatus.Completed) return downloadedBytes
        if (contentLength > 0) return maxOf(contentLength, downloadedBytes)
        if (percent > 0f && percent < 100f && downloadedBytes > 0) {
            return maxOf((downloadedBytes * 100.0 / percent).toLong(), downloadedBytes)
        }
        return maxOf(hintBytes, 0L)
    }

    /** 0..1 ilerleme; toplam bilinmiyorsa null (belirsiz çubuk). */
    fun fraction(downloadedBytes: Long, totalBytes: Long): Float? =
        if (totalBytes > 0) (downloadedBytes.toDouble() / totalBytes).toFloat().coerceIn(0f, 1f) else null

    // ---- alan denetimi ----

    /** İndirmeden sonra cihazda bırakılacak boş alan: telefon yine kullanılabilir kalsın. */
    const val RESERVE_BYTES = 200L * 1024 * 1024

    /** Boyutu bilinmeyen (HLS/DASH, boyut bildirmeyen kaynak) indirme için varsayılan gereksinim. */
    const val UNKNOWN_SIZE_ASSUMED_BYTES = 500L * 1024 * 1024

    sealed interface Space {
        data object Enough : Space

        /** [neededBytes] bilinmeyen boyutta varsayımdır; [estimated] true ise "yaklaşık". */
        data class Short(val neededBytes: Long, val freeBytes: Long, val estimated: Boolean) : Space
    }

    fun checkSpace(knownBytes: Long?, freeBytes: Long): Space {
        val known = knownBytes?.takeIf { it > 0 }
        val needed = known ?: UNKNOWN_SIZE_ASSUMED_BYTES
        return if (needed + RESERVE_BYTES <= freeBytes) Space.Enough else Space.Short(needed, freeBytes, estimated = known == null)
    }

    // ---- açılış uzlaştırması ----

    /** Media3'ün dizininde görünen bir indirme (kimlik = satır kimliği metni). */
    data class Managed(
        val id: String,
        val status: DownloadStatus,
        val downloadedBytes: Long,
        val totalBytes: Long,
    )

    sealed interface Fix {
        /** Satırın durumu/baytları Media3'tekine uyar. */
        data class UpdateRow(val rowId: Long, val status: DownloadStatus, val downloadedBytes: Long, val totalBytes: Long) : Fix

        /** Media3'te karşılığı olmayan satır: veri yok, satır anlamsız (ör. uygulama verisi/önbellek silindi). */
        data class DeleteRow(val rowId: Long) : Fix

        /** Satırı olmayan Media3 indirmesi: kimsenin göremeyeceği yer kaplamasın diye silinir. */
        data class RemoveManaged(val id: String) : Fix
    }

    /**
     * Uygulama açılışında satırlar ile Media3 dizinini eşitler. Doğruluk kaynağı Media3'tür (gerçek durum/bayt oradadır);
     * yalnızca farklı olan satırlar güncellenir.
     */
    fun reconcile(rows: List<DownloadEntity>, managed: List<Managed>): List<Fix> {
        val byId = managed.associateBy { it.id }
        val rowIds = rows.map { it.id.toString() }.toSet()
        return buildList {
            for (row in rows) {
                val m = byId[row.id.toString()]
                when {
                    m == null -> add(Fix.DeleteRow(row.id))
                    m.status != row.status || m.downloadedBytes != row.downloadedBytes || (m.totalBytes != row.totalBytes && m.totalBytes > 0) ->
                        add(Fix.UpdateRow(row.id, m.status, m.downloadedBytes, if (m.totalBytes > 0) m.totalBytes else row.totalBytes))
                }
            }
            for (m in managed) if (m.id !in rowIds) add(Fix.RemoveManaged(m.id))
        }
    }
}
