@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.kaan.play.ui.player

import com.kaan.play.data.model.MediaType
import com.kaan.play.data.stream.FailureReason
import com.kaan.play.data.stream.PlayableStream
import com.kaan.play.data.stream.StreamRequest
import com.kaan.play.data.stream.StreamSource
import com.kaan.play.data.stream.StreamSubtitle
import com.kaan.play.data.subtitle.FakeSubtitleDao
import com.kaan.play.data.subtitle.OnlineSubtitleProvider
import com.kaan.play.data.subtitle.SubtitleKind
import com.kaan.play.data.subtitle.SubtitleCue
import com.kaan.play.data.subtitle.SubtitleItem
import com.kaan.play.data.subtitle.SubtitleLoad
import com.kaan.play.data.subtitle.SubtitleMemory
import com.kaan.play.data.subtitle.SubtitleProvider
import com.kaan.play.data.subtitle.SubtitleResult
import com.kaan.play.data.subtitle.SubtitleSelection
import com.kaan.play.data.subtitle.SubtitleTrack
import com.kaan.play.data.subtitle.TrackLoader
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Seçim, hafıza ve senkron mantığı; oynatıcı (Media3) ve ağ sahtedir, zaman sanaldır. */
class SubtitleControllerTest {

    private class FakeHost : SubtitleHost {
        var position = 0L
        var embedded: List<EmbeddedSubtitle> = emptyList()
        val selectedEmbedded = mutableListOf<Int>()
        var embeddedDisabled = 0

        override fun positionMs() = position
        override fun embeddedSubtitles() = embedded
        override fun selectEmbeddedSubtitle(index: Int) { selectedEmbedded += index }
        override fun disableEmbeddedSubtitles() { embeddedDisabled++ }
    }

    private class FakeLoader : TrackLoader {
        val results = HashMap<String, SubtitleLoad>()
        val requested = mutableListOf<String>()
        override suspend fun load(track: SubtitleTrack): SubtitleLoad {
            requested += track.url
            return results[track.url] ?: SubtitleLoad.Failed(SubtitleLoad.Reason.Http)
        }
    }

    private val request = StreamRequest(1396, "tt0903747", MediaType.Tv, season = 1, episode = 2)
    private val src = StreamSource(7, "Akış Addon")

    private fun loaded(vararg cues: SubtitleCue) = SubtitleLoad.Loaded(cues.toList(), "UTF-8", fromCache = false)

    private fun stream(vararg subs: Pair<String, String?>) = PlayableStream(
        source = src, url = "https://v/1.mp4", resolution = 1080, sizeBytes = null, headers = emptyMap(),
        subtitles = subs.map { (url, lang) -> StreamSubtitle(url, lang) }, language = null,
    )

    private inner class Env(
        scope: TestScope,
        val dao: FakeSubtitleDao = FakeSubtitleDao(),
        request: StreamRequest = this@SubtitleControllerTest.request,
        app: String = "tr",
    ) {
        val host = FakeHost()
        val loader = FakeLoader()
        val incoming = Channel<SubtitleResult>(Channel.UNLIMITED)
        val memory = SubtitleMemory(dao)
        var shown: List<SubtitleCue> = emptyList()
        val controller = SubtitleController(
            scope = scope.backgroundScope,
            playback = host,
            request = request,
            loader = loader,
            memory = memory,
            provider = SubtitleProvider { incoming.receiveAsFlow() },
            preferredLanguage = { app },
            persist = scope.backgroundScope,
            io = kotlinx.coroutines.test.StandardTestDispatcher(scope.testScheduler),
        ).also { it.attach { cues -> shown = cues } }

        fun addon(name: String, vararg subs: Pair<String, String?>) {
            incoming.trySend(
                SubtitleResult.Found(StreamSource(100, name), subs.map { (u, l) -> SubtitleTrack(u, com.kaan.play.data.subtitle.SubtitleLanguages.normalize(l), name) }),
            )
        }

        fun selectedUrl() = (controller.selected as? SubtitleItem.External)?.track?.url
    }

    private val hello = SubtitleCue(1_000, 3_000, "Merhaba")

