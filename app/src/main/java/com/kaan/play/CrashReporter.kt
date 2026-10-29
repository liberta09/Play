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
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .writeTimeout(5, TimeUnit.SECONDS)
            .build()
    }

    // Deduplication ve Rate Limit (Spam Koruması)
    private val lastSentByFingerprint = ConcurrentHashMap<String, Long>()
    @Volatile private var lastGlobalSendTime = 0L
    private const val DEDUP_COOLDOWN_MS = 60_000L // Aynı hata 60 sn içinde tekrar gönderilmez
    private const val GLOBAL_MIN_INTERVAL_MS = 3_000L // İki mesaj arasında en az 3 sn beklenir
    private const val PENDING_CRASH_FILE = "pending_crash.json"

    fun init(context: Context) {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            handleUncaughtException(context, t, e, defaultHandler)
        }
        // Uygulama aniden kapandıysa önceki oturumdan kalan raporu ilet
        checkAndSendPendingReport(context.applicationContext)
    }

    fun sendCrashReport(message: String) {
        val threadName = Thread.currentThread().name
        sendTelegramReportAsync(threadName, message, "Manuel test tetiklendi: $message", null)
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

        // 2) Telegram bildirimi gönder (arka planda, BuildConfig üzerinden, dedup korumalı)
        sendTelegramReportAsync(thread.name, errMsg, stackTrace, context.applicationContext)

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
                Log.i(TAG, "Önceki oturumdan kalan çökme raporu Telegram'a iletiliyor...")
                sendTelegramReportAsync(tName, "⏳ [Açılışta İletildi] $msg", stack, appContext)
            } catch (t: Throwable) {
                Log.w(TAG, "checkAndSendPendingReport hatası: ${t.message}")
            }
        }
    }

    private fun sendTelegramReportAsync(
        threadName: String,
        message: String,
        stackTrace: String,
        appContext: Context?
    ) {
        val botToken = BuildConfig.TELEGRAM_BOT_TOKEN
        val targetChatId = BuildConfig.TELEGRAM_CHAT_ID
        if (botToken.isBlank()) {
            Log.d(TAG, "TELEGRAM_BOT_TOKEN tanımlı değil, Telegram bildirimi atlanıyor.")
            return
        }

        val now = System.currentTimeMillis()
        val stackSummary = stackTrace.lines().filter { it.isNotBlank() }.take(4).joinToString(";")
        val fingerprint = "${message.take(120)}|$stackSummary"

        val lastSent = lastSentByFingerprint[fingerprint] ?: 0L
        if (now - lastSent < DEDUP_COOLDOWN_MS) {
            Log.w(TAG, "Spam koruması: Bu hata son 60 sn içinde bildirildi, tekrar gönderilmiyor: ${message.take(80)}")
            return
        }

        if (now - lastGlobalSendTime < GLOBAL_MIN_INTERVAL_MS) {
            Log.w(TAG, "Rate-limit koruması: Mesajlar arası asgari bekleme süresi aşılmadı, atlandı.")
            return
        }

        lastSentByFingerprint[fingerprint] = now
        lastGlobalSendTime = now

        if (lastSentByFingerprint.size > 50) {
            lastSentByFingerprint.entries.removeIf { now - it.value > DEDUP_COOLDOWN_MS }
        }

        thread(start = true, name = "CrashReporter-Telegram") {
            try {
                val chatId = if (targetChatId.isNotBlank()) {
                    targetChatId
                } else {
                    // Dinamik getUpdates ile ilk chat.id bul
                    val getUpdatesUrl = "https://api.telegram.org/bot$botToken/getUpdates"
                    val getReq = Request.Builder().url(getUpdatesUrl).build()
                    val getResp = httpClient.newCall(getReq).execute()
                    val bodyStr = getResp.body.string()
                    val json = JSONObject(bodyStr)
                    if (!json.optBoolean("ok", false)) return@thread
                    val results = json.optJSONArray("result") ?: return@thread
                    if (results.length() == 0) return@thread

                    var foundId: String? = null
                    for (i in results.length() - 1 downTo 0) {
                        val item = results.optJSONObject(i) ?: continue
                        val msg = item.optJSONObject("message") ?: item.optJSONObject("channel_post")
                        val chat = msg?.optJSONObject("chat")
                        if (chat != null && chat.has("id")) {
                            foundId = chat.get("id").toString()
                            break
                        }
                    }
                    foundId ?: return@thread
                }

                val text = buildString {
                    append("🚨 *Play TV Box Çökme Raporu*\n")
                    append("📦 *Sürüm:* ").append(BuildConfig.VERSION_NAME).append(" (").append(BuildConfig.VERSION_CODE).append(")\n")
                    append("📱 *Cihaz:* ").append(Build.MANUFACTURER).append(" ").append(Build.MODEL).append(" (Android ").append(Build.VERSION.RELEASE).append(")\n")
                    append("🧵 *Thread:* `").append(threadName).append("`\n")
                    append("⚠️ *Hata:* `").append(message.take(150).replace("`", "'")).append("`\n\n")
                    append("```\n").append(formatStackTraceForReport(stackTrace)).append("\n```")
                }

                val postJson = JSONObject().apply {
                    put("chat_id", chatId)
                    put("text", text)
                    put("parse_mode", "Markdown")
                }
                val postReq = Request.Builder()
                    .url("https://api.telegram.org/bot$botToken/sendMessage")
                    .post(postJson.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
                    .build()
                val resp = httpClient.newCall(postReq).execute()
                val success = resp.isSuccessful
                resp.close()

                // Başarıyla gönderildiyse yerel bekleyen raporu temizle
                if (success && appContext != null) {
                    runCatching {
                        val pending = File(appContext.filesDir, PENDING_CRASH_FILE)
                        if (pending.exists()) pending.delete()
                    }
                }
            } catch (t: Throwable) {
                Log.w(TAG, "Telegram bildirim gönderilemedi: ${t.message}")
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