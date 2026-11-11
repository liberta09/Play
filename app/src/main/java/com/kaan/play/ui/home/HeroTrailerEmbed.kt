package com.kaan.play.ui.home

import java.util.Locale

private val YOUTUBE_VIDEO_ID = Regex("^[A-Za-z0-9_-]{11}$")
private val ALLOWED_EMBED_HOSTS = setOf("youtube-nocookie.com", "www.youtube-nocookie.com")

/**
 * Ana sayfa karuseli için sessiz YouTube fragman embed oluşturucu.
 * Güvenlik kuralları: youtube-nocookie, 11 karakter video ID doğrulaması, ana çerçeve gezinme engeli.
 */
object HeroTrailerEmbed {

    fun isValidKey(rawKey: String?): Boolean =
        !rawKey.isNullOrBlank() && YOUTUBE_VIDEO_ID.matches(rawKey.trim())

    /**
     * Sessiz YouTube IFrame API dokümanı.
     * Parametreler: mute=1, controls=0, playsinline=1, rel=0, fs=0, disablekb=1, enablejsapi=1.
     * PLAYING olunca window.HeroTrailerBridge.onPlaying() çağrılır.
     * ENDED olunca window.HeroTrailerBridge.onEnded() çağrılır.
     * Hata olunca window.HeroTrailerBridge.onError() çağrılır.
     */
    fun silentTrailerHtml(rawKey: String): String? {
        val key = rawKey.trim()
        if (!YOUTUBE_VIDEO_ID.matches(key)) return null

        return """
            <!doctype html>
            <html><head>
              <meta name="viewport" content="width=device-width,initial-scale=1,maximum-scale=1,user-scalable=no" />
              <meta name="referrer" content="strict-origin-when-cross-origin" />
              <style>
                html,body{margin:0;padding:0;position:fixed;inset:0;width:100%;height:100%;background:transparent !important;overflow:hidden;pointer-events:none;-webkit-touch-callout:none;-webkit-user-select:none;user-select:none;}
                #player,iframe{position:absolute;inset:0;width:100%;height:100%;min-width:100%;min-height:100%;border:0;pointer-events:none;background:transparent !important;}
              </style>
            </head><body>
              <div id="player"></div>
              <script>
                var tag = document.createElement('script');
                tag.src = "https://www.youtube.com/iframe_api";
                var firstScriptTag = document.getElementsByTagName('script')[0];
                firstScriptTag.parentNode.insertBefore(tag, firstScriptTag);

                var player;
                var frameCheckTimer = null;
                function onYouTubeIframeAPIReady() {
                  if (window.HeroTrailerBridge && typeof window.HeroTrailerBridge.onApiReady === 'function') {
                    window.HeroTrailerBridge.onApiReady();
                  }
                  player = new YT.Player('player', {
                    width: '100%',
                    height: '100%',
                    videoId: '$key',
                    host: 'https://www.youtube-nocookie.com',
                    playerVars: {
                      'autoplay': 1,
                      'controls': 0,
                      'mute': 1,
                      'playsinline': 1,
                      'rel': 0,
                      'fs': 0,
                      'disablekb': 1,
                      'enablejsapi': 1,
                      'iv_load_policy': 3,
                      'modestbranding': 1,
                      'origin': 'https://www.youtube-nocookie.com'
                    },
                    events: {
                      'onReady': onPlayerReady,
                      'onStateChange': onPlayerStateChange,
                      'onError': onPlayerError
                    }
                  });
                }
                function onPlayerReady(event) {
                  if (window.HeroTrailerBridge && typeof window.HeroTrailerBridge.onPlayerReady === 'function') {
                    window.HeroTrailerBridge.onPlayerReady();
                  }
                  try { event.target.mute(); event.target.playVideo(); } catch(e){}
                }
                function checkRealFrame() {
                  try {
                    if (player && typeof player.getCurrentTime === 'function') {
                      var t = player.getCurrentTime();
                      if (t > 0) {
                        if (window.HeroTrailerBridge) { window.HeroTrailerBridge.onPlaying(); }
                        return;
                      }
                    }
                  } catch(e){}
                  frameCheckTimer = setTimeout(checkRealFrame, 250);
                }
                function onPlayerStateChange(event) {
                  try {
                    if (window.HeroTrailerBridge && typeof window.HeroTrailerBridge.onStateChange === 'function') {
                      window.HeroTrailerBridge.onStateChange(event.data);
                    }
                    if (event.data === 1) {
                      if (frameCheckTimer) { clearTimeout(frameCheckTimer); }
                      frameCheckTimer = setTimeout(checkRealFrame, 500);
                    } else if (event.data === 0) {
                      if (frameCheckTimer) { clearTimeout(frameCheckTimer); }
                      if (window.HeroTrailerBridge) { window.HeroTrailerBridge.onEnded(); }
                    }
                  } catch(e){}
                }
                function onPlayerError(event) {
                  try {
                    if (frameCheckTimer) { clearTimeout(frameCheckTimer); }
                    if (window.HeroTrailerBridge) {
                      window.HeroTrailerBridge.onError(event.data || 0);
                      window.HeroTrailerBridge.onError();
                    }
                  } catch(e){}
                }
              </script>
            </body></html>
        """.trimIndent()
    }

    /**
     * Ana çerçevede youtube-nocookie dışındaki gezinmeleri engeller.
     */
    fun shouldBlockTrailerNavigation(isMainFrame: Boolean, scheme: String?, host: String?): Boolean {
        if (!isMainFrame) return false
        return !scheme.equals("https", ignoreCase = true) || host?.lowercase(Locale.ROOT) !in ALLOWED_EMBED_HOSTS
    }
}
