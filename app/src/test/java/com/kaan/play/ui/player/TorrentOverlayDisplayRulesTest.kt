package com.kaan.play.ui.player

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TorrentOverlayDisplayRulesTest {

    @Test
    fun `ilk acilis torrent kaynaginda ekran cikar`() {
        val show = TorrentOverlayDisplayRules.shouldShow(
            isTorrent = true,
            action = TorrentOverlayDisplayRules.PlaybackAction.InitialOpen,
            firstFrameRendered = false,
        )
        assertTrue("İlk açılışta torrent için ekran çıkmalı", show)

        val eval = TorrentOverlayDisplayRules.evaluate(
            isTorrent = true,
            isNewStream = true,
            firstFrameRendered = false,
        )
        assertTrue(eval)
    }

    @Test
    fun `farkli kaynaga geciste yeni torrent baslarken ekran cikar`() {
        val show = TorrentOverlayDisplayRules.shouldShow(
            isTorrent = true,
            action = TorrentOverlayDisplayRules.PlaybackAction.SwitchSource,
            firstFrameRendered = false,
        )
        assertTrue("Farklı torrent kaynağına geçişte ekran çıkmalı", show)

        val isNew = TorrentOverlayDisplayRules.isNewStream(
            currentStreamUrl = "magnet:?xt=urn:btih:2222222222222222222222222222222222222222",
            lastRenderedStreamUrl = "magnet:?xt=urn:btih:1111111111111111111111111111111111111111",
        )
        assertTrue("Farklı URL yeni akış sayılmalı", isNew)
        assertTrue(TorrentOverlayDisplayRules.evaluate(isTorrent = true, isNewStream = isNew, firstFrameRendered = false))
    }

    @Test
    fun `ayni kaynak yeniden prepare edilirken ekran cikmaz`() {
        val show = TorrentOverlayDisplayRules.shouldShow(
            isTorrent = true,
            action = TorrentOverlayDisplayRules.PlaybackAction.RetrySameStream,
            firstFrameRendered = false,
        )
        assertFalse("Aynı kaynak yeniden hazırlanırken ekran çıkmamalı", show)

        val isNew = TorrentOverlayDisplayRules.isNewStream(
            currentStreamUrl = "magnet:?xt=urn:btih:1111111111111111111111111111111111111111",
            lastRenderedStreamUrl = "magnet:?xt=urn:btih:1111111111111111111111111111111111111111",
        )
        assertFalse("Aynı URL yeni akış sayılmamalı", isNew)
        assertFalse(TorrentOverlayDisplayRules.evaluate(isTorrent = true, isNewStream = isNew, firstFrameRendered = false, isSameStreamRetry = true))
    }

    @Test
    fun `ayni kaynakta ileri geri sarma yapilirken ekran cikmaz`() {
        val show = TorrentOverlayDisplayRules.shouldShow(
            isTorrent = true,
            action = TorrentOverlayDisplayRules.PlaybackAction.Seek,
            firstFrameRendered = false,
        )
        assertFalse("Sarma yapılırken ekran çıkmamalı", show)
        assertFalse(TorrentOverlayDisplayRules.evaluate(isTorrent = true, isNewStream = false, firstFrameRendered = false, isSeeking = true))
    }

    @Test
    fun `oynama sirasinda gecici tamponlamada ekran cikmaz`() {
        val show = TorrentOverlayDisplayRules.shouldShow(
            isTorrent = true,
            action = TorrentOverlayDisplayRules.PlaybackAction.BufferingDuringPlayback,
            firstFrameRendered = false,
        )
        assertFalse("Oynarken tamponlamada ekran çıkmamalı", show)
        assertFalse(TorrentOverlayDisplayRules.evaluate(isTorrent = true, isNewStream = false, firstFrameRendered = false, isBufferingDuringPlayback = true))
    }

    @Test
    fun `dogrudan kaynakta ekran asla cikmaz`() {
        val showInitial = TorrentOverlayDisplayRules.shouldShow(
            isTorrent = false,
            action = TorrentOverlayDisplayRules.PlaybackAction.InitialOpen,
            firstFrameRendered = false,
        )
        assertFalse("Doğrudan kaynakta ilk açılışta çıkmamalı", showInitial)

        val showSwitch = TorrentOverlayDisplayRules.shouldShow(
            isTorrent = false,
            action = TorrentOverlayDisplayRules.PlaybackAction.SwitchSource,
            firstFrameRendered = false,
        )
        assertFalse("Doğrudan kaynakta geçişte çıkmamalı", showSwitch)

        assertFalse(TorrentOverlayDisplayRules.evaluate(isTorrent = false, isNewStream = true, firstFrameRendered = false))
    }

    @Test
    fun `ilk kare geldikten sonra cikis animasyonu surerken ekran gosterilir`() {
        val show = TorrentOverlayDisplayRules.shouldShow(
            isTorrent = true,
            action = TorrentOverlayDisplayRules.PlaybackAction.InitialOpen,
            firstFrameRendered = true,
            isDismissing = true,
        )
        assertTrue("Çıkış animasyonu sürerken ekran görünür kalmalı", show)

        val eval = TorrentOverlayDisplayRules.evaluate(
            isTorrent = true,
            isNewStream = true,
            firstFrameRendered = true,
            isDismissing = true,
        )
        assertTrue("Çıkış animasyonu sürerken evaluate true dönmeli", eval)
    }

    @Test
    fun `ilk kare cizilip cikis animasyonu bittikten sonra ekran artik gosterilmez`() {
        assertFalse(
            TorrentOverlayDisplayRules.shouldShow(
                isTorrent = true,
                action = TorrentOverlayDisplayRules.PlaybackAction.InitialOpen,
                firstFrameRendered = true,
                isDismissing = false,
            )
        )
        assertFalse(
            TorrentOverlayDisplayRules.evaluate(
                isTorrent = true,
                isNewStream = true,
                firstFrameRendered = true,
                isDismissing = false,
            )
        )
    }

    @Test
    fun `isNewStream edge cases`() {
        assertFalse("currentStreamUrl null ise false dönmeli", TorrentOverlayDisplayRules.isNewStream(null, "some_url"))
        assertTrue("lastRenderedStreamUrl null ise ilk acilis true donmeli", TorrentOverlayDisplayRules.isNewStream("url1", null))
        assertFalse("Aynı URLler false dönmeli", TorrentOverlayDisplayRules.isNewStream("url1", "url1"))
        assertTrue("Farklı URLler true dönmeli", TorrentOverlayDisplayRules.isNewStream("url2", "url1"))
    }

    // ==========================================
    // Saf Durum Sınıfı (TorrentOverlayState) Geçiş Testleri
    // ==========================================

    @Test
    fun `durum gecisi - direct ten torrente geciste ekran cikar ve dogru sonlanir`() {
        var state = TorrentOverlayDisplayRules.TorrentOverlayState()
        
        // 1. Doğrudan (HTTP) kaynak başlar
        state = state.onStreamSelected("https://cdn.example.com/stream.m3u8", isTorrent = false)
        assertFalse("Doğrudan kaynakta yükleme ekranı gösterilmemeli", state.shouldShow())
        
        // Doğrudan kaynağın ilk karesi gelir
        state = state.onFirstFrameRendered()
        assertFalse("Doğrudan kaynakta isDismissing tetiklenmemeli", state.isDismissing)
        assertFalse("Doğrudan kaynakta ekran çıkmamalı", state.shouldShow())
        
        // 2. Kullanıcı Kaynak panelinden Torrent (Torrentio/magnet) seçer
        state = state.onStreamSelected("magnet:?xt=urn:btih:fightclub111", isTorrent = true)
        assertTrue("Torrent kaynağına geçildiğinde ekran HEMEN görünür olmalı", state.shouldShow())
        assertFalse("Yeni akışta dismissed sıfırlanmalı", state.dismissed)
        assertFalse("Yeni akışta isDismissing sıfırlanmalı", state.isDismissing)
        
        // 3. Torrent ilk karesi çizildi -> animasyon fazı
        state = state.onFirstFrameRendered()
        assertTrue("İlk kare geldiğinde çıkış animasyonu fazına geçmeli", state.isDismissing)
        assertTrue("Çıkış animasyonu sürerken ekran görünür kalmalı", state.shouldShow())
        
        // 4. Animasyon bitti
        state = state.onDismissFinished()
        assertTrue("Animasyon bitince dismissed true olmalı", state.dismissed)
        assertFalse("Animasyon bitince ekran kapanmalı", state.shouldShow())
    }

    @Test
    fun `durum gecisi - torrent ten baska bir torrente geciste ekran tekrar cikar`() {
        var state = TorrentOverlayDisplayRules.TorrentOverlayState()
        
        // 1. Torrent 1 açılır ve biter
        state = state.onStreamSelected("magnet:?xt=urn:btih:torrent1", isTorrent = true)
        assertTrue(state.shouldShow())
        state = state.onFirstFrameRendered()
        state = state.onDismissFinished()
        assertFalse("Torrent 1 bittiğinde ekran kapalı olmalı", state.shouldShow())
        
        // 2. Farklı bir Torrent 2'ye geçiş yapılır
        state = state.onStreamSelected("magnet:?xt=urn:btih:torrent2", isTorrent = true)
        assertTrue("Farklı torrent seçildiğinde ekran TEKRAR görünür olmalı", state.shouldShow())
        assertFalse("Yeni torrent akışında dismissed false olmalı", state.dismissed)
        
        // Torrent 2 ilk karesi ve çıkışı
        state = state.onFirstFrameRendered()
        assertTrue(state.shouldShow())
        state = state.onDismissFinished()
        assertFalse(state.shouldShow())
    }

    @Test
    fun `durum gecisi - torrent ten direct e sonra tekrar torrente gecis`() {
        var state = TorrentOverlayDisplayRules.TorrentOverlayState()
        
        // 1. Torrent 1 tamamlandı
        state = state.onStreamSelected("magnet:?xt=urn:btih:torrent1", isTorrent = true)
        state = state.onFirstFrameRendered()
        state = state.onDismissFinished()
        assertFalse(state.shouldShow())
        
        // 2. Doğrudan kaynağa geçildi
        state = state.onStreamSelected("https://cdn.example.com/direct.mp4", isTorrent = false)
        assertFalse("Doğrudan kaynakta ekran çıkmaz", state.shouldShow())
        state = state.onFirstFrameRendered()
        
        // 3. Tekrar Torrent 1'e dönüldü
        state = state.onStreamSelected("magnet:?xt=urn:btih:torrent1", isTorrent = true)
        assertTrue("Doğrudan kaynaktan torrente dönüldüğünde ekran tekrar açılmalı", state.shouldShow())
        assertFalse(state.dismissed)
    }

    @Test
    fun `durum gecisi - ayni torrent icinde seek yapildiginda ekran ASLA cikmaz`() {
        var state = TorrentOverlayDisplayRules.TorrentOverlayState()
        
        // Torrent 1 normal açıldı ve ilk karesi oynatıldı
        state = state.onStreamSelected("magnet:?xt=urn:btih:torrent1", isTorrent = true)
        state = state.onFirstFrameRendered()
        state = state.onDismissFinished()
        assertFalse(state.shouldShow())
        
        // Kullanıcı 30 saniye ileri sarar
        assertFalse("Seek sırasında ekran ÇIKMAMALI", state.shouldShow(isSeeking = true))
        assertFalse("Seek bittikten sonra da ekran ÇIKMAMALI", state.shouldShow(isSeeking = false))
        
        // Sarma sırasında aynı streamUrl tekrar iletilirse
        state = state.onStreamSelected("magnet:?xt=urn:btih:torrent1", isTorrent = true)
        assertFalse("Aynı URL ile iletilen akışta dismissed korunmalı ve ekran ÇIKMAMALI", state.shouldShow())
    }

    @Test
    fun `durum gecisi - ayni akis yeniden hazirlanirken (retry) ekran cikmaz`() {
        var state = TorrentOverlayDisplayRules.TorrentOverlayState()
        
        // Torrent açıldı ve oynuyor
        state = state.onStreamSelected("magnet:?xt=urn:btih:torrent1", isTorrent = true)
        state = state.onFirstFrameRendered()
        state = state.onDismissFinished()
        
        // MidPlaybackRecovery aynı akışı tekrar yükler
        state = state.onStreamSelected("magnet:?xt=urn:btih:torrent1", isTorrent = true)
        assertFalse("Aynı akış retry edildiğinde ekran ÇIKMAMALI", state.shouldShow())
    }

    @Test
    fun `durum gecisi - oynatma sirasinda tamponlamada ekran cikmaz`() {
        var state = TorrentOverlayDisplayRules.TorrentOverlayState()
        
        state = state.onStreamSelected("magnet:?xt=urn:btih:torrent1", isTorrent = true)
        state = state.onFirstFrameRendered()
        state = state.onDismissFinished()
        
        assertFalse("Oynatma sırasında tamponlama olurken özel yükleme ekranı çıkmamalı", state.shouldShow(isBuffering = true))
    }
}
