package com.kaan.play.data.sync.local

import kotlinx.serialization.Serializable

/**
 * Kalıcı Wi-Fi eşleşmesi (telefon ↔ TV gibi aynı ağdaki iki Play cihazı). Bir kez tek kullanımlık kodla `POST /pair`
 * yapılır; iki taraf [PairRecord] saklar ve sonraki istekler kod girilmeden [LocalSyncProtocol.HEADER_PAIR] ile kimlik
 * doğrular (bkz. [LocalSyncCrypto.pairSecret]). Eşleşmeyi bilmeyen eski sürümler kodlu akışla çalışmayı sürdürür.
 */

/** `/pair` gövdesi (iki yön de şifreli). Yanıtta [pairId] dolu gelir. */
@Serializable
data class PairHello(
    val publicKey: String,
    val deviceId: String,
    val deviceName: String,
    val pairId: String? = null,
)

/** İki tarafta saklanan eşleşme. [secret] ağdan geçmez; yalnız bu cihazda durur. */
@Serializable
data class PairRecord(
    val pairId: String,
    val secret: String,
    val peerId: String,
    val peerName: String,
    /** İstemci tarafında karşının son bilinen adresi (özel IPv4). */
    val lastHost: String? = null,
    val lastSyncAt: Long = 0,
)

/** Bu cihazın eşleşmede görünen kimliği: kalıcı rastgele [id] (onaltılık) ve okunur [name]. */
data class DeviceIdentity(val id: String, val name: String)

/** Sunucunun eşleşme dizini (saklama Android tarafında). */
interface PairDirectory {
    val identity: DeviceIdentity

    /** Bu eşleşmenin sırrı; bilinmiyorsa (silinmiş) null. */
    fun secretFor(pairId: String): String?

    /** Yeni eşleşme kuruldu: kalıcı olarak sakla. */
    fun paired(record: PairRecord)
}
