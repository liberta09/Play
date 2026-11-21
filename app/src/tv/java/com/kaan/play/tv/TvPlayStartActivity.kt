package com.kaan.play.tv

import com.kaan.play.R
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.ListItem
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.kaan.play.AppContainer
import com.kaan.play.PlayApp
import com.kaan.play.data.stream.PlayableStream
import com.kaan.play.data.tmdb.TmdbImages
import com.kaan.play.ui.player.PlaySession
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Oynatıcıdan önceki kısa hazırlık: kaynaklar bulunup yoklanır ([TvSourcePicker]), TV oynatıcısı
 * ([TvPlayerActivity]) çalışan kaynakla açılır. Bir şey bulunamazsa ya da süre dolarsa oynatıcı kaynakları kendisi arar.
 * Geri tuşu hazırlığı iptal eder.
 *
 * Play'in iki ayarı burada uygulanır (Play'de PlayRoot.play'in karşılığı):
 * - "Her zaman kaldığım yerden devam et" kapalıysa ([EXTRA_ASK_RESUME]) önce "devam / baştan" sorulur; bu sırada kaynaklar
 *   arka planda aranır, yanıttan sonra beklenmez.
 * - "Kaynağı otomatik seç" kapalıysa kaynak listesi gösterilir, kullanıcı seçer. Liste boş biterse oynatıcının kendi araması
 *   ("kaynak bulunamadı" iletisi) devralır: oynatma kaynaksız beklemede kalmaz.
 */
