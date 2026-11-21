package com.kaan.play.tv

import com.kaan.play.R
import androidx.compose.ui.res.stringResource

import android.content.Context
import android.content.res.Configuration
import com.kaan.play.i18n.AppLanguage
import java.util.Locale
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import androidx.tv.material3.darkColorScheme
import androidx.tv.material3.lightColorScheme
import com.kaan.play.PlayApp
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import coil3.compose.AsyncImage
import com.kaan.play.data.tmdb.PosterUrlTemplate
import kotlinx.coroutines.delay

/** Tema renkleri (Play'in görsel kimliği: koyu sinematik zemin, tek camgöbeği vurgu); Play'in "Tema" ayarına göre PlayTvTheme verir. */
@Immutable
internal data class TvColors(
    val bg: Color,
    val surface1: Color,
    val accent: Color,
    val accentFocused: Color,
    /** Vurgu zemini üstündeki yazı. */
    val onAccent: Color,
    val textMain: Color,
    val textDim: Color,
    /** Hata yazısı (yanlış PIN, profil silme). */
    val error: Color,
)

internal val DarkTvColors = TvColors(
    bg = Color(0xFF07111A),
    surface1 = Color(0xFF0F1E2E),
    accent = Color(0xFF00E5FF),
    // Vurgulu düğme odaktayken (açık ton).
    accentFocused = Color(0xFF7DF2FF),
    onAccent = Color(0xFF07111A),
    textMain = Color(0xFFF2F4F7),
    textDim = Color(0xFFA9B1BD),
    error = Color(0xFFE57373),
)

// Açık tema: vurgu açık zeminde okunsun diye koyu camgöbeği (Play'in açık tema vurgusu); odakta daha koyu.
internal val LightTvColors = TvColors(
    bg = Color(0xFFEEF1F5),
    surface1 = Color(0xFFFFFFFF),
    accent = Color(0xFF0097A7),
    accentFocused = Color(0xFF00727F),
    onAccent = Color(0xFFFFFFFF),
    textMain = Color(0xFF07111A),
    textDim = Color(0xFF4A5462),
    error = Color(0xFFB3261E),
)

internal val LocalTvColors = staticCompositionLocalOf { DarkTvColors }

// Ekranlar renkleri bu adlarla okur; değer etkin temadan gelir (yalnız Compose içinde okunabilir).
internal val Bg: Color @Composable @ReadOnlyComposable get() = LocalTvColors.current.bg
internal val Surface1: Color @Composable @ReadOnlyComposable get() = LocalTvColors.current.surface1
internal val Accent: Color @Composable @ReadOnlyComposable get() = LocalTvColors.current.accent
/** Vurgulu düğme odaktayken. */
internal val AccentFocused: Color @Composable @ReadOnlyComposable get() = LocalTvColors.current.accentFocused
internal val OnAccent: Color @Composable @ReadOnlyComposable get() = LocalTvColors.current.onAccent
internal val TextMain: Color @Composable @ReadOnlyComposable get() = LocalTvColors.current.textMain
internal val TextDim: Color @Composable @ReadOnlyComposable get() = LocalTvColors.current.textDim
internal val ErrorText: Color @Composable @ReadOnlyComposable get() = LocalTvColors.current.error
internal val LiveRed = Color(0xFFE5483B)

/** Yazı ölçeği (sp). Ekranlar yalnız bu değerleri kullanır. */
internal object TvText {
    val Hero = 26.sp      // hero/detay başlığı
    val Screen = 22.sp    // ekran başlığı
    val Row = 17.sp       // satır/bölüm başlığı
    val Body = 14.sp      // açıklama, durum iletisi
    val Meta = 13.sp      // yıl/tür/saat satırı
    val Label = 12.sp     // kart altı, rozet
}

/** Kenar boşluğu: TV'nin taşma payı (overscan) için içerik kenardan bu kadar içeride başlar. */
internal val Gutter = 48.dp

/**
 * Play'in "Tema" ayarı (container.settings.themeMode; Play MainActivity ile aynı anlam): Koyu, Açık ya da Sistem
 * (cihazın gece modu). [forceDark]: video üstündeki oynatıcı her zaman koyu kalır.
 */