    @Test
    fun autoSelectsAppLanguageOverEnglishThenShowsTheCueAtThePlayerPosition() = runTest {
        val env = Env(this)
        env.loader.results["https://s/tr.srt"] = loaded(hello)
        env.controller.setStream(stream("https://s/en.srt" to "eng", "https://s/tr.srt" to "tur"))
        env.controller.start()
        runCurrent()

        assertEquals("https://s/tr.srt", env.selectedUrl())
        assertEquals(SubtitleStatus.Idle, env.controller.status)
        assertEquals(listOf("https://s/tr.srt"), env.loader.requested) // yalnızca seçilen indirilir

        env.host.position = 500
        advanceTimeBy(100)
        assertTrue(env.shown.isEmpty())
        env.host.position = 1_500
        advanceTimeBy(100)
        assertEquals(listOf("Merhaba"), env.shown.map { it.text })
        env.host.position = 3_100
        advanceTimeBy(100)
        assertTrue(env.shown.isEmpty())
    }

    @Test
    fun fallsBackToEnglishAndStaysOffWhenNothingMatches() = runTest {
        val en = Env(this)
        en.loader.results["https://s/en.srt"] = loaded(hello)
        en.controller.setStream(stream("https://s/en.srt" to "eng", "https://s/fr.srt" to "fre"))
        en.controller.start()
        runCurrent()
        assertEquals("https://s/en.srt", en.selectedUrl())

        val none = Env(this)
        none.controller.setStream(stream("https://s/fr.srt" to "fre", "https://s/xx.srt" to null))
        none.controller.start()
        runCurrent()
        assertNull(none.controller.selected)
        assertTrue(none.loader.requested.isEmpty())
    }

    @Test
    fun lateArrivingBetterLanguageUpgradesTheSelectionButNeverDowngradesIt() = runTest {
        val env = Env(this)
        env.loader.results["https://s/en.srt"] = loaded(hello)
        env.loader.results["https://s/tr.srt"] = loaded(hello)
        env.loader.results["https://s/en2.srt"] = loaded(hello)
        env.controller.start()
        runCurrent()
        assertNull(env.controller.selected)
        assertTrue(env.controller.searching)

        env.addon("Altyazıcı", "https://s/en.srt" to "eng")
        runCurrent()
        assertEquals("https://s/en.srt", env.selectedUrl())

        env.addon("Altyazıcı", "https://s/tr.srt" to "tur") // daha iyi dil geç geldi
        runCurrent()
        assertEquals("https://s/tr.srt", env.selectedUrl())

        env.addon("Altyazıcı", "https://s/en2.srt" to "eng") // daha kötü: seçim değişmez
        runCurrent()
        assertEquals("https://s/tr.srt", env.selectedUrl())

        env.incoming.close()
        runCurrent()
        assertTrue(!env.controller.searching)
        assertEquals(3, env.controller.items.size)
    }

    @Test
    fun userChoiceIsRespectedAndRememberedForTheWholeSeries() = runTest {
        val dao = FakeSubtitleDao()
        val env = Env(this, dao)
        env.loader.results["https://s/en.srt"] = loaded(hello)
        env.loader.results["https://s/tr.srt"] = loaded(hello)
        env.controller.setStream(stream("https://s/en.srt" to "eng"))
        env.controller.start()
        runCurrent()
        assertEquals("https://s/en.srt", env.selectedUrl())

        // Kullanıcı İngilizceyi bilerek seçti; sonradan gelen Türkçe seçimi değiştirmez.
        env.controller.choose(env.controller.items.single())
        env.addon("Altyazıcı", "https://s/tr.srt" to "tur")
        runCurrent()
        assertEquals("https://s/en.srt", env.selectedUrl())
        assertEquals("en", dao.languages["tv:1396"]!!.language)

        // Dizinin sonraki bölümü: hatırlanan İngilizce, uygulama dili Türkçe olsa da seçilir.
        val next = Env(this, dao, request = request.copy(episode = 3))
        next.loader.results["https://s/e3-en.srt"] = loaded(hello)
        next.loader.results["https://s/e3-tr.srt"] = loaded(hello)
        next.controller.setStream(stream("https://s/e3-tr.srt" to "tur", "https://s/e3-en.srt" to "eng"))
        next.controller.start()
        runCurrent()
        assertEquals("https://s/e3-en.srt", next.selectedUrl())
    }

