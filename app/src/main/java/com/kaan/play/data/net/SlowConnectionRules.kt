package com.kaan.play.data.net

import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Yavaş bağlantı modu ve oynatma sırasında yavaş bağlantı öneri kartı kuralları (Android'den bağımsız).
 *
 * - Açıkken OkHttp bağlantı/okuma zaman aşımı en çok 3 katına çıkar.
 * - Yalnızca GET ve HEAD istekleri ağ hatasında (`IOException`; SSRF engeli hariç) en çok 1 kez yeniden denenir.
 * - POST (ve PUT/PATCH/DELETE) asla yeniden denenmez.
 */
object SlowConnectionRules {

    const val TIMEOUT_MULTIPLIER = 3
    const val MAX_RETRY_COUNT = 1
    const val CONTINUOUS_BUFFERING_PROMPT_MS = 15_000L

    private val RETRYABLE_METHODS = setOf("GET", "HEAD")

    fun scaledTimeoutMs(baseMs: Int, slowMode: Boolean): Int {
        if (!slowMode || baseMs <= 0) return baseMs
        val scaled = baseMs.toLong() * TIMEOUT_MULTIPLIER
        return scaled.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    }

    fun isRetryableMethod(method: String): Boolean =
        method.uppercase(Locale.ROOT) in RETRYABLE_METHODS

    fun shouldRetryAfterFailure(
        method: String,
        slowMode: Boolean,
        retryAttempted: Int,
        failure: IOException,
        canceled: Boolean = false,
    ): Boolean {
        if (!slowMode || canceled) return false
        if (retryAttempted >= MAX_RETRY_COUNT) return false
        if (!isRetryableMethod(method)) return false
        if (failure is com.kaan.play.data.cloudstream.BlockedAddressException) return false
        if (failure is com.kaan.play.data.plugin.BlockedAddressException) return false
        if (failure.message == "blocked") return false
        return true
    }
}

/** OkHttp istemcisine yavaş bağlantı zaman aşımı çarpanı (en çok 3×) ve GET/HEAD tek yeniden deneme uygular. */
class SlowConnectionInterceptor(
    private val slowModeEnabled: () -> Boolean,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val slow = slowModeEnabled()
        val activeChain = if (slow) {
            chain
                .withConnectTimeout(
                    SlowConnectionRules.scaledTimeoutMs(chain.connectTimeoutMillis(), true),
                    TimeUnit.MILLISECONDS,
                )
                .withReadTimeout(
                    SlowConnectionRules.scaledTimeoutMs(chain.readTimeoutMillis(), true),
                    TimeUnit.MILLISECONDS,
                )
        } else {
            chain
        }
        val request = activeChain.request()
        return try {
            activeChain.proceed(request)
        } catch (e: IOException) {
            if (!SlowConnectionRules.shouldRetryAfterFailure(
                    method = request.method,
                    slowMode = slow,
                    retryAttempted = 0,
                    failure = e,
                    canceled = activeChain.call().isCanceled(),
                )
            ) {
                throw e
            }
            activeChain.proceed(request)
        }
    }
}

/** Oynatma sırasında tek seferde ~15 sn aralıksız arabelleğe almada oturumda bir kez yavaş mod kartı gösterme kuralı. */
object SlowConnectionPromptRules {

    fun shouldShowPrompt(
        suggestEnabled: Boolean,
        slowModeEnabled: Boolean,
        promptedThisSession: Boolean,
        isOffline: Boolean,
        isTorrent: Boolean,
        firstFrameRendered: Boolean,
        isSeeking: Boolean,
        currentBufferingStartMs: Long?,
        nowMs: Long,
    ): Boolean {
        if (!suggestEnabled || slowModeEnabled || promptedThisSession) return false
        if (isOffline || isTorrent) return false
        if (!firstFrameRendered || isSeeking || currentBufferingStartMs == null) return false
        return (nowMs - currentBufferingStartMs) >= SlowConnectionRules.CONTINUOUS_BUFFERING_PROMPT_MS
    }
}
