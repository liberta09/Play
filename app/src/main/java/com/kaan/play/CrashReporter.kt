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
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

object CrashReporter {
    const val TAG = "CrashReporter"
    const val TELEGRAM_BOT_TOKEN = "8722594690:AAHiX-jUeeCjPUxl5hVRXOaotFKPLqhVRx4"

    private val httpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .writeTimeout(5, TimeUnit.SECONDS)
            .build()
    }

    fun init(context: Context) {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            handleUncaughtException(context, t, e, defaultHandler)
        }
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

        // 1) Dosyaya yaz (filesDir/last_crash.txt)
        try {
            val crashFile = File(context.filesDir, "last_crash.txt")
            crashFile.writeText(
                "Thread: ${thread.name}\n" +
                "Device: ${Build.MANUFACTURER} ${Build.MODEL} (SDK ${Build.VERSION.SDK_INT})\n" +
                "Time: ${System.currentTimeMillis()}\n" +
                "Message: ${throwable.message}\n" +
                "Stack:\n$stackTrace"
            )
        } catch (t: Throwable) {
            Log.e(TAG, "last_crash.txt yazılamadı: ${t.message}")
        }

        // 2) Telegram bildirimi gönder (arka planda, getUpdates ile chat.id çekerek)
        sendTelegramReportAsync(thread.name, throwable.message ?: "Unknown", stackTrace)

        if (!isMain && isBgTarget) {
            Log.e(TAG, "Arka plan çökmesi engellendi [thread=${thread.name}]: ${throwable.message}", throwable)
        } else {
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }

    private fun sendTelegramReportAsync(threadName: String, message: String, stackTrace: String) {
        thread(start = true, name = "CrashReporter-Telegram") {
            try {
                // Dinamik getUpdates ile ilk chat.id bul
                val getUpdatesUrl = "https://api.telegram.org/bot$TELEGRAM_BOT_TOKEN/getUpdates"
                val getReq = Request.Builder().url(getUpdatesUrl).build()
                val getResp = httpClient.newCall(getReq).execute()
                val bodyStr = getResp.body.string()
                val json = JSONObject(bodyStr)
                if (!json.optBoolean("ok", false)) return@thread
                val results = json.optJSONArray("result") ?: return@thread
                if (results.length() == 0) return@thread

                var chatId: Long? = null
                for (i in results.length() - 1 downTo 0) {
                    val item = results.optJSONObject(i) ?: continue
                    val msg = item.optJSONObject("message") ?: item.optJSONObject("channel_post")
                    val chat = msg?.optJSONObject("chat")
                    if (chat != null && chat.has("id")) {
                        chatId = chat.getLong("id")
                        break
                    }
                }

                if (chatId == null) return@thread

                val text = buildString {
                    append("🚨 *Play TV Box Çökme Raporu*\n")
                    append("📦 Sürüm: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})\n")
                    append("📱 Cihaz: ${Build.MANUFACTURER} ${Build.MODEL}\n")
                    append("• *Thread:* `").append(threadName).append("`\n")
                    append("• *Cihaz Detay:* ").append(Build.MANUFACTURER).append(" ").append(Build.MODEL).append(" (Android ").append(Build.VERSION.RELEASE).append(")\n")
                    append("• *Hata:* ").append(message.take(150)).append("\n\n")
                    append("```\n").append(stackTrace.take(800)).append("\n```")
                }

                val postJson = JSONObject().apply {
                    put("chat_id", chatId)
                    put("text", text)
                    put("parse_mode", "Markdown")
                }
                val postReq = Request.Builder()
                    .url("https://api.telegram.org/bot$TELEGRAM_BOT_TOKEN/sendMessage")
                    .post(postJson.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
                    .build()
                httpClient.newCall(postReq).execute().close()
            } catch (t: Throwable) {
                Log.w(TAG, "Telegram bildirim gönderilemedi: ${t.message}")
            }
        }
    }
}
