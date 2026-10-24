package com.kaan.play.ui.sources

import com.kaan.play.data.stream.PlayableStream
import com.kaan.play.data.stream.StreamResult
import com.kaan.play.data.stream.StreamSource
import com.kaan.play.data.stream.ranked
import com.kaan.play.data.stream.recommended

/** Kaynak seçim listesinin durumu; addon cevapları geldikçe [plus] ile büyür. */
data class SourcesState(
    /** Sorulan addon'lar (sabit sıra); cevaplardan önce bilinir. Sağlayıcı bilmiyorsa boş, sıra cevaplardan türer. */
    val asked: List<StreamSource> = emptyList(),
    /** Sıralı (yüksek kalite önce). */
    val streams: List<PlayableStream> = emptyList(),
    /** "Yanıt vermedi" bilgisi taşıyan addon'lar. */
    val failed: List<StreamSource> = emptyList(),
    /** Cevabı gelmiş (kaynaklı, boş ya da başarısız) addon kimlikleri. */
    val answered: Set<Long> = emptySet(),
    val loading: Boolean = true,
) {
    val recommended: PlayableStream? get() = streams.recommended()

    /** Filtre çipleri ve bölümler için addon'lar: sorulanlar, sonra (listede olmayan) cevap verenler. */
    val addons: List<StreamSource>
        get() = (asked + streams.map { it.source } + failed).distinctBy { it.addonId }

    /** Henüz cevap vermemiş addon'lar (yükleme sürerken). */
    val pending: List<StreamSource>
        get() = if (loading) asked.filter { it.addonId !in answered } else emptyList()

    fun streamsOf(addonId: Long): List<PlayableStream> = streams.filter { it.source.addonId == addonId }

    operator fun plus(result: StreamResult): SourcesState = when (result) {
        is StreamResult.Found -> copy(
            streams = (streams + result.streams).distinctBy { it.url }.ranked(),
            answered = answered + result.source.addonId,
        )
        is StreamResult.Failed -> copy(failed = failed + result.source, answered = answered + result.source.addonId)
    }

    fun finished() = copy(loading = false)
}
