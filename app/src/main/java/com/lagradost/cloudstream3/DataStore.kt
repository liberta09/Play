package com.lagradost.cloudstream3

import android.content.Context
import android.content.SharedPreferences

/**
 * CloudStream DataStore root package köprüsü.
 * Eklentiler hem `com.lagradost.cloudstream3.DataStore` hem de `com.lagradost.cloudstream3.utils.DataStore`
 * çağrıları yapabildiğinden her iki paket üzerinden de tam uyumluluk sağlanır.
 */
object DataStore {

    @JvmStatic
    fun init(context: Context, name: String = "cs_plugin_data") {
        com.lagradost.cloudstream3.utils.DataStore.init(context, name)
    }

    @JvmStatic
    fun getPreferences(context: Context? = null): SharedPreferences? =
        com.lagradost.cloudstream3.utils.DataStore.getPreferences(context)

    @JvmStatic
    fun getFolderName(folder: String, path: String): String =
        com.lagradost.cloudstream3.utils.DataStore.getFolderName(folder, path)

    @JvmStatic
    fun <T> setKey(key: String, value: T) {
        com.lagradost.cloudstream3.utils.DataStore.setKey(key, value)
    }

    @JvmStatic
    fun <T> setKey(folder: String, path: String, value: T) {
        com.lagradost.cloudstream3.utils.DataStore.setKey(folder, path, value)
    }

    @JvmStatic
    fun <T> getKey(key: String, defVal: T? = null): T? {
        return com.lagradost.cloudstream3.utils.DataStore.getKey(key, defVal)
    }

    @JvmStatic
    fun <T> getKey(folder: String, path: String, defVal: T? = null): T? {
        return com.lagradost.cloudstream3.utils.DataStore.getKey(folder, path, defVal)
    }

    @JvmStatic
    fun <T : Any> getKey(path: String, valueType: Class<T>): T? {
        return com.lagradost.cloudstream3.utils.DataStore.getKey(path, valueType)
    }

    @JvmStatic
    fun removeKey(key: String) {
        com.lagradost.cloudstream3.utils.DataStore.removeKey(key)
    }

    @JvmStatic
    fun removeKey(folder: String, path: String) {
        com.lagradost.cloudstream3.utils.DataStore.removeKey(folder, path)
    }

    @JvmStatic
    fun containsKey(key: String): Boolean =
        com.lagradost.cloudstream3.utils.DataStore.containsKey(key)

    @JvmStatic
    fun containsKey(folder: String, path: String): Boolean =
        com.lagradost.cloudstream3.utils.DataStore.containsKey(folder, path)

    fun <T> Context.setKey(path: String, value: T) =
        com.lagradost.cloudstream3.utils.DataStore.run { setKey(path, value) }

    fun <T> Context.getKey(path: String, defVal: T? = null): T? =
        com.lagradost.cloudstream3.utils.DataStore.run { getKey(path, defVal) }

    fun Context.removeKey(path: String) =
        com.lagradost.cloudstream3.utils.DataStore.run { removeKey(path) }
}
