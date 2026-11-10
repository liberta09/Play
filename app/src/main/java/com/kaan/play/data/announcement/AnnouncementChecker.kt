package com.kaan.play.data.announcement

import com.kaan.play.data.net.PlayJson
import com.kaan.play.data.net.await
import com.kaan.play.data.settings.SettingsRepository
import com.kaan.play.data.settings.WhatsNewCoordinator
import com.kaan.play.data.update.UpdateChecker
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * GitHub raw announcements.json kontrolü.
 *
 * Kurallar:
 * - Açılışta oturum (süreç) başına en çok bir kontrol.
 * - 8 sn zaman aşımı, en çok 64 KB yanıt gövdesi.
 * - Hata, zaman aşımı, bozuk JSON sessizce geçilir.
 * - Güncelleme penceresi çıkıyorsa (UpdateChecker bir güncelleme sunuyorsa) duyuru bu açılışta gösterilmez.
 * - "Tamam" veya "Aç" dendiğinde duyuru kimliği DataStore'a yazılır, bir daha gösterilmez.
 * - Gizlilik: GitHub'a yalnızca herkese açık bir GET gider (kimlik, cihaz bilgisi, ayar gönderilmez).
 */
class AnnouncementChecker(
    private val client: OkHttpClient,
    private val json: Json = PlayJson,
    private val installedVersion: String,
    private val settings: SettingsRepository,
    private val endpoint: String = DEFAULT_ENDPOINT,
    private val timeoutSeconds: Long = 8,
    private val today: () -> String = { AnnouncementRules.todayString() },
) {
    private val started = AtomicBoolean(false)

    @Volatile
    var debugHost: String? = null
        private set
    @Volatile
    private var endpointOverride: String? = null

    private val _available = MutableStateFlow<Announcement?>(null)
    val available: StateFlow<Announcement?> = _available.asStateFlow()

    /**
     * Oturumda tek bir kontrol başlatır.
     * [updateChecker] verilmişse onun tamamlanmasını bekler; eğer güncelleme mevcutsa duyuruyu göstermez.
     */
    fun checkOnce(
        scope: CoroutineScope,
        updateChecker: UpdateChecker? = null,
        debugEndpoint: String? = null,
        whatsNewCoordinator: WhatsNewCoordinator? = null,
    ) {
        if (!started.compareAndSet(false, true)) return
        if (debugEndpoint != null) {
            endpointOverride = debugEndpoint
            debugHost = debugEndpoint.toHttpUrlOrNull()?.host
        }
        scope.launch {
            if (updateChecker != null) {
                // Güncelleme kontrolünün bitmesini bekle
                updateChecker.completed.first { it }
                if (updateChecker.available.value != null) {
                    // Güncelleme penceresi çıkıyor: duyuru aynı açılışta gösterilmez (sırayla, önce güncelleme)
                    return@launch
                }
            }
            if (whatsNewCoordinator != null) {
                whatsNewCoordinator.decided.first { it }
                if (whatsNewCoordinator.blocksAnnouncementThisLaunch()) {
                    // Sürüm notları penceresi bu açılışta gösteriliyor: duyuru bir sonraki açılışa bırakılır
                    return@launch
                }
            }
            val seen = settings.seenAnnouncementIds.first()
            _available.value = try {
                fetch(seen)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                null
            }
        }
    }

    suspend fun fetch(seenIds: Set<String>): Announcement? {
        val url = endpointOverride ?: endpoint
        val httpUrl = url.toHttpUrlOrNull() ?: return null
        val request = Request.Builder()
            .url(httpUrl)
            .header("User-Agent", "Play-Android-AnnouncementCheck")
            .build()
        val call = client.newBuilder()
            .callTimeout(timeoutSeconds, TimeUnit.SECONDS)
            .build()
            .newCall(request)
        val text = call.await().use { response ->
            if (!response.isSuccessful) return null
            val source = response.body.source()
            source.request(MAX_BODY_BYTES + 1)
            if (source.buffer.size > MAX_BODY_BYTES) return null
            source.readUtf8()
        }
        val list = AnnouncementRules.parse(text, json) ?: return null
        return AnnouncementRules.pick(list, installedVersion, seenIds, today())
    }

    suspend fun dismiss(announcement: Announcement? = _available.value) {
        _available.value = null
        if (announcement != null) {
            settings.markAnnouncementSeen(announcement.id)
        }
    }

    companion object {
        const val DEFAULT_ENDPOINT = "https://raw.githubusercontent.com/liberta09/Play/main/announcements.json"
        private const val MAX_BODY_BYTES = 64L * 1024
    }
}
