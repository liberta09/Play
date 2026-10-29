package com.kaan.play.data.library

import com.kaan.play.data.db.FavoriteDao
import com.kaan.play.data.db.FavoriteEntity
import com.kaan.play.data.db.WatchHistoryDao
import com.kaan.play.data.db.WatchHistoryEntity
import com.kaan.play.data.model.Episode
import com.kaan.play.data.model.MediaSummary
import com.kaan.play.data.model.MediaType
import com.kaan.play.data.model.SeasonDetail
import com.kaan.play.data.stream.PlayTarget
import com.kaan.play.data.stream.StreamRequest
import com.kaan.play.data.tmdb.TmdbException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Room'un REPLACE + "içerik başına son kayıt" davranışını taklit eden bellek içi DAO. */
private class FakeHistoryDao : WatchHistoryDao {
    val rows = MutableStateFlow<List<WatchHistoryEntity>>(emptyList())
    private var nextId = 1L

    private fun latestPerTitle(list: List<WatchHistoryEntity>, userId: String, profileId: String): List<WatchHistoryEntity> =
        list.filter { it.userId == userId && it.profileId == profileId }
            .groupBy { it.tmdbId to it.type }
            .values
            .mapNotNull { rows -> rows.maxWithOrNull(compareBy<WatchHistoryEntity> { it.lastWatchedAt }.thenBy { it.id }) }
            .filter { it.durationMs > 0 }

    override fun observeLatestPerContent(userId: String, profileId: String): Flow<List<WatchHistoryEntity>> = rows.map { list ->
        latestPerTitle(list, userId, profileId).filterNot { it.manuallyMarkedWatched }.sortedByDescending { it.lastWatchedAt }
    }

    override fun observeManuallyWatched(userId: String, profileId: String): Flow<List<WatchHistoryEntity>> = rows.map { list ->
        latestPerTitle(list, userId, profileId).filter { it.manuallyMarkedWatched }.sortedByDescending { it.lastWatchedAt }
    }

    override suspend fun upsert(entity: WatchHistoryEntity) {
        val kept = rows.value.filterNot {
            (it.userId == entity.userId && it.profileId == entity.profileId && it.id == entity.id && entity.id != 0L) ||
                (it.userId == entity.userId && it.profileId == entity.profileId && it.tmdbId == entity.tmdbId && it.type == entity.type && it.season == entity.season && it.episode == entity.episode)
        }
        rows.value = kept + entity.copy(id = if (entity.id != 0L) entity.id else nextId++)
    }

    override fun observeHistory(userId: String, profileId: String): Flow<List<WatchHistoryEntity>> = rows.map { list ->
        list.filter { it.userId == userId && it.profileId == profileId }.sortedWith(compareByDescending<WatchHistoryEntity> { it.lastWatchedAt }.thenByDescending { it.id }).take(200)
    }

    override suspend fun all(userId: String, profileId: String): List<WatchHistoryEntity> = rows.value.filter { it.userId == userId && it.profileId == profileId }

    override suspend fun delete(userId: String, profileId: String, id: Long) {
        rows.value = rows.value.filterNot { it.userId == userId && it.profileId == profileId && it.id == id }
    }

    override suspend fun deleteContent(userId: String, profileId: String, tmdbId: Int, type: MediaType) {
        rows.value = rows.value.filterNot { it.userId == userId && it.profileId == profileId && it.tmdbId == tmdbId && it.type == type }
    }

    override suspend fun markWatched(userId: String, profileId: String, id: Long, watchedAt: Long): Int {
        val selected = rows.value.firstOrNull { it.userId == userId && it.profileId == profileId && it.id == id && it.durationMs > 0 }
            ?: return 0
        val matching = rows.value.filter {
            it.userId == userId && it.profileId == profileId && it.tmdbId == selected.tmdbId && it.type == selected.type && it.durationMs > 0
        }
        val recordedAt = maxOf(watchedAt, (matching.maxOfOrNull { it.lastWatchedAt } ?: 0L) + 1L)
        rows.value = rows.value.map {
            if (it.userId == userId && it.profileId == profileId && it.tmdbId == selected.tmdbId && it.type == selected.type && it.durationMs > 0) {
                it.copy(positionMs = it.durationMs, lastWatchedAt = recordedAt, manuallyMarkedWatched = true)
            } else it
        }
        return matching.size
    }

