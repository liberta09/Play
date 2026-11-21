package com.kaan.play.tv

import com.kaan.play.R
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Button
import androidx.tv.material3.ListItem
import androidx.tv.material3.Text
import com.kaan.play.PlayApp
import com.kaan.play.data.settings.ThemeMode
import com.kaan.play.data.speedtest.InternetSpeedResult
import com.kaan.play.data.speedtest.SpeedTestError
import com.kaan.play.data.speedtest.SpeedTestOutcome
import com.kaan.play.data.tmdb.PosterUrlTemplate
import com.kaan.play.i18n.AppLanguage
import com.kaan.play.i18n.LanguageChoice
import kotlinx.coroutines.launch

/**
 * Ayarlar → Genel (TV'ye özgü). Yalnız TV'de etkisi olan ayarlar gösterilir: Dil, Tema, İnternet hız testi, Poster URL şablonu.
 * Dil ve Tema Play'in ayarlarıdır: aynı değerler, aynı anlam (Sistem seçenekleri dahil).
 */
@Composable
internal fun TvGeneralSettings() {
    SettingsPageFrame(stringResource(R.string.tv_settings_general_title)) {
        item(key = "language") { LanguageSetting() }
        item(key = "theme") { ThemeSetting() }
        item(key = "speed-test") { Column { Spacer(Modifier.height(10.dp)); SpeedTest() } }
        item(key = "poster-url") { PosterUrlSetting() }
    }
}

/**
 * Play'in "Dil" ayarı (AppLanguage → AppCompat): seçilince ekran yeni dilde yeniden açılır (Play'deki gibi). Seçenekler
 * döngüyle değil tek tek listelenir: her seçim ekranı yeniden açtığı için döngüde istenen dile birkaç yeniden açılışla varılırdı.
 * Sayfa açılınca (dil değişiminden sonra da) odak seçili dildedir.
 */
@Composable
private fun LanguageSetting() {
    // Seçim burada da tutulur: seçilen dil cihaz diliyle aynıysa ekran yeniden açılmaz, işaret yine de geçmeli (Play gibi).
    var current by remember { mutableStateOf(AppLanguage.choice()) }
    val selected = remember { FocusRequester() }
    var focused by remember { mutableStateOf(false) }
    Column {
        SectionTitle(stringResource(R.string.tv_general_language))
        Text(stringResource(R.string.tv_general_language_note), color = TextDim, fontSize = TvText.Body)
        Spacer(Modifier.height(6.dp))
        LanguageChoice.entries.forEach { choice ->
            val isCurrent = choice == current
            ChoiceItem(
                label = stringResource(choice.labelRes()),
                checked = isCurrent,
                modifier = if (isCurrent) Modifier.focusRequester(selected).onFocusChanged { focused = it.isFocused } else Modifier,
                onClick = { if (!isCurrent) { current = choice; AppLanguage.apply(choice) } },
            )
        }
    }
    InitialFocus(selected) { focused }
}

/** Play'in "Tema" ayarı (container.settings.themeMode): PlayTvTheme aynı akışı dinler, ekran anında değişir. */
@Composable
private fun ThemeSetting() {
    val context = LocalContext.current
    val settings = remember { (context.applicationContext as PlayApp).container.settings }
    val scope = rememberCoroutineScope()
    val mode by settings.themeMode.collectAsStateWithLifecycle(initialValue = null)
    val current = mode ?: return
    Column {
        Spacer(Modifier.height(10.dp))
        SectionTitle(stringResource(R.string.tv_general_theme))
        Spacer(Modifier.height(6.dp))
        // TV bu turda yalnız eski üç seçeneği gösterir (Play 1.3.1'in yeni temaları TV'ye ayrı işte gelecek).
        listOf(ThemeMode.Dark, ThemeMode.Light, ThemeMode.System).forEach { m ->
            ChoiceItem(stringResource(m.labelRes()), checked = m == current, onClick = { scope.launch { settings.setThemeMode(m) } })
        }
    }
}

/** Seçenek satırı: seçili olanın yanında vurgu onay işareti. */
@Composable
private fun ChoiceItem(label: String, checked: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    ListItem(
        selected = false,
        onClick = onClick,
        headlineContent = { Text(label) },
        trailingContent = { if (checked) Text("✓", color = Accent, fontSize = TvText.Row) },
        modifier = modifier,
    )
}

private fun LanguageChoice.labelRes(): Int = when (this) {
    LanguageChoice.System -> R.string.tv_system
    LanguageChoice.English -> R.string.tv_language_english
    LanguageChoice.Turkish -> R.string.tv_language_turkish
}

