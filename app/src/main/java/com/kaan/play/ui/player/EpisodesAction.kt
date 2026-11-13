package com.kaan.play.ui.player

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.kaan.play.R
import com.kaan.play.data.stream.StreamRequest
import com.kaan.play.ui.nav.PlayIcons

/**
 * Oynatıcı alt çubuğundaki "Bölümler" düğmesi. Düğme gösterilmeyecekse (film, canlı, Watch Party konuğu) null döner.
 * PlayerScreen'in dev composable'ından ayrı tutulur: R8 çıktısı Android 9 doğrulayıcısından geçsin (VerifyError).
 */
@Composable
internal fun episodesBarAction(request: StreamRequest, isGuest: Boolean, onOpen: () -> Unit): BarAction? {
    val visible = EpisodePanelRules.canShowButton(
        type = request.type,
        season = request.season,
        episode = request.episode,
        addonType = request.addonType,
        isGuest = isGuest,
    )
    if (!visible) return null
    return BarAction(PlayIcons.Episodes, stringResource(R.string.player_btn_episodes), onOpen)
}
