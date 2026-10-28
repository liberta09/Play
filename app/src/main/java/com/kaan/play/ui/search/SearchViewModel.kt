package com.kaan.play.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kaan.play.data.model.MediaSummary
import com.kaan.play.data.model.MediaType
import com.kaan.play.data.model.Page
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

/** Arama türü süzgeci: Tümü (`search/multi`), Filmler (`search/movie`), Diziler (`search/tv`). */
enum class SearchFilter(val type: MediaType?) {
    All(null),
    Movies(MediaType.Movie),
    Series(MediaType.Tv),
}

data class SearchState(
    val filter: SearchFilter = SearchFilter.All,
    /** Kutudaki ham metin. */
    val query: String = "",
    /** Sonuçları gösterilen (kırpılmış) sorgu; boşsa henüz arama yok. */
    val activeQuery: String = "",
    val items: List<MediaSummary> = emptyList(),
    val loading: Boolean = false,
    val loadingMore: Boolean = false,
    val error: TmdbException? = null,
    val page: Int = 0,
    val hasMore: Boolean = false,
)

@OptIn(FlowPreview::class)
class SearchViewModel(
    private val search: suspend (query: String, page: Int, type: MediaType?) -> Page<MediaSummary>,
    private val debounceMs: Long = DEBOUNCE_MS,
) : ViewModel() {

    constructor(tmdb: TmdbRepository) : this({ q, page, type -> tmdb.search(q, page, type) })

    private val _state = MutableStateFlow(SearchState())
    val state: StateFlow<SearchState> = _state.asStateFlow()

    private val queries = MutableStateFlow("")
    private var moreJob: Job? = null
    private var filterJob: Job? = null

    init {
        // Yazmayı bitirince ara; yeni sorgu gelince eskisinin isteği iptal edilir.
        viewModelScope.launch {
            queries.debounce(debounceMs).map { it.trim() }.distinctUntilChanged().collectLatest { q ->
                moreJob?.cancel()
                if (q.isEmpty()) {
                    _state.update { it.copy(activeQuery = "", items = emptyList(), loading = false, loadingMore = false, error = null, page = 0, hasMore = false) }
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

    /**
     * Tür çipi değişti: aynı sorgu yeni türle yeniden aranır, sayfalama sıfırlanır (ızgara başa döner). Sorgu boşsa
     * (ya da yazma bitip arama henüz başlamadıysa) istek yapılmaz; bekleyen arama yeni türle çalışır.
     */
    fun onFilterChange(filter: SearchFilter) {
        if (_state.value.filter == filter) return
        moreJob?.cancel()
        filterJob?.cancel()
        _state.update { it.copy(filter = filter) }
        val q = _state.value.activeQuery
        if (q.isEmpty()) return
        filterJob = viewModelScope.launch { runSearch(q) }
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
        val filter = s.filter
        _state.update { it.copy(loadingMore = true) }
        moreJob = viewModelScope.launch {
            try {
                val next = search(q, s.page + 1, filter.type)
                _state.update {
                    if (it.activeQuery != q || it.filter != filter) it else it.copy(
                        items = it.items + next.items.filter { n -> it.items.none { o -> o.id == n.id && o.type == n.type } },
                        page = next.page,
                        hasMore = next.hasMore,
                        loadingMore = false,
                    )
                }
            } catch (e: TmdbException) {
                // Sonraki sayfa hatası listeyi bozmaz; kullanıcı kaydırmaya devam edince yeniden denenir.
                _state.update { it.copy(loadingMore = false) }
            }
        }
    }

    private suspend fun runSearch(q: String) {
        val filter = _state.value.filter
        _state.update { it.copy(activeQuery = q, loading = true, loadingMore = false, error = null, items = emptyList(), page = 0, hasMore = false) }
        try {
            val first = search(q, 1, filter.type)
            _state.update { if (it.filter != filter) it else it.copy(items = first.items, page = first.page, hasMore = first.hasMore, loading = false) }
        } catch (e: TmdbException) {
            _state.update { if (it.filter != filter) it else it.copy(loading = false, error = e) }
        }
    }

    companion object {
        const val DEBOUNCE_MS = 400L
    }
}
