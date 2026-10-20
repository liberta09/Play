package com.kaan.play.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kaan.play.data.library.ContinueItem
import com.kaan.play.data.library.ContinueWatching
import com.kaan.play.data.model.HeroItem
import com.kaan.play.data.model.MediaSummary
import com.kaan.play.data.model.MediaType
import com.kaan.play.data.model.Page
import com.kaan.play.data.tmdb.TmdbRepository
import com.kaan.play.ui.components.Loadable
import com.kaan.play.ui.components.loadable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Ana sayfadaki TMDB sıraları (üstten alta). Her biri "tümü" ekranında sayfa sayfa açılır ([CategoryScreen]). */
enum class HomeRow {
    NewMovies, NewSeries, WeeklyMovies, TurkishMovies, TurkishSeries;

    suspend fun page(tmdb: TmdbRepository, page: Int): Page<MediaSummary> = when (this) {
        NewMovies -> tmdb.newMovies(page)
        NewSeries -> tmdb.newSeries(page)
        WeeklyMovies -> tmdb.weeklyMovies(page)
        TurkishMovies -> tmdb.turkishMovies(page)
        TurkishSeries -> tmdb.turkishSeries(page)
    }
}

typealias RowState = Loadable<List<MediaSummary>>

/** Her satır (ve kapak karuseli) bağımsız yüklenir ve bağımsız hata verir; biri başarısız olunca diğerleri etkilenmez. */
class HomeViewModel(
    private val tmdb: TmdbRepository,
    continueWatching: ContinueWatching,
) : ViewModel() {

    private val _rows = MutableStateFlow<Map<HomeRow, RowState>>(HomeRow.entries.associateWith { Loadable.Loading })
    val rows: StateFlow<Map<HomeRow, RowState>> = _rows.asStateFlow()

    private val _hero = MutableStateFlow<Loadable<List<HeroItem>>>(Loadable.Loading)
    val hero: StateFlow<Loadable<List<HeroItem>>> = _hero.asStateFlow()

    val continueWatching: StateFlow<List<ContinueItem>> =
        continueWatching.items.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        loadHero()
        HomeRow.entries.forEach(::load)
    }

    fun loadHero() {
        _hero.value = Loadable.Loading
        viewModelScope.launch { _hero.value = loadable { tmdb.hero() } }
    }

    fun load(row: HomeRow) {
        _rows.update { it + (row to Loadable.Loading) }
        viewModelScope.launch {
            val result = loadable { row.page(tmdb, 1).items }
            _rows.update { it + (row to result) }
        }
    }
}
