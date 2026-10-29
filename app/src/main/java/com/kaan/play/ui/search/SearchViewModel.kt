package com.kaan.play.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kaan.play.data.model.MediaSummary
import com.kaan.play.data.model.MediaType
import com.kaan.play.data.tmdb.TmdbException
import com.kaan.play.data.tmdb.TmdbRepository
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SearchState(
    /** Kutudaki ham metin. */
    val query: String = "",
    /** Sonuçları gösterilen (kırpılmış) sorgu; boşsa henüz arama yok. */
    val activeQuery: String = "",
    /** Geçerli filtreye göre ekrana çizilecek sonuçlar. */
    val items: List<MediaSummary> = emptyList(),
    /** Filtre değiştirirken yeniden ağ isteği yapılmaması için son ham sonuçlar. */
    val allItems: List<MediaSummary> = emptyList(),
    val filter: SearchFilter = SearchFilter.All,
    val loading: Boolean = false,
    val loadingMore: Boolean = false,
    val error: TmdbException? = null,
    val page: Int = 0,
    val hasMore: Boolean = false,
)

enum class SearchFilter {
    All,
    Movies,
    Series,
}

@OptIn(FlowPreview::class)
class SearchViewModel(private val tmdb: TmdbRepository) : ViewModel() {

    private val _state = MutableStateFlow(SearchState())
    val state: StateFlow<SearchState> = _state.asStateFlow()

    private val queries = MutableStateFlow("")
    private var moreJob: Job? = null

    init {
        // Yazmayı bitirince ara; yeni sorgu gelince eskisinin isteği iptal edilir.
        viewModelScope.launch {
            queries.debounce(DEBOUNCE_MS).map { it.trim() }.distinctUntilChanged().collectLatest { q ->
                moreJob?.cancel()
                if (q.isEmpty()) {
                    _state.update {
                        it.copy(
                            activeQuery = "",
                            items = emptyList(),
                            allItems = emptyList(),
                            loading = false,
                            loadingMore = false,
                            error = null,
                            page = 0,
                            hasMore = false,
                        )
                    }
                } else {
                    runSearch(q)
                }
            }
        }
    }

    fun onQueryChange(text: String) {
        _state.update { it.copy(query = text) }
        queries.value = text
    }

    /** Sonuçları yeniden indirmeden film/dizi/tümü görünümünü değiştirir. */
    fun setFilter(filter: SearchFilter) {
        _state.update { state ->
            state.copy(filter = filter, items = state.allItems.filterFor(filter))
        }
    }

    fun retry() {
        val q = _state.value.activeQuery.ifEmpty { _state.value.query.trim() }
        if (q.isNotEmpty()) viewModelScope.launch { runSearch(q) }
    }

    /** Listenin sonuna yaklaşılınca sonraki sayfa (varsa) eklenir. */
    fun loadMore() {
        val s = _state.value
        if (s.loading || s.loadingMore || s.error != null || !s.hasMore || s.activeQuery.isEmpty()) return
        val q = s.activeQuery
        _state.update { it.copy(loadingMore = true) }
        moreJob = viewModelScope.launch {
            try {
                val next = tmdb.search(q, s.page + 1)
                _state.update {
                    if (it.activeQuery != q) it else {
                        val merged = it.allItems + next.items.filter { candidate ->
                            it.allItems.none { existing -> existing.id == candidate.id && existing.type == candidate.type }
                        }
                        it.copy(
                            allItems = merged,
                            items = merged.filterFor(it.filter),
                            page = next.page,
                            hasMore = next.hasMore,
                            loadingMore = false,
                        )
                    }
                }
            } catch (e: TmdbException) {
                // Sonraki sayfa hatası listeyi bozmaz; kullanıcı kaydırmaya devam edince yeniden denenir.
                _state.update { it.copy(loadingMore = false) }
            }
        }
    }

    private suspend fun runSearch(q: String) {
        _state.update {
            it.copy(
                activeQuery = q,
                loading = true,
                loadingMore = false,
                error = null,
                items = emptyList(),
                allItems = emptyList(),
                page = 0,
                hasMore = false,
            )
        }
        try {
            val first = tmdb.search(q, 1)
            _state.update {
                it.copy(
                    allItems = first.items,
                    items = first.items.filterFor(it.filter),
                    page = first.page,
                    hasMore = first.hasMore,
                    loading = false,
                )
            }
        } catch (e: TmdbException) {
            _state.update { it.copy(loading = false, error = e) }
        }
    }

    private companion object {
        const val DEBOUNCE_MS = 400L
    }
}

private fun List<MediaSummary>.filterFor(filter: SearchFilter): List<MediaSummary> = when (filter) {
    SearchFilter.All -> this
    SearchFilter.Movies -> filter { it.type == MediaType.Movie }
    SearchFilter.Series -> filter { it.type == MediaType.Tv }
}
