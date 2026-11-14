package com.kaan.play.data.library

import android.content.Context
import com.kaan.play.data.model.MediaType

/**
 * Bu cihazda eklenti detayından eklenen favorinin eklenti + içerik adresi (favori satırı bunları taşımaz). Hiç izlenmemiş
 * eklenti favorisi Kütüphane'den bununla açılır; izlenmişse geçmiş satırı da yeter ([LibraryRepository.providerSourceOf]).
 * Wi-Fi eşitlemesi bunları karşı cihaza taşır (eklenti iç adıyla; karşı cihaz kendi eklenti numarasına çevirip buraya yazar).
 * TV de kendi favori açılış bilgilerini buraya kopyalar (tek ortak kayıt).
 */
class ProviderFavoriteRefs(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("play_provider_favorite_refs", Context.MODE_PRIVATE)

    fun put(type: MediaType, id: Int, providerId: String, url: String) {
        prefs.edit().putString(key(type, id), "$providerId\n$url").apply()
    }

    fun get(type: MediaType, id: Int): Pair<String, String>? =
        prefs.getString(key(type, id), null)?.split('\n', limit = 2)?.takeIf { it.size == 2 }?.let { it[0] to it[1] }

    /** Kayıtlı tüm açılış bilgileri: (tür, kimlik) → (eklenti, adres). Wi-Fi eşitlemesi karşı cihaza bunları gönderir. */
    fun all(): Map<Pair<MediaType, Int>, Pair<String, String>> = prefs.all.mapNotNull { (k, v) ->
        val id = k.substringBefore(':').toIntOrNull() ?: return@mapNotNull null
        val type = MediaType.entries.firstOrNull { it.name == k.substringAfter(':') } ?: return@mapNotNull null
        val parts = (v as? String)?.split('\n', limit = 2)?.takeIf { it.size == 2 } ?: return@mapNotNull null
        (type to id) to (parts[0] to parts[1])
    }.toMap()

    private fun key(type: MediaType, id: Int) = "$id:${type.name}"
}