    override suspend fun clear(userId: String, profileId: String) {
        rows.value = rows.value.filterNot { it.userId == userId && it.profileId == profileId }
    }

    override suspend fun find(userId: String, profileId: String, tmdbId: Int, type: MediaType, season: Int, episode: Int) =
        rows.value.firstOrNull { it.userId == userId && it.profileId == profileId && it.tmdbId == tmdbId && it.type == type && it.season == season && it.episode == episode }
    override suspend fun latestTimestampForContent(userId: String, profileId: String, tmdbId: Int, type: MediaType): Long? =
        rows.value.filter { it.userId == userId && it.profileId == profileId && it.tmdbId == tmdbId && it.type == type }.maxOfOrNull { it.lastWatchedAt }
    override suspend fun adoptLegacy(userId: String, profileId: String) { rows.value = rows.value.map { if (it.userId == "legacy-user") it.copy(userId=userId, profileId=profileId) else it } }
}

/** Room'un REPLACE davranışını taklit eden bellek içi favori DAO'su. */
private class FakeFavorites : FavoriteDao {
    val rows = MutableStateFlow<List<FavoriteEntity>>(emptyList())
    override fun observeAll(userId: String, profileId: String): Flow<List<FavoriteEntity>> = rows.map { it.filter { f -> f.userId == userId && f.profileId == profileId }.sortedByDescending { f -> f.addedAt } }
    override suspend fun all(userId: String, profileId: String): List<FavoriteEntity> = rows.value.filter { it.userId == userId && it.profileId == profileId }
    override fun observeIsFavorite(userId: String, profileId: String, tmdbId: Int, type: MediaType): Flow<Boolean> =
        rows.map { list -> list.any { it.userId == userId && it.profileId == profileId && it.tmdbId == tmdbId && it.type == type } }
    override suspend fun add(entity: FavoriteEntity) {
        rows.value = rows.value.filterNot { it.userId == entity.userId && it.profileId == entity.profileId && it.tmdbId == entity.tmdbId && it.type == entity.type } + entity
    }
    override suspend fun remove(userId: String, profileId: String, tmdbId: Int, type: MediaType) {
        rows.value = rows.value.filterNot { it.userId == userId && it.profileId == profileId && it.tmdbId == tmdbId && it.type == type }
    }

    override suspend fun clear(userId: String, profileId: String) {
        rows.value = rows.value.filterNot { it.userId == userId && it.profileId == profileId }
    }
    override suspend fun adoptLegacy(userId: String, profileId: String) { rows.value = rows.value.map { if (it.userId == "legacy-user") it.copy(userId=userId, profileId=profileId) else it } }
}

class WatchRulesTest {

    @Test
    fun recordsOnlyFromThirtySeconds() {
        assertFalse(WatchRules.shouldRecord(29_999, 600_000))
        assertTrue(WatchRules.shouldRecord(30_000, 600_000))
        assertFalse(WatchRules.shouldRecord(0, 600_000))
    }

    @Test
    fun unknownDurationRecordsNothing() {
        assertFalse(WatchRules.shouldRecord(120_000, 0))
        assertFalse(WatchRules.isWatched(120_000, 0))
    }

    @Test
    fun watchedMeansStrictlyOverNinetyPercent() {
        assertFalse(WatchRules.isWatched(90_000, 100_000)) // tam %90 henüz değil
        assertTrue(WatchRules.isWatched(90_001, 100_000))
        assertTrue(WatchRules.isWatched(100_000, 100_000))
    }

    @Test
    fun resumableIsBetweenThirtySecondsAndNinetyPercent() {
        assertFalse(WatchRules.isResumable(10_000, 600_000)) // çok az
        assertTrue(WatchRules.isResumable(30_000, 600_000))
        assertTrue(WatchRules.isResumable(540_000, 600_000)) // tam %90
        assertFalse(WatchRules.isResumable(540_001, 600_000)) // izlendi
    }

    @Test
    fun shortVideosStillFollowTheThirtySecondFloor() {
        // 40 sn'lik bir klibin 35. saniyesi %87: devam edilebilir; 25. saniyesi kaydedilmez.
        assertTrue(WatchRules.isResumable(35_000, 40_000))
        assertFalse(WatchRules.shouldRecord(25_000, 40_000))
    }
}

