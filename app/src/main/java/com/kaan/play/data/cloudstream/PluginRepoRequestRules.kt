package com.kaan.play.data.cloudstream

import java.net.URI

data class PluginRepoItem(
    val name: String,
    val url: String,
)

object PluginRepoRequestRules {
    const val MAX_REPOS = 20

    fun filterRequests(
        candidates: List<PluginRepoItem>,
        installedUrls: Set<String>,
        declinedUrls: Set<String>,
        isBlockedHost: (host: String, port: Int) -> Boolean = { host, port -> PlayNetworkGuard.isBlockedHost(host, port) },
    ): List<PluginRepoItem> {
        val normalizedInstalled = installedUrls.map { normalizeUrl(it) }.toSet()
        val normalizedDeclined = declinedUrls.map { normalizeUrl(it) }.toSet()

        val result = mutableListOf<PluginRepoItem>()
        val seenUrls = mutableSetOf<String>()

        for (candidate in candidates) {
            val rawUrl = candidate.url.trim()
            if (!rawUrl.startsWith("https://", ignoreCase = true)) {
                continue
            }
            val normalized = normalizeUrl(rawUrl)
            if (normalized.isEmpty() || normalized in seenUrls) {
                continue
            }
            if (normalized in normalizedInstalled || normalized in normalizedDeclined) {
                continue
            }
            val uri = try {
                URI(rawUrl)
            } catch (_: Exception) {
                continue
            }
            val host = uri.host ?: continue
            val port = if (uri.port != -1) uri.port else 443
            if (isBlockedHost(host, port)) {
                continue
            }
            seenUrls.add(normalized)
            result.add(candidate.copy(url = rawUrl))
            if (result.size >= MAX_REPOS) {
                break
            }
        }
        return result
    }

    fun normalizeUrl(url: String): String {
        return url.trim().removeSuffix("/").lowercase()
    }
}
