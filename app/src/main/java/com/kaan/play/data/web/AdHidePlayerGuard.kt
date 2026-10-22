package com.kaan.play.data.web

/**
 * Video oynatıcı koruma kuralları ve JS güvenlik denetleyicisi (Android'den bağımsız, birim testli).
 *
 * Enjekte edilen CSS'in `video`, `iframe`, `.jwplayer`, `.plyr`, `[id*="player"]`
 * ve bunları içeren ata kutuları gizlemesini engeller:
 * 1. Kural seviyesinde: Riskli ve korumalı seçicileri filtreler.
 * 2. CSS seviyesinde: Oynatıcı elemanları için `display: revert !important` kuralı ekler.
 * 3. Çalışma zamanı JS seviyesinde: onPageFinished + 1,5 sn sonra sayfadaki video/oynatıcı
 *    öğelerinin ve ata kutularının computed display durumunu denetler; gizlenmişse stili kaldırır.
 */
object AdHidePlayerGuard {

    private val DANGEROUS_SELECTORS = setOf(
        """[class*="money"]""",
        ".ps-head-container",
        """div[class^="r_header_"]""",
    )

    /**
     * Seçicinin oynatıcıyı gizleme riski taşıyıp taşımadığını veya korumalı bir öğeyi
     * doğrudan hedefleyip hedeflemediğini denetler.
     */
    fun isProtectedOrDangerous(selector: String): Boolean {
        val s = selector.trim().lowercase()
        if (DANGEROUS_SELECTORS.any { s.contains(it) }) return true
        if (s == "video" || s.startsWith("video ") || s.startsWith("video.") || s.startsWith("video#") || s.startsWith("video[")) return true
        if (s == "iframe" || s.startsWith("iframe ") || s.startsWith("iframe.") || s.startsWith("iframe#") || s.startsWith("iframe[")) return true
        if (s.contains(".jwplayer") || s.contains(".plyr") || s.contains("player")) return true
        return false
    }

    /**
     * Listeden korumalı/riskli seçicileri süzer.
     */
    fun filterSafeSelectors(selectors: Collection<String>): List<String> {
        return selectors.filterNot { isProtectedOrDangerous(it) }
    }

    /**
     * Sayfa yüklendikten (onPageFinished + 1,5 sn) sonra çalıştırılacak
     * JavaScript koruma denetim betiğini üretir.
     *
     * Sayfadaki herhangi bir `video`, oynatıcı `iframe` ya da bunların herhangi bir ata
     * kutusunun getComputedStyle ile display === 'none' olup olmadığını denetler.
     * Eğer gizlenmişse Play'in enjekte ettiği <style id="__play_ad_hide__"> elemanını kaldırır
     * ve 'REMOVED' döner.
     */
    fun buildGuardScript(): String {
        return """
            (function() {
                try {
                    var styleEl = document.getElementById('__play_ad_hide__');
                    if (!styleEl) return 'NO_STYLE';

                    var targets = [];
                    var vids = document.querySelectorAll('video, iframe, .jwplayer, .plyr, [id*="player"], [class*="player"]');
                    for (var i = 0; i < vids.length; i++) {
                        targets.push(vids[i]);
                    }

                    var isHidden = function(el) {
                        while (el && el !== document.body && el !== document.documentElement) {
                            try {
                                var comp = window.getComputedStyle(el);
                                if (comp && comp.display === 'none') {
                                    return true;
                                }
                            } catch(e1) {}
                            el = el.parentElement;
                        }
                        return false;
                    };

                    for (var k = 0; k < targets.length; k++) {
                        if (isHidden(targets[k])) {
                            styleEl.remove();
                            return 'REMOVED';
                        }
                    }
                    return 'OK';
                } catch(e) {
                    return 'ERROR';
                }
            })();
        """.trimIndent()
    }
}
