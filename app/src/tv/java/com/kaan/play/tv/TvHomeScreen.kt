package com.kaan.play.tv

import com.kaan.play.R
import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.background
import androidx.tv.material3.ListItem
import androidx.compose.ui.window.Dialog
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.Lifecycle
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kaan.play.PlayApp
import androidx.tv.material3.Button
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.kaan.play.data.provider.CatalogItem
import com.kaan.play.data.provider.CatalogRow
import com.kaan.play.data.provider.ProviderItem
import com.kaan.play.data.provider.ProviderKind
import com.kaan.play.data.provider.ProviderRules
import com.kaan.play.data.model.MediaType
import com.kaan.play.data.tmdb.PosterUrlTemplate

/**
 * Düzen: üstte odaktaki dizinin büyük tanıtımı, altında sağlayıcı/katalog satırları.
 * Kumandayla yukarı-aşağı satır, sağa-sola kart; odaktaki kart büyür ve vurgu çerçeve alır.
 */
@Composable
fun TvHomeScreen(model: TvHomeModel, onOpen: (DetailRef) -> Unit, onPlay: PlayLauncher, onSeeAll: (CategoryGrid) -> Unit) {
    val state by model.state.collectAsStateWithLifecycle()
    Box(Modifier.fillMaxSize().background(Bg)) {
        when (val s = state) {
            is HomeState.Preparing -> Centered { Brand(); Spacer(Modifier.height(16.dp)); Text(stringResource(s.message), color = TextDim, fontSize = TvText.Body) }
            is HomeState.Failed -> Centered {
                Brand(); Spacer(Modifier.height(16.dp))
                Text(stringResource(s.message), color = TextMain, fontSize = TvText.Body)
                Spacer(Modifier.height(20.dp))
                val retry = remember { FocusRequester() }
                Button(onClick = model::load, modifier = Modifier.focusRequester(retry)) { Text(stringResource(R.string.tv_home_retry)) }
                LaunchedEffect(Unit) { runCatching { retry.requestFocus() } }
            }
            is HomeState.Ready -> Home(model, s, onOpen, onPlay, onSeeAll)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class) // BringIntoViewSpec: TV'de odak kaydırmasını ayarlamanın resmi yolu
@Composable
private fun Home(model: TvHomeModel, state: HomeState.Ready, onOpen: (DetailRef) -> Unit, onPlay: PlayLauncher, onSeeAll: (CategoryGrid) -> Unit) {
    val focused by model.focused.collectAsStateWithLifecycle()
    // kart 3 sn odakta kalınca sesli fragman/ilk bölüm önizlemesi; ekran arka plana geçince durur.
    val previewModel: TvPreviewModel = viewModel(key = "preview-${model.kind}")
    val preview by previewModel.preview.collectAsStateWithLifecycle()
    LaunchedEffect(focused) { previewModel.focus(focused) }
    // ON_PAUSE: oynatıcı ekranı açılırken önizleme hemen susar (ON_STOP geç gelir, kısa süre çift ses/iki çözücü);
    // dönüşte odaktaki kart yeniden önizlenir.
    LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) { previewModel.stop() }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { previewModel.focus(focused) }
    DisposableEffect(Unit) { onDispose { previewModel.stop() } }
    val tmdbRows by model.tmdbRows.collectAsStateWithLifecycle()
    // Play ana sayfasının sırası: devam et → addon katalogları (Play Özel Kataloglar önde) →
    // seçili drama eklentileri ("Eklenti seç" başlığıyla) → TMDB satırları. Filmler / Diziler aynı sırayla, yalnız kendi türü.
    val shownRows = state.sections.flatMap { it.rows.orEmpty() }.filter { it.items.isNotEmpty() }
    val kindMovies = stringResource(R.string.tv_home_kind_movies)
    val kindSeries = stringResource(R.string.tv_home_kind_series)
    val addonRows: List<CatalogRow> = withSourceNames(
        state.sections.filter { it.provider.kind != ProviderKind.CloudStream }.flatMap { it.rows.orEmpty() }, kindMovies, kindSeries,
    ).filter { it.items.isNotEmpty() }
    val dramaRows: List<CatalogRow> = state.sections.filter { it.provider.kind == ProviderKind.CloudStream }
        .flatMap { it.rows.orEmpty() }.filter { it.items.isNotEmpty() }
    // Play gibi: görünen bir eklenti satırıyla aynı adı taşıyan TMDB satırı (ör. "Popüler filmler") ikinci kopya olarak gizlenir.
    val hiddenTmdb = ProviderRules.duplicateTmdbRows(tmdbRows.associate { it.id to it.title }, shownRows.map { it.title })
    val tmdbShown: List<CatalogRow> = tmdbRows.filter { it.id !in hiddenTmdb && it.items.isNotEmpty() }
    val rows: List<CatalogRow> = addonRows + dramaRows + tmdbShown
    val waiting = state.sections.filter { it.rows == null }.map { it.provider.name }
    // Play'deki "Doğrula": satır vermeyen ve bot doğrulaması isteyen sağlayıcılar; doğrulama başarılıysa yeniden yüklenir.
    val blocked by model.blocked.collectAsStateWithLifecycle()
    val verifyRows = state.sections.filter { it.rows?.isEmpty() == true }.mapNotNull { s -> blocked[s.provider.id]?.let { s.provider.id to it } }
    var verifying by remember { mutableStateOf<String?>(null) }
    val verifyLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        val id = verifying
        verifying = null
        if (r.resultCode == Activity.RESULT_OK && id != null) model.reloadProvider(id)
    }
    val context = LocalContext.current
    val verifyRow: @Composable (Pair<String, VerifySite>) -> Unit = { (id, site) ->
        VerifyRow(site, onVerify = { verifying = id; verifyLauncher.launch(site.intent(context)) }, onRetry = { model.reloadProvider(id) })
    }
    // Devam et: Ana sayfada hepsi; Filmler / Diziler'de o türün TMDB kayıtları.
    val continueLoaded = rememberContinueItemsOrNull()
    val continueItems = continueLoaded.orEmpty().filter { c ->
        when (model.kind) {
            HomeKind.Home -> true
            HomeKind.Movies -> c.entry.pluginId == null && c.entry.type == MediaType.Movie
            HomeKind.Series -> c.entry.pluginId == null && c.entry.type == MediaType.Tv
        }
    }
    // Açılışta odak: geri dönüşte son kart, yoksa devam et satırının ilk kartı, yoksa ilk katalog kartı. Devam et listesi
    // veritabanından gelmeden odak verilmez; yoksa önbellekteki satır odağı kapar ve devam et satırı ekranın dışında kalırdı.
    val firstKey = if (continueLoaded == null) null
        else continueItems.firstOrNull()?.let(::continueKey) ?: rows.firstOrNull()?.let { cardKey(it, it.items.first()) }

    val listState = rememberLazyListState(model.listIndex, model.listOffset)
    DisposableEffect(listState) {
        onDispose {
            model.listIndex = listState.firstVisibleItemIndex; model.listOffset = listState.firstVisibleItemScrollOffset
            model.listKey = listState.layoutInfo.visibleItemsInfo.firstOrNull()?.key
        }
    }
    // Dönüşte yer anahtarla bulunur: devam et satırı veritabanından sonra gelip sıraları bir kaydırıyordu.
    val columnKeys: List<Any> = buildList {
        if (continueItems.isNotEmpty()) add("continue")
        addonRows.forEach { add("${it.providerId}|${it.id}") }
        if (model.kind == HomeKind.Home) add("drama-picker")
        verifyRows.forEach { add("verify|${it.first}") }
        (dramaRows + tmdbShown).forEach { add("${it.providerId}|${it.id}") }
    }
    LaunchedEffect(continueLoaded != null) {
        if (continueLoaded == null) return@LaunchedEffect
        val key = model.listKey ?: return@LaunchedEffect
        model.listKey = null
        columnKeys.indexOf(key).takeIf { it >= 0 }?.let { runCatching { listState.scrollToItem(it, model.listOffset) } }
    }
    val rowSpec = LocalBringIntoViewSpec.current // yatay satırlar varsayılan kaydırmayı korur
    val titleSpacePx = with(LocalDensity.current) { RowTitleSpace.toPx() }
    // odaklanan kartın satırı, başlığı görünecek şekilde listenin en üstüne oturur; yarım satır görünmez.
    val columnSpec = remember(titleSpacePx) {
        object : BringIntoViewSpec {
            override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float = offset - titleSpacePx
        }
    }

    // İlk satır gelene kadar liste kurulmaz: boş listeye önce başka bir öğe girerse liste kaydırma konumunu ona sabitler
    // ve sonradan gelen satırlar görünür alanın üstünde kalır.
    if (rows.isEmpty()) {
        Centered {
            Brand(); Spacer(Modifier.height(16.dp))
            if (waiting.isNotEmpty()) {
                Text(stringResource(R.string.tv_common_loading), color = TextDim, fontSize = TvText.Body)
            } else {
                Text(stringResource(R.string.tv_home_no_content), color = TextMain, fontSize = TvText.Body)
                verifyRows.forEach { Spacer(Modifier.height(16.dp)); verifyRow(it) }
                Spacer(Modifier.height(20.dp))
                val retry = remember { FocusRequester() }
                Button(onClick = model::load, modifier = Modifier.focusRequester(retry)) { Text(stringResource(R.string.tv_home_retry)) }
                LaunchedEffect(Unit) { runCatching { retry.requestFocus() } }
            }
        }
        return
    }

    val focusedLandscape by model.focusedLandscape.collectAsStateWithLifecycle()
    val heroItem = focused ?: rows.first().items.first()
    val heroKey = "${heroItem.providerId}|${heroItem.rawUrl ?: heroItem.id}"
    val heroPreview = preview?.takeIf { it.first == heroKey }?.second
    val heroLandscape = if (focused != null) focusedLandscape else rows.first().isHorizontalImages
    Column(Modifier.fillMaxSize()) {
        Hero(
            heroItem,
            preview = heroPreview,
            onPreviewEnded = { failed -> if (failed) previewModel.forgetFocused(); previewModel.stop() },
            landscape = heroLandscape,
            modifier = Modifier.fillMaxWidth().fillMaxHeight(0.44f),
        )
        // Devam et satırı önbellekteki satırlardan sonra gelir ve listenin başına eklenir; kullanıcı henüz aşağı inmediyse
        // liste başa döner ki satır görünsün (odak yerinde kalır).
        val hasContinue = continueItems.isNotEmpty()
        LaunchedEffect(hasContinue) {
            if (hasContinue && listState.firstVisibleItemIndex <= 1) runCatching { listState.scrollToItem(0) }
        }
        CompositionLocalProvider(LocalBringIntoViewSpec provides columnSpec) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = 4.dp, bottom = 48.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                if (continueItems.isNotEmpty()) {
                    item(key = "continue") {
                        val continueLabel = stringResource(R.string.tv_home_continue)
                        CompositionLocalProvider(LocalBringIntoViewSpec provides rowSpec) {
                            ContinueRow(
                                continueItems, onPlay, onOpen,
                                initialKey = model.lastFocusedKey ?: firstKey,
                                onFocused = { key ->
                                    model.lastFocusedKey = key
                                    continueItems.firstOrNull { continueKey(it) == key }?.entry?.let { e ->
                                        model.focusedLandscape.value = false
                                        // Önizleme detay adresini bundan çözer: eklentide eklenti + içerik adresi, TMDB'de `tmdb` + tür:kimlik.
                                        val ref = e.toRef()
                                        model.focused.value = CatalogItem(
                                            id = key, title = e.title, subtitle = continueLabel,
                                            posterUrl = e.posterPath, providerId = ref.providerId, rawUrl = ref.url.ifBlank { null },
                                        )
                                    }
                                },
                            )
                        }
                    }
                }
                items(addonRows, key = { "${it.providerId}|${it.id}" }) { row ->
                    CompositionLocalProvider(LocalBringIntoViewSpec provides rowSpec) {
                        CatalogRowView(row, model, initialKey = model.lastFocusedKey ?: firstKey, onOpen = onOpen, onSeeAll = onSeeAll)
                    }
                }
                if (model.kind == HomeKind.Home) item(key = "drama-picker") { DramaPicker(model) }
                items(verifyRows, key = { "verify|${it.first}" }) { verifyRow(it) }
                items(dramaRows + tmdbShown, key = { "${it.providerId}|${it.id}" }) { row ->
                    CompositionLocalProvider(LocalBringIntoViewSpec provides rowSpec) {
                        CatalogRowView(row, model, initialKey = model.lastFocusedKey ?: firstKey, onOpen = onOpen, onSeeAll = onSeeAll)
                    }
                }
            }
        }
    }
}

