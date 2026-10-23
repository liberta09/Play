package com.kaan.play.data.update

import kotlinx.serialization.Serializable
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/** Release'teki bir dosya (GitHub API `assets[]`). */
data class ReleaseAsset(val name: String, val url: String, val size: Long)

/** Seçilen APK: indirme adresi, beklenen boyut ve SHA-256 dosyasının adresi. */
@Serializable
data class UpdateApk(val name: String, val url: String, val size: Long, val sha256Url: String)

/** Android'den bağımsız APK seçimi ve adres/özet kuralları (birim testli). */
object UpdateApkRules {

    /** Bunun üstü şüpheli sayılır (gerçek APK'lar ~13 MB). */
    const val MAX_APK_BYTES = 100L * 1024 * 1024

    /** SHA-256 dosyası küçük bir metindir. */
    const val MAX_HASH_FILE_BYTES = 16L * 1024

    /**
     * [tag] sürümünün APK'sı seçilir. Önce tek evrensel `Play-<sürüm>.apk` aranır; yoksa eski release'lerle uyum
     * için [abis] (cihazın tercih sırası) içinden ilk uyan `Play-<sürüm>-<abi>.apk` seçilir. Adlar kesin eşleşir:
     * `Play-<sürüm>.apk` (veya `Play-<sürüm>-<abi>.apk`) ve `Play-<sürüm>-SHA256.txt`; ikisinden biri yoksa ya da
     * adres güvenli değilse null (kullanıcı release sayfasına yönlenir).
     */
    fun pick(tag: String, assets: List<ReleaseAsset>, abis: List<String>, debugHost: String? = null): UpdateApk? {
        val version = tag.trim().removePrefix("v").removePrefix("V")
        if (version.isEmpty()) return null
        val byName = assets.associateBy { it.name }
        val hashAsset = byName["Play-$version-SHA256.txt"]?.takeIf { isSafeDownload(it.url, debugHost) } ?: return null

        val universal = byName["Play-$version.apk"]
        if (universal != null && universal.size in 1..MAX_APK_BYTES && isSafeDownload(universal.url, debugHost)) {
            return UpdateApk(universal.name, universal.url, universal.size, hashAsset.url)
        }

        for (abi in abis) {
            val asset = byName["Play-$version-$abi.apk"] ?: continue
            if (asset.size !in 1..MAX_APK_BYTES || !isSafeDownload(asset.url, debugHost)) continue
            return UpdateApk(asset.name, asset.url, asset.size, hashAsset.url)
        }
        return null
    }

    /** İndirme klasörüne yazılan/okunan APK adı: `Play-<...>.apk`, yol ayırıcı yok (klasör dışına çıkamaz). */
    fun isPlausibleApkName(name: String): Boolean =
        name.startsWith("Play-") && name.endsWith(".apk") && name.none { it == '/' || it == '\\' } && ".." !in name

    /**
     * `sha256sum` biçimi (`<64 onaltılık> *<ad>` ya da `<özet>  <ad>`): [fileName] satırının özeti, küçük harfle.
     * Satır yoksa ya da özet 64 onaltılık basamak değilse null.
     */
    fun parseSha256(text: String, fileName: String): String? {
        val clean = text.removePrefix("\uFEFF")
        for (line in clean.lineSequence()) {
            val trimmedLine = line.removePrefix("\uFEFF").trim()
            val parts = trimmedLine.split(Regex("\\s+"), limit = 2)
            if (parts.size != 2) continue
            if (parts[1].trim().removePrefix("*") != fileName) continue
            val hash = parts[0].lowercase()
            if (hash.length == 64 && hash.all { it in '0'..'9' || it in 'a'..'f' }) return hash
        }
        return null
    }

    /**
     * APK/özet adresi (ve her yönlendirme adımı): https ve GitHub'a ait ana bilgisayar. [debugHost] yalnızca debug
     * derlemede sahte sunucu içindir.
     */
    fun isSafeDownload(url: String, debugHost: String? = null): Boolean {
        val http = url.toHttpUrlOrNull() ?: return false
        return isSafeDownload(http, debugHost)
    }

    fun isSafeDownload(url: HttpUrl, debugHost: String? = null): Boolean {
        if (debugHost != null && url.host == debugHost) return true
        return url.isHttps && (url.host == "github.com" || url.host.endsWith(".githubusercontent.com"))
    }
}