class TvPlayStartActivity : AppCompatActivity() {
    // Metin onCreate'te etkinliğin (seçili dildeki) kaynağından verilir: alan ilk değeri bağlam bağlanmadan kurulur.
    private var status by mutableStateOf("")
    /** Sorulan kayıtlı konum (ms); null: soru yok ya da yanıtlandı. */
    private var resumeAsk by mutableStateOf<Long?>(null)
    /** Elle seçim listesi; null: liste gösterilmiyor (otomatik seçim ya da soru henüz yanıtlanmadı). */
    private var choices by mutableStateOf<List<SourceChoice>?>(null)
    /** "Devam / baştan" yanıtı: başlangıç konumu (ms). */
    private val resumeChoice = CompletableDeferred<Long>()
    /** Elle seçilen kaynak; null: liste boş bitti. */
    private val chosen = CompletableDeferred<PlayableStream?>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        status = getString(R.string.tv_play_searching_sources)
        val session = PlaySession.from(intent) ?: return finish()
        val container = (application as PlayApp).container
        val target = session.target
        if (intent.getBooleanExtra(EXTRA_ASK_RESUME, false) && session.startPositionMs > 0) {
            resumeAsk = session.startPositionMs
        } else {
            resumeChoice.complete(session.startPositionMs)
        }
        setContent {
            PlayTvTheme {
                Box(Modifier.fillMaxSize().background(Bg)) {
                    TmdbImages.url(target.posterPath, TmdbImages.Size.BackdropLarge)?.let {
                        AsyncImage(it, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize().alpha(0.25f))
                    }
                    val ep = target.request.episode
                    val episodeText = if (ep != null) stringResource(R.string.tv_play_season_episode, target.request.season ?: 1, ep) else null
                    val list = choices
                    if (list != null) {
                        SourceList(target.title, episodeText, status, list, onPick = { chosen.complete(it.stream) })
                    } else {
                        Centered {
                            Text(target.title, color = TextMain, fontSize = TvText.Hero, fontWeight = FontWeight.Bold)
                            if (episodeText != null) Text(episodeText, color = TextDim, fontSize = TvText.Body)
                            Spacer(Modifier.height(18.dp))
                            Text(status, color = Accent, fontSize = TvText.Body)
                        }
                    }
                    resumeAsk?.let { at ->
                        ResumePrompt(
                            positionMs = at,
                            onResume = { resumeAsk = null; resumeChoice.complete(at) },
                            onRestart = { resumeAsk = null; resumeChoice.complete(0L) },
                        )
                    }
                }
            }
        }
        lifecycleScope.launch {
            val autoSource = runCatching { container.settings.autoSource.first() }.getOrDefault(true)
            val next = if (autoSource) autoPick(session, container) else manualPick(session, container)
            if (isFinishing) return@launch
            startActivity(next.toIntent(this@TvPlayStartActivity).setClass(this@TvPlayStartActivity, TvPlayerActivity::class.java))
            @Suppress("DEPRECATION") overridePendingTransition(0, 0)
            finish()
        }
    }

    /** "Kaynağı otomatik seç" açık (varsayılan): en iyi çalışan kaynak ve yedekleri. Soru açıkken yoklama arka planda sürer. */
    private suspend fun autoPick(session: PlaySession, container: AppContainer): PlaySession = coroutineScope {
        val picking = async {
            withTimeoutOrNull(MAX_PICK_MS) {
                TvSourcePicker(container).pick(session.target.request) { p ->
                    status = if (p.dead > 0) getString(R.string.tv_play_status_testing_dead, p.found, p.alive, p.dead)
                    else getString(R.string.tv_play_status_testing, p.found, p.alive)
                }
            }
        }
        val start = started(session, resumeChoice.await())
        val picked = picking.await()
        if (picked != null) {
            start.copy(stream = picked.best, fallbacks = picked.fallbacks, autoSource = false)
        } else {
            Log.i(TAG, "pick: çalışan kaynak yok ya da süre doldu; Play'in kaynak araması kullanılıyor")
            start.copy(stream = null, fallbacks = emptyList(), autoSource = true)
        }
    }

    /**
     * "Kaynağı otomatik seç" kapalı: kaynaklar geldikçe listelenir ve yoklanır, kullanıcı OK ile seçer. Seçilmeyen çalışan
     * kaynaklar yedek olur (seçilen açılmazsa oynatıcı sıradakine geçer). Hiç kaynak çıkmazsa oynatıcı kendisi arar.
     */
    private suspend fun manualPick(session: PlaySession, container: AppContainer): PlaySession = coroutineScope {
        var rows: List<SourceChoice> = emptyList()
        val listing = launch {
            // Kaynak araması hata verirse elde olanla devam edilir (hazırlık ekranı kapanmadan takılmasın).
            runCatching {
                TvSourcePicker(container).list(session.target.request) { r ->
                    rows = r
                    if (resumeChoice.isCompleted) choices = r
                    val testing = r.count { it.probe == null }
                    val alive = r.count { it.probe == TvSourcePicker.Probe.Alive }
                    status = if (testing > 0) getString(R.string.tv_play_status_testing, r.size, alive)
                    else resources.getQuantityString(R.plurals.tv_play_status_listed, r.size, r.size, alive)
                }
            }.onFailure { if (it is CancellationException) throw it else Log.w(TAG, "kaynak listesi: ${it.javaClass.simpleName}") }
            if (rows.isEmpty()) chosen.complete(null)
        }
        val startMs = resumeChoice.await()
        choices = rows
        val stream = chosen.await()
        listing.cancel()
        val start = started(session, startMs)
        if (stream != null) {
            Log.i(TAG, "elle seçilen kaynak: ${stream.source.addonName} ${stream.resolution ?: "?"}p")
            start.copy(stream = stream, fallbacks = TvSourcePicker.fallbacks(stream, rows), autoSource = false)
        } else {
            Log.i(TAG, "elle seçim: kaynak bulunamadı; Play'in kaynak araması kullanılıyor")
            choices = null
            start.copy(stream = null, fallbacks = emptyList(), autoSource = true)
        }
    }

    /** Soru sorulduysa yanıt kullanıcı seçimidir (Play'deki gibi "sormadan devam edildi" bildirimi gerekmez). */
    private fun started(session: PlaySession, startMs: Long) =
        session.copy(startPositionMs = startMs, autoResumed = session.autoResumed && startMs > 0)

    companion object {
        /** [PlayLauncher]: kayıtlı konumdan devam edilsin mi diye sorulsun ("Her zaman kaldığım yerden devam et" kapalı). */
        const val EXTRA_ASK_RESUME = "com.kaan.play.tv.ASK_RESUME"
        private const val TAG = "PlayTvPick"
        private const val MAX_PICK_MS = 25_000L
    }
}

