package com.kaan.play.data.plugin

import com.kaan.play.data.addon.AddonException
import com.kaan.play.data.addon.AddonException.Kind
import com.kaan.play.data.addon.AddonRepository
import com.kaan.play.data.addon.AddonUrl
import com.kaan.play.data.addon.ManifestFetcher
import com.kaan.play.data.cloudstream.CsRepository
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/** "Eklenti ekle" sonucu: ne eklendi. Arayüz buna göre sade bir bildirim gösterir. */
sealed interface AddResult {
    data class Addon(val name: String) : AddResult
    data class PluginRepo(val name: String, val installed: Int, val skipped: Int) : AddResult
    data class CsRepo(val name: String, val installed: Int, val skipped: Int) : AddResult
}

/** Kullanıcının seçtiği ya da bağlantı parametresinden gelen kaynak türü. */
enum class SourceType {
    CLOUDSTREAM,
    NUVIO_REPO,
    NUVIO_PLUGIN,
    EXTERNAL_CATALOG,
}

/**
 * "Eklenti ekle" penceresinin tek kapısı: adresi indirir ve hem kullanıcının seçtiği türle uyuşup
 * uyuşmadığını doğrular hem de içeriğe göre doğru veri katmanına kurar.
 */
class SourceInstaller(
    private val addons: AddonRepository,
    private val plugins: PluginRepository,
    private val csRepos: CsRepository,
    private val fetcher: ManifestFetcher,
) {

    /**
     * @param input Kullanıcının girdiği ya da bağlantıdan gelen adres.
     * @param expectedType Beklenen kaynak türü; verilirse manifest türü ile kesin uyuşma aranır.
     * @throws AddonException
     */
    suspend fun add(input: String, expectedType: SourceType? = null): AddResult {
        val trimmed = input.trim()
        val isDirectCs3 = trimmed.removeSuffix("/").endsWith(".cs3", ignoreCase = true)
        val url = if (isDirectCs3) {
            val withScheme = if ("://" in trimmed) trimmed else "https://$trimmed"
            val parsed = withScheme.toHttpUrlOrNull() ?: throw AddonException(Kind.InvalidUrl)
            parsed.newBuilder().fragment(null).build().toString().removeSuffix("/")
        } else {
            AddonUrl.normalize(input)
        }

        if (addons.isKnown(url)) throw AddonException(Kind.AlreadyAdded)

        if (isDirectCs3) {
            if (expectedType != null && expectedType != SourceType.CLOUDSTREAM) {
                throw AddonException(
                    kind = Kind.KindMismatch,
                    detail = expectedType.name.lowercase(),
                    detectedType = SourceType.CLOUDSTREAM,
                )
            }
            val added = csRepos.addDirectCs3(url)
            return AddResult.CsRepo(added.repo.name, added.installed, added.skipped)
        }

        val raw = fetcher.fetch(url)
        val kind = ManifestKind.classify(raw)

        val detectedType = when (kind) {
            ManifestKind.PluginRepo -> SourceType.NUVIO_PLUGIN
            ManifestKind.External -> SourceType.CLOUDSTREAM
            ManifestKind.Addon -> SourceType.NUVIO_REPO
            ManifestKind.Unknown -> null
        }

        val compatibleExternalCatalog = expectedType == SourceType.EXTERNAL_CATALOG && kind == ManifestKind.Addon
        if (expectedType != null && detectedType != null && detectedType != expectedType && !compatibleExternalCatalog) {
            throw AddonException(
                kind = Kind.KindMismatch,
                detail = expectedType.name.lowercase(),
                detectedType = detectedType,
            )
        }

        val targetType = expectedType ?: detectedType ?: SourceType.NUVIO_REPO

        return when (targetType) {
            SourceType.CLOUDSTREAM -> {
                if (kind != ManifestKind.External) {
                    throw AddonException(Kind.KindMismatch, "cloudstream", detectedType = detectedType)
                }
                csRepos.addFetched(url, raw).let { AddResult.CsRepo(it.repo.name, it.installed, it.skipped) }
            }
            SourceType.NUVIO_REPO -> {
                if (kind == ManifestKind.PluginRepo || kind == ManifestKind.External) {
                    throw AddonException(Kind.KindMismatch, "nuvio_repo", detectedType = detectedType)
                }
                AddResult.Addon(addons.addFetched(url, raw).manifest.name)
            }
            SourceType.NUVIO_PLUGIN -> {
                if (kind != ManifestKind.PluginRepo) {
                    throw AddonException(Kind.KindMismatch, "nuvio_plugin", detectedType = detectedType)
                }
                plugins.addFetched(url, raw).let { AddResult.PluginRepo(it.repo.name, it.installed, it.skipped) }
            }
            SourceType.EXTERNAL_CATALOG -> {
                if (kind == ManifestKind.PluginRepo || kind == ManifestKind.External) {
                    throw AddonException(Kind.KindMismatch, "external_catalog", detectedType = detectedType)
                }
                AddResult.Addon(addons.addFetched(url, raw, catalogOnly = true).manifest.name)
            }
        }
    }
}
