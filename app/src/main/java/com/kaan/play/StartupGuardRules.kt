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
    const val KEY_SAFE_WINDOW = "safe_window"
    const val EXTRA_SAFE_WINDOW = "play_safe_window"

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
        fun getBoolean(key: String, defValue: Boolean = false): Boolean =
            getString(key)?.toBooleanStrictOrNull() ?: defValue
        fun putBoolean(key: String, value: Boolean) {
            putString(key, value.toString())
        }
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

    fun shouldUseSafeTheme(intentFlag: Boolean, storedFlag: Boolean): Boolean =
        intentFlag || storedFlag

    fun isSafeWindow(store: Store): Boolean =
        store.getBoolean(KEY_SAFE_WINDOW, false)

    fun isSafeWindow(context: Context): Boolean =
        isSafeWindow(getStore(context))

    fun setSafeWindow(store: Store, enabled: Boolean) {
        if (enabled) {
            store.putBoolean(KEY_SAFE_WINDOW, true)
        } else {
            store.remove(KEY_SAFE_WINDOW)
        }
    }

    fun setSafeWindow(context: Context, enabled: Boolean) =
        setSafeWindow(getStore(context), enabled)

    fun clearSafeWindow(store: Store) {
        store.remove(KEY_SAFE_WINDOW)
    }

    fun clearSafeWindow(context: Context) =
        clearSafeWindow(getStore(context))

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
            override fun getBoolean(key: String, defValue: Boolean): Boolean =
                prefs.getBoolean(key, defValue)
            override fun putBoolean(key: String, value: Boolean) {
                prefs.edit().putBoolean(key, value).apply()
            }
        }
    }

    fun recordFailureAndDecide(context: Context, activityName: String): Decision =
        recordFailureAndDecide(getStore(context), activityName)

    fun recordSuccess(context: Context, activityName: String) =
        recordSuccess(getStore(context), activityName)
}