private fun ThemeMode.labelRes(): Int = when (this) {
    ThemeMode.Dark -> R.string.tv_theme_dark
    ThemeMode.Light -> R.string.tv_theme_light
    ThemeMode.System -> R.string.tv_theme_system
    else -> R.string.tv_theme_dark
}

/**
 * Play'in "Poster URL" ayarı (aynı DataStore değeri): {imdb_id} içeren https adresi; boş bırakılırsa eklenti / TMDB
 * afişi kullanılır. Geçersiz adres kaydedilmez (Play sessizce varsayılana döndürürdü; kumandada fark edilmezdi).
 * Adres telefon kumandasının klavyesinden ya da TV klavyesinden yazılır.
 */
@Composable
private fun PosterUrlSetting() {
    val context = LocalContext.current
    val settings = remember { (context.applicationContext as PlayApp).container.settings }
    val scope = rememberCoroutineScope()
    val saved by settings.posterUrlTemplate.collectAsStateWithLifecycle(initialValue = null)
    val current = saved ?: return
    var draft by remember(current) { mutableStateOf(current) }
    val valid = draft.isBlank() || PosterUrlTemplate.isValid(draft.trim())
    val changed = valid && draft.trim() != current
    fun save() {
        if (changed) scope.launch { settings.setPosterUrlTemplate(draft) }
    }

    Column {
        Spacer(Modifier.height(18.dp))
        SectionTitle(stringResource(R.string.tv_poster_title))
        Text(
            stringResource(if (current.isBlank()) R.string.tv_poster_off else R.string.tv_poster_custom),
            color = if (current.isBlank()) TextDim else Accent, fontSize = TvText.Body,
        )
        Spacer(Modifier.height(10.dp))
        TvTextField(
            value = draft, onValueChange = { draft = it }, placeholder = "https://…/{imdb_id}.jpg",
            onDone = { save() }, keyboardType = KeyboardType.Uri, modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(6.dp))
        Text(
            stringResource(if (valid) R.string.tv_poster_help else R.string.tv_poster_invalid),
            color = if (valid) TextDim else Accent, fontSize = TvText.Body,
        )
        Spacer(Modifier.height(10.dp))
        // Düğmeler devre dışı bırakılmaz: odaktaki düğme kapanınca kumanda odağı kaybolurdu; gereksiz basış etkisizdir.
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Button(onClick = { save() }) { Text(stringResource(R.string.tv_save)) }
            Button(onClick = { scope.launch { settings.setPosterUrlTemplate(PosterUrlTemplate.DEFAULT) } }) { Text(stringResource(R.string.tv_poster_reset)) }
            Button(onClick = { scope.launch { settings.setPosterUrlTemplate("") } }) { Text(stringResource(R.string.tv_poster_disable)) }
        }
    }
}

/** Play'in hız testi mantığı (container.internetSpeedTest) aynen kullanılır; yalnız kumanda için TV arayüzü çizilir. */
@Composable
private fun SpeedTest() {
    val context = LocalContext.current
    val speedTest = remember { (context.applicationContext as PlayApp).container.internetSpeedTest }
    // Bu kapsam sayfadan çıkınca iptal olur: geri basınca süren ölçüm de durur.
    val scope = rememberCoroutineScope()
    var running by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<InternetSpeedResult?>(null) }
    var failed by remember { mutableStateOf<SpeedTestError?>(null) }

    Column {
        SectionTitle(stringResource(R.string.tv_speed_title))
        Text(stringResource(R.string.tv_speed_body), color = TextDim, fontSize = TvText.Body)
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            Button(
                onClick = {
                    if (!running) scope.launch {
                        running = true
                        failed = null
                        when (val res = speedTest.runTest()) {
                            is SpeedTestOutcome.Success -> result = res.result
                            is SpeedTestOutcome.Failure -> failed = res.error
                        }
                        running = false
                    }
                },
            ) { Text(stringResource(if (result != null) R.string.tv_speed_retest else R.string.tv_speed_start)) }
            val r = result
            when {
                running -> Text(stringResource(R.string.tv_speed_measuring), color = TextDim, fontSize = TvText.Body)
                failed != null -> Text(
                    stringResource(
                        R.string.tv_speed_failed,
                        stringResource(if (failed == SpeedTestError.Timeout) R.string.tv_speed_timeout else R.string.tv_speed_unreachable),
                    ),
                    color = Accent, fontSize = TvText.Body,
                )
                r != null -> {
                    Text("%.1f Mbps".format(r.downloadMbps), color = Accent, fontSize = TvText.Hero, fontWeight = FontWeight.Bold)
                    Text(stringResource(R.string.tv_speed_ping, r.pingMs), color = TextDim, fontSize = TvText.Body)
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.tv_speed_note), color = TextDim, fontSize = TvText.Body)
    }
}
