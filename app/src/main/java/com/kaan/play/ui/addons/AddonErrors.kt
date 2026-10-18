package com.kaan.play.ui.addons

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.kaan.play.R
import com.kaan.play.data.addon.AddonException
import com.kaan.play.data.addon.AddonException.Kind

/** Hata türünden yerelleştirilmiş, anlaşılır mesaj. Sunucudan gelen metin ya da URL asla gösterilmez. */
@Composable
fun AddonException.userMessage(): String = when (kind) {
    Kind.InvalidUrl -> stringResource(R.string.addon_error_invalid_url)
    Kind.AlreadyAdded -> stringResource(R.string.addon_error_already_added)
    Kind.UnsupportedKind -> stringResource(R.string.addon_error_unsupported_kind)
    Kind.WrongSourceType -> stringResource(R.string.addon_error_wrong_source_type)
    Kind.Network -> stringResource(R.string.addon_error_network)
    Kind.Http -> stringResource(R.string.addon_error_http, detail.orEmpty())
    Kind.TooLarge -> stringResource(R.string.addon_error_too_large)
    Kind.NotJson -> stringResource(R.string.addon_error_not_json)
    Kind.MissingField -> stringResource(R.string.addon_error_missing_field, detail.orEmpty())
    Kind.InvalidField -> stringResource(R.string.addon_error_invalid_field, detail.orEmpty())
    Kind.NoStreamResource -> stringResource(R.string.addon_error_no_stream)
    Kind.UnsupportedTypes -> stringResource(R.string.addon_error_unsupported_types)
    Kind.NoPlugins -> stringResource(R.string.addon_error_no_plugins)
    Kind.PluginFileFailed -> pluginFileFailedMessage(detail)
}

/**
 * [Kind.PluginFileFailed] ayrıntısı Faz 3'te (CloudStream) "ad::sebepKodu" biçiminde gelebilir
 * (sebep `CsRepository.downloadOne`'da belirlenir); Faz 2 (Nuvio) yalnızca adı gönderir, sebep yok.
 */
@Composable
private fun pluginFileFailedMessage(detail: String?): String {
    val name = detail?.substringBefore("::").orEmpty()
    val code = detail?.substringAfter("::", "").orEmpty()
    val reason = pluginFailReasonText(code)
    return if (reason != null) {
        stringResource(R.string.addon_error_plugin_file_reason, name, reason)
    } else {
        stringResource(R.string.addon_error_plugin_file, name)
    }
}

@Composable
internal fun pluginFailReasonText(code: String): String? = when {
    code.isEmpty() -> null
    code.startsWith("http:") -> stringResource(R.string.cs_fail_reason_http, code.removePrefix("http:"))
    code == "toolarge" -> stringResource(R.string.cs_fail_reason_too_large)
    code == "network" -> stringResource(R.string.cs_fail_reason_network)
    code == "hash" -> stringResource(R.string.cs_fail_reason_hash)
    code == "db" -> stringResource(R.string.cs_fail_reason_db)
    else -> stringResource(R.string.cs_fail_reason_invalid)
}