    @Test
    fun turningSubtitlesOffIsRememberedAndNextEpisodeStartsWithoutSubtitles() = runTest {
        val dao = FakeSubtitleDao()
        val env = Env(this, dao)
        env.loader.results["https://s/tr.srt"] = loaded(hello)
        env.controller.setStream(stream("https://s/tr.srt" to "tur"))
        env.controller.start()
        runCurrent()
        assertEquals("https://s/tr.srt", env.selectedUrl())

        env.controller.choose(null)
        runCurrent()
        assertNull(env.controller.selected)
        assertEquals(SubtitleSelection.REMEMBERED_OFF, dao.languages["tv:1396"]!!.language)
        assertTrue(env.shown.isEmpty())

        val next = Env(this, dao, request = request.copy(episode = 3))
        next.loader.results["https://s/e3.srt"] = loaded(hello)
        next.controller.setStream(stream("https://s/e3.srt" to "tur"))
        next.controller.start()
        runCurrent()
        assertNull(next.controller.selected)
        assertTrue(next.loader.requested.isEmpty())
    }

    @Test
    fun failedDownloadFallsThroughToTheNextCandidateAndReportsFailure() = runTest {
        val env = Env(this)
        env.loader.results["https://s/en.srt"] = loaded(hello)
        // tr.srt için sonuç yok: sahte yükleyici Failed döndürür.
        env.controller.setStream(stream("https://s/tr.srt" to "tur", "https://s/en.srt" to "eng"))
        env.controller.start()
        runCurrent()

        assertEquals(listOf("https://s/tr.srt", "https://s/en.srt"), env.loader.requested)
        assertEquals("https://s/en.srt", env.selectedUrl())
    }

    @Test
    fun failedUserChoiceShowsFailureAndDoesNotRetryOthers() = runTest {
        val env = Env(this)
        env.loader.results["https://s/en.srt"] = loaded(hello)
        env.controller.setStream(stream("https://s/en.srt" to "eng", "https://s/de.srt" to "ger"))
        env.controller.start()
        runCurrent()

        env.controller.choose(env.controller.items.first { (it as SubtitleItem.External).track.url == "https://s/de.srt" })
        runCurrent()
        assertNull(env.controller.selected) // yüklenemedi: kapalı
        assertEquals(SubtitleStatus.Failed, env.controller.status)
        assertNull(env.dao.languages["tv:1396"]) // yüklenemeyen seçim dil tercihi sayılmaz
        env.controller.clearStatus()
        assertEquals(SubtitleStatus.Idle, env.controller.status)
    }

    @Test
    fun syncOffsetShiftsCuesInstantlyAndIsPersistedPerContentAndSubtitle() = runTest {
        val dao = FakeSubtitleDao()
        val env = Env(this, dao)
        val url = "https://s/tr.srt"
        env.loader.results[url] = loaded(hello) // 1000..3000
        env.controller.setStream(stream(url to "tur"))
        env.controller.start()
        runCurrent()

        env.host.position = 1_200
        advanceTimeBy(100)
        assertEquals(listOf("Merhaba"), env.shown.map { it.text })

        // +0,5 sn: altyazı geç görünür; ekran ANINDA güncellenir (zamanlayıcı beklemeden).
        env.controller.adjustOffset(500)
        assertEquals(500L, env.controller.offsetMs)
        assertTrue(env.shown.isEmpty())
        env.host.position = 1_600
        advanceTimeBy(100)
        assertEquals(listOf("Merhaba"), env.shown.map { it.text })

        // İnce adım: +0,5 − 0,1 = +0,4 sn.
        env.controller.adjustOffset(-100)
        assertEquals(400L, env.controller.offsetMs)

        // Kalıcı yazma gecikmeli (art arda basışlar tek yazma olur).
        assertTrue(dao.offsets.isEmpty())
        advanceTimeBy(400)
        runCurrent()
        assertEquals(1, dao.offsets.size)
        assertEquals(400L, dao.offsets.values.single().offsetMs)

        // Aynı bölüm aynı altyazıyla yeniden açılınca kaydırma uygulanır.
        val reopened = Env(this, dao)
        reopened.loader.results[url] = loaded(hello)
        reopened.controller.setStream(stream(url to "tur"))
        reopened.controller.start()
        runCurrent()
        assertEquals(400L, reopened.controller.offsetMs)
        reopened.host.position = 1_200 // 1000 + 400 = 1400'e kadar görünmez
        advanceTimeBy(100)
        assertTrue(reopened.shown.isEmpty())
        reopened.host.position = 1_450
        advanceTimeBy(100)
        assertEquals(listOf("Merhaba"), reopened.shown.map { it.text })

        // Başka bölümde aynı altyazının kaydırması yoktur.
        val other = Env(this, dao, request = request.copy(episode = 9))
        other.loader.results[url] = loaded(hello)
        other.controller.setStream(stream(url to "tur"))
        other.controller.start()
        runCurrent()
        assertEquals(0L, other.controller.offsetMs)

        // Sıfırla: satır silinir.
        env.controller.resetOffset()
        assertEquals(0L, env.controller.offsetMs)
        advanceTimeBy(400)
        runCurrent()
        assertTrue(dao.offsets.isEmpty())
    }

