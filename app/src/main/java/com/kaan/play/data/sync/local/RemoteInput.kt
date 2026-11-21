package com.kaan.play.data.sync.local

import kotlinx.serialization.Serializable

/** Kumandanın tuşları: karşı cihaz bunları kendi kumandasından basılmış gibi uygular. */
@Serializable
enum class RemoteKey {
    Up, Down, Left, Right, Ok, Back, Home, PlayPause,

    // Yeni kumanda tuşları: eski TV bunları çözemez, isteği reddeder (zararsız).
    VolumeUp, VolumeDown, Mute, Menu, ListUp, ListDown, Rewind, FastForward,
}

/**
 * Eşleşmiş cihaza ([LocalSyncProtocol.PATH_REMOTE]) giden kumanda/klavye isteği: ya bir [key] ya da [text]. [text] karşıdaki
 * yazı alanının tamamının yerine geçer (telefonda yazdıkça gönderilir); [submit] alanın "Ara/Tamam" eylemini de çalıştırır.
 */
@Serializable
data class RemoteInput(
    val key: RemoteKey? = null,
    val text: String? = null,
    val submit: Boolean = false,
    /** Yalnız durum sorusu: karşı cihaz tuş basmaz, [RemoteState] JSON'u döndürür (yeni kumanda ekranının üst kartı). */
    val state: Boolean = false,
) {
    companion object {
        const val MAX_TEXT = 200
    }
}

/** [LocalSyncController.remoteToPeer] sonucu. */
enum class RemoteSendResult { Sent, Unsupported, Unreachable }

/**
 * Kumandanın gördüğü TV durumu: oynayan yapım (yoksa [title] null) ve ses düzeyi (bilinmiyorsa -1). [v] = 1 gerçek yanıtı
 * işaretler; eski TV'nin `{"error":…}` yanıtı varsayılanlara (v = 0) çözülür ve yok sayılır.
 */
@Serializable
data class RemoteState(
    val v: Int = 0,
    val title: String? = null,
    val subtitle: String? = null,
    val posterUrl: String? = null,
    val playing: Boolean = false,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val volume: Int = -1,
    val maxVolume: Int = -1,
    val muted: Boolean = false,
)