/** Bot doğrulaması isteyen sağlayıcı (Play ana sayfasındaki hata kutusunun TV satırı): ileti + "Doğrula" + "Yeniden dene". */
@Composable
private fun VerifyRow(site: VerifySite, onVerify: () -> Unit, onRetry: () -> Unit) {
    Row(Modifier.padding(start = Gutter, end = Gutter), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Column(Modifier.weight(1f, fill = false)) {
            Text(
                site.name + " · " + stringResource(com.kaan.play.R.string.provider_verification_required),
                color = TextMain, fontSize = TvText.Body, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Text(stringResource(com.kaan.play.R.string.provider_bot_protection_failed) + " (" + site.host + ")", color = TextDim, fontSize = TvText.Meta, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Button(onClick = onVerify) { Text(stringResource(com.kaan.play.R.string.provider_verify_action)) }
        Button(onClick = onRetry) { Text(stringResource(com.kaan.play.R.string.provider_catalog_retry)) }
    }
}

/** Ana sayfada dramaların başlığı + Play'deki gibi "Eklenti seç": sabitlenen eklentiler (Play'in iğnesi) ana sayfada gösterilir. */
@Composable
private fun DramaPicker(model: TvHomeModel) {
    var open by remember { mutableStateOf(false) }
    var count by remember { mutableStateOf(0) }
    LaunchedEffect(open) { if (!open) count = model.pickerOptions().count { it.isPinned } }
    Row(Modifier.padding(start = Gutter), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(stringResource(R.string.tv_home_dramas), color = TextMain, fontSize = TvText.Row, fontWeight = FontWeight.SemiBold)
        Button(onClick = { open = true }) { Text(if (count == 0) stringResource(R.string.tv_home_pick_default) else pluralStringResource(R.plurals.tv_home_pick_count, count, count)) }
    }
    if (open) DramaPickerDialog(model, onDismiss = { open = false })
}

@Composable
private fun DramaPickerDialog(model: TvHomeModel, onDismiss: () -> Unit) {
    var options by remember { mutableStateOf<List<ProviderItem>?>(null) }
    var chosen by remember { mutableStateOf<Set<String>>(emptySet()) }
    // Seçim = Play'in sabitlemesi: işaretli olanlar sabitlenir; addon'lar da listede (Play seçicisi gibi).
    LaunchedEffect(Unit) { val o = model.pickerOptions(); chosen = o.filter { it.isPinned }.mapTo(HashSet()) { it.id }; options = o }
    val csChosen = options.orEmpty().any { it.kind == ProviderKind.CloudStream && it.id in chosen }
    val first = remember { FocusRequester() }
    // Liste ~650 satır olabildiği için düğmeler listenin üstünde; Geri de kaydeder (alttaki düğmeye inilemezdi).
    val save = { model.setPinned(options.orEmpty(), chosen); onDismiss() }
    Dialog(onDismissRequest = save) {
        Column(
            Modifier.width(620.dp).heightIn(max = 560.dp).background(Surface1, RoundedCornerShape(16.dp)).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(stringResource(R.string.tv_home_picker_title), color = TextMain, fontSize = TvText.Row, fontWeight = FontWeight.SemiBold)
            Text(stringResource(R.string.tv_home_picker_note), color = TextDim, fontSize = TvText.Meta)
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Button(onClick = save) { Text(stringResource(R.string.tv_save)) }
                Button(onClick = onDismiss) { Text(stringResource(R.string.tv_cancel)) }
            }
            LazyColumn(Modifier.weight(1f, fill = false)) {
                item(key = "default") {
                    // Varsayılan: hiçbir CloudStream eklentisi sabitli değil → eklenti satırı yok. Basınca CloudStream
                    // sabitlemeleri kalkar; addon sabitlemeleri kalır.
                    ListItem(
                        selected = !csChosen, onClick = { chosen = chosen.filterTo(HashSet()) { id -> options.orEmpty().none { it.id == id && it.kind == ProviderKind.CloudStream } } },
                        headlineContent = { Text(stringResource(R.string.tv_home_picker_default)) },
                        trailingContent = { if (!csChosen) Text("✓", color = Accent) },
                        modifier = Modifier.focusRequester(first),
                    )
                }
                items(options.orEmpty(), key = { it.id }) { p ->
                    val id = p.id
                    ListItem(
                        selected = id in chosen, onClick = { chosen = if (id in chosen) chosen - id else chosen + id },
                        headlineContent = { Text(p.name) },
                        supportingContent = { Text(if (p.kind == ProviderKind.CloudStream) "CloudStream" else stringResource(R.string.tv_home_picker_addon), color = TextDim) },
                        trailingContent = { if (id in chosen) Text(stringResource(R.string.tv_home_picker_pinned), color = Accent) },
                    )
                }
            }
        }
        LaunchedEffect(options) { if (options != null) runCatching { first.requestFocus() } }
    }
}

/** Satır başlığının yüksekliği + boşluk: odaklanan kart bu kadar aşağıda durur ki başlık görünsün. */
private val RowTitleSpace = 36.dp

@Composable
private fun CatalogRowView(
    row: CatalogRow,
    model: TvHomeModel,
    initialKey: String?,
    onOpen: (DetailRef) -> Unit,
    onSeeAll: (CategoryGrid) -> Unit,
) {
    // Kart yönü: eklenti satırı yatay işaretlediyse ya da satırın ilk gelen afişi genişse 16:9 (film/dizi kareleri),
    // değilse dikey 2:3 afiş (kısa drama). İşareti koymayan eklentilerde gerçek görsel oranı karar verir.
    var landscape by remember(row.providerId, row.id) { mutableStateOf(row.isHorizontalImages) }
    Column {
        Text(
            row.title,
            color = TextMain, fontSize = TvText.Row, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(start = Gutter, bottom = 8.dp),
        )
        // Dönüşte satır odaktaki karttan başlar: satırın sonundaki "Tümünü gör" kartı (ya da sağdaki kart) oluşmadan odak alamazdı.
        val startIndex = remember(row.providerId, row.id) {
            if (initialKey == "${row.providerId}|${row.id}|see-all") row.items.size
            else row.items.indexOfFirst { cardKey(row, it) == initialKey }.coerceAtLeast(0)
        }
        LazyRow(
            state = rememberLazyListState(startIndex),
            contentPadding = PaddingValues(horizontal = Gutter, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(row.items, key = { it.id }) { item ->
                val key = cardKey(row, item)
                PosterCard(
                    title = item.title,
                    imageUrl = item.posterUrl,
                    landscape = landscape,
                    requestInitialFocus = key == initialKey,
                    onFocused = {
                        model.focused.value = item; model.focusedLandscape.value = landscape
                        model.lastFocusedKey = key
                    },
                    onImageSize = { w, h -> if (!landscape && w > h * 1.2f) landscape = true },
                    onClick = { item.toRef(landscape)?.let(onOpen) },
                    // Play'deki gibi kimlik + içerik adresinden IMDb kimliği aranır (Poster URL şablonu için).
                    posterId = PosterUrlTemplate.findImdbId(item.id, item.rawUrl),
                )
            }
            // Play'deki "Tümünü göster": satırın sonunda kart; ızgara sonraki sayfaları kaydırdıkça yükler.
            item(key = "see-all") {
                val key = "${row.providerId}|${row.id}|see-all"
                SeeAllCard(
                    landscape = landscape,
                    requestInitialFocus = key == initialKey,
                    onFocused = { model.lastFocusedKey = key },
                    onClick = { onSeeAll(CategoryGrid(row, landscape)) },
                )
            }
        }
    }
}

/**
 * Odaktaki içeriğin büyük tanıtımı: görsel sağa yaslı, sola ve aşağı zemine yumuşak geçiş; solda ad (26 sp)
 * ve bilgi satırı. Film/dizi karesi (16:9) tam genişlik arka plandır; dikey drama afişi arka planda karartılır, kendisi sağda durur.
 */
@Composable
private fun Hero(
    item: CatalogItem?, preview: Preview?, onPreviewEnded: (failed: Boolean) -> Unit, landscape: Boolean, modifier: Modifier,
) {
    // önizleme üstteki tanıtımda, görselin yerinde (sağda) oynar; ilk görüntü gelince görsel söner, kenarlar zemine
    // kararır. Dikey dramada ilk 30 sn başa dönerek sürer, filmde fragman sonuna kadar.
    var videoShown by remember(preview) { mutableStateOf(false) }
    val behind by animateFloatAsState(if (preview != null && videoShown) 1f else 0f, tween(600), label = "hero")
    // Dikey afişte Poster URL şablonu (karttaki afişle aynı görsel); yatay karede 16:9 görsel kalır.
    val poster = if (landscape) item?.posterUrl else item?.let { templatedPoster(it.posterUrl, it.id, it.rawUrl) }
    Box(modifier) {
        val art = Modifier.fillMaxHeight().fillMaxWidth(0.70f).align(Alignment.TopEnd)
        if (preview != null) {
            // zoom kipinde video kutudan taşar; kutuya kırpılır.
            PreviewView(preview, art.clipToBounds(), loop = !landscape, onRendered = { videoShown = true }, onEnded = onPreviewEnded)
        }
        if (poster != null) {
            AsyncImage(
                model = poster, contentDescription = null, contentScale = ContentScale.Crop, alignment = Alignment.TopCenter,
                modifier = art.alpha((if (landscape) 1f else 0.30f) * (1f - behind)),
            )
        }
        // Videonun üst ve sağ kenarı hafifçe kararır (sol ve alt geçişler aşağıda, görselle ortak).
        Box(art.alpha(behind).background(Brush.verticalGradient(0f to Bg.copy(alpha = 0.6f), 0.18f to Color.Transparent)))
        Box(art.alpha(behind).background(Brush.horizontalGradient(0.85f to Color.Transparent, 1f to Bg.copy(alpha = 0.7f))))
        Box(
            Modifier.fillMaxSize().background(
                Brush.horizontalGradient(0f to Bg, 0.30f to Bg, 0.55f to Bg.copy(alpha = 0.55f), 1f to Color.Transparent),
            ),
        )
        Box(Modifier.fillMaxWidth().fillMaxHeight(0.45f).align(Alignment.BottomCenter).background(Brush.verticalGradient(listOf(Color.Transparent, Bg))))
        Row(Modifier.fillMaxSize().padding(start = Gutter, top = 32.dp, end = Gutter), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f).fillMaxHeight()) {
                if (item != null) {
                    Text(
                        item.title, color = TextMain, fontSize = TvText.Hero, lineHeight = 32.sp, fontWeight = FontWeight.Bold,
                        maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.fillMaxWidth(0.75f),
                    )
                    item.subtitle?.takeIf { it.isNotBlank() }?.let {
                        Spacer(Modifier.height(8.dp))
                        Text(it, color = TextDim, fontSize = TvText.Meta, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.fillMaxWidth(0.75f))
                    }
                }
            }
            if (poster != null && !landscape) {
                AsyncImage(
                    model = poster, contentDescription = null, contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxHeight(0.88f).aspectRatio(2f / 3f).clip(RoundedCornerShape(8.dp)).alpha(1f - behind),
                )
            }
        }
    }
}

private fun cardKey(row: CatalogRow, item: CatalogItem) = "${row.providerId}|${row.id}|${item.id}"

/**
 * Addon satırları kaynak adıyla başlar (ör. "izlelan · Popüler"), TMDB satırlarıyla karışmaz (Play satırın altına kaynak adını yazar).
 * Aynı addon'da aynı adlı iki satır (izlelan'ın film ve dizi "Popüler"i) türüyle ayrılır; satır kimliği "tür:katalog".
 */
private fun withSourceNames(rows: List<CatalogRow>, movies: String, series: String): List<CatalogRow> = rows.map { r ->
    val twin = rows.count { it.providerId == r.providerId && it.title == r.title } > 1
    val kind = when (r.id.substringBefore(':')) { "movie" -> " $movies"; "series" -> " $series"; else -> "" }
    r.copy(title = "${r.providerName} · ${r.title}" + if (twin) kind else "")
}
