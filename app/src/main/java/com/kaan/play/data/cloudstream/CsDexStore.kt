package com.kaan.play.data.cloudstream

import android.os.Build
import android.util.Log
import com.kaan.play.data.addon.AddonException
import com.kaan.play.data.addon.AddonException.Kind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.zip.ZipInputStream

private const val TAG = "PlayCs"

/**
 * CloudStream `.cs3` dosyalarının disk yönetimi.
 * Her eklenti için iki dosya saklanır:
 *   - `{id}.dex`: DEX bytecode (PathClassLoader ile yüklenir)
 *   - `{id}.manifest`: ZIP içindeki manifest.json içeriği (eklentinin kendi beyanı)
 *
 * Dizin: `filesDir/cloudstream/`
 */
class CsDexStore(private val dir: File) {

    init { dir.mkdirs() }

    fun dexFile(id: Long): File = File(dir, "$id.dex")
    fun manifestFile(id: Long): File = File(dir, "$id.manifest")

    fun exists(id: Long): Boolean = dexFile(id).exists()

    fun delete(id: Long) {
        dexFile(id).delete()
        manifestFile(id).delete()
    }

    fun readManifest(id: Long): String? = manifestFile(id).takeIf { it.exists() }?.readText()

    /**
     * DEX dosyasını okunur-salt moda alır (Android 14+ zorunluluğu).
     * Hata olursa sessizce geçer — eski Android'lerde `setReadOnly()` yeterlidir.
     */
    fun setReadOnly(id: Long) {
        val f = dexFile(id)
        if (!f.exists()) return
        try { f.setReadOnly() } catch (_: Exception) {}
        if (Build.VERSION.SDK_INT >= 34) {
            // Android 14 (API 34): DEX'in read-only olması için OS-düzeyinde izin gerekliyse
            // PathClassLoader zaten hata verir; bu çağrı yeterlidir.
            try { f.setReadOnly() } catch (_: Exception) {}
        }
    }
}

// ---------------------------------------------------------------------------
// .cs3 indirme + açma
// ---------------------------------------------------------------------------

const val CS3_MAX_BYTES = 30 * 1024 * 1024L // 30 MB

/**
 * `.cs3` dosyasını indirir, SHA-256 özetini doğrular (boşsa atlanır), DEX ve manifest'i çıkarır.
 * @throws AddonException
 */
suspend fun downloadCs3(
    url: String,
    expectedHash: String,
    client: OkHttpClient,
    store: CsDexStore,
    pluginId: Long,
): Cs3Contents = withContext(Dispatchers.IO) {
    val host = url.toHttpUrlOrNull()?.host
    val request = Request.Builder().url(url).build()
    val bytes: ByteArray = try {
        client.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) {
                Log.d(TAG, "downloadCs3: http ${resp.code} host=$host")
                throw AddonException(Kind.Http, "${resp.code}")
            }
            val body = resp.body ?: run {
                Log.d(TAG, "downloadCs3: boş gövde host=$host")
                throw AddonException(Kind.InvalidField, "empty body")
            }
            val len = body.contentLength()
            if (len > CS3_MAX_BYTES) {
                Log.d(TAG, "downloadCs3: çok büyük (contentLength=$len) host=$host")
                throw AddonException(Kind.TooLarge)
            }
            body.byteStream().use { readBounded(it, CS3_MAX_BYTES.toInt()) }
        }
    } catch (e: AddonException) { throw e }
    catch (e: Exception) {
        Log.d(TAG, "downloadCs3: ağ hatası host=$host ${e.javaClass.simpleName}: ${e.message}")
        throw AddonException(Kind.Network, cause = e)
    }

    // SHA-256 doğrulama. Gerçek depolarda fileHash "sha256-<hex>" biçiminde gelir (ön ek atılır).
    if (expectedHash.isNotEmpty()) {
        val normalized = normalizeSha256(expectedHash)
        if (normalized == null) {
            Log.d(TAG, "downloadCs3: fileHash biçimi tanınmadı (64 haneli hex değil), doğrulama atlandı host=$host rawHash=$expectedHash")
        } else {
            val actual = sha256Hex(bytes)
            if (!actual.equals(normalized, ignoreCase = true)) {
                Log.d(TAG, "downloadCs3: sha-256 uyuşmazlığı host=$host beklenen=$normalized gerçek=$actual boyut=${bytes.size}")
                throw AddonException(Kind.InvalidField, "hash mismatch: expected=$normalized actual=$actual")
            }
        }
    }

    // ZIP'ten DEX ve manifest çıkar
    extractCs3(bytes, store, pluginId, host)
}

data class Cs3Contents(val sha256: String, val sizeBytes: Long, val pluginManifest: String)