@Composable
fun PlayTvTheme(forceDark: Boolean = false, content: @Composable () -> Unit) {
    val context = LocalContext.current
    val settings = remember { (context.applicationContext as PlayApp).container.settings }
    // Kayıtlı tema ilk karede doğru çıksın, koyudan açığa yanıp sönmesin (Play MainActivity gibi tek seferlik okuma).
    val initial = remember { runBlocking { settings.themeMode.first() } }
    val mode by settings.themeMode.collectAsStateWithLifecycle(initialValue = initial)
    // Play 1.3.1 tema listesi büyüdü (Amoled, Okyanus, Mor gece, Yüksek kontrast): TV şimdilik bunları koyu çizer.
    val dark = forceDark || com.kaan.play.data.settings.ThemeRules.isDark(mode, isSystemInDarkTheme())
    val c = if (dark) DarkTvColors else LightTvColors
    CompositionLocalProvider(LocalTvColors provides c) {
        MaterialTheme(
            colorScheme = if (dark) {
                darkColorScheme(primary = c.accent, onPrimary = c.onAccent, background = c.bg, surface = c.surface1, onSurface = c.textMain)
            } else {
                lightColorScheme(primary = c.accent, onPrimary = c.onAccent, background = c.bg, surface = c.surface1, onSurface = c.textMain)
            },
            content = content,
        )
    }
}

/**
 * Uygulamanın seçili diliyle (Play'in "Dil" ayarı) kaynak okuyan bağlam. Compose dışı kod (ViewModel, bildirim, uygulama
 * bağlamıyla Toast) içindir: API 33 altında AppCompat dili yalnız ekranlara uygular, uygulama bağlamı cihaz dilinde kalırdı.
 */
internal fun Context.localized(): Context {
    val iso = AppLanguage.effective().iso
    // Dil başına tek bağlam (uygulama bağlamından): kart başına çağrılır, her seferinde yeni Resources kurulmasın (2 GB kutu).
    localizedCache?.let { (cachedIso, cached) -> if (cachedIso == iso) return cached }
    val app = applicationContext
    val config = Configuration(app.resources.configuration)
    config.setLocale(Locale.forLanguageTag(iso))
    return app.createConfigurationContext(config).also { localizedCache = iso to it }
}

@Volatile private var localizedCache: Pair<String, Context>? = null

/**
 * Play'in "Poster URL" şablonu (Ayarlar → Genel). TvApp bir kez okuyup verir; kart başına ayrı DataStore okuması olmasın
 * (2 GB kutu). Verilmeyen yerde boş: şablon kapalı.
 */
internal val LocalPosterTemplate = compositionLocalOf { "" }

/**
 * Dikey afiş için Play'in kuralı: kimliklerde ya da görsel adresinde IMDb kimliği (tt…) varsa şablondan gelen afiş,
 * yoksa (ya da şablon boşsa) eldeki görsel. TMDB → IMDb çevirisi (ağ isteği) yapılmaz. Yatay (16:9) karelerde
 * kullanılmaz: şablon dikey afiş verir, 16:9 çerçevede kırpılırdı.
 */
@Composable
internal fun templatedPoster(imageUrl: String?, vararg ids: String?): String? =
    PosterUrlTemplate.build(LocalPosterTemplate.current, PosterUrlTemplate.findImdbId(*ids, imageUrl)) ?: imageUrl

/** Kart genişliği: dikey afiş 2:3, yatay kare 16:9 (§4.1). */
internal fun cardWidth(landscape: Boolean): Dp = if (landscape) 176.dp else 108.dp

/**
 * Odakla büyüyen, vurgu çerçeveli kart. Dikey afişte ad görselin üstünde yazılıdır; yatay karede ad alt şeritte yazılır.
 * [progress] doluysa (0..1) altta izleme ilerlemesi çizilir. [requestInitialFocus] ekran açılınca odağı bu karta verir.
 * Dikey kartta [posterId] (ya da görsel adresi) IMDb kimliği taşıyorsa afiş Poster URL şablonundan gelir.
 */
@Composable
internal fun PosterCard(
    title: String,
    imageUrl: String?,
    landscape: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    progress: Float? = null,
    requestInitialFocus: Boolean = false,
    onFocused: () -> Unit = {},
    onImageSize: (Int, Int) -> Unit = { _, _ -> },
    onLongClick: (() -> Unit)? = null,
    posterId: String? = null,
) {
    val image = if (landscape) imageUrl else templatedPoster(imageUrl, posterId)
    val requester = remember { FocusRequester() }
    var hasFocus by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(6.dp)
    Card(
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = modifier
            .width(cardWidth(landscape))
            .aspectRatio(if (landscape) 16f / 9f else 2f / 3f)
            .focusRequester(requester)
            .onFocusChanged { hasFocus = it.isFocused; if (it.isFocused) onFocused() },
        shape = CardDefaults.shape(shape),
        scale = CardDefaults.scale(focusedScale = 1.05f),
        border = CardDefaults.border(focusedBorder = Border(BorderStroke(2.dp, Accent), shape = shape)),
    ) {
        Box(Modifier.fillMaxSize()) {
            AsyncImage(
                model = image, contentDescription = title, contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                onSuccess = { s -> onImageSize(s.result.image.width, s.result.image.height) },
            )
            if (image == null) {
                Text(title, color = TextMain, fontSize = TvText.Label, modifier = Modifier.align(Alignment.Center).padding(8.dp))
            } else if (landscape) {
                Box(
                    Modifier.fillMaxWidth().align(Alignment.BottomCenter)
                        .background(Brush.verticalGradient(listOf(Color.Transparent, Bg.copy(alpha = 0.9f)))),
                ) {
                    Text(
                        title, color = TextMain, fontSize = TvText.Label, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(start = 10.dp, end = 10.dp, top = 18.dp, bottom = if (progress != null) 12.dp else 8.dp),
                    )
                }
            }
            if (progress != null) {
                Box(Modifier.fillMaxWidth().height(4.dp).align(Alignment.BottomStart).background(Color.Black.copy(alpha = 0.6f))) {
                    Box(Modifier.fillMaxWidth(progress.coerceIn(0f, 1f)).height(4.dp).background(Accent))
                }
            }
        }
    }
    if (requestInitialFocus) InitialFocus(requester) { hasFocus }
}

