package com.kaan.play.data.stream

/**
 * Bir kaynağın hangi içeriğe ait olduğunun damgası ve oturum boyunca "bu içerik değil" kararları (Android'den bağımsız).
 *
 * Her kaynak, birleşik sağlayıcıdan çıkarken istenen içeriğin anahtarıyla damgalanır ([PlayableStream.forKey]). Oynatıcı ve kaynak
 * listesi yalnızca kendi içeriğinin damgasını taşıyan kaynakları kabul eder: başka bir filmin/bölümün kaynakları (yedek listeye
 * karışsa bile) asla oynatılmaz.
 */
object StreamOwnership {
    /** İçerik anahtarı: tür, TMDB kimliği, sezon, bölüm (film için sezon/bölüm 0). */
    fun keyOf(request: StreamRequest): String =
        "${request.type.tmdbPath}:${request.tmdbId}:${request.season ?: 0}:${request.episode ?: 0}"

    /** Damgası yoksa (eski/yerel kaynak) kabul edilir; varsa [request]'in anahtarıyla aynı olmalıdır. */
    fun belongs(stream: PlayableStream, request: StreamRequest): Boolean =
        stream.forKey == null || stream.forKey == keyOf(request)

    fun stamp(stream: PlayableStream, request: StreamRequest): PlayableStream = stream.copy(forKey = keyOf(request))
}

/**
 * Oynatma sırasında içeriğe uymadığı anlaşılan kaynaklar ([com.kaan.play.data.match.ContentDurationRules]): içerik başına, bellek içi,
 * uygulama kapanınca gider (site düzelmiş olabilir). Aynı içerik yeniden açılınca bu kaynaklar listeye girmez.
 */
class StreamVerdicts(private val maxPerContent: Int = 200) {
    private val rejected = HashMap<String, LinkedHashSet<String>>()

    @Synchronized
    fun reject(key: String, url: String) {
        val set = rejected.getOrPut(key) { LinkedHashSet() }
        set.remove(url)
        set.add(url)
        while (set.size > maxPerContent) set.remove(set.first())
    }

    @Synchronized
    fun isRejected(key: String, url: String): Boolean = rejected[key]?.contains(url) == true
}