class LibraryRepositoryTest {

    private val dao = FakeHistoryDao()
    private var now = 1_000L
    private val repo = LibraryRepository(dao, FakeFavorites(), clock = { now })

    private fun movie(id: Int = 1) = PlayTarget(StreamRequest(id, "tt$id", MediaType.Movie), "Film $id", "/p$id.jpg")
    private fun episode(id: Int = 2, s: Int = 1, e: Int = 1) =
        PlayTarget(StreamRequest(id, "tt$id", MediaType.Tv, s, e), "Dizi $id", "/p$id.jpg")

    @Test
    fun lessThanThirtySecondsIsNotAddedToHistory() = runTest {
        assertFalse(repo.saveProgress(movie(), 29_999, 600_000))
        assertTrue(dao.rows.value.isEmpty())
        assertNull(repo.progress(movie()))
    }

    @Test
    fun unknownDurationIsNotAdded() = runTest {
        assertFalse(repo.saveProgress(movie(), 90_000, 0))
        assertTrue(dao.rows.value.isEmpty())
    }

    @Test
    fun savesAndUpdatesTheSameRowKeepingItsId() = runTest {
        assertTrue(repo.saveProgress(movie(), 60_000, 600_000))
        val first = repo.progress(movie())!!
        now = 2_000
        assertTrue(repo.saveProgress(movie(), 120_000, 600_000))

        assertEquals(1, dao.rows.value.size)
        val second = repo.progress(movie())!!
        assertEquals(first.id, second.id)
        assertEquals(120_000L, second.positionMs)
        assertEquals(2_000L, second.lastWatchedAt)
        assertEquals("Film 1", second.title)
        assertEquals("/p1.jpg", second.posterPath)
    }

    @Test
    fun marksHistoryEntryWatchedAndRemovesWholeTitleHistory() = runTest {
        repo.saveProgress(episode(id = 8, s = 1, e = 1), 120_000, 600_000)
        val entry = repo.progress(episode(id = 8, s = 1, e = 1))!!
        assertTrue(repo.markHistoryWatched(entry.id))
        assertTrue(WatchRules.isWatched(repo.progress(episode(id = 8, s = 1, e = 1))!!.positionMs, 600_000))

        repo.saveProgress(episode(id = 8, s = 1, e = 2), 60_000, 600_000)
        repo.removeContentHistory(entry)
        assertTrue(dao.rows.value.none { it.tmdbId == 8 && it.type == MediaType.Tv })
    }

    @Test
    fun manuallyMarkedSeriesLeavesContinueAndAppearsInLibraryWatchedCategory() = runTest {
        val previousEpisode = episode(id = 43, s = 2, e = 3)
        val target = episode(id = 43, s = 2, e = 4)
        repo.saveProgress(previousEpisode, 600_000, 600_000)
        repo.saveProgress(target, 240_000, 600_000)
        val entry = repo.progress(target)!!
        assertEquals(2, dao.rows.value.count { it.tmdbId == 43 && it.type == MediaType.Tv })

        assertTrue(repo.markHistoryWatched(entry.id))
        assertTrue(repo.latestPerContent.first().none { it.tmdbId == 43 && it.type == MediaType.Tv })
        assertTrue(ContinueWatching(repo) { _ -> null }.items.first().none { it.entry.tmdbId == 43 })
        val watched = repo.manuallyWatched.first().single { it.tmdbId == 43 && it.type == MediaType.Tv }
        assertTrue(watched.manuallyMarkedWatched)
        assertEquals(600_000L, watched.positionMs)

        // Daha önce kaydı olmayan yeni bölüm de işaretli durumdan daha yeni hâle gelir.
        val newEpisode = episode(id = 43, s = 2, e = 5)
        repo.saveProgress(newEpisode, 35_000, 600_000)
        assertTrue(repo.manuallyWatched.first().none { it.tmdbId == 43 })
        assertTrue(repo.latestPerContent.first().any { it.tmdbId == 43 })
    }

    @Test
    fun positionNeverExceedsDuration() = runTest {
        repo.saveProgress(movie(), 700_000, 600_000)
        assertEquals(600_000L, repo.progress(movie())!!.positionMs)
    }

