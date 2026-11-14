package com.kaan.play.data.sync.local

import android.content.Context
import android.os.Build
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.security.SecureRandom

/**
 * Kalıcı Wi-Fi eşleşmelerinin bu cihazdaki kaydı (uygulamanın özel deposu; yedeklenmez, ağdan geçmez) ve cihaz
 * kimliği. Hem sunucu ([PairDirectory]: karşı cihaz bağlandığında sırrı verir) hem istemci (otomatik eşitlemede
 * karşının son adresi) tarafında aynı liste kullanılır.
 */
class LocalSyncPairStore(context: Context) : PairDirectory {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }
    private val serializer = ListSerializer(PairRecord.serializer())
    private val _pairs = MutableStateFlow(load())

    /** Eşleşmiş cihazlar (arayüz). */
    val pairs: StateFlow<List<PairRecord>> = _pairs

    override val identity: DeviceIdentity by lazy {
        val id = prefs.getString(KEY_DEVICE_ID, null)?.takeIf { LocalSyncCrypto.isHexId(it) }
            ?: LocalSyncCrypto.newNonce(SecureRandom()).also { prefs.edit().putString(KEY_DEVICE_ID, it).apply() }
        DeviceIdentity(id, Build.MODEL.orEmpty().ifBlank { "Play" })
    }

    override fun secretFor(pairId: String): String? = _pairs.value.firstOrNull { it.pairId == pairId }?.secret

    /** Aynı karşı cihazla yeniden eşleşme eskisinin yerine geçer (bir cihaz başına tek kayıt). */
    @Synchronized
    override fun paired(record: PairRecord) = save(_pairs.value.filterNot { it.peerId == record.peerId } + record)

    @Synchronized
    fun update(pairId: String, change: (PairRecord) -> PairRecord) =
        save(_pairs.value.map { if (it.pairId == pairId) change(it) else it })

    @Synchronized
    fun remove(pairId: String) = save(_pairs.value.filterNot { it.pairId == pairId })

    private fun save(list: List<PairRecord>) {
        prefs.edit().putString(KEY_PAIRS, json.encodeToString(serializer, list)).apply()
        _pairs.value = list
    }

    private fun load(): List<PairRecord> =
        prefs.getString(KEY_PAIRS, null)?.let { runCatching { json.decodeFromString(serializer, it) }.getOrNull() }.orEmpty()

    private companion object {
        const val PREFS = "play_sync_pairs"
        const val KEY_PAIRS = "pairs"
        const val KEY_DEVICE_ID = "device_id"
    }
}
