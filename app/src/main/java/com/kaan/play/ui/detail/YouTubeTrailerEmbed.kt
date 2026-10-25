package com.kaan.play.ui.detail

import java.util.Locale

private val YOUTUBE_VIDEO_ID = Regex("^[A-Za-z0-9_-]{11}$")

/** Returns the official privacy-enhanced embed URL; raw video IDs cannot inject query parameters. */
internal fun youtubeTrailerEmbedUrl(rawKey: String): String? {
    val key = rawKey.trim()
    if (!YOUTUBE_VIDEO_ID.matches(key)) return null
    return "https://www.youtube-nocookie.com/embed/$key?autoplay=1&playsinline=1&rel=0&controls=1&fs=0&enablejsapi=1&origin=https%3A%2F%2Fwww.youtube-nocookie.com"
}

/** Returns a fixed official YouTube embed document; raw video IDs cannot inject URLs or HTML. */
internal fun youtubeTrailerEmbedHtml(rawKey: String): String? {
    val url = youtubeTrailerEmbedUrl(rawKey) ?: return null
    val htmlUrl = url.replace("&", "&amp;")
    return """
        <!doctype html>
        <html><head>
          <meta name="viewport" content="width=device-width,initial-scale=1,maximum-scale=1" />
          <meta name="referrer" content="strict-origin-when-cross-origin" />
          <style>html,body{margin:0;width:100%;height:100%;background:#000;overflow:hidden}iframe{position:absolute;inset:0;width:100%;height:100%;border:0}</style>
        </head><body>
          <iframe src="$htmlUrl"
            title="YouTube video player"
            allow="autoplay; encrypted-media; gyroscope; picture-in-picture; web-share"
            referrerpolicy="strict-origin-when-cross-origin"></iframe>
        </body></html>
    """.trimIndent()
}

/** Embedded player internals use other HTTPS Google/YouTube hosts; restrict only top-level navigation. */
internal fun shouldBlockTrailerNavigation(isMainFrame: Boolean, scheme: String?, host: String?): Boolean {
    if (!isMainFrame) return false
    return !scheme.equals("https", ignoreCase = true) || host?.lowercase(Locale.ROOT) !in ALLOWED_EMBED_HOSTS
}

private val ALLOWED_EMBED_HOSTS = setOf("youtube-nocookie.com", "www.youtube-nocookie.com")
