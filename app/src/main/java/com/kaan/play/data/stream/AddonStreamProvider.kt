package com.kaan.play.data.stream

import com.kaan.play.data.addon.Addon
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Aktif addon'lara paralel `GET {base}/stream/{movie|series}/{id}.json` atar.
 *
 * - Manifest'e uymayan addon'a (tür / `idPrefixes`) istek gitmez ve sonuç da üretilmez.
 * - Her addon kendi zaman aşımıyla çalışır ([timeout], çağrının tamamı için); biri takılınca diğerleri beklemez,
 *   sonuçlar hazır oldukça akar. Hata alan addon [StreamResult.Failed] olarak bildirilir, yutulmaz.
 * - [addons]: istek anında sorgulanacak aktif addon'lar.
 */
class AddonStreamProvider(
    private val addons: suspend () -> List<Addon>,
    private val client: OkHttpClient,
    private val timeout: suspend () -> Duration = { 15.seconds },
    private val maxBytes: Long = MAX_BYTES,
) : StreamProvider {

    override fun streams(request: StreamRequest): Flow<StreamResult> = channelFlow {
        val contentId = request.contentId ?: return@channelFlow
        val type = request.addonType ?: request.type.addonType
        val limit = timeout()
        val targetAddonId = if (request.pluginId?.startsWith("nuvio_addon:") == true) request.pluginId.removePrefix("nuvio_addon:").toLongOrNull() else null
        val gate = Semaphore(4)
        for (addon in askable(type, contentId, targetAddonId)) {
            launch {
                gate.withPermit {
                    try {
                        send(query(addon, type, contentId, limit))
                    } catch (t: Throwable) {
                        android.util.Log.e("PlayAddon", "${addon.manifest.name} çağrı hatası: ${t.message}", t)
                        send(StreamResult.Failed(StreamSource(addon.id, addon.manifest.name), FailureReason.Network))
                    }
                }
            }
        }
    }

    override suspend fun sources(request: StreamRequest): List<StreamSource> {
        val contentId = request.contentId ?: return emptyList()
        val type = request.addonType ?: request.type.addonType
        val targetAddonId = if (request.pluginId?.startsWith("nuvio_addon:") == true) request.pluginId.removePrefix("nuvio_addon:").toLongOrNull() else null
        return askable(type, contentId, targetAddonId).map { StreamSource(it.id, it.manifest.name) }
    }

    /** Manifest'e uyan (tür / `idPrefixes`) aktif addon'lar; diğerlerine istek gitmez. */
    private suspend fun askable(type: String, contentId: String, targetAddonId: Long? = null): List<Addon> =
        addons().filter { targetAddonId == null || it.id == targetAddonId }.filter { it.manifest.supportsStream(type, contentId) }

    private suspend fun query(addon: Addon, type: String, contentId: String, limit: Duration): StreamResult {
        val source = StreamSource(addon.id, addon.manifest.name)
        // Tanı: addon'a giden kimlik (IMDb "tt…" + sezon/bölüm); adres yazılmaz.
        android.util.Log.d("PlayAddon", "${addon.manifest.name}: istek tür=$type kimlik=$contentId")
        val url = "${addon.baseUrl}/stream/$type/$contentId.json".toHttpUrlOrNull()
            ?: return StreamResult.Failed(source, FailureReason.Network)

        return try {
            StreamResult.Found(source, StreamParser.parse(source, fetchAddonText(client, url, limit, maxBytes)))
        } catch (_: InvalidStreamResponse) {
            StreamResult.Failed(source, FailureReason.InvalidResponse)
        } catch (e: AddonFetchFailure) {
            StreamResult.Failed(source, e.reason)
        }
    }

    companion object {
        /** Akış cevapları manifest'ten büyük olabilir; yine de makul bir üst sınır. */
        const val MAX_BYTES = 5L * 1024 * 1024
    }
}