/** "İzlemeye devam edilsin mi?" (Play'deki pencere): odak kaldığı yerden devamda başlar; Geri hazırlığı iptal eder. */
@Composable
private fun ResumePrompt(positionMs: Long, onResume: () -> Unit, onRestart: () -> Unit) {
    val resume = remember { FocusRequester() }
    var resumeFocused by remember { mutableStateOf(false) }
    val colors = ButtonDefaults.colors(containerColor = Bg, contentColor = TextMain, focusedContainerColor = Accent, focusedContentColor = OnAccent)
    Box(Modifier.fillMaxSize().background(Bg.copy(alpha = 0.6f)), contentAlignment = Alignment.Center) {
        Column(
            Modifier.clip(RoundedCornerShape(16.dp)).background(Surface1).padding(horizontal = 36.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(stringResource(R.string.tv_play_resume_title), color = TextMain, fontSize = TvText.Screen, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Button(
                    onClick = onResume,
                    colors = colors,
                    modifier = Modifier.focusRequester(resume).onFocusChanged { resumeFocused = it.isFocused },
                ) { Text(stringResource(R.string.tv_play_resume_continue, positionText(positionMs)), fontSize = TvText.Body) }
                Button(onClick = onRestart, colors = colors) { Text(stringResource(R.string.tv_play_resume_restart), fontSize = TvText.Body) }
            }
        }
    }
    InitialFocus(resume) { resumeFocused }
}

/** Elle kaynak seçimi: eklenti, çözünürlük, ses ve yoklama sonucu; OK seçer. */
@Composable
private fun SourceList(title: String, episode: String?, status: String, rows: List<SourceChoice>, onPick: (SourceChoice) -> Unit) {
    val first = remember { FocusRequester() }
    var firstFocused by remember { mutableStateOf(false) }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = Gutter, end = Gutter, top = 36.dp, bottom = 48.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        item(key = "title") {
            Column(Modifier.padding(bottom = 10.dp)) {
                Text(title, color = TextMain, fontSize = TvText.Hero, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (episode != null) Text(episode, color = TextDim, fontSize = TvText.Body)
                Spacer(Modifier.height(10.dp))
                Text(stringResource(R.string.tv_play_choose_source), color = TextMain, fontSize = TvText.Row, fontWeight = FontWeight.SemiBold)
                Text(status, color = Accent, fontSize = TvText.Body)
            }
        }
        // Anahtar adres: yoklama bitip satırlar yer değiştirse de odak seçili kaynakta kalır.
        itemsIndexed(rows, key = { _, c -> c.stream.url }) { i, c ->
            val s = c.stream
            val details = listOfNotNull(
                s.resolution?.let { "${it}p" },
                when {
                    s.dubbed -> stringResource(R.string.tv_play_turkish_dub)
                    s.language.equals("TR", ignoreCase = true) -> stringResource(R.string.tv_play_turkish_audio)
                    else -> s.language
                },
                "Torrent".takeIf { s.url.startsWith("magnet:") },
                s.sizeBytes?.takeIf { it > 0 }?.let { "%.1f GB".format(it / 1_073_741_824.0) },
            ).joinToString(" · ")
            val (state, stateColor) = when (c.probe) {
                null -> stringResource(R.string.tv_play_probe_testing) to TextDim
                TvSourcePicker.Probe.Alive -> stringResource(R.string.tv_play_probe_alive) to Accent
                TvSourcePicker.Probe.Dead -> stringResource(R.string.tv_play_probe_dead) to TextDim
                TvSourcePicker.Probe.Unknown -> stringResource(R.string.tv_play_probe_unknown) to TextDim
            }
            ListItem(
                selected = false,
                onClick = { onPick(c) },
                headlineContent = { Text(s.source.addonName, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                supportingContent = details.takeIf { it.isNotEmpty() }?.let { d -> @Composable { Text(d, fontSize = TvText.Meta) } },
                trailingContent = { Text(state, color = stateColor, fontSize = TvText.Body) },
                modifier = if (i == 0) Modifier.focusRequester(first).onFocusChanged { firstFocused = it.isFocused } else Modifier,
            )
        }
    }
    // İlk kaynak gelince odak listeye: kumanda boşa düşmesin.
    if (rows.isNotEmpty()) InitialFocus(first) { firstFocused }
}

private fun positionText(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, s / 60 % 60, s % 60) else "%d:%02d".format(s / 60, s % 60)
}
