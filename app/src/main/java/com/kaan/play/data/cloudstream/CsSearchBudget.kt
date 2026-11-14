package com.kaan.play.data.cloudstream

import android.app.ActivityManager
import android.content.Context

/**
 * Kaynak aramasının (tüm açık CloudStream eklentileri) bellek bütçesi.
 *
 * Az bellekli cihazda (ör. 2 GB'lık TV kutusu) ~200 eklentiyle arama süreci 1 GB'a çıkarıp sistemin uygulamayı kapatmasına yol
 * açıyordu: aynı anda 6 eklenti, her yüklenen eklentinin kodu süreç boyunca bellekte, ve bot doğrulama
 * sayfasında gizli WebView açılması. Bu cihazlarda arama daha az eş zamanlı yapılır, yalnız arama için yüklenen eklentiler iş
 * bitince bırakılır, arama sürerken gizli doğrulama tarayıcısı açılmaz (site "doğrulama gerekiyor" diye işaretlenir) ve tek bir
 * yanıtın boyutu sınırlanır. Yeterli bellekli cihazda (telefon) davranış değişmez.
 *
 * Memory budget for the source search across all enabled CloudStream plugins. On low-memory devices the search runs with fewer
 * plugins at once, releases plugins it loaded only for the search, never opens the hidden challenge WebView while searching and
 * caps a single response's size. Devices with enough memory keep the previous behaviour.
 */
data class CsSearchBudget(
    /** Aynı anda aranan en çok eklenti sayısı (CloudStream eklentileri; JS eklentileri de en çok bu kadar, AppContainer). */
    val maxParallel: Int,
    /** Yalnız bu arama için yüklenen eklentiler iş bitince bırakılsın (kaynak bulan eklenti kalır). */
    val releaseAfterSearch: Boolean,
    /** Kaynak araması sürerken bot doğrulaması için gizli WebView açılmasın. */
    val skipChallengeSolve: Boolean,
    /** Eklentilerin tek bir HTTP yanıtı için üst sınır (bayt); null = sınırsız. */
    val maxResponseBytes: Long?,
    /**
     * Kaynak aramasında hiç sorulmayan eklentiler (adıyla, büyük/küçük harf duyarsız): çalışan kodu durdurulamayan ve tek
     * aramada yüzlerce MB yerel bellek ayıran eklentiler. Ana sayfa ve detayda kullanılmaya devam eder.
     */
    val skipPlugins: Set<String> = emptySet(),
    /**
     * Java belleği üst sınırın bu oranını aşmışken sıradaki eklenti başlatılmaz; çöp toplanıp en çok [HEAP_WAIT_MS] beklenir
     * (ölçüm: 3+3 eklenti aynı anda Java'yı 2 sn'de 121 → 233/256 MB'a çıkardı). null = beklenmez.
     */
    val heapStartFraction: Double? = null,
) {
    /** Kullanılan Java belleği [heapStartFraction] sınırının üstünde mi (saf, birim testli). */
    fun heapTight(usedBytes: Long, maxBytes: Long): Boolean =
        heapStartFraction != null && maxBytes > 0 && usedBytes > maxBytes * heapStartFraction

    companion object {
        /** Önceki davranış (telefon). */
        val Default = CsSearchBudget(maxParallel = 6, releaseAfterSearch = false, skipChallengeSolve = false, maxResponseBytes = null)

        /** Az bellekli cihaz. */
        val LowMemory = CsSearchBudget(
            maxParallel = 3, releaseAfterSearch = true, skipChallengeSolve = true, maxResponseBytes = 8L shl 20,
            // MoreShort'un araması (başlık haritası) 2 GB'lık kutuda 5 sn'de PSS'i 364 → 629 MB'a çıkardı; arama kesildikten sonra da
            // sürdü ve sistem uygulamayı kapattı. TV aramasında da aynı sebeple atlanıyor (TvSearch).
            skipPlugins = setOf("MoreShort"),
            heapStartFraction = 0.55,
        )

        /** Java belleği sıkışıkken sıradaki eklentinin en çok bekleyeceği süre; sonra yine de başlar. */
        const val HEAP_WAIT_MS = 10_000L

        /** Toplam RAM bunun altındaysa cihaz az bellekli sayılır (2 GB'lık TV kutuları; Play'in TV arabelleği eşiğiyle aynı). */
        const val LOW_MEMORY_TOTAL_BYTES = 3L * 1024 * 1024 * 1024

        /** Uygulama yığın sınıfı (MB) bunun altındaysa da az bellekli sayılır. */
        const val LOW_MEMORY_CLASS_MB = 192

        /** Saf karar (birim testli): sistemin düşük RAM bayrağı, uygulama yığın sınıfı (MB) ve toplam RAM (bayt). */
        fun of(isLowRamDevice: Boolean, memoryClassMb: Int, totalMemBytes: Long): CsSearchBudget =
            if (isLowRamDevice || memoryClassMb < LOW_MEMORY_CLASS_MB || totalMemBytes in 1 until LOW_MEMORY_TOTAL_BYTES) LowMemory
            else Default

        fun forDevice(context: Context): CsSearchBudget {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager ?: return Default
            val info = ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }
            return of(am.isLowRamDevice, am.memoryClass, info.totalMem)
        }
    }
}
