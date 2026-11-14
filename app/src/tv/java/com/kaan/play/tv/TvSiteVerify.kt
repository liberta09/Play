package com.kaan.play.tv

import android.content.Context
import android.content.Intent
import com.kaan.play.AppContainer
import com.kaan.play.data.cloudstream.cloudflare.CloudflareCache
import com.kaan.play.data.cloudstream.cloudflare.CloudflareSolver
import com.kaan.play.ui.cloudflare.CloudflareVerifyActivity

internal object TvSiteVerify {
    /**
     * Bot doğrulaması (Cloudflare) isteyen site: Play ana sayfasının "Doğrula" kuralıyla aynı (CatalogRepository) — [sinceMs]'ten
     * beri engel sayfasında kalan alan adı, yoksa eklentinin engellenmiş ana adresi. Bulunamazsa null.
     */
    suspend fun verifySite(container: AppContainer, providerId: String?, sinceMs: Long, name: String): VerifySite? {
        CloudflareSolver.unresolvedSince(sinceMs)?.let { (host, url) -> return VerifySite(host, url, name) }
        val id = providerId?.takeIf { it.startsWith("cs:") }?.removePrefix("cs:")?.toLongOrNull() ?: return null
        val api = container.csRepos.loadApis(id).apisOrEmpty().firstOrNull { CloudflareSolver.wasBlocked(it.mainUrl) } ?: return null
        val host = CloudflareCache.normalizeHost(api.mainUrl)
        return VerifySite(host, CloudflareSolver.getBlockedUrl(host) ?: api.mainUrl, name)
    }
}

/** Doğrulanacak site; [intent] Play'in doğrulama ekranını açar (telefondakiyle aynı ekran). */
internal data class VerifySite(val host: String, val url: String, val name: String) {
    fun intent(context: Context): Intent = CloudflareVerifyActivity.intent(context, url, host, name)
}
