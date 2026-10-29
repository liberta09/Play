package com.lagradost.cloudstream3

import android.content.Context
import android.content.SharedPreferences

/**
 * CloudStream DataStore. Eklentiler kalıcı ayarlarını kaydetmek için bu sınıfı kullanabilir.
 * Play bu depoyu paylaşımlı tercihler (SharedPreferences) üzerinde çalıştırır.
 */
object DataStore {
    private var prefs: SharedPreferences? = null

    internal fun init(context: Context, name: String = "cs_plugin_data") {
        prefs = context.getSharedPreferences(name, Context.MODE_PRIVATE)
    }

    fun getFolderName(folder: String, path: String): String = "$folder/$path"

    @JvmStatic
    fun <T> setKey(key: String, value: T) {
        val p = prefs?.edit() ?: return
        when (value) {
            is String -> p.putString(key, value)
            is Int -> p.putInt(key, value)
            is Long -> p.putLong(key, value)
            is Float -> p.putFloat(key, value)
            is Boolean -> p.putBoolean(key, value)
            else -> p.putString(key, value.toString())
        }.apply()
    }

    @Suppress("UNCHECKED_CAST")
    @JvmStatic
    fun <T> getKey(key: String, defVal: T? = null): T? {
        val p = prefs ?: return defVal
        return when (defVal) {
            is String -> p.getString(key, defVal as? String) as T?
            is Int -> p.getInt(key, defVal as? Int ?: 0) as T?
            is Long -> p.getLong(key, defVal as? Long ?: 0L) as T?
            is Float -> p.getFloat(key, defVal as? Float ?: 0f) as T?
            is Boolean -> p.getBoolean(key, defVal as? Boolean ?: false) as T?
            null -> p.getString(key, null) as T?
            else -> defVal
        }
    }

    @JvmStatic
    fun removeKey(key: String) {
        prefs?.edit()?.remove(key)?.apply()
    }

    @JvmStatic
    fun containsKey(key: String): Boolean = prefs?.contains(key) ?: false

    fun <T> Context.setKey(path: String, value: T) = DataStore.setKey(path, value)
    fun <T> Context.getKey(path: String, defVal: T? = null): T? = DataStore.getKey(path, defVal)
    fun Context.removeKey(path: String) = DataStore.removeKey(path)
}
