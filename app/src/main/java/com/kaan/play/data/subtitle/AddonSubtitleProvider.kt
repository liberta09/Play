package com.kaan.play.data.subtitle

import com.kaan.play.data.addon.Addon
import com.kaan.play.data.net.PlayJson
import com.kaan.play.data.stream.AddonFetchFailure
import com.kaan.play.data.stream.FailureReason
import com.kaan.play.data.stream.StreamRequest
import com.kaan.play.data.stream.StreamSource
import com.kaan.play.data.stream.fetchAddonText
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * `subtitles` kaynağı olan aktif addon'lara paralel `GET {taban}/subtitles/{movie|series}/{imdb}[:sezon:bölüm].json` atar.
 * [com.kaan.play.data.stream.AddonStreamProvider] ile aynı kurallar: manifest'in tür / `idPrefixes` koşuluna uymayan addon'a
 * istek gitmez, her addon kendi zaman aşımıyla çalışır, biri takılınca diğerleri beklemez, sonuçlar hazır oldukça akar,
 * hata "yanıt vermedi" olarak bildirilir. Cevap `{"subtitles":[{"id","url","lang"}]}`; yalnızca http(s) adresli kayıtlar alınır.
 */
class AddonSubtitleProvider(
    private val addons: suspend () -> List<Addon>,
    private val client: OkHttpClient,
    private val timeout: suspend () -> Duration = { 15.seconds },
    private val maxBytes: Long = MAX_BYTES,
) : SubtitleProvider {

    override fun subtitles(request: StreamRequest): Flow<SubtitleResult> = channelFlow {
        val contentId = request.contentId ?: return@channelFlow
        val type = request.type.addonType
        val limit = timeout()
        for (addon in askable(type, contentId)) {
            launch { send(query(addon, type, contentId, limit)) }
        }
    }

    override suspend fun sources(request: StreamRequest): List<StreamSource> {
        val contentId = request.contentId ?: return emptyList()
        return askable(request.type.addonType, contentId).map { StreamSource(it.id, it.manifest.name) }
    }

    private suspend fun askable(type: String, contentId: String): List<Addon> =
        addons().filter { it.manifest.supportsSubtitles(type, contentId) }

    private suspend fun query(addon: Addon, type: String, contentId: String, limit: Duration): SubtitleResult {
        val source = StreamSource(addon.id, addon.manifest.name)
        val url = "${addon.baseUrl}/subtitles/$type/$contentId.json".toHttpUrlOrNull()
            ?: return SubtitleResult.Failed(source, FailureReason.Network)
        return try {
            val tracks = parse(source.addonName, fetchAddonText(client, url, limit, maxBytes))
                ?: return SubtitleResult.Failed(source, FailureReason.InvalidResponse)
            SubtitleResult.Found(source, tracks)
        } catch (e: AddonFetchFailure) {
            SubtitleResult.Failed(source, e.reason)
        }
    }

    companion object {
        const val MAX_BYTES = 5L * 1024 * 1024

        /** Dil başına en çok bu kadar sonuç (Altyazı menüsü taşmasın: addon başına dil başına en çok 10 sonuç). */
        const val MAX_PER_LANGUAGE = 5

        /** Bir addon'dan en çok bu kadar parça alınır (tüm diller toplamı güvenlik sınırı). */
        const val MAX_PER_ADDON = 100

        /**
         * `{"subtitles":[...]}` -> parçalar; cevap bu biçimde değilse null. Bozuk tek kayıt yalnızca kendini düşürür,
         * aynı adres tek kez alınır. Addon başına dil başına en çok 10 sonuç gösterilir.
         */
        fun parse(origin: String, body: String): List<SubtitleTrack>? {
            val root = try {
                PlayJson.parseToJsonElement(body)
            } catch (_: IllegalArgumentException) { // SerializationException da buradan geçer
                return null
            }
            val array = (root as? JsonObject)?.get("subtitles") as? JsonArray ?: return null
            val countByLang = mutableMapOf<String?, Int>()
            return array.asSequence()
                .mapNotNull { it as? JsonObject }
                .mapNotNull { obj ->
                    val url = (obj["url"] as? JsonPrimitive)?.contentOrNull?.trim()
                        ?.takeIf { it.startsWith("http://", true) || it.startsWith("https://", true) }
                        ?: return@mapNotNull null
                    val lang = (obj["lang"] as? JsonPrimitive)?.contentOrNull
                    val rawLabel = (obj["file"] as? JsonPrimitive)?.contentOrNull?.trim()
                        ?: (obj["filename"] as? JsonPrimitive)?.contentOrNull?.trim()
                        ?: (obj["label"] as? JsonPrimitive)?.contentOrNull?.trim()
                        ?: (obj["release"] as? JsonPrimitive)?.contentOrNull?.trim()
                        ?: (obj["title"] as? JsonPrimitive)?.contentOrNull?.trim()
                    val label = SubtitleSelection.cleanLabel(rawLabel)
                    SubtitleTrack(url, SubtitleLanguages.normalize(lang), origin, label = label)
                }
                .distinctBy { it.url }
                .filter { track ->
                    val count = countByLang.getOrDefault(track.language, 0)
                    if (count < MAX_PER_LANGUAGE) {
                        countByLang[track.language] = count + 1
                        true
                    } else {
                        false
                    }
                }
                .take(MAX_PER_ADDON)
                .toList()
        }
    }
}
