package com.kaan.play.ui.components

/**
 * Afiş satırlarında yatay ve dikey görsel önbelleğe alma (prefetch) pencerelerini
 * hesaplayan Android ve Compose bağımsız saf kural sınıfı.
 */
object PosterPrefetchRules {

    const val DEFAULT_HORIZONTAL_WINDOW = 4
    const val DEFAULT_VERTICAL_WINDOW = 6
    const val MAX_CONCURRENT_PREFETCH = 4

    /**
     * Bir satırda odaklanan veya görünür olan kart değiştiğinde,
     * sağındaki ve solundaki en çok [windowSize] kartın indekslerini döner.
     *
     * Öncelik kuralı: D-pad ile gezinme yönüne göre mevcut karta en yakın
     * komşular önce gelir (örn. +1, -1, +2, -2, +3, -3, +4, -4).
     *
     * @param currentIndex Odaklanan kartın indeksi (0-tabanlı)
     * @param itemCount Satırdaki toplam kart sayısı
     * @param windowSize Sağa ve sola taranacak azami kart sayısı (varsayılan: 4)
     * @return Prefetch edilecek geçerli indeksler listesi
     */
    fun calculateHorizontalIndices(
        currentIndex: Int,
        itemCount: Int,
        windowSize: Int = DEFAULT_HORIZONTAL_WINDOW,
    ): List<Int> {
        if (itemCount <= 1 || currentIndex !in 0 until itemCount || windowSize <= 0) {
            return emptyList()
        }

        val result = ArrayList<Int>(windowSize * 2)
        for (step in 1..windowSize) {
            val right = currentIndex + step
            if (right < itemCount) {
                result.add(right)
            }
            val left = currentIndex - step
            if (left >= 0) {
                result.add(left)
            }
        }
        return result
    }

    /**
     * Odak bir satırdayken, bir alttaki satırın ilk [windowSize] kartının
     * indekslerini döner.
     *
     * @param nextRowCount Bir alttaki satırın toplam kart sayısı
     * @param windowSize Alttaki satırdan yüklenecek azami kart sayısı (varsayılan: 6)
     * @return Prefetch edilecek indeksler (0..min(windowSize, nextRowCount)-1)
     */
    fun calculateVerticalIndices(
        nextRowCount: Int,
        windowSize: Int = DEFAULT_VERTICAL_WINDOW,
    ): List<Int> {
        if (nextRowCount <= 0 || windowSize <= 0) {
            return emptyList()
        }
        val count = minOf(nextRowCount, windowSize)
        return (0 until count).toList()
    }

    /**
     * Mevcut satır indeksine göre bir sonraki satırın indeksini belirler.
     * Son satırda ise veya mevcut indeks geçersizse null döner.
     */
    fun nextRowIndex(currentRowIndex: Int, totalRows: Int): Int? {
        if (currentRowIndex !in 0 until totalRows) return null
        val next = currentRowIndex + 1
        return if (next in 0 until totalRows) next else null
    }
}
