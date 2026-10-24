package com.kaan.play.data.stream

/** Listede önce yüksek çözünürlük, sonra büyük dosya; kalitesi/boyutu bilinmeyenler sonda. */
private data class DecoratedRanked(
    val stream: PlayableStream,
    val res: Int,
    val size: Long,
)

fun List<PlayableStream>.ranked(): List<PlayableStream> {
    if (size <= 1) return this
    return map {
        DecoratedRanked(it, it.resolution ?: 0, it.sizeBytes ?: 0L)
    }.sortedWith(
        compareByDescending<DecoratedRanked> { it.res }.thenByDescending { it.size }
    ).map { it.stream }
}

/**
 * En uygun kaynak: 1080p'ye kadar olanların en iyisi (4K çoğu cihazda takılır ve çok yer tutar); doğrudan bağlantı torrentten
 * önce gelir (torrent açılışı eş bulmayı bekler). Hiçbiri yoksa listenin başı. [ranked] sıralı liste bekler.
 */
fun List<PlayableStream>.recommended(): PlayableStream? {
    fun fits(s: PlayableStream) = (s.resolution ?: 0) in 1..1080
    return firstOrNull { fits(it) && !TorrentLinks.isMagnet(it.url) } ?: firstOrNull(::fits) ?: firstOrNull()
}
