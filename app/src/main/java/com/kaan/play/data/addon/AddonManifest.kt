package com.kaan.play.data.addon

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Doğrulanmış addon manifest'i (yalnızca Play'in kullandığı alanlar).
 * [AddonManifestParser] dışında doğrudan kurulmaz; Room'da bu modelin JSON'u saklanır.
 */
@Serializable
data class AddonManifest(
    val id: String,
    val name: String,
    val version: String? = null,
    val description: String? = null,
    val logo: String? = null,
    val types: List<String>,
    val resources: List<ManifestResource>,
    val idPrefixes: List<String>? = null,
    val catalogs: List<ManifestCatalog> = emptyList(),
) {
    /**
     * Bu addon'a `stream/{type}/{contentId}.json` isteği gönderilmeli mi?
     * Kaynak düzeyindeki `types`/`idPrefixes` varsa manifest düzeyindekini geçersiz kılar;
     * `idPrefixes` hiç yoksa her kimlik kabul edilir. [type]: `movie` / `series`.
     */
    fun supportsStream(type: String, contentId: String): Boolean = supports(STREAM, type, contentId)

    /** Aynı kural `subtitles` kaynağı için: `subtitles/{type}/{contentId}.json` istenmeli mi? */
    fun supportsSubtitles(type: String, contentId: String): Boolean = supports(SUBTITLES, type, contentId)

    /** Film/dizi akışı sunuyor mu? Yalnızca altyazı veren addon'lar kaynak listesinde ve "kaynak var mı" denetiminde sayılmaz. */
    val providesStreams: Boolean
        get() = servesPlayableType(STREAM) || servesLiveType(STREAM)

    /** Katalog sunuyor mu? */
    val providesCatalogs: Boolean
        get() = resources.any { it.name == CATALOG } && catalogs.isNotEmpty()

    /** Altyazı sunuyor mu? */
    val providesSubtitles: Boolean
        get() = servesPlayableType(SUBTITLES)

    private fun supports(resource: String, type: String, contentId: String): Boolean =
        resources.filter { it.name == resource }.any { r ->
            val allowedTypes = r.types ?: types
            val prefixes = r.idPrefixes ?: idPrefixes
            type in allowedTypes && (prefixes.isNullOrEmpty() || prefixes.any { contentId.startsWith(it) })
        }

    /** [resource] kaynağı film ya da dizi için bildirilmiş mi (kaynak düzeyindeki `types` manifesttekini geçersiz kılar)? */
    internal fun servesPlayableType(resource: String): Boolean =
        resources.filter { it.name == resource }.any { r -> (r.types ?: types).any { it in PLAYABLE_TYPES } }

    /** [resource] kaynağı canlı TV/kanal için bildirilmiş mi? */
    internal fun servesLiveType(resource: String): Boolean =
        resources.filter { it.name == resource }.any { r -> (r.types ?: types).any { it in LIVE_TYPES } }

    companion object {
        const val STREAM = "stream"
        const val SUBTITLES = "subtitles"
        const val CATALOG = "catalog"
        const val META = "meta"
        private val PLAYABLE_TYPES = setOf("movie", "series")
        private val LIVE_TYPES = setOf("tv", "channel")
    }
}

/** Manifest'te kaynak ya düz isim (`"stream"`) ya da nesnedir; ikisi de bu modele çevrilir. */
@Serializable(with = ManifestResourceSerializer::class)
data class ManifestResource(
    val name: String,
    val types: List<String>? = null,
    val idPrefixes: List<String>? = null,
)

object ManifestResourceSerializer : KSerializer<ManifestResource> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("ManifestResource") {
        element("name", String.serializer().descriptor)
        element("types", ListSerializer(String.serializer()).descriptor, isOptional = true)
        element("idPrefixes", ListSerializer(String.serializer()).descriptor, isOptional = true)
    }

    override fun serialize(encoder: Encoder, value: ManifestResource) {
        val jsonEncoder = encoder as? JsonEncoder
        if (jsonEncoder != null) {
            if (value.types == null && value.idPrefixes == null) {
                jsonEncoder.encodeJsonElement(JsonPrimitive(value.name))
            } else {
                val map = mutableMapOf<String, JsonElement>("name" to JsonPrimitive(value.name))
                value.types?.let { map["types"] = JsonArray(it.map { t -> JsonPrimitive(t) }) }
                value.idPrefixes?.let { map["idPrefixes"] = JsonArray(it.map { p -> JsonPrimitive(p) }) }
                jsonEncoder.encodeJsonElement(JsonObject(map))
            }
            return
        }
        val composite = encoder.beginStructure(descriptor)
        composite.encodeStringElement(descriptor, 0, value.name)
        value.types?.let { composite.encodeSerializableElement(descriptor, 1, ListSerializer(String.serializer()), it) }
        value.idPrefixes?.let { composite.encodeSerializableElement(descriptor, 2, ListSerializer(String.serializer()), it) }
        composite.endStructure(descriptor)
    }

    override fun deserialize(decoder: Decoder): ManifestResource {
        val jsonDecoder = decoder as? JsonDecoder ?: return ManifestResource(decoder.decodeString())
        return when (val element = jsonDecoder.decodeJsonElement()) {
            is JsonPrimitive -> ManifestResource(name = element.content)
            is JsonObject -> {
                val name = (element["name"] as? JsonPrimitive)?.content.orEmpty()
                val types = (element["types"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.content }
                val idPrefixes = (element["idPrefixes"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.content }
                ManifestResource(name = name, types = types, idPrefixes = idPrefixes)
            }
            else -> ManifestResource(name = "")
        }
    }
}

/** Stremio protokolü katalog tanımı: `{base}/catalog/{type}/{id}.json`. */
@Serializable
data class ManifestCatalog(
    val type: String,
    val id: String,
    val name: String? = null,
)