private fun extractCs3(bytes: ByteArray, store: CsDexStore, id: Long, host: String?): Cs3Contents {
    var dexBytes: ByteArray? = null
    var manifestText: String? = null
    val entryNames = mutableListOf<String>()

    try {
        ZipInputStream(bytes.inputStream()).use { zip ->
            var entry = zip.nextEntry
            var entries = 0
            var inflated = 0L
            while (entry != null) {
                if (++entries > 128) throw AddonException(Kind.TooLarge)
                val name = entry.name
                entryNames += name
                var remainingIgnored = CS3_MAX_BYTES
                when {
                    name == "classes.dex" -> {
                        if (dexBytes != null) throw AddonException(Kind.InvalidField, "duplicate dex")
                        dexBytes = readBounded(zip, CS3_MAX_BYTES.toInt())
                    }
                    name == "manifest.json" -> {
                        if (manifestText != null) throw AddonException(Kind.InvalidField, "duplicate manifest")
                        manifestText = readBounded(zip, 256 * 1024).toString(Charsets.UTF_8)
                    }
                    else -> {
                        // Bound ignored entries too: closeEntry must not inflate an unbounded ZIP bomb.
                        val scratch = ByteArray(8192)
                        while (true) {
                            val n = zip.read(scratch)
                            if (n < 0) break
                            remainingIgnored -= n
                            if (remainingIgnored < 0) throw AddonException(Kind.TooLarge)
                        }
                    }
                }
                inflated += when (name) {
                    "classes.dex" -> dexBytes?.size?.toLong() ?: 0L
                    "manifest.json" -> manifestText?.toByteArray(Charsets.UTF_8)?.size?.toLong() ?: 0L
                    else -> CS3_MAX_BYTES - remainingIgnored
                }
                if (inflated > 2 * CS3_MAX_BYTES) throw AddonException(Kind.TooLarge)
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
    } catch (e: AddonException) { throw e }
    catch (e: Exception) {
        val sniff = bytes.take(16).joinToString(" ") { "%02x".format(it) }
        Log.d(TAG, "extractCs3: zip açılamadı host=$host boyut=${bytes.size} ilkBaytlar=$sniff ${e.javaClass.simpleName}: ${e.message}")
        throw AddonException(Kind.InvalidField, "not a valid .cs3 zip", cause = e)
    }

    val dex = dexBytes ?: run {
        Log.d(TAG, "extractCs3: classes.dex yok host=$host zipBoyutu=${bytes.size} girişler=$entryNames")
        throw AddonException(Kind.MissingField, "classes.dex not found in .cs3")
    }
    val manifest = manifestText ?: throw AddonException(Kind.MissingField, "manifest missing")
    if (dex.size < 8 || !dex.copyOfRange(0, 4).contentEquals(byteArrayOf(100, 101, 120, 10))) {
        throw AddonException(Kind.InvalidField, "invalid dex header")
    }

    // DEX dosyasına yaz
    val dexFile = store.dexFile(id)
    dexFile.parentFile?.mkdirs()
    val stageDex = File.createTempFile("native-", ".dex", dexFile.parentFile)
    val stageManifest = File.createTempFile("native-", ".manifest", dexFile.parentFile)
    try {
        FileOutputStream(stageDex).use { out ->
            if (!stageDex.setReadOnly()) throw AddonException(Kind.InvalidField, "readonly dex failed")
            out.write(dex)
            out.fd.sync()
        }
        stageManifest.writeText(manifest)
        java.nio.file.Files.move(stageDex.toPath(), dexFile.toPath(),
            java.nio.file.StandardCopyOption.ATOMIC_MOVE, java.nio.file.StandardCopyOption.REPLACE_EXISTING)
        java.nio.file.Files.move(stageManifest.toPath(), store.manifestFile(id).toPath(),
            java.nio.file.StandardCopyOption.ATOMIC_MOVE, java.nio.file.StandardCopyOption.REPLACE_EXISTING)
    } finally {
        stageDex.delete()
        stageManifest.delete()
    }

    return Cs3Contents(
        sha256 = sha256Hex(bytes),
        sizeBytes = bytes.size.toLong(),
        pluginManifest = manifest,
    )
}

private fun sha256Hex(bytes: ByteArray): String =
    MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

private val SHA256_HEX = Regex("^[0-9a-fA-F]{64}$")

/** `fileHash` alanı gerçek depolarda "sha256-<64 haneli hex>" biçimindedir; öneki atar, biçimi doğrular. */
internal fun normalizeSha256(raw: String): String? {
    val trimmed = raw.trim()
    val stripped = if (trimmed.startsWith("sha256-", ignoreCase = true)) trimmed.substring(7) else trimmed
    return stripped.takeIf { SHA256_HEX.matches(it) }?.lowercase()
}

/** Stops before allocating an unbounded response/ZIP entry. */
internal fun readBounded(input: java.io.InputStream, limit: Int): ByteArray {
    val output = java.io.ByteArrayOutputStream(minOf(limit, 8192))
    val buffer = ByteArray(8192)
    var total = 0
    while (true) {
        val count = input.read(buffer, 0, minOf(buffer.size, limit - total + 1))
        if (count < 0) break
        total += count
        if (total > limit) throw AddonException(Kind.TooLarge)
        output.write(buffer, 0, count)
    }
    return output.toByteArray()
}
