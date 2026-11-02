package com.kaan.play

import android.content.Context
import android.content.SharedPreferences

/**
 * Android'den bağımsız açılış kalkanı kuralları (birim testli).
 *
 * Son 10 dakikada etkinlik adı başına başarısız açılış denemelerini takip eder:
 * - İlk başarısızlıkta: [Decision.RETRY] ("yeniden dene")
 * - İkinci veya daha fazla başarısızlıkta: [Decision.GIVE_UP] ("vazgeç")
 */
object StartupGuardRules {

    const val PREFS_NAME = "play_startup_guard"
    const val WINDOW_MS = 10 * 60 * 1000L // 10 dakika

    enum class Decision {
        RETRY,
        GIVE_UP
    }

    /**
     * Testlerde Android bağımlılığı olmadan çalışabilmesi için soyut anahtar-değer arayüzü.
     */
    interface Store {
        fun getString(key: String): String?
        fun putString(key: String, value: String)
        fun remove(key: String)
    }

    fun parseTimestamps(raw: String?): List<Long> {
        if (raw.isNullOrBlank()) return emptyList()
        return raw.split(",").mapNotNull { it.trim().toLongOrNull() }
    }

    fun serializeTimestamps(timestamps: List<Long>): String =
        timestamps.joinToString(",")

    /**
     * Geçmiş başarısızlık zaman damgalarını [windowMs] penceresine göre süzer,
     * geçerli [nowMs] damgasını ekler ve karar ile güncel listeyi döndürür.
     */
    fun evaluate(
        pastTimestamps: List<Long>,
        nowMs: Long = System.currentTimeMillis(),
        windowMs: Long = WINDOW_MS
    ): Pair<Decision, List<Long>> {
        val active = pastTimestamps.filter { nowMs - it in 0..windowMs }
        val updated = active + nowMs
        val decision = if (updated.size <= 1) Decision.RETRY else Decision.GIVE_UP
        return decision to updated
    }

    fun keyFor(activityName: String): String = "fail_history_$activityName"

    /**
     * Son [windowMs] süresi içindeki başarısızlık sayısını döndürür.
     */
    fun getRecentFailureCount(
        store: Store,
        activityName: String,
        nowMs: Long = System.currentTimeMillis(),
        windowMs: Long = WINDOW_MS
    ): Int {
        val raw = store.getString(keyFor(activityName))
        val past = parseTimestamps(raw)
        return past.count { nowMs - it in 0..windowMs }
    }

    /**
     * Başarısızlığı kaydeder ve kararı üretir.
     */
    fun recordFailureAndDecide(
        store: Store,
        activityName: String,
        nowMs: Long = System.currentTimeMillis(),
        windowMs: Long = WINDOW_MS
    ): Decision {
        val key = keyFor(activityName)
        val raw = store.getString(key)
        val past = parseTimestamps(raw)
        val (decision, updated) = evaluate(past, nowMs, windowMs)
        store.putString(key, serializeTimestamps(updated))
        return decision
    }

    /**
     * Başarılı açılışta veya sıfırlamada etkinlik geçmişini temizler.
     */
    fun recordSuccess(store: Store, activityName: String) {
        store.remove(keyFor(activityName))
    }

    // Android Context / SharedPreferences bağlayıcıları
    fun getStore(context: Context): Store {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return object : Store {
            override fun getString(key: String): String? = prefs.getString(key, null)
            override fun putString(key: String, value: String) {
                prefs.edit().putString(key, value).apply()
            }
            override fun remove(key: String) {
                prefs.edit().remove(key).apply()
            }
        }
    }

    fun recordFailureAndDecide(context: Context, activityName: String): Decision =
        recordFailureAndDecide(getStore(context), activityName)

    fun recordSuccess(context: Context, activityName: String) =
        recordSuccess(getStore(context), activityName)
}
