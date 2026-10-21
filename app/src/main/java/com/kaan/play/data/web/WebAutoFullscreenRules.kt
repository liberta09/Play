package com.kaan.play.data.web

/**
 * TV / kumanda kipinde web sayfasındaki videoların oynatılmaya başlandığında otomatik tam ekrana
 * geçirilmesi kuralları ve JavaScript yerleştirme betiği (K12, K13).
 * Android'den bağımsız birim testli mantık.
 */
object WebAutoFullscreenRules {

    /**
     * Otomatik tam ekran betiği.
     * Ağ isteği yapmaz, veri toplamaz, console.log yazmaz.
     * Capture aşamasında 'play' olayını yakalar; yalnızca kullanıcı etkinliğiyle (userActivation.isActive)
     * başlayan ve ana video kriterlerini (süre >= 60s veya canlı; genişlik >= %40) sağlayan videoları
     * tam ekrana geçirir.
     */
    val AUTO_FULLSCREEN_SCRIPT: String = """
        (function() {
          document.addEventListener('play', function(e) {
            try {
              var v = e.target;
              if (!v || v.tagName !== 'VIDEO') return;
              if (document.fullscreenElement) return;

              var userActive = false;
              try {
                if (navigator.userActivation && navigator.userActivation.isActive) {
                  userActive = true;
                }
              } catch (_) {}
              if (!userActive) return;

              var duration = v.duration;
              var validDuration = isNaN(duration) || duration === Infinity || duration >= 60;
              if (!validDuration) return;

              var rect = v.getBoundingClientRect();
              var winWidth = window.innerWidth || document.documentElement.clientWidth || 1;
              if ((rect.width / winWidth) < 0.4) return;

              if (v.requestFullscreen) {
                v.requestFullscreen().catch(function() {});
              } else if (v.webkitRequestFullscreen) {
                v.webkitRequestFullscreen();
              }
            } catch (_) {}
          }, true);
        })();
    """.trimIndent()

    /**
     * Otomatik tam ekran betiğinin sayfaya yerleştirilip yerleştirilmeyeceğini belirler (K12, K9).
     *
     * @param isRemoteMode Kumanda kipinde olunup olunmadığı (telefonda false olmalıdır)
     * @param isDocumentStartScriptSupported WebViewFeature.DOCUMENT_START_SCRIPT özelliğinin cihazda desteklenip desteklenmediği
     * @return Yalnızca kumanda kipinde ve özellik destekleniyorsa true döner.
     */
    fun shouldInjectScript(
        isRemoteMode: Boolean,
        isDocumentStartScriptSupported: Boolean
    ): Boolean {
        return isRemoteMode && isDocumentStartScriptSupported
    }
}