/**
 * Açılışta pencere henüz odak almamışken istenen odak boşa düşer: hedef odağı alana kadar kısa aralıklarla yeniden
 * istenir (en çok ~3 sn). Kullanıcı başka yere geçerse deneme kendiliğinden durur.
 */
@Composable
internal fun InitialFocus(requester: FocusRequester, isFocused: () -> Boolean) {
    LaunchedEffect(Unit) {
        repeat(20) {
            withFrameNanos { }
            if (isFocused()) return@LaunchedEffect
            runCatching { requester.requestFocus() }
            delay(150)
        }
    }
}

@Composable
internal fun Brand() {
    Row(verticalAlignment = Alignment.Bottom) {
        Text(stringResource(R.string.app_name), color = Accent, fontSize = TvText.Screen, fontWeight = FontWeight.Bold)
    }
}

@Composable
internal fun Centered(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) { content() }
    }
}

@Composable
internal fun ScreenTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, color = TextMain, fontSize = TvText.Screen, fontWeight = FontWeight.SemiBold, modifier = modifier)
}

/**
 * Kumandayla kullanılan metin kutusu. Odak gelince yalnız vurgu çerçeve alır; ekran klavyesi OK'a basınca açılır
 * (TV'de odakla kendiliğinden açılan klavye, ekrana girer girmez önünü kapatırdı). Klavyedeki eylem düğmesi [onDone]'u
 * çağırır ve klavye kapanır.
 */
@Composable
internal fun TvTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Done,
) {
    val secret = keyboardType == KeyboardType.Password || keyboardType == KeyboardType.NumberPassword
    var editing by remember { mutableStateOf(false) }
    var boxFocused by remember { mutableStateOf(false) }
    var fieldFocused by remember { mutableStateOf(false) }
    val box = remember { FocusRequester() }
    val field = remember { FocusRequester() }
    val shape = RoundedCornerShape(10.dp)
    val active = boxFocused || fieldFocused
    // Odaktayken telefonun klavyesinden gelen yazı bu alana yazılır (TvRemote).
    val change by rememberUpdatedState(onValueChange)
    val done by rememberUpdatedState(onDone)
    if (active) DisposableEffect(Unit) {
        val unregister = TvRemote.register { text, submit -> change(text); if (submit) done() }
        onDispose { unregister() }
    }
    val finish = {
        editing = false
        runCatching { box.requestFocus() }
        Unit
    }
    Box(
        modifier
            .focusRequester(box)
            .onFocusChanged { boxFocused = it.isFocused }
            .background(Surface1, shape)
            .border(if (active) 3.dp else 1.dp, if (active) Accent else TextDim.copy(alpha = 0.4f), shape)
            .clickable { editing = true }
            .padding(horizontal = 18.dp, vertical = 14.dp),
    ) {
        if (editing) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = TextStyle(color = TextMain, fontSize = TvText.Row),
                cursorBrush = SolidColor(Accent),
                keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = imeAction),
                // Parola türündeki alanlar (ör. Nuvio eklenti ayarı) ekranda gizlenir.
                visualTransformation = if (secret) PasswordVisualTransformation() else VisualTransformation.None,
                keyboardActions = KeyboardActions(
                    onDone = { onDone(); finish() }, onSearch = { onDone(); finish() }, onGo = { onDone(); finish() },
                ),
                modifier = Modifier.fillMaxWidth().focusRequester(field).onFocusChanged {
                    if (it.isFocused) fieldFocused = true
                    else if (fieldFocused) { fieldFocused = false; editing = false }
                },
            )
            LaunchedEffect(Unit) { runCatching { field.requestFocus() } }
        } else {
            Text(
                if (value.isEmpty()) placeholder else if (secret) "•".repeat(value.length) else value,
                color = if (value.isEmpty()) TextDim else TextMain, fontSize = TvText.Row, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