    @Test
    fun moviesStoreZeroSeasonAndEpisodeSeriesStoreTheirOwn() = runTest {
        repo.saveProgress(movie(), 60_000, 600_000)
        repo.saveProgress(episode(2, 3, 4), 60_000, 600_000)

        val m = dao.rows.value.single { it.type == MediaType.Movie }
        val e = dao.rows.value.single { it.type == MediaType.Tv }
        assertEquals(0 to 0, m.season to m.episode)
        assertEquals(3 to 4, e.season to e.episode)
        assertNull(repo.progress(episode(2, 3, 5))) // başka bölümün kaydı yok
    }
}

class ContinueWatchingTest {

    private val dao = FakeHistoryDao()
    private var clock = 0L
    private val repo = LibraryRepository(dao, FakeFavorites(), clock = { ++clock })

    private fun movie(id: Int) = PlayTarget(StreamRequest(id, "tt$id", MediaType.Movie), "Film $id", null)
    private fun episode(id: Int, s: Int, e: Int) = PlayTarget(StreamRequest(id, "tt$id", MediaType.Tv, s, e), "Dizi $id", null)

    private fun continueWatching(next: suspend (WatchHistoryEntity) -> Pair<Int, Int>? = { null }) =
        ContinueWatching(repo, next)

    @Test
    fun listsPartiallyWatchedNewestFirstAndDropsFinishedMovies() = runTest {
        repo.saveProgress(movie(1), 60_000, 600_000) // %10
        repo.saveProgress(movie(2), 590_000, 600_000) // %98 -> izlendi, listeden kalkar
        repo.saveProgress(movie(3), 300_000, 600_000) // %50
        repo.saveProgress(movie(4), 10_000, 600_000) // 30 sn'den az -> hiç yok

        val items = continueWatching().items.first()

        assertEquals(listOf(3, 1), items.map { it.entry.tmdbId })
        assertTrue(items.all { it is ContinueItem.Resume })
    }

    @Test
    fun finishedEpisodeSuggestsTheNextOneAfterItIsResolved() = runTest {
        repo.saveProgress(episode(10, 1, 2), 1_380_000, 1_400_000) // %98 -> bitti

        val emissions = continueWatching { row -> row.season to row.episode + 1 }.items.take(2).toList()

        assertTrue(emissions[0].isEmpty()) // ağ yanıtı gelmeden öneri yok
        val next = emissions[1].single() as ContinueItem.Next
        assertEquals(10, next.entry.tmdbId)
        assertEquals(1 to 3, next.season to next.episode)
    }

    @Test
    fun finishedSeriesWithoutANextEpisodeIsHiddenAndNetworkErrorsDoNotBreakTheList() = runTest {
        repo.saveProgress(episode(10, 1, 2), 1_380_000, 1_400_000) // bitti, sonraki yok
        repo.saveProgress(episode(11, 1, 1), 600_000, 1_400_000) // yarım
        repo.saveProgress(episode(12, 1, 1), 1_390_000, 1_400_000) // bitti, ağ hatası

        val list = continueWatching { row ->
            if (row.tmdbId == 12) throw TmdbException(TmdbException.Kind.Network, path = "tv/12/season/1") else null
        }.items.first()

        assertEquals(listOf(11), list.map { it.entry.tmdbId })
        assertTrue(list.single() is ContinueItem.Resume)
    }

    @Test
    fun latestEpisodeDecidesTheSeriesState() = runTest {
        repo.saveProgress(episode(10, 1, 1), 1_390_000, 1_400_000) // bölüm 1 bitti
        repo.saveProgress(episode(10, 1, 2), 300_000, 1_400_000) // bölüm 2 başlandı (daha yeni)

        val item = continueWatching { error("sonraki bölüm sorulmamalı") }.items.first().single()

        assertTrue(item is ContinueItem.Resume)
        assertEquals(2, item.entry.episode)
    }

