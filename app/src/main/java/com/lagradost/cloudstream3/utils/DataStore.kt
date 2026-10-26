@file:Suppress("DEPRECATION")

package com.lagradost.cloudstream3.utils

import android.content.Context
import android.content.SharedPreferences
import android.preference.PreferenceManager
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.lagradost.cloudstream3.AcraApplication
import com.lagradost.cloudstream3.CloudStreamApp
import java.lang.ref.WeakReference

const val DOWNLOAD_HEADER_CACHE = "download_header_cache"
const val DOWNLOAD_HEADER_CACHE_BACKUP = "BACKUP_download_header_cache"
const val DOWNLOAD_EPISODE_CACHE = "download_episode_cache"
const val DOWNLOAD_EPISODE_CACHE_BACKUP = "BACKUP_download_episode_cache"
const val VIDEO_PLAYER_BRIGHTNESS = "video_player_alpha_key"
const val USER_SELECTED_HOMEPAGE_API = "home_api_used"
const val USER_PROVIDER_API = "user_custom_sites"
const val PREFERENCES_NAME = "rebuild_preference"

data class Editor(
    val editor: SharedPreferences.Editor
) {
    fun <T> setKeyRaw(path: String, value: T) {
        try {
            when (value) {
                null -> editor.remove(path)
                is Boolean -> editor.putBoolean(path, value)
                is Int -> editor.putInt(path, value)
                is Long -> editor.putLong(path, value)
                is Float -> editor.putFloat(path, value)
                is String -> editor.putString(path, value)
                is Set<*> -> {
                    @Suppress("UNCHECKED_CAST")
                    editor.putStringSet(path, value.filterIsInstance<String>().toSet())
                }
                else -> {
                    try {
                        editor.putString(path, DataStore.mapper.writeValueAsString(value))
                    } catch (_: Throwable) {
                        editor.putString(path, value.toString())
                    }
                }
            }
        } catch (_: Throwable) {}
    }

    fun apply() {
        try { editor.apply() } catch (_: Throwable) {}
    }
}

/**
 * CloudStream DataStore uyumluluk uygulaması.
 * Eklentiler (TraktSync, Comix, WatchParty vb.) ayarlarını, durumlarını ve token'larını
 * bu sınıf üzerinden saklar ve okur.
 */
object DataStore {
    private var appContextRef: WeakReference<Context>? = null

    val mapper: ObjectMapper by lazy {
        try {
            jacksonObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        } catch (_: Throwable) {
            ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        }
    }

    @JvmStatic
    fun init(context: Context, name: String = PREFERENCES_NAME) {
        appContextRef = WeakReference(context.applicationContext)
    }

    private fun resolveContext(context: Context?): Context? {
        return context?.applicationContext
            ?: appContextRef?.get()
            ?: AcraApplication.context
            ?: CloudStreamApp.context
            ?: runCatching { com.kaan.play.PlayApp.appContext }.getOrNull()
    }

    @JvmStatic
    fun getPreferences(context: Context? = null): SharedPreferences? {
        val ctx = resolveContext(context) ?: return null
        return ctx.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    }

    fun Context.getSharedPrefs(): SharedPreferences {
        return getPreferences(this) ?: this.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    }

    @JvmStatic
    fun getSharedPrefs(): SharedPreferences? {
        return getPreferences(null)
    }

    fun Context.getDefaultSharedPrefs(): SharedPreferences {
        return PreferenceManager.getDefaultSharedPreferences(this)
    }

    @JvmStatic
    fun getDefaultSharedPrefs(): SharedPreferences? {
        val ctx = resolveContext(null) ?: return null
        return PreferenceManager.getDefaultSharedPreferences(ctx)
    }

