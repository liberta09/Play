package com.kaan.play.ui.search

import com.kaan.play.data.model.MediaSummary
import com.kaan.play.data.model.MediaType
import com.kaan.play.data.model.Page
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Tür çipi: doğru tür isteği, değişince sayfalama sıfırlanması, boş sorguda istek yok. */
@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val calls = mutableListOf<Triple<String, Int, MediaType?>>()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun item(id: Int, type: MediaType) =
        MediaSummary(id = id, type = type, title = "T$id", overview = "", posterPath = null, backdropPath = null, year = null, rating = null)

    private fun vm() = SearchViewModel(
        search = { q, page, type ->
            calls += Triple(q, page, type)
            val t = type ?: MediaType.Movie
            Page(List(3) { item(page * 10 + it, t) }, page, totalPages = 5)
        },
        debounceMs = 100,
    )

    private fun TestScope.typeText(vm: SearchViewModel, text: String) {
        vm.onQueryChange(text)
        advanceTimeBy(150)
        runCurrent()
    }

    @Test
    fun defaultIsAllAndSearchesMulti() = runTest(dispatcher) {
        val vm = vm()
        assertEquals(SearchFilter.All, vm.state.value.filter)
        typeText(vm, "kara")
        assertEquals(listOf(Triple("kara", 1, null)), calls)
    }

    @Test
    fun chipChangeSearchesSameQueryWithNewTypeAndResetsPaging() = runTest(dispatcher) {
        val vm = vm()
        typeText(vm, "kara")
        vm.loadMore()
        advanceUntilIdle()
        assertEquals(2, vm.state.value.page)
        assertEquals(6, vm.state.value.items.size)

        vm.onFilterChange(SearchFilter.Movies)
        advanceUntilIdle()
        assertEquals(Triple("kara", 1, MediaType.Movie), calls.last())
        assertEquals(1, vm.state.value.page) // sayfalama sıfırlandı
        assertEquals(3, vm.state.value.items.size) // ızgara yeniden başladı
        assertTrue(vm.state.value.items.all { it.type == MediaType.Movie })

        vm.onFilterChange(SearchFilter.Series)
        advanceUntilIdle()
        assertEquals(Triple("kara", 1, MediaType.Tv), calls.last())
        assertTrue(vm.state.value.items.all { it.type == MediaType.Tv })

        // Sonraki sayfa da seçili türle istenir.
        vm.loadMore()
        advanceUntilIdle()
        assertEquals(Triple("kara", 2, MediaType.Tv), calls.last())

        vm.onFilterChange(SearchFilter.All)
        advanceUntilIdle()
        assertEquals(Triple("kara", 1, null), calls.last())
    }

    @Test
    fun blankQueryChipChangeMakesNoRequest() = runTest(dispatcher) {
        val vm = vm()
        vm.onFilterChange(SearchFilter.Movies)
        advanceUntilIdle()
        assertTrue(calls.isEmpty())
        assertEquals(SearchFilter.Movies, vm.state.value.filter)
        // Sonra yazılan sorgu seçili türle aranır.
        typeText(vm, "kara")
        assertEquals(listOf(Triple("kara", 1, MediaType.Movie)), calls)
    }

    @Test
    fun selectingTheSameChipDoesNothing() = runTest(dispatcher) {
        val vm = vm()
        typeText(vm, "kara")
        vm.onFilterChange(SearchFilter.All)
        advanceUntilIdle()
        assertEquals(1, calls.size)
    }
}