    @Test
    fun startingAnotherSeriesKeepsEarlierUnfinishedMoviesInContinueWatching() = runTest {
        repo.saveProgress(movie(1), 180_000, 600_000)
        repo.saveProgress(episode(10, 1, 1), 240_000, 1_200_000)
        repo.saveProgress(movie(2), 120_000, 600_000)

        val items = continueWatching().items.first()

        assertEquals(setOf(1, 2, 10), items.map { it.entry.tmdbId }.toSet())
        assertEquals(listOf(2, 1, 10), sortContinueItems(items, ContinueSortMode.MoviesFirst).map { it.entry.tmdbId })
        assertEquals(listOf(10, 2, 1), sortContinueItems(items, ContinueSortMode.SeriesFirst).map { it.entry.tmdbId })
        assertEquals(listOf(1, 10, 2), sortContinueItems(items, ContinueSortMode.OldestFirst).map { it.entry.tmdbId })
    }

    @Test
    fun continueWatchingDoesNotDropOlderUnfinishedTitlesAfterThirtyEntries() = runTest {
        (1..40).forEach { id -> repo.saveProgress(movie(id), 60_000, 600_000) }

        val items = continueWatching().items.first()

        assertEquals(40, items.size)
        assertTrue(items.any { it.entry.tmdbId == 1 })
    }
}

class NextEpisodeResolverTest {

    private fun ep(n: Int, air: String? = "2020-01-01") = Episode(n, "B$n", "", null, null, air)

    private val seasons = mutableMapOf<Int, List<Episode>>()
    private var calls = 0

    private fun resolver(today: String = "2026-06-01") = NextEpisodeResolver(
        loadSeason = { tv, s ->
            calls++
            val eps = seasons[s] ?: throw TmdbException(TmdbException.Kind.NotFound, 404, "tv/$tv/season/$s")
            SeasonDetail(s, eps)
        },
        today = { today },
    )

    @Test
    fun nextEpisodeInTheSameSeason() = runTest {
        seasons[1] = listOf(ep(1), ep(2), ep(3))
        assertEquals(1 to 2, resolver().next(7, 1, 1))
    }

    @Test
    fun lastEpisodeOfASeasonMovesToTheNextSeasonsFirstEpisode() = runTest {
        seasons[1] = listOf(ep(1), ep(2))
        seasons[2] = listOf(ep(1), ep(2))
        assertEquals(2 to 1, resolver().next(7, 1, 2))
    }

    @Test
    fun lastEpisodeOfTheLastSeasonHasNoNext() = runTest {
        seasons[1] = listOf(ep(1), ep(2))
        assertNull(resolver().next(7, 1, 2)) // 2. sezon 404
    }

    @Test
    fun unairedOrUndatedEpisodesAreNotSuggestedAndDoNotSkipAhead() = runTest {
        seasons[1] = listOf(ep(1), ep(2, air = "2026-12-01"), ep(3, air = "2020-01-01"))
        seasons[2] = listOf(ep(1))
        assertNull(resolver().next(7, 1, 1)) // bölüm 2 henüz yayınlanmadı: 3'e ya da 2. sezona atlanmaz

        seasons[1] = listOf(ep(1), ep(2, air = null))
        assertNull(resolver().next(7, 1, 1)) // tarihi bilinmeyen bölüm
    }

    @Test
    fun airDateOfTodayCounts() = runTest {
        seasons[1] = listOf(ep(1), ep(2, air = "2026-06-01"))
        assertEquals(1 to 2, resolver("2026-06-01").next(7, 1, 1))
    }

    @Test
    fun otherTmdbErrorsPropagateAndAreNotCached() = runTest {
        seasons[1] = listOf(ep(1))
        val broken = NextEpisodeResolver({ _, _ -> throw TmdbException(TmdbException.Kind.Network, path = "x") }, { "2026-06-01" })
        try {
            broken.next(7, 1, 1)
            org.junit.Assert.fail()
        } catch (e: TmdbException) {
            assertEquals(TmdbException.Kind.Network, e.kind)
        }
    }

    @Test
    fun successfulLookupsAreCached() = runTest {
        seasons[1] = listOf(ep(1), ep(2))
        val r = resolver()
        r.next(7, 1, 1)
        r.next(7, 1, 1)
        assertEquals(1, calls)
    }
}

/** Kütüphane ekranının işlemleri: geçmiş listesi, tek tek silme, hepsini temizleme, favoriler. */
class LibraryManagementTest {

    private val history = FakeHistoryDao()
    private val favorites = FakeFavorites()
    private var clock = 0L
    private val repo = LibraryRepository(history, favorites, clock = { ++clock })

