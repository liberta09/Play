package com.kaan.play.data.plugin

import com.kaan.play.data.addon.AddonException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/** `play://add?repo=<adres>` ya da `play://add?nuvio=<adres>` bağlantısının sonucu (tek seferlik bildirim). */
sealed interface DeepLinkEvent {
    data class Added(val result: AddResult) : DeepLinkEvent
    data class Failed(val error: AddonException) : DeepLinkEvent
    data object Invalid : DeepLinkEvent
}

/** Android'den bağımsız bağlantı kuralları (birim testli). */
object DeepLink {
    const val SCHEME = "play"
    const val HOST = "add"

    /** `nuvio` ile `nuvio_plugin` eşanlamlıdır; boş/boşluk değer ötekine düşer (getQueryParameter boşta "" döndürür). */
    fun nuvioParam(nuvio: String?, nuvioPlugin: String?): String? =
        listOf(nuvio, nuvioPlugin).firstOrNull { !it.isNullOrBlank() }

    /**
     * Eklenecek adres; bağlantı `play://add` değilse, `repo`/`nuvio` yoksa ya da değer http(s) adresi değilse null.
     * Tür (addon / Nuvio deposu / CloudStream deposu) parametre adından değil, adresin içeriğinden anlaşılır.
     */
    fun target(scheme: String?, host: String?, repo: String?, nuvio: String?): String? {
        if (scheme != SCHEME || host != HOST) return null
        val raw = listOf(repo, nuvio).firstOrNull { !it.isNullOrBlank() }?.trim() ?: return null
        val url = raw.toHttpUrlOrNull() ?: return null
        return url.toString().takeIf { url.scheme == "https" || url.scheme == "http" }
    }
}

/**
 * Gelen `play://add` bağlantısı: geçerliyse önce kullanıcıdan onay ister ([pending]); "Ekle" denince mevcut
 * [SourceInstaller] ile ekler ve sonucu [events] ile bildirir. Onay olmadan hiçbir şey eklenmez.
 */
class DeepLinkHandler(
    private val installer: SourceInstaller,
    private val scope: CoroutineScope,
) {
    // Kanal: bildirim, arayüz dinlemeye başlamadan önce üretilse de (soğuk açılış) bir kez ve kaybolmadan ulaşır.
    private val channel = Channel<DeepLinkEvent>(Channel.BUFFERED)
    val events: Flow<DeepLinkEvent> = channel.receiveAsFlow()

    // Başarılı kurulumun hedef ekranını PlayRoot'a ayrıca bildirir. Snackbar kanalıyla yarışmaz.
    private val _installedSource = MutableStateFlow<AddResult?>(null)
    val installedSource: StateFlow<AddResult?> = _installedSource.asStateFlow()

    fun consumeInstalledSource() {
        _installedSource.value = null
    }

    /** Onay bekleyen adres; null = onay penceresi kapalı. Etkinlik yeniden oluşsa da (döndürme) pencere kalır. */
    private val _pending = MutableStateFlow<String?>(null)
    val pending: StateFlow<String?> = _pending.asStateFlow()

    fun handle(scheme: String?, host: String?, repo: String?, nuvio: String?) {
        if (scheme != DeepLink.SCHEME) return
        val target = DeepLink.target(scheme, host, repo, nuvio)
        if (target == null) channel.trySend(DeepLinkEvent.Invalid) else _pending.value = target
    }

    fun dismiss() {
        _pending.value = null
    }

    fun confirm() {
        val target = _pending.value ?: return
        _pending.value = null
        scope.launch {
            val event = try {
                val result = installer.add(target)
                _installedSource.value = result
                DeepLinkEvent.Added(result)
            } catch (e: AddonException) {
                DeepLinkEvent.Failed(e)
            }
            channel.send(event)
        }
    }
}
