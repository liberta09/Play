package com.kaan.play.data.catalog

import com.kaan.play.data.net.PlayJson
import kotlinx.serialization.Serializable

@Serializable
data class ExternalCatalog(
    val name: String,
    val metas: List<ExternalCatalogMeta>,
)

@Serializable
data class ExternalCatalogMeta(
    val id: String,
    val type: String,
    val name: String,
    val poster: String? = null,
)

sealed class ExternalCatalogException(val reason: Reason, message: String) : Exception(message) {
    enum class Reason {
        TooLarge,
        InvalidJson,
        MissingName,
        EmptyMetas,
        TooManyItems,
        MissingId,
        UnknownType,
        InvalidPoster,
    }

    class TooLarge(val bytes: Long) : ExternalCatalogException(
        Reason.TooLarge,
        "Dosya boyutu 1 MB sınırını aşıyor: $bytes bayt",
    )

    class InvalidJson(cause: Throwable? = null) : ExternalCatalogException(
        Reason.InvalidJson,
        "Bozuk veya geçersiz JSON dosyası: ${cause?.message}",
    )

    class MissingName : ExternalCatalogException(
        Reason.MissingName,
        "Katalog adı (name) boş olamaz",
    )

    class EmptyMetas : ExternalCatalogException(
        Reason.EmptyMetas,
        "Katalogda en az bir içerik bulunmalıdır",
    )

    class TooManyItems(val count: Int) : ExternalCatalogException(
        Reason.TooManyItems,
        "Katalog en fazla 500 içerik barındırabilir (mevcut: $count)",
    )

    class MissingId(val index: Int) : ExternalCatalogException(
        Reason.MissingId,
        "İçerik kimliği (id) eksik veya boş: dizin $index",
    )

    class UnknownType(val type: String, val index: Int) : ExternalCatalogException(
        Reason.UnknownType,
        "Bilinmeyen içerik türü: '$type' (yalnızca 'movie' veya 'series' desteklenir): dizin $index",
    )

    class InvalidPoster(val poster: String, val index: Int) : ExternalCatalogException(
        Reason.InvalidPoster,
        "Afiş adresi http:// veya https:// ile başlamalıdır: '$poster': dizin $index",
    )

    fun messageRes(): Int = when (reason) {
        Reason.TooLarge -> com.kaan.play.R.string.external_catalog_error_too_large
        Reason.InvalidJson -> com.kaan.play.R.string.external_catalog_error_invalid_json
        Reason.MissingName -> com.kaan.play.R.string.external_catalog_error_missing_name
        Reason.EmptyMetas -> com.kaan.play.R.string.external_catalog_error_empty_metas
        Reason.TooManyItems -> com.kaan.play.R.string.external_catalog_error_too_many_items
        Reason.MissingId -> com.kaan.play.R.string.external_catalog_error_missing_id
        Reason.UnknownType -> com.kaan.play.R.string.external_catalog_error_unknown_type
        Reason.InvalidPoster -> com.kaan.play.R.string.external_catalog_error_invalid_poster
    }
}

object ExternalCatalogParser {
    const val MAX_BYTE_SIZE: Long = 1024L * 1024L // 1 MB
    const val MAX_ITEMS: Int = 500

    private val VALID_TYPES = setOf("movie", "series")

    @Throws(ExternalCatalogException::class)
    fun parse(rawJson: String, byteSize: Long = rawJson.toByteArray(Charsets.UTF_8).size.toLong()): ExternalCatalog {
        if (byteSize > MAX_BYTE_SIZE) {
            throw ExternalCatalogException.TooLarge(byteSize)
        }

        val catalog: ExternalCatalog = try {
            PlayJson.decodeFromString(ExternalCatalog.serializer(), rawJson)
        } catch (t: Throwable) {
            throw ExternalCatalogException.InvalidJson(t)
        }

        if (catalog.name.isBlank()) {
            throw ExternalCatalogException.MissingName()
        }

        if (catalog.metas.isEmpty()) {
            throw ExternalCatalogException.EmptyMetas()
        }

        if (catalog.metas.size > MAX_ITEMS) {
            throw ExternalCatalogException.TooManyItems(catalog.metas.size)
        }

        catalog.metas.forEachIndexed { index, meta ->
            if (meta.id.isBlank()) {
                throw ExternalCatalogException.MissingId(index)
            }

            val normalizedType = meta.type.trim().lowercase()
            if (normalizedType !in VALID_TYPES) {
                throw ExternalCatalogException.UnknownType(meta.type, index)
            }

            val poster = meta.poster?.trim()
            if (!poster.isNullOrEmpty()) {
                val isHttp = poster.startsWith("http://", ignoreCase = true) ||
                    poster.startsWith("https://", ignoreCase = true)
                if (!isHttp) {
                    throw ExternalCatalogException.InvalidPoster(meta.poster, index)
                }
            }
        }

        return catalog
    }
}