    private fun movie(id: Int) = PlayTarget(StreamRequest(id, "tt$id", MediaType.Movie), "Film $id", null)
    private fun episode(id: Int, s: Int, e: Int) = PlayTarget(StreamRequest(id, "tt$id", MediaType.Tv, s, e), "Dizi $id", null)
    private fun summary(id: Int, type: MediaType = MediaType.Movie) =
        MediaSummary(id, type, "Başlık $id", "", "/p$id.jpg", null, 2020, null)

    @Test
    fun historyListsEveryRecordNewestFirstIncludingWatchedOnes() = runTest {
        repo.saveProgress(movie(1), 60_000, 600_000)
        repo.saveProgress(episode(2, 1, 1), 1_390_000, 1_400_000) // izlendi
        repo.saveProgress(episode(2, 1, 2), 300_000, 1_400_000) // aynı dizinin ikinci bölümü: ayrı satır
        repo.saveProgress(movie(1), 120_000, 600_000) // güncelleme: en üste çıkar, satır çoğalmaz

        val list = repo.watchHistory.first()

        assertEquals(listOf(1 to 0, 2 to 2, 2 to 1), list.map { it.tmdbId to it.episode })
    }

    @Test
    fun removingOneHistoryRowLeavesTheOthers() = runTest {
        repo.saveProgress(movie(1), 60_000, 600_000)
        repo.saveProgress(movie(2), 60_000, 600_000)
        val first = repo.watchHistory.first().first { it.tmdbId == 1 }

        repo.removeHistory(first.id)

        assertEquals(listOf(2), repo.watchHistory.first().map { it.tmdbId })
    }

    @Test
    fun clearingHistoryKeepsFavorites() = runTest {
        repo.saveProgress(movie(1), 60_000, 600_000)
        repo.setFavorite(summary(9), true)

        repo.clearHistory()

        assertTrue(repo.watchHistory.first().isEmpty())
        assertEquals(listOf(9), repo.allFavorites.first().map { it.tmdbId })
    }

    @Test
    fun favoritesToggleKeepOneRowAndListNewestFirst() = runTest {
        repo.setFavorite(summary(1), true)
        repo.setFavorite(summary(2, MediaType.Tv), true)
        repo.setFavorite(summary(1), true) // tekrar ekleme satır çoğaltmaz
        assertTrue(repo.isFavorite(MediaType.Movie, 1).first())
        assertEquals(listOf(1 to MediaType.Movie, 2 to MediaType.Tv), repo.allFavorites.first().map { it.tmdbId to it.type })

        repo.setFavorite(summary(1), false)
        assertFalse(repo.isFavorite(MediaType.Movie, 1).first())

        repo.removeFavorite(MediaType.Tv, 2)
        assertTrue(repo.allFavorites.first().isEmpty())
    }

    @Test
    fun favoriteKeepsTitleAndPosterCopyForOfflineDisplay() = runTest {
        repo.setFavorite(summary(5), true)
        val fav = repo.allFavorites.first().single()
        assertEquals("Başlık 5", fav.title)
        assertEquals("/p5.jpg", fav.posterPath)
    }

    @Test
    fun pluginProgressSavingStoresPluginIdAndUrls() = runTest {
        val target = PlayTarget(
            request = StreamRequest(
                tmdbId = -101,
                imdbId = null,
                type = MediaType.Tv,
                season = 1,
                episode = 3,
                pluginId = "cs:42",
                dataUrl = "https://example.com/ep3",
                contentUrl = "https://example.com/series",
            ),
            title = "Eklenti Dizisi",
            posterPath = "https://example.com/poster.jpg",
        )
        val saved = repo.saveProgress(target, 120_000, 1_200_000)
        assertTrue(saved)

        val retrieved = repo.progress(target)
        org.junit.Assert.assertNotNull(retrieved)
        assertEquals("cs:42", retrieved?.pluginId)
        assertEquals("https://example.com/ep3", retrieved?.dataUrl)
        assertEquals("https://example.com/series", retrieved?.contentUrl)
        assertEquals(120_000L, retrieved?.positionMs)
        assertEquals(1, retrieved?.season)
        assertEquals(3, retrieved?.episode)
    }
}
