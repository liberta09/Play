package com.kaan.play.tv

import com.kaan.play.AppContainer
import com.kaan.play.data.addon.AddonException
import com.kaan.play.data.cloudstream.CsRepo
import com.kaan.play.data.cloudstream.InstalledCsPlugin
import java.net.URI
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.tv.material3.Button
import androidx.tv.material3.Text
import com.kaan.play.R

// TR: Shorts Drama menüsü Arcana Drama TV uygulamasında eklenmiştir; kısa dizileri Arcana Drama eklenti deposundan gösterir.
// Depo kendiliğinden eklenmez: kullanıcı Shorts Drama'yı açınca bir kez onay penceresi çıkar (deponun adresi görünür,
// Ekle/Vazgeç); Vazgeç'te bölümde "Depo eklenmedi" ve "Tekrar sor" düğmesi kalır. Ana sayfa, Filmler ve Diziler depoyu hiç
// eklemez. Yine de Play'in "varsayılan depo gömülmez" kuralıyla çelişir; istemezseniz şu dosyaları/satırları kaldırarak
// tamamen çıkarabilirsiniz:
//   - bu dosya (ShortsDrama.kt) ve app/src/testTv/java/com/kaan/play/tv/ShortDramaRecordTest.kt;
//   - TvApp.kt: `Section.Shorts`, `shorts` modeli, `Section.Shorts ->` dalları ve `shorts.load()`;
//   - TvHomeModel.kt: `HomeKind.Shorts`, `HomeState.ShortsRepoMissing`, `addShortsRepo`, `declineShortsRepo`,
//     `askShortsRepoAgain`, `shortsProviders`, `shortsRepos`, `KEY_SHORTS_REPO_DECLINED` ve "Shorts Drama" yorumlu satırlar
//     (onaylı depo ekleme, Ana sayfa seçicisinden/satırlarından ayıklama);
//   - TvHomeScreen.kt: "Shorts Drama" yorumlu satırlar (satır başlığı ve "İzlemeye devam et" süzgeci);
//   - TvHomeCache.kt: `HomeKind.Shorts` satırı;
//   - TvHomeScreen.kt: `HomeState.ShortsRepoMissing` dalı;
//   - res/values*/strings.xml ve strings_home.xml: `tv_section_shorts`, `tv_home_installing_shorts`, `tv_home_shorts_install_failed`,
//     `tv_shorts_repo_*`.
// Ayrıntı: docs/android-tv.md, "Shorts Drama".
//
// EN: The Shorts Drama menu was added in the Arcana Drama TV app; it shows short dramas from the Arcana Drama add-on
// repository. The repository is never added silently: opening Shorts Drama shows a one-time confirmation dialog (repository
// address visible, Add/Cancel); after Cancel the section shows "Repository not added" with an "Ask again" button. Home,
// Movies and Series never add it. It still conflicts with Play's "no default repository is bundled" rule; if you don't want
// it, remove it completely by deleting the files/lines listed above. Details: docs/android-tv.md,
// "Shorts Drama".
internal object ShortsDrama {
    /** Arcana Drama eklenti deposunun kök adresi (yalnız kullanıcı onaylayınca eklenir). */
    const val ROOT_REPO = "https://raw.githubusercontent.com/banadabi/arcanadrama-addons/main/repo.json"

    private const val SUB_REPO_HOST = "arcanadrama.download"
    private val SUB_REPO_PATH = Regex("/ArcanaDrama/(?:[A-Za-z0-9_.-]+-)?repo\\.json")

    /** Eski kurulumlarda eklenti adları "(Arcana Drama) X" ya da "Arcana Drama X" biçiminde; satır başlığında atılır. */
    private val NAME_PREFIXES = listOf("(Arcana Drama)", "Arcana Drama")

    /**
     * Arcana Drama deposu mu: kök depo ya da `https://arcanadrama.download/ArcanaDrama/<ad>-repo.json` alt depoları. Adres katı
     * denetlenir (yalnız https, kullanıcı bilgisi/port/sorgu/parça yok, `.`/`..` yol parçası yok).
     */
    fun isRepo(raw: String): Boolean = runCatching {
        if (raw == ROOT_REPO) return true
        val u = URI(raw)
        if (u.scheme != "https" || u.rawUserInfo != null || u.port != -1 || u.rawQuery != null || u.rawFragment != null) return false
        if (u.rawAuthority != u.host?.lowercase()) return false
        if (u.path.split('/').any { it == "." || it == ".." }) return false
        u.host == SUB_REPO_HOST && u.path.matches(SUB_REPO_PATH)
    }.getOrDefault(false)

