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
}