    @JvmStatic
    fun editor(context: Context? = null, isEditingAppSettings: Boolean = false): Editor? {
        val ctx = resolveContext(context) ?: return null
        val edit = if (isEditingAppSettings) {
            PreferenceManager.getDefaultSharedPreferences(ctx).edit()
        } else {
            ctx.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE).edit()
        }
        return Editor(edit)
    }

    @JvmStatic
    fun getFolderName(folder: String, path: String): String = "$folder/$path"

    @JvmStatic
    fun <T> setKey(key: String, value: T) {
        try {
            val prefs = getPreferences() ?: return
            val p = prefs.edit()
            when (value) {
                null -> p.remove(key)
                is String -> p.putString(key, value)
                is Int -> p.putInt(key, value)
                is Long -> p.putLong(key, value)
                is Float -> p.putFloat(key, value)
                is Boolean -> p.putBoolean(key, value)
                is Set<*> -> {
                    @Suppress("UNCHECKED_CAST")
                    p.putStringSet(key, value.filterIsInstance<String>().toSet())
                }
                else -> {
                    try {
                        p.putString(key, mapper.writeValueAsString(value))
                    } catch (_: Throwable) {
                        p.putString(key, value.toString())
                    }
                }
            }
            p.apply()
        } catch (_: Throwable) {}
    }

    @JvmStatic
    fun <T> setKey(folder: String, path: String, value: T) {
        setKey(getFolderName(folder, path), value)
    }

    @Suppress("UNCHECKED_CAST")
    @JvmStatic
    fun <T> getKey(key: String, defVal: T? = null): T? {
        val prefs = getPreferences() ?: return defVal
        if (!prefs.contains(key)) return defVal
        return try {
            when (defVal) {
                is String -> prefs.getString(key, defVal) as T?
                is Int -> prefs.getInt(key, defVal) as T?
                is Long -> prefs.getLong(key, defVal) as T?
                is Float -> prefs.getFloat(key, defVal) as T?
                is Boolean -> prefs.getBoolean(key, defVal) as T?
                null -> prefs.getString(key, null) as T?
                else -> {
                    val str = prefs.getString(key, null) ?: return defVal
                    try {
                        mapper.readValue(str, (defVal as Any)::class.java) as T?
                    } catch (_: Throwable) {
                        defVal
                    }
                }
            }
        } catch (_: Throwable) {
            defVal
        }
    }

    @JvmStatic
    fun <T> getKey(folder: String, path: String, defVal: T? = null): T? {
        return getKey(getFolderName(folder, path), defVal)
    }

    @JvmStatic
    fun <T : Any> getKey(path: String, valueType: Class<T>): T? {
        val prefs = getPreferences() ?: return null
        val str = prefs.getString(path, null) ?: return null
        return try {
            mapper.readValue(str, valueType)
        } catch (_: Throwable) {
            null
        }
    }

    @JvmStatic
    fun removeKey(key: String) {
        try {
            getPreferences()?.edit()?.remove(key)?.apply()
        } catch (_: Throwable) {}
    }

    @JvmStatic
    fun removeKey(folder: String, path: String) {
        removeKey(getFolderName(folder, path))
    }

    @JvmStatic
    fun removeKeys(folder: String): Int {
        return try {
            val prefs = getPreferences() ?: return 0
            val fixedFolder = folder.trimEnd('/') + "/"
            val keysToRemove = prefs.all.keys.filter { it.startsWith(fixedFolder) }
            val edit = prefs.edit()
            keysToRemove.forEach { edit.remove(it) }
            edit.apply()
            keysToRemove.size
        } catch (_: Throwable) { 0 }
    }

    @JvmStatic
    fun containsKey(key: String): Boolean = try { getPreferences()?.contains(key) ?: false } catch (_: Throwable) { false }

    @JvmStatic
    fun containsKey(folder: String, path: String): Boolean = containsKey(getFolderName(folder, path))

    @JvmStatic
    fun getKeys(folder: String): List<String> {
        return try {
            val prefs = getPreferences() ?: return emptyList()
            val fixedFolder = folder.trimEnd('/') + "/"
            prefs.all.keys.filter { it.startsWith(fixedFolder) }
        } catch (_: Throwable) { emptyList() }
    }

    fun Context.getKeys(folder: String): List<String> = DataStore.getKeys(folder)
    fun Context.containsKey(path: String): Boolean = DataStore.containsKey(path)
    fun Context.containsKey(folder: String, path: String): Boolean = DataStore.containsKey(folder, path)
    fun Context.removeKey(path: String) = DataStore.removeKey(path)
    fun Context.removeKey(folder: String, path: String) = DataStore.removeKey(folder, path)
    fun Context.removeKeys(folder: String): Int = DataStore.removeKeys(folder)
    fun <T> Context.setKey(path: String, value: T) = DataStore.setKey(path, value)
    fun <T> Context.setKey(folder: String, path: String, value: T) = DataStore.setKey(folder, path, value)
    fun <T> Context.getKey(path: String, defVal: T? = null): T? = DataStore.getKey(path, defVal)
    fun <T> Context.getKey(folder: String, path: String, defVal: T? = null): T? = DataStore.getKey(folder, path, defVal)
    fun <T : Any> Context.getKey(path: String, valueType: Class<T>): T? = DataStore.getKey(path, valueType)
}
