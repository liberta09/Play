package com.kaan.play.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kaan.play.data.db.WatchHistoryEntity
import com.kaan.play.data.library.LibraryRepository
import com.kaan.play.data.model.Episode
import com.kaan.play.data.model.MediaDetail
import com.kaan.play.data.model.MediaType
import com.kaan.play.data.tmdb.TmdbRepository
import com.kaan.play.ui.components.Loadable
import com.kaan.play.ui.components.loadable
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class DetailViewModel(
    private val tmdb: TmdbRepository,
    private val library: LibraryRepository,
    private val type: MediaType,
    private val id: Int,
) : ViewModel() {

    private val _detail = MutableStateFlow<Loadable<MediaDetail>>(Loadable.Loading)
    val detail: StateFlow<Loadable<MediaDetail>> = _detail.asStateFlow()

    /** Seçili sezon (yalnızca diziler). */
    private val _season = MutableStateFlow<Int?>(null)
    val season: StateFlow<Int?> = _season.asStateFlow()

    private val _episodes = MutableStateFlow<Loadable<List<Episode>>>(Loadable.Loading)
    val episodes: StateFlow<Loadable<List<Episode>>> = _episodes.asStateFlow()

    val isFavorite: StateFlow<Boolean> =
        library.isFavorite(type, id).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val latestHistory: StateFlow<WatchHistoryEntity?> = library.latestPerContent
        .map { list -> list.firstOrNull { it.tmdbId == id && it.type == type } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private var episodesJob: Job? = null

    init {
        loadDetail()
    }

    fun loadDetail() {
        _detail.value = Loadable.Loading
        viewModelScope.launch {
            val result = loadable { tmdb.detail(type, id) }
            _detail.value = result
            if (result is Loadable.Ready && type == MediaType.Tv) {
                // Varsayılan: ilk sezon.
                result.value.seasons.firstOrNull()?.let { selectSeason(it.number) }
            }
        }
    }

    fun selectSeason(number: Int) {
        _season.value = number
        loadEpisodes(number)
    }

    fun retryEpisodes() {
        _season.value?.let(::loadEpisodes)
    }

    private fun loadEpisodes(number: Int) {
        episodesJob?.cancel()
        _episodes.value = Loadable.Loading
        episodesJob = viewModelScope.launch {
            _episodes.value = loadable { tmdb.season(id, number).episodes }
        }
    }

    fun toggleFavorite() {
        val media = (_detail.value as? Loadable.Ready)?.value?.summary ?: return
        val target = !isFavorite.value
        viewModelScope.launch { library.setFavorite(media, target) }
    }
}
