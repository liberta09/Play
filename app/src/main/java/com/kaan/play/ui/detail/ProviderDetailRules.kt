package com.kaan.play.ui.detail

/**
 * Sağlayıcı detay ekranında içeriğin canlı (tv/channel), dizi veya tek parça oynatılabilir
 * olup olmadığını belirleyen kurallar.
 */
object ProviderDetailRules {
    /**
     * Addon meta türü "tv" veya "channel" olan, ya da meta içinde bölüm listesi (videos)
     * boş olan içerikler canlı veya tek parça oynatılabilir sayılır.
     */
    fun isLive(type: String?, videosCount: Int): Boolean {
        val t = type?.lowercase().orEmpty()
        return t in listOf("tv", "channel") || videosCount == 0
    }

    /**
     * Canlı olmayan ve açıkça "series" türüne ait olan içerikler dizidir; sezon/bölüm seçici gerektirir.
     */
    fun isSeries(type: String?, videosCount: Int): Boolean {
        val t = type?.lowercase().orEmpty()
        return !isLive(t, videosCount) && t == "series"
    }
}
