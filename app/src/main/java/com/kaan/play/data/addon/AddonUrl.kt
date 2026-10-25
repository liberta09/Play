package com.kaan.play.data.addon

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

object AddonUrl {

    private const val MANIFEST = "manifest.json"

    /**
     * Kullanıcının girdiği metni kanonik manifest adresine çevirir:
     * şema yoksa `https://` eklenir; yol bir `.json` dosyasıyla bitmiyorsa `manifest.json` eklenir; `#` kısmı atılır.
     * Yalnızca http/https kabul edilir. Sorgu parametreleri (token/auth gibi) manifest ve kaynak isteklerinde korunur.
     */
    fun normalize(input: String): String {
        val raw = input.trim()
        if (raw.isEmpty() || raw.any { it.isWhitespace() }) throw invalid()

        val withScheme = when {
            raw.startsWith("stremio://", ignoreCase = true) -> "https://" + raw.substring(10)
            raw.startsWith("cloudstreamrepo://", ignoreCase = true) -> "https://" + raw.substring(18)
            "://" in raw -> raw
            else -> "https://$raw"
        }
        val url = withScheme.toHttpUrlOrNull() ?: throw invalid()
        if (url.scheme != "http" && url.scheme != "https") throw invalid()

        val segments = url.pathSegments.toMutableList()
        if (segments.last().isEmpty()) segments.removeAt(segments.lastIndex)
        // Son parça zaten bir .json dosyasıysa (manifest.json, CloudStream repo.json vb.) olduğu gibi kalır;
        // yalnızca klasör gibi görünen adreslere manifest.json eklenir.
        val last = segments.lastOrNull()
        if (last == null || !last.endsWith(".json", ignoreCase = true)) segments += MANIFEST

        return url.newBuilder()
            .fragment(null)
            .encodedPath("/")
            .apply { segments.forEach { addEncodedPathSegment(it) } }
            .build()
            .toString()
    }

    /** Stremio endpoint'ini manifest'in aynı yoluna ve query token'larına göre üretir. */
    fun endpoint(manifestUrl: String, vararg routeSegments: String): okhttp3.HttpUrl? {
        val url = manifestUrl.toHttpUrlOrNull() ?: return null
        val segments = url.pathSegments.filter { it.isNotEmpty() }.toMutableList()
        if (segments.lastOrNull()?.endsWith(".json", ignoreCase = true) == true) segments.removeAt(segments.lastIndex)
        return url.newBuilder()
            .encodedPath("/")
            .apply {
                segments.forEach(::addPathSegment)
                routeSegments.forEach(::addPathSegment)
            }
            .build()
    }

    /** `https://host/yol/manifest.json?token=x` -> `https://host/yol?token=x`. */
    fun baseOf(manifestUrl: String): String {
        val url = manifestUrl.toHttpUrlOrNull() ?: return manifestUrl.substringBeforeLast('/')
        val segments = url.pathSegments.filter { it.isNotEmpty() }.toMutableList()
        if (segments.lastOrNull()?.endsWith(".json", ignoreCase = true) == true) segments.removeAt(segments.lastIndex)
        return url.newBuilder().encodedPath("/").apply { segments.forEach(::addPathSegment) }.build().toString().trimEnd('/')
    }

    private fun invalid() = AddonException(AddonException.Kind.InvalidUrl)
}