    @Test
    fun flushWritesThePendingOffsetImmediately() = runTest {
        val dao = FakeSubtitleDao()
        val env = Env(this, dao)
        env.loader.results["https://s/tr.srt"] = loaded(hello)
        env.controller.setStream(stream("https://s/tr.srt" to "tur"))
        env.controller.start()
        runCurrent()
        env.controller.adjustOffset(-500)
        env.controller.flush() // etkinlik duruyor: beklemeden yaz
        runCurrent()
        assertEquals(-500L, dao.offsets.values.single().offsetMs)
    }

    @Test
    fun syncIsIgnoredWhenNothingIsLoaded() = runTest {
        val env = Env(this)
        env.controller.start()
        runCurrent()
        env.controller.adjustOffset(500)
        assertEquals(0L, env.controller.offsetMs)
        assertTrue(env.dao.offsets.isEmpty())
    }

    @Test
    fun embeddedTrackInTheSameLanguageBeatsDownloadableOnes() = runTest {
        val env = Env(this)
        env.host.embedded = listOf(EmbeddedSubtitle(0, "tur", selected = false))
        env.loader.results["https://s/tr.srt"] = loaded(hello)
        env.controller.setStream(stream("https://s/tr.srt" to "tur"))
        env.controller.onTracksChanged()
        env.controller.start()
        runCurrent()

        assertEquals(SubtitleItem.Embedded(0, "tr"), env.controller.selected)
        assertEquals(listOf(0), env.host.selectedEmbedded)
        assertTrue(env.loader.requested.isEmpty()) // gereksiz indirme yok
        env.controller.adjustOffset(500) // videonun içindeki parçada senkron yok
        assertEquals(0L, env.controller.offsetMs)
    }

    @Test
    fun externalSelectionSwitchesTheVideoTextOffAndOffClearsTheCues() = runTest {
        val env = Env(this)
        env.loader.results["https://s/tr.srt"] = loaded(hello)
        env.controller.setStream(stream("https://s/tr.srt" to "tur"))
        env.controller.start()
        runCurrent()
        assertTrue(env.host.embeddedDisabled >= 1) // iki kez çizilmesin

        env.host.position = 1_500
        advanceTimeBy(100)
        assertEquals(1, env.shown.size)
        env.controller.choose(null)
        assertTrue(env.shown.isEmpty()) // kapatınca satırlar hemen kalkar
    }

    @Test
    fun sameUrlFromStreamAndAddonAppearsOnce() = runTest {
        val env = Env(this)
        env.controller.setStream(stream("https://s/tr.srt" to "tur"))
        env.controller.start()
        env.addon("Altyazıcı", "https://s/tr.srt" to "tur", "https://s/en.srt" to "eng")
        runCurrent()
        assertEquals(2, env.controller.items.size)
        assertEquals("Akış Addon", (env.controller.items[0] as SubtitleItem.External).track.origin) // ilk görülen kalır
    }

    @Test
    fun failedAddonIsReported() = runTest {
        val env = Env(this)
        env.controller.start()
        env.incoming.trySend(SubtitleResult.Failed(StreamSource(5, "Ölü Addon"), FailureReason.Timeout))
        env.incoming.close()
        runCurrent()
        assertEquals(listOf(StreamSource(5, "Ölü Addon")), env.controller.failedSources)
        assertTrue(!env.controller.searching)
    }

