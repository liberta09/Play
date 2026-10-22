package com.kaan.play.data.cloudstream

import com.lagradost.cloudstream3.MainAPI

/**
 * CloudStream eklenti yükleme sonuç türü.
 *
 * Boş liste yerine yükleme durumunu veya başarısızlık sebebini taşır.
 * Sebepler:
 * - [Success]: Eklenti başarıyla yüklendi ve en az bir [MainAPI] sunuyor.
 * - [PluginRowMissing]: Veritabanında eklenti satırı bulunamadı.
 * - [DexFileMissing]: Diskte `.dex` dosyası eksik / silinmiş.
 * - [IncompatibleLibrary]: Eklenti ABI uyumsuzluğu (NoSuchMethodError, NoClassDefFoundError, vb.).
 * - [LoadFailed]: Sınıf bulunamadı, kurucu hatası veya çalışma zamanı hatası.
 * - [NoApiRegistered]: Yükleme hatasız bitti ancak hiçbir MainAPI kaydedilmedi.
 */
sealed interface CsApiResult {
    data class Success(val apis: List<MainAPI>) : CsApiResult

    sealed interface Failure : CsApiResult {
        val pluginId: String?
    }

    data class PluginRowMissing(val rowId: Long = 0L) : Failure {
        override val pluginId: String? get() = null
    }

    data class DexFileMissing(override val pluginId: String = "") : Failure

    data class IncompatibleLibrary(
        val causeClass: String,
        override val pluginId: String = "",
    ) : Failure

    data class LoadFailed(
        val causeClass: String,
        override val pluginId: String = "",
    ) : Failure

    data class NoApiRegistered(override val pluginId: String = "") : Failure

    fun apisOrEmpty(): List<MainAPI> = when (this) {
        is Success -> apis
        is Failure -> emptyList()
    }
}

/**
 * DEX yükleme / çalıştırma hatasını sınıflandırır.
 *
 * Yalnızca hatanın sınıf adını alır; mesaj, adres, çerez veya belirteç asla kaydedilmez.
 * [NoSuchMethodError], [NoClassDefFoundError], [AbstractMethodError] ve [LinkageError]
 * kütüphane ikili (ABI) uyumsuzluğu olarak ([CsApiResult.IncompatibleLibrary]),
 * diğer istisnalar ise [CsApiResult.LoadFailed] olarak işaretlenir.
 */
fun classifyDexError(t: Throwable, pluginId: String = ""): CsApiResult.Failure {
    val root = when {
        t is java.lang.reflect.InvocationTargetException && t.targetException != null -> t.targetException
        t is ExceptionInInitializerError && t.exception != null -> t.exception
        else -> t
    }
    val causeClass = root.javaClass.simpleName.ifEmpty { root.javaClass.name }
    return when (root) {
        is NoSuchMethodError,
        is NoClassDefFoundError,
        is AbstractMethodError,
        is LinkageError -> CsApiResult.IncompatibleLibrary(causeClass = causeClass, pluginId = pluginId)
        else -> CsApiResult.LoadFailed(causeClass = causeClass, pluginId = pluginId)
    }
}

/**
 * PlayCs etiketiyle günlüğe yazılacak tek satırlık hata özeti üretir.
 * Adres, çerez ve belirteç içermez: yalnızca eklenti kimliği (internalName) ve sebep türü.
 */
fun formatCsLog(result: CsApiResult.Failure): String {
    val id = when (result) {
        is CsApiResult.PluginRowMissing -> "row#${result.rowId}"
        else -> result.pluginId?.ifEmpty { "unknown" } ?: "unknown"
    }
    val reason = when (result) {
        is CsApiResult.PluginRowMissing -> "PluginRowMissing"
        is CsApiResult.DexFileMissing -> "DexFileMissing"
        is CsApiResult.IncompatibleLibrary -> "IncompatibleLibrary(${result.causeClass})"
        is CsApiResult.LoadFailed -> "LoadFailed(${result.causeClass})"
        is CsApiResult.NoApiRegistered -> "NoApiRegistered"
    }
    return "$id: $reason"
}
