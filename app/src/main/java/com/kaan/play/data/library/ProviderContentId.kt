package com.kaan.play.data.library

import kotlin.math.abs

/**
 * TMDB'de bulunamayan eklenti içeriğinin kitaplık kimliği (izleme geçmişi, favoriler, silinenler; eşitleme bu kimlikle
 * birleştirir). Negatiftir, TMDB kimlikleriyle çakışmaz.
 *
 * Kalıcıdır: eklentinin cihaza özel satır numarası (`cs:N`) yerine eklenti TÜRÜ (`cs`) + normalleştirilmiş içerik adresinden
 * türetilir; aynı içerik her cihazda ve eklenti yeniden kurulsa da aynı kimliği alır. Eski biçim ([legacy]) numarayı da
 * içeriyordu: aynı dizi telefonda ve TV'de iki ayrı kayıt oluyor, bulutta yinelenen "devam et" kartı ve silinenlerin geri
 * gelmesi buradan çıkıyordu. Eski kayıtlar HistoryPluginRepair.migrateIds ile taşınır.
 *
 * Sitenin alan adı değişirse (ayna/yeni adres) kimlik de değişir: alan adı kimlikte kalır ki iki ayrı sitenin aynı yolu
 * birbirine karışmasın.
 */
object ProviderContentId {
    fun of(providerId: String, url: String): Int = negative("${kind(providerId)}:${normalize(url)}")

    /** 1.1.51 / TV 0.2.13 ve öncesinin kimliği (ProviderDetailScreen/TvDetailModel'deki eski hesap); yalnız geçiş için. */
    fun legacy(providerId: String, url: String): Int = negative("$providerId:$url")

    /** `cs:718` → `cs`, `nuvio_addon:3` → `nuvio_addon`. */
    fun kind(providerId: String): String = providerId.substringBefore(':')

    /** http(s) adresinde şema, `www.`, alan adının harf büyüklüğü ve sondaki `/` farkı yok sayılır; adres değilse olduğu gibi. */
    fun normalize(url: String): String {
        val trimmed = url.trim()
        val scheme = SCHEME.find(trimmed) ?: return trimmed
        val rest = trimmed.substring(scheme.range.last + 1)
        val hostPart = rest.substringBefore('/')
        val host = hostPart.lowercase().removePrefix("www.")
        return host + rest.substring(hostPart.length).trimEnd('/')
    }

    private val SCHEME = Regex("^https?://", RegexOption.IGNORE_CASE)

    private fun negative(key: String): Int = -(abs(key.hashCode()).coerceAtLeast(1))
}