    @Test
    fun onlineTurkishIsPickedAutomaticallyAndLateEmbeddedTurkishTakesOver() = runTest {
        val env = Env(this)
        env.loader.results["https://os/tr.srt"] = loaded(hello)
        env.controller.start()
        env.incoming.trySend(
            SubtitleResult.Found(
                OnlineSubtitleProvider.SOURCE,
                listOf(
                    SubtitleTrack("https://os/tr.srt", "tr", "OpenSubtitles", SubtitleKind.Online),
                    SubtitleTrack("https://os/en.srt", "en", "OpenSubtitles", SubtitleKind.Online),
                ),
            ),
        )
        runCurrent()
        assertEquals("https://os/tr.srt", env.selectedUrl())

        // Videonun içinde Türkçe parça sonradan görünürse listede önce o gelir ve (otomatik seçimde) önceliklidir: gömülü Türkçe,
        // çevrimiçi Türkçeden önce gelir.
        env.host.embedded = listOf(EmbeddedSubtitle(0, "tur", selected = false, label = "Forced"))
        env.controller.onTracksChanged()
        runCurrent()
        assertEquals(SubtitleItem.Embedded(0, "tr", "Forced"), env.controller.items.first())
        assertEquals(SubtitleItem.Embedded(0, "tr", "Forced"), env.controller.selected)
    }

    @Test
    fun englishPreferenceSelectsEnglishOverTurkish() = runTest {
        val env = Env(this, app = "en")
        env.loader.results["https://s/en.srt"] = loaded(hello)
        env.controller.setStream(stream("https://s/tr.srt" to "tur", "https://s/en.srt" to "eng"))
        env.controller.start()
        runCurrent()
        assertEquals("https://s/en.srt", env.selectedUrl())
        assertEquals(listOf("en"), env.controller.menuLanguages)
    }

    @Test
    fun fileFromDiskIsAddedSelectedAndItsLanguageGuessedFromTheName() = runTest {
        val env = Env(this)
        env.loader.results["content://docs/1"] = loaded(hello)
        env.controller.start()
        runCurrent()
        env.controller.addLocal("content://docs/1", "Film.2020.tr.srt")
        runCurrent()

        val track = (env.controller.selected as SubtitleItem.External).track
        assertEquals(SubtitleKind.Local, track.kind)
        assertEquals("tr", track.language)
        assertEquals("Film.2020.tr.srt", track.origin)
        env.host.position = 1_500
        advanceTimeBy(100)
        assertEquals(listOf(hello), env.shown)
        env.controller.adjustOffset(500) // senkron dosyadan eklenen altyazıda da çalışır
        assertEquals(500L, env.controller.offsetMs)
    }

    @Test
    fun languageFromFileName() {
        assertEquals("tr", SubtitleController.languageFromFileName("Film.2020.tr.srt"))
        assertEquals("tr", SubtitleController.languageFromFileName("Film Turkish.srt"))
        assertEquals("en", SubtitleController.languageFromFileName("film_eng.ass"))
        assertEquals(null, SubtitleController.languageFromFileName("Breaking.Bad.S01E01.srt"))
        assertEquals(null, SubtitleController.languageFromFileName("film.1080p.srt"))
    }

    // ---- kaynak/altyazı paneli: dublaj, çevrimiçi sıra, "ilk kullanılabiliri yükle" ----

    @Test
    fun suppressedAutoSelectionKeepsSubtitlesOffOnADubbedSourceAndResumesLater() = runTest {
        val env = Env(this)
        env.loader.results["https://s/tr.srt"] = loaded(hello)
        env.controller.suppressAuto() // dublajlı kaynak: Türkçe altyazıya gerek yok
        env.controller.setStream(stream("https://s/tr.srt" to "tur"))
        env.controller.start()
        runCurrent()
        assertNull(env.controller.selected)

        env.controller.resumeAuto() // altyazılı kaynağa dönüldü
        runCurrent()
        assertEquals("https://s/tr.srt", env.selectedUrl())
    }

    @Test
    fun suppressingClosesAnAutoSelectionButNotTheUsersChoice() = runTest {
        val env = Env(this)
        env.loader.results["https://s/tr.srt"] = loaded(hello)
        env.loader.results["https://s/en.srt"] = loaded(hello)
        env.controller.setStream(stream("https://s/tr.srt" to "tur", "https://s/en.srt" to "eng"))
        env.controller.start()
        runCurrent()
        assertEquals("https://s/tr.srt", env.selectedUrl())
        env.controller.suppressAuto()
        assertNull("otomatik seçim kapanır", env.controller.selected)

        val env2 = Env(this, dao = FakeSubtitleDao(), app = "tr")
        env2.loader.results["https://s/en.srt"] = loaded(hello)
        env2.controller.setStream(stream("https://s/en.srt" to "eng"))
        env2.controller.start()
        runCurrent()
        env2.controller.choose(env2.controller.items.first())
        runCurrent()
        env2.controller.suppressAuto()
        assertEquals("elle seçilene dokunulmaz", "https://s/en.srt", env2.selectedUrl())
    }