    /** Kurulu CS depolarından Arcana Drama depolarının satır kimlikleri. Eklenti ada göre değil depoya göre tanınır. */
    fun repoIds(repos: List<CsRepo>): Set<Long> = repos.filter { isRepo(it.sourceUrl) }.mapTo(HashSet()) { it.id }

    /** Shorts Drama bölümünün eklentileri: Arcana Drama depolarındakiler; aynı eklenti iki depodan kuruluysa tek kopya. */
    fun plugins(options: List<InstalledCsPlugin>, repoIds: Set<Long>): List<InstalledCsPlugin> =
        options.filter { it.repoId in repoIds }.groupBy { it.pluginId }.map { (_, copies) -> copies.minBy { it.id } }

    /** Satır başlığındaki eklenti adı: "(Arcana Drama) AnyShort" ve eski "Arcana Drama Narto" → "AnyShort", "Narto". */
    fun shortName(name: String): String = NAME_PREFIXES.fold(name.trim()) { n, p -> n.removePrefix(p).trim() }

    /** Kısa dizi kaydı ("İzlemeye devam et"te yalnız Shorts Drama'da görünür): bir Arcana Drama eklentisinden (`cs:<id>`). */
    fun isRecord(pluginId: String?, plugins: List<InstalledCsPlugin>, repoIds: Set<Long>): Boolean =
        pluginId != null && plugins.any { it.repoId in repoIds && "cs:${it.id}" == pluginId }

    /** Onay penceresinde gösterilen adres: host + yol. */
    val repoAddress: String get() = URI(ROOT_REPO).let { it.host + it.path }

    /**
     * Kullanıcı onayladıktan sonra depoyu ekler (yalnız bu depo; başka depo/eklenti gömülmez). Zaten ekliyse başarı sayılır.
     * true: depo kurulu.
     */
    suspend fun addRepo(container: AppContainer): Boolean = try {
        container.sourceInstaller.add(ROOT_REPO)
        true
    } catch (e: AddonException) {
        if (e.kind != AddonException.Kind.AlreadyAdded) android.util.Log.w("PlayTv", "Shorts Drama deposu eklenemedi: ${e.kind}")
        e.kind == AddonException.Kind.AlreadyAdded
    } catch (t: Throwable) {
        if (t is kotlinx.coroutines.CancellationException) throw t
        android.util.Log.w("PlayTv", "Shorts Drama deposu eklenemedi: ${t.javaClass.simpleName}")
        false
    }
}

/**
 * Shorts Drama deposu kurulu değil. [declined] false ise onay penceresi (deponun host + yolu, Ekle/Vazgeç; kumandayla
 * kullanılır), true ise "Depo eklenmedi" ve "Tekrar sor".
 */
@Composable
internal fun ShortsRepoConsent(declined: Boolean, onAdd: () -> Unit, onCancel: () -> Unit, onAskAgain: () -> Unit) {
    Centered {
        Brand(); Spacer(Modifier.height(16.dp))
        if (declined) {
            Text(stringResource(R.string.tv_shorts_repo_not_added), color = TextMain, fontSize = TvText.Body)
            Spacer(Modifier.height(20.dp))
            val ask = remember { FocusRequester() }
            Button(onClick = onAskAgain, modifier = Modifier.focusRequester(ask)) { Text(stringResource(R.string.tv_shorts_repo_ask_again)) }
            LaunchedEffect(Unit) { runCatching { ask.requestFocus() } }
        }
    }
    if (!declined) {
        val add = remember { FocusRequester() }
        Dialog(onDismissRequest = onCancel) {
            Column(
                Modifier.width(620.dp).background(Surface1, RoundedCornerShape(16.dp)).padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(stringResource(R.string.tv_shorts_repo_title), color = TextMain, fontSize = TvText.Row, fontWeight = FontWeight.SemiBold)
                Text(stringResource(R.string.tv_shorts_repo_body), color = TextMain, fontSize = TvText.Body)
                Text(ShortsDrama.repoAddress, color = TextDim, fontSize = TvText.Meta)
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Button(onClick = onAdd, modifier = Modifier.focusRequester(add)) { Text(stringResource(R.string.tv_shorts_repo_add)) }
                    Button(onClick = onCancel) { Text(stringResource(R.string.tv_cancel)) }
                }
            }
            LaunchedEffect(Unit) { runCatching { add.requestFocus() } }
        }
    }
}
