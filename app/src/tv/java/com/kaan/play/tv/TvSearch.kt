package com.kaan.play.tv

import com.kaan.play.R
import android.app.Activity
import android.app.Application
import android.content.ActivityNotFoundException
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.tv.material3.Icon
import com.kaan.play.ui.nav.PlayIcons
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Text
import com.kaan.play.PlayApp
import com.kaan.play.data.model.MediaType
import com.kaan.play.data.provider.CatalogItem
import com.lagradost.cloudstream3.TvType
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeoutOrNull

/** Bir eklentinin arama sonuçları; null: hâlâ aranıyor. */
data class SearchSection(val providerId: String, val name: String, val items: List<CatalogItem>?)

/**
 * Eklentilerde arama. Play'in araması yalnız TMDB'de arar; eklenti araması (CsRuntime.search) dışarıya kapalı, burada
 * aynı yolla yapılır: açık CloudStream eklentileri → loadApis → api.search(sorgu). Her eklenti kendi süresiyle, en çok
 * üçü aynı anda; sonuçlar geldikçe görünür.
 */
class TvSearchModel(app: Application) : AndroidViewModel(app) {
    private val container get() = getApplication<PlayApp>().container
    private val _sections = MutableStateFlow<List<SearchSection>>(emptyList())
    val sections: StateFlow<List<SearchSection>> = _sections
    var lastQuery: String? = null
        private set
    /** Detaya geçilen son sonuç kartı ve liste konumu: detaydan dönünce ekran yeniden kurulur, odak/konum buradan gelir. */
    var lastOpenedKey: String? = null
    var listIndex = 0
    var listOffset = 0
    /** Sonuç satırlarının yatay konumu (eklenti → ilk görünen kart, kaydırma): açılan kart dönüşte yeniden çizilsin. */
    val rowScroll = HashMap<String, Pair<Int, Int>>()
    private var job: Job? = null

    fun search(query: String) {
        val q = query.trim()
        if (q.length < 2) return
        // Aynı arama yalnız sürüyorsa ya da sonuç verdiyse tekrarlanmaz; hepsi boş/başarısız bittiyse "Ara" yeniden dener.
        if (q == lastQuery && _sections.value.any { it.items == null || it.items.isNotEmpty() }) return
        lastQuery = q
        lastOpenedKey = null; listIndex = 0; listOffset = 0; rowScroll.clear()
        job?.cancel()
        // Bölüm adı ve "Dizi" etiketi uygulamanın seçili dilinde (uygulama bağlamı API 33 altında cihaz dilinde kalırdı).
        val res = getApplication<Application>().localized()
        job = viewModelScope.launch {
            val plugins = container.csRepos.enabledPlugins().filterNot { it.name.equals(SEARCH_UNSAFE_PLUGIN, ignoreCase = true) }
            val films = container.hasFilmSources()
            _sections.value = plugins.map { SearchSection("cs:${it.id}", it.name, null) } +
                listOfNotNull(if (films) SearchSection(TMDB_PROVIDER, res.getString(R.string.tv_section_films), null) else null)
            if (films) launch {
                val (page, person) = runCatching { container.tmdb.searchWithPerson(q) }.getOrNull() ?: (null to false)
                val items = page?.items.orEmpty().map { it.toCatalogItem() }
                _sections.update { all ->
                    val updated = all.map { if (it.providerId == TMDB_PROVIDER) it.copy(items = items) else it }
                    // Aranan ad bir oyuncuysa onun film/dizileri en üstte, eklenti sonuçları altta.
                    if (person) updated.sortedByDescending { it.providerId == TMDB_PROVIDER } else updated
                }
            }
            val gate = Semaphore(3)
            plugins.forEach { p ->
                launch {
                    // Eklenti kodu arka planda: bazı eklentiler (ör. MoreShort) aramada ağır işini çağıranın iş parçacığında
                    // yapıyor; ana iş parçacığında arayüz kilitleniyor ve uygulama ANR ile kapanıyordu.
                    // Az bellekli kutuda yalnız arama için yüklenen eklenti iş bitince bırakılır (203 eklentinin kodu bellekte
                    // birikip süreci ~440 MB'ta tutuyordu; sonuç açılınca eklenti yeniden yüklenir). Play'in kaynak aramasıyla aynı kural.
                    val found = gate.withPermit { withContext(Dispatchers.IO) {
                        runCatching {
                            container.csRepos.useApis(p.id, release = container.csSearchBudget.releaseAfterSearch) { loaded ->
                                loaded.apisOrEmpty().flatMap { api ->
                                    withTimeoutOrNull(SEARCH_TIMEOUT_MS) { runCatching { api.search(q) }.getOrNull() }.orEmpty()
                                }
                            }
                        }.getOrDefault(emptyList())
                    } }
                    val providerId = "cs:${p.id}"
                    val items = found.distinctBy { it.url.ifBlank { it.name } }.map { r ->
                        val series = r.type == TvType.TvSeries || r.type == TvType.Anime
                        CatalogItem(
                            id = "${r.name}_${r.url}", title = r.name,
                            subtitle = if (series) res.getString(R.string.tv_search_series) else null,
                            posterUrl = r.posterUrl, posterHeaders = r.posterHeaders,
                            mediaType = if (series) MediaType.Tv else MediaType.Movie,
                            providerId = providerId, rawUrl = r.url,
                        )
                    }
                    _sections.update { all -> all.map { if (it.providerId == providerId) it.copy(items = items) else it } }
                }
            }
        }
    }