    @Test
    fun subdlResultsComeBeforeOpenSubtitlesNoMatterWhichAnswersFirst() = runTest {
        val env = Env(this)
        env.loader.results["https://dl.subdl/tr.zip"] = loaded(hello)
        env.loader.results["https://os/tr.srt"] = loaded(hello)
        env.controller.start()
        runCurrent()
        // OpenSubtitles daha önce cevap verdi.
        env.incoming.trySend(
            SubtitleResult.Found(
                OnlineSubtitleProvider.SOURCE,
                listOf(SubtitleTrack("https://os/tr.srt", "tr", "OpenSubtitles", SubtitleKind.Online, "OS.rel")),
            ),
        )
        runCurrent()
        env.incoming.trySend(
            SubtitleResult.Found(
                StreamSource(-2, "SubDL"),
                listOf(SubtitleTrack("https://dl.subdl/tr.zip", "tr", "SubDL", SubtitleKind.Online, "SubDL.rel")),
            ),
        )
        runCurrent()
        assertEquals(listOf("SubDL", "OpenSubtitles"), env.controller.onlineItems().map { it.track.origin })
    }

    @Test
    fun firstUsableOnlineSkipsBrokenResultsWithoutSelectingAnything() = runTest {
        val env = Env(this)
        env.loader.results["https://dl/ok.zip"] = loaded(hello)
        env.controller.start()
        env.incoming.trySend(
            SubtitleResult.Found(
                StreamSource(-2, "SubDL"),
                listOf(
                    SubtitleTrack("https://dl/bad.zip", "tr", "SubDL", SubtitleKind.Online, "bad"),
                    SubtitleTrack("https://dl/ok.zip", "tr", "SubDL", SubtitleKind.Online, "ok"),
                    SubtitleTrack("https://dl/en.zip", "en", "SubDL", SubtitleKind.Online, "en"),
                ),
            ),
        )
        runCurrent()
        env.controller.choose(null) // otomatik seçim karışmasın
        env.loader.requested.clear()
        val found = env.controller.firstUsableOnline("tr")
        assertEquals("https://dl/ok.zip", found?.track?.url)
        // Otomatik seçim bozuk olanı zaten eledi (tekrar indirilmez); çalışan bulunca durulur.
        assertEquals(listOf("https://dl/ok.zip"), env.loader.requested)
        assertNull("yalnızca bulur, seçmez", env.controller.selected)
        assertNull(env.controller.firstUsableOnline("de"))
    }

    @Test
    fun searchOnlineAsksTheProvidersAgainOnlyWhenIdle() = runTest {
        var calls = 0
        val controller = SubtitleController(
            scope = backgroundScope,
            playback = FakeHost(),
            request = request,
            loader = FakeLoader(),
            memory = SubtitleMemory(FakeSubtitleDao()),
            provider = SubtitleProvider { calls++; kotlinx.coroutines.flow.flowOf() },
            preferredLanguage = { "tr" },
            persist = backgroundScope,
        )
        controller.start()
        runCurrent()
        assertEquals(1, calls)
        assertEquals(false, controller.searching)
        controller.searchOnline()
        runCurrent()
        assertEquals(2, calls)
    }

    @Test
    fun subdlUpgradesTheAutoSelectionEvenWhenOpenSubtitlesAnsweredFirst() = runTest {
        val env = Env(this)
        env.loader.results["https://dl.subdl/tr.zip"] = loaded(hello)
        env.loader.results["https://os/tr.srt"] = loaded(hello)
        env.controller.start()
        runCurrent()
        env.incoming.trySend(
            SubtitleResult.Found(
                OnlineSubtitleProvider.SOURCE,
                listOf(SubtitleTrack("https://os/tr.srt", "tr", "OpenSubtitles", SubtitleKind.Online, "OS.rel")),
            ),
        )
        runCurrent()
        assertEquals("https://os/tr.srt", env.selectedUrl())
        env.incoming.trySend(
            SubtitleResult.Found(
                StreamSource(-2, "SubDL"),
                listOf(SubtitleTrack("https://dl.subdl/tr.zip", "tr", "SubDL", SubtitleKind.Online, "SubDL.rel")),
            ),
        )
        runCurrent()
        assertEquals("sağlayıcı sırası: SubDL önce", "https://dl.subdl/tr.zip", env.selectedUrl())
    }
}
