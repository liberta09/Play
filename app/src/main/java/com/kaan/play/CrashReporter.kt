package com.kaan.play

import android.content.Context
import android.os.Build
import android.os.Looper
import android.util.Log
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

object CrashReporter {
    const val TAG = "CrashReporter"

    private val httpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(8, TimeUnit.SECONDS)
            .writeTimeout(8, TimeUnit.SECONDS)
            .build()
    }

    // Deduplication ve Rate Limit (Spam Koruması)
    private val lastSentByFingerprint = ConcurrentHashMap<String, Long>()
    @Volatile private var lastGlobalSendTime = 0L
    const val DEDUP_COOLDOWN_MS = 60_000L // Aynı hata 60 sn içinde tekrar gönderilmez
    const val GLOBAL_MIN_INTERVAL_MS = 3_000L // İki mesaj arasında en az 3 sn beklenir
    const val PENDING_CRASH_FILE = "pending_crash.json"
    const val MAX_TEXT_LENGTH = 3500

    private val URL_REGEX = Regex("""https?://[^\s<>"']+""", RegexOption.IGNORE_CASE)
    private val MAGNET_REGEX = Regex("""magnet:\?[^\s<>"']+""", RegexOption.IGNORE_CASE)

    fun sanitizeText(raw: String): String {
        if (raw.isBlank()) return ""
        val withoutMagnet = raw.replace(MAGNET_REGEX, "<magnet>")
        val withoutUrl = withoutMagnet.replace(URL_REGEX, "<url>")
        return withoutUrl.take(MAX_TEXT_LENGTH)
    }

    fun buildReportText(
        version: String,
        versionCode: Int,
        device: String,
        threadName: String,
        message: String,
        stackTrace: String
    ): String {
        val cleanTrace = formatStackTraceForReport(stackTrace)
        val raw = buildString {
            append("Sürüm: ").append(version).append(" (").append(versionCode).append(")\n")
            append("Cihaz: ").append(device).append("\n")
            if (threadName.isNotBlank()) {
                append("Thread: ").append(threadName).append("\n")
            }
            if (message.isNotBlank()) {
                append("Hata: ").append(message).append("\n\n")
            }
            append(cleanTrace)
        }
        return sanitizeText(raw)
    }

    fun buildPayloadJson(
        version: String,
        versionCode: Int,
        device: String,
        text: String
    ): JSONObject {
        return JSONObject().apply {
            put("version", version)
            put("versionCode", versionCode)
            put("device", device)
            put("text", text)
        }
    }

    fun buildReportPayload(
        version: String,
        versionCode: Int,
        device: String,
        threadName: String,
        message: String,
        stackTrace: String
    ): JSONObject {
        val text = buildReportText(version, versionCode, device, threadName, message, stackTrace)
        return buildPayloadJson(version, versionCode, device, text)
    }

    fun shouldSendReport(
        message: String,
        stackTrace: String,
        now: Long = System.currentTimeMillis()
    ): Boolean {
        val stackSummary = stackTrace.lines().filter { it.isNotBlank() }.take(4).joinToString(";")
        val fingerprint = "${message.take(120)}|$stackSummary"

        val lastSent = lastSentByFingerprint[fingerprint]
        if (lastSent != null && now - lastSent < DEDUP_COOLDOWN_MS) {
            Log.w(TAG, "Spam koruması: Bu hata son 60 sn içinde bildirildi, tekrar gönderilmiyor: ${message.take(80)}")
            return false
        }

        if (lastGlobalSendTime != 0L && now - lastGlobalSendTime < GLOBAL_MIN_INTERVAL_MS) {
            Log.w(TAG, "Rate-limit koruması: Mesajlar arası asgari bekleme süresi aşılmadı, atlandı.")
            return false
        }

        lastSentByFingerprint[fingerprint] = now
        lastGlobalSendTime = now

        if (lastSentByFingerprint.size > 50) {
            lastSentByFingerprint.entries.removeIf { now - it.value > DEDUP_COOLDOWN_MS }
        }

        return true
    }

    fun resetDedupState() {
        lastSentByFingerprint.clear()
        lastGlobalSendTime = 0L
    }

    fun sendReportHttp(
        supabaseUrl: String,
        supabaseKey: String,
        payload: JSONObject,
        client: OkHttpClient = httpClient
    ): Boolean {
        if (supabaseUrl.isBlank() || supabaseKey.isBlank()) {
            Log.d(TAG, "Supabase yapılandırması eksik, çökme raporu atlandı.")
            return false
        }
        return try {
            val endpoint = "${supabaseUrl.trimEnd('/')}/functions/v1/crash-report"
            val requestBody = payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
            val request = Request.Builder()
                .url(endpoint)
                .header("apikey", supabaseKey)
                .header("Authorization", "Bearer $supabaseKey")
                .post(requestBody)
                .build()

            client.newCall(request).execute().use { response ->
                response.isSuccessful
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Supabase crash-report gönderilemedi: ${t.message}")
            false
        }
    }

    fun init(context: Context) {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            handleUncaughtException(context, t, e, defaultHandler)
        }
        // Uygulama aniden kapandıysa önceki oturumdan kalan raporu ilet
        checkAndSendPendingReport(context.applicationContext)
    }

    fun recordPendingReport(
        context: Context,
        message: String,
        stackTrace: String,
        threadName: String = Thread.currentThread().name
    ) {
        try {
            val crashFile = File(context.filesDir, "last_crash.txt")
            crashFile.writeText(
                "Thread: $threadName\n" +
                "Device: ${Build.MANUFACTURER} ${Build.MODEL} (SDK ${Build.VERSION.SDK_INT})\n" +
                "Time: ${System.currentTimeMillis()}\n" +
                "Message: $message\n" +
                "Stack:\n$stackTrace"
            )
            val pendingFile = File(context.filesDir, PENDING_CRASH_FILE)
            val json = JSONObject().apply {
                put("threadName", threadName)
                put("message", message)
                put("stackTrace", stackTrace)
                put("timestamp", System.currentTimeMillis())
            }
            pendingFile.writeText(json.toString())
            Log.i(TAG, "Bekleyen çökme/teşhis raporu kaydedildi: $message")
        } catch (t: Throwable) {
            Log.e(TAG, "Bekleyen çökme/teşhis raporu kaydedilemedi: ${t.message}")
        }
    }

    fun sendCrashReport(message: String) {
        val threadName = Thread.currentThread().name
        sendReportAsync(threadName, message, "Manuel test tetiklendi: $message", null)
    }

    fun handleUncaughtException(
        context: Context,
        thread: Thread,
        throwable: Throwable,
        defaultHandler: Thread.UncaughtExceptionHandler? = null
    ) {
        val isMain = Looper.myLooper() == Looper.getMainLooper() || thread == Looper.getMainLooper().thread
        val tName = thread.name
        val isBgTarget = !isMain ||
            tName.contains("PlayPlugin", ignoreCase = true) ||
            tName.contains("PlayCs", ignoreCase = true) ||
            tName.contains("PlayAddon", ignoreCase = true) ||
            tName.contains("OkHttp", ignoreCase = true) ||
            tName.contains("DefaultDispatcher", ignoreCase = true) ||
            tName.contains("ExoPlayer", ignoreCase = true)

        val writer = StringWriter()
        throwable.printStackTrace(PrintWriter(writer))
        val stackTrace = writer.toString()
        val errMsg = throwable.message ?: throwable.javaClass.simpleName

        // 1) Dosyaya yaz (filesDir/last_crash.txt ve pending_crash.json)
        try {
            val crashFile = File(context.filesDir, "last_crash.txt")
            crashFile.writeText(
                "Thread: ${thread.name}\n" +
                "Device: ${Build.MANUFACTURER} ${Build.MODEL} (SDK ${Build.VERSION.SDK_INT})\n" +
                "Time: ${System.currentTimeMillis()}\n" +
                "Message: $errMsg\n" +
                "Stack:\n$stackTrace"
            )
            // Senkron olarak bekleyen rapor dosyasına kaydet (uygulama aniden ölse bile sonraki açılışta gider)
            val pendingFile = File(context.filesDir, PENDING_CRASH_FILE)
            val json = JSONObject().apply {
                put("threadName", thread.name)
                put("message", errMsg)
                put("stackTrace", stackTrace)
                put("timestamp", System.currentTimeMillis())
            }
            pendingFile.writeText(json.toString())
        } catch (t: Throwable) {
            Log.e(TAG, "last_crash / pending_crash yazılamadı: ${t.message}")
        }

        // 2) Çökme bildirimi gönder (arka planda, Supabase Edge Function üzerinden, dedup korumalı)
        sendReportAsync(thread.name, errMsg, stackTrace, context.applicationContext)

        // 3) Dış eklenti ve arka plan coroutine kalkanı:
        val isPluginTrace = stackTrace.contains("com.pltmustafa", ignoreCase = true) ||
            stackTrace.contains("com.comix", ignoreCase = true) ||
            stackTrace.contains("traktsync", ignoreCase = true) ||
            stackTrace.contains("WatchlistSyncService", ignoreCase = true) ||
            stackTrace.contains("com.lagradost.cloudstream3.plugins", ignoreCase = true) ||
            (stackTrace.contains("com.lagradost.", ignoreCase = true) && !stackTrace.contains("com.kaan.play", ignoreCase = true))

        val isLinkageError = throwable is LinkageError ||
            throwable is NoClassDefFoundError ||
            throwable is ClassNotFoundException ||
            throwable is NoSuchMethodError ||
            throwable is NoSuchFieldError

        if (!isMain && (isBgTarget || isPluginTrace || isLinkageError)) {
            Log.e(TAG, "Arka plan eklenti/coroutine çökmesi engellendi [thread=${thread.name}]: $errMsg", throwable)
            return
        }

        if (isMain && (isPluginTrace || isLinkageError)) {
            Log.e(TAG, "Ana iş parçacığında dış eklenti Linkage/NoClassDefFoundError engellendi, UI döngüsü korunuyor: $errMsg", throwable)
            while (true) {
                try {
                    Looper.loop()
                } catch (t: Throwable) {
                    val sWriter = StringWriter()
                    t.printStackTrace(PrintWriter(sWriter))
                    val sTrace = sWriter.toString()
                    val isNestedPlugin = sTrace.contains("com.pltmustafa", ignoreCase = true) ||
                        sTrace.contains("com.comix", ignoreCase = true) ||
                        sTrace.contains("traktsync", ignoreCase = true) ||
                        sTrace.contains("com.lagradost.", ignoreCase = true) ||
                        t is LinkageError || t is NoClassDefFoundError
                    if (isNestedPlugin) {
                        Log.e(TAG, "İç içe eklenti çökmesi engellendi: ${t.message}")
                    } else {
                        defaultHandler?.uncaughtException(Thread.currentThread(), t)
                        break
                    }
                }
            }
            return
        }

        defaultHandler?.uncaughtException(thread, throwable)
    }

    private fun checkAndSendPendingReport(appContext: Context) {
        thread(start = true, name = "CrashReporter-PendingCheck") {
            try {
                val pendingFile = File(appContext.filesDir, PENDING_CRASH_FILE)
                if (!pendingFile.exists()) return@thread
                val content = pendingFile.readText()
                if (content.isBlank()) return@thread
                val json = JSONObject(content)
                val tName = json.optString("threadName", "PendingThread")
                val msg = json.optString("message", "Önceki oturumdan kalan çökme")
                val stack = json.optString("stackTrace", "")
                val ts = json.optLong("timestamp", 0L)
                val ageMs = System.currentTimeMillis() - ts
                if (ageMs > 24 * 60 * 60 * 1000L) { // 24 saatten eskiyse temizle
                    pendingFile.delete()
                    return@thread
                }
                Log.i(TAG, "Önceki oturumdan kalan çökme raporu Supabase'e iletiliyor...")
                sendReportAsync(tName, "⏳ [Açılışta İletildi] $msg", stack, appContext)
            } catch (t: Throwable) {
                Log.w(TAG, "checkAndSendPendingReport hatası: ${t.message}")
            }
        }
    }

    private fun sendReportAsync(
        threadName: String,
        message: String,
        stackTrace: String,
        appContext: Context?
    ) {
        val supabaseUrl = BuildConfig.SUPABASE_URL
        val supabaseKey = BuildConfig.SUPABASE_PUBLISHABLE_KEY
        if (supabaseUrl.isBlank() || supabaseKey.isBlank()) {
            Log.d(TAG, "Supabase bilgileri tanımlı değil, çökme raporu atlanıyor.")
            return
        }

        if (!shouldSendReport(message, stackTrace)) {
            return
        }

        val version = BuildConfig.VERSION_NAME
        val versionCode = BuildConfig.VERSION_CODE
        val device = "${Build.MANUFACTURER} ${Build.MODEL} (Android ${Build.VERSION.RELEASE})"
        val payload = buildReportPayload(version, versionCode, device, threadName, message, stackTrace)

        thread(start = true, name = "CrashReporter-Supabase") {
            try {
                val success = sendReportHttp(supabaseUrl, supabaseKey, payload, httpClient)
                // Başarıyla gönderildiyse yerel bekleyen raporu temizle
                if (success && appContext != null) {
                    runCatching {
                        val pending = File(appContext.filesDir, PENDING_CRASH_FILE)
                        if (pending.exists()) pending.delete()
                    }
                }
            } catch (t: Throwable) {
                Log.w(TAG, "Çökme raporu iletimi başarısız: ${t.message}")
            }
        }
    }

    private fun formatStackTraceForReport(stackTrace: String): String {
        val cleanTrace = stackTrace.replace("```", "'''").trim()
        if (cleanTrace.isBlank()) return "Boş stack trace"
        val lines = cleanTrace.lines().map { it.trimEnd() }.filter { it.isNotEmpty() }
        if (lines.size <= 25) {
            return lines.joinToString("\n").take(2500)
        }

        // Caused by satırlarını ve root cause'u tespit et
        val causedByIndex = lines.indexOfFirst { it.startsWith("Caused by:") || it.contains("Caused by:") }
        val lastCausedByIndex = lines.indexOfLast { it.startsWith("Caused by:") || it.contains("Caused by:") }

        return when {
            // Birden fazla Caused by varsa (derin zincir): ilk satırlar + ilk Caused by özeti + son Caused by (Root cause)
            lastCausedByIndex != -1 && lastCausedByIndex != causedByIndex && lastCausedByIndex > 5 -> {
                val topLines = lines.take(5)
                val firstCaused = lines.subList(causedByIndex, minOf(causedByIndex + 4, lastCausedByIndex))
                val rootCause = lines.drop(lastCausedByIndex).take(14)
                (topLines + listOf("   ...") + firstCaused + listOf("   ...", "   [Root Cause]") + rootCause)
                    .joinToString("\n")
                    .take(2500)
            }
            // Tek bir Caused by varsa
            causedByIndex != -1 && causedByIndex > 5 -> {
                val topLines = lines.take(6)
                val causedLines = lines.drop(causedByIndex).take(16)
                (topLines + listOf("   ...", "   [Caused by]") + causedLines)
                    .joinToString("\n")
                    .take(2500)
            }
            // Caused by yoksa veya en baştaysa
            else -> {
                val top = lines.take(22)
                (top + if (lines.size > 22) listOf("   ...") else emptyList())
                    .joinToString("\n")
                    .take(2500)
            }
        }
    }
}