    private companion object {
        const val SEARCH_TIMEOUT_MS = 15_000L
        /**
         * Aramada belleği bitiren eklenti: MoreShort'un `search`'ü (ensureTitleMap) büyük sayfada düzenli ifadeyle başlık haritası
         * kurarken saniyeler içinde ~1 GB yerel bellek ayırıyor; çalışan eklenti kodu durdurulamadığı için 2 GB'lık kutuda uygulama
         * kapanıyordu (2 GB'lık kutu ölçümü: arama başladıktan 1,8 sn sonra 37 MB → 1 GB). Ana sayfa ve detayda kalır.
         */
        const val SEARCH_UNSAFE_PLUGIN = "MoreShort"
    }
}

/**
 * Arama: solda arama kutusu, kumandayla kullanılan Türkçe ekran klavyesi ve son aramalar; sağda eklenti sonuçları.
 * Arama "Ara" tuşuyla başlar (yazarken her harfte arama, kesilemeyen eklenti aramalarını üst üste bindirirdi).
 */
@Composable
fun TvSearchScreen(onOpen: (DetailRef) -> Unit, pendingQuery: String? = null, onQueryUsed: () -> Unit = {}) {
    val model: TvSearchModel = viewModel()
    val sections by model.sections.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf(model.lastQuery.orEmpty()) }
    val context = LocalContext.current
    val recent = remember { RecentSearches(context) }
    var recentItems by remember { mutableStateOf(recent.items()) }
    val search: (String) -> Unit = { q ->
        if (q.trim().length >= 2) { model.search(q); recent.add(q.trim()); recentItems = recent.items() }
    }

    // Detaydaki oyuncu kartından gelen arama (Play'deki gibi oyuncunun adıyla).
    LaunchedEffect(pendingQuery) { pendingQuery?.let { query = it; search(it); onQueryUsed() } }

    // Telefonun klavyesi: yazı arama kutusuna gelir, "Ara" ile arama başlar (TvRemote).
    DisposableEffect(Unit) {
        val unregister = TvRemote.register { text, submit -> query = text; if (submit) search(text) }
        onDispose { unregister() }
    }

    // Sesli arama: sistemin konuşma tanıyıcısı (Google). Yoksa tuş gizlenir.
    val voiceIntent = remember {
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "tr-TR")
    }
    val hasVoice = remember { voiceIntent.resolveActivity(context.packageManager) != null }
    val voice = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        val text = r.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
        if (r.resultCode == Activity.RESULT_OK && !text.isNullOrBlank()) { query = text; search(text) }
    }

    Row(Modifier.fillMaxSize().padding(start = Gutter, top = 28.dp)) {
        // Kaydırılabilir: küçük TV ekranında (~540 dp) klavye + son aramalar sığmıyor; odaklanan öğe görünür alana gelir.
        Column(Modifier.width(KEYBOARD_WIDTH).fillMaxHeight().verticalScroll(rememberScrollState())) {
            SearchBox(query)
            Spacer(Modifier.height(12.dp))
            Keyboard(
                onKey = { query += it },
                onSpace = { if (query.isNotEmpty() && !query.endsWith(" ")) query += " " },
                onDelete = { query = query.dropLast(1) },
                onClear = { query = "" },
                onSearch = { search(query) },
                onVoice = if (hasVoice) ({ try { voice.launch(voiceIntent) } catch (_: ActivityNotFoundException) {} }) else null,
                // Detaydan dönüşte odak açılan sonuç kartına döner; klavye yalnız henüz kart açılmadıysa odak alır.
                requestFocus = model.lastOpenedKey == null,
            )
            if (recentItems.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                Text(stringResource(R.string.tv_search_recent), color = TextDim, fontSize = TvText.Label)
                Spacer(Modifier.height(6.dp))
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    recentItems.take(4).forEach { r ->
                        OutlinedButton(onClick = { query = r; search(r) }, contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp)) {
                            Text(r, fontSize = TvText.Meta, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
        Spacer(Modifier.width(28.dp))
        val listState = rememberLazyListState(model.listIndex, model.listOffset)
        DisposableEffect(listState) {
            onDispose { model.listIndex = listState.firstVisibleItemIndex; model.listOffset = listState.firstVisibleItemScrollOffset }
        }
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxHeight(),
            contentPadding = PaddingValues(bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            val searching = sections.count { it.items == null }
            if (model.lastQuery == null) {
                item(key = "hint") {
                    Text(stringResource(R.string.tv_search_input_hint), color = TextDim, fontSize = TvText.Body, modifier = Modifier.padding(top = 8.dp))
                }
            }
            if (model.lastQuery != null && sections.isNotEmpty() && sections.all { it.items?.isEmpty() == true }) {
                item(key = "none") { Text(stringResource(R.string.tv_search_no_results, model.lastQuery.orEmpty()), color = TextMain, fontSize = TvText.Body, modifier = Modifier.padding(top = 8.dp)) }
            }
            items(sections.filter { !it.items.isNullOrEmpty() }, key = { it.providerId }) { s ->
                ResultRow(s, model.lastOpenedKey, model.rowScroll, onOpen = { key, ref -> model.lastOpenedKey = key; onOpen(ref) })
            }
            if (searching > 0) {
                item(key = "wait") {
                    // Eklenti adları tek tek yazılmaz (100+ eklentide ekranı doldururdu); yalnız ilerleme.
                    Text(stringResource(R.string.tv_search_progress, sections.size - searching, sections.size), color = TextDim, fontSize = TvText.Label)
                }
            }
        }
    }
}

/** Arama kutusu (yalnız gösterir; yazı ekran klavyesinden gelir). */
@Composable
private fun SearchBox(query: String) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Surface1)
            .border(1.dp, TextDim.copy(alpha = 0.4f), RoundedCornerShape(24.dp)).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(PlayIcons.Search, contentDescription = null, tint = TextDim, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Text(
            query.ifEmpty { stringResource(R.string.tv_search_placeholder) }, color = if (query.isEmpty()) TextDim else TextMain, fontSize = TvText.Body,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Türkçe ekran klavyesi: 7 sütun harf/rakam, altta boşluk, sil, temizle ve Ara (ses varsa mikrofon). */
@Composable
private fun Keyboard(
    onKey: (String) -> Unit, onSpace: () -> Unit, onDelete: () -> Unit, onClear: () -> Unit, onSearch: () -> Unit, onVoice: (() -> Unit)?,
    requestFocus: Boolean,
) {
    val first = remember { FocusRequester() }
    var firstFocused by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(KEY_GAP)) {
        KEY_ROWS.forEachIndexed { r, row ->
            Row(horizontalArrangement = Arrangement.spacedBy(KEY_GAP)) {
                row.forEachIndexed { c, k ->
                    val m = if (r == 0 && c == 0) Modifier.focusRequester(first).onFocusChanged { firstFocused = it.isFocused } else Modifier
                    Key(k, m.width(KEY_SIZE)) { onKey(k) }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(KEY_GAP)) {
            Key(stringResource(R.string.tv_search_key_space), Modifier.width(KEY_SIZE * 2 + KEY_GAP), onClick = onSpace)
            Key(stringResource(R.string.tv_search_key_delete), Modifier.width(KEY_SIZE * 2 + KEY_GAP), onClick = onDelete)
            Key(stringResource(R.string.tv_search_key_clear), Modifier.width(KEY_SIZE * 3 + KEY_GAP * 2), onClick = onClear)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(KEY_GAP)) {
            val w = if (onVoice != null) KEY_SIZE * 5 + KEY_GAP * 4 else KEY_SIZE * 7 + KEY_GAP * 6
            Button(
                onClick = onSearch, modifier = Modifier.width(w).height(KEY_SIZE), contentPadding = PaddingValues(0.dp),
                shape = ButtonDefaults.shape(RoundedCornerShape(6.dp)),
                colors = ButtonDefaults.colors(containerColor = Accent, contentColor = OnAccent, focusedContainerColor = AccentFocused, focusedContentColor = OnAccent),
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(stringResource(R.string.tv_search_button), fontSize = TvText.Body, fontWeight = FontWeight.SemiBold) }
            }
            if (onVoice != null) Key("🎤", Modifier.width(KEY_SIZE * 2 + KEY_GAP), onClick = onVoice)
        }
    }
    if (requestFocus) InitialFocus(first) { firstFocused }
}

@Composable
private fun Key(label: String, modifier: Modifier, onClick: () -> Unit) {
    Button(
        onClick = onClick, modifier = modifier.height(KEY_SIZE), contentPadding = PaddingValues(0.dp),
        shape = ButtonDefaults.shape(RoundedCornerShape(6.dp)),
        colors = ButtonDefaults.colors(containerColor = Surface1, contentColor = TextMain, focusedContainerColor = TextMain, focusedContentColor = Bg),
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(label, fontSize = if (label.length == 1) TvText.Row else TvText.Label, maxLines = 1)
        }
    }
}

/** Son 4 arama (cihazda, profilden bağımsız; ekranda gösterilen kadar saklanır). */
private class RecentSearches(context: android.content.Context) {
    private val prefs = context.getSharedPreferences("play_tv_search", android.content.Context.MODE_PRIVATE)
    fun items(): List<String> = prefs.getString(KEY, "").orEmpty().split('\n').filter { it.isNotBlank() }
    fun add(q: String) {
        val now = (listOf(q) + items().filterNot { it.equals(q, ignoreCase = true) }).take(4)
        prefs.edit().putString(KEY, now.joinToString("\n")).apply()
    }
    private companion object { const val KEY = "recent" }
}

private val KEY_ROWS = listOf(
    listOf("a", "b", "c", "ç", "d", "e", "f"),
    listOf("g", "ğ", "h", "ı", "i", "j", "k"),
    listOf("l", "m", "n", "o", "ö", "p", "q"),
    listOf("r", "s", "ş", "t", "u", "ü", "v"),
    listOf("w", "x", "y", "z", "1", "2", "3"),
    listOf("4", "5", "6", "7", "8", "9", "0"),
)
private val KEY_SIZE = 38.dp
private val KEY_GAP = 5.dp
private val KEYBOARD_WIDTH = KEY_SIZE * 7 + KEY_GAP * 6

@Composable
private fun ResultRow(s: SearchSection, openedKey: String?, rowScroll: MutableMap<String, Pair<Int, Int>>, onOpen: (String, DetailRef) -> Unit) {
    val saved = rowScroll[s.providerId]
    val rowState = rememberLazyListState(saved?.first ?: 0, saved?.second ?: 0)
    DisposableEffect(rowState) { onDispose { rowScroll[s.providerId] = rowState.firstVisibleItemIndex to rowState.firstVisibleItemScrollOffset } }
    var landscape by remember(s.providerId) { mutableStateOf(s.providerId == TMDB_PROVIDER) }
    Column {
        Text("${s.name} (${s.items.orEmpty().size})", color = TextMain, fontSize = TvText.Row, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(bottom = 10.dp))
        LazyRow(state = rowState, horizontalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(end = Gutter, top = 4.dp, bottom = 4.dp)) {
            items(s.items.orEmpty(), key = { it.id }) { item ->
                val key = "${s.providerId}|${item.id}"
                PosterCard(
                    title = item.title, imageUrl = item.posterUrl, landscape = landscape,
                    requestInitialFocus = key == openedKey,
                    onImageSize = { w, h -> if (!landscape && w > h * 1.2f) landscape = true },
                    onClick = { item.toRef(landscape)?.let { onOpen(key, it) } },
                )
            }
        }
    }
}
