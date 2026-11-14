package com.kaan.play.data.cloudstream

import android.content.Context
import android.util.Log
import com.kaan.play.data.net.PlayJson
import com.lagradost.cloudstream3.APIHolder
import com.lagradost.cloudstream3.CommonActivity
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.plugins.BasePlugin
import com.lagradost.cloudstream3.plugins.Plugin
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.extractorApis
import dalvik.system.PathClassLoader
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.io.File

internal fun resolveMainClassName(manifestJson: String?, fallbackInternalName: String): String {
    if (manifestJson.isNullOrBlank()) return fallbackInternalName
    return try {
        val obj = PlayJson.decodeFromString<JsonElement>(manifestJson) as? JsonObject
        (obj?.get("pluginClassName") as? JsonPrimitive)?.content?.trim()?.ifEmpty { null }
            ?: fallbackInternalName
    } catch (_: Exception) {
        fallbackInternalName
    }
}

interface PluginDexLoader {
    suspend fun loadPlugin(dexFile: File, className: String, pluginId: String = ""): CsApiResult
}

class CsDexLoader(context: Context) : PluginDexLoader {

    private val context = context.applicationContext ?: context

    private val loadLock = Mutex()

    override suspend fun loadPlugin(dexFile: File, className: String, pluginId: String): CsApiResult = loadLock.withLock {
        val isTrakt = pluginId.contains("traktsync", ignoreCase = true) ||
            className.contains("traktsync", ignoreCase = true) ||
            className.contains("com.pltmustafa", ignoreCase = true) ||
            className.contains("TraktAuthManager", ignoreCase = true) ||
            className.contains("WatchlistSyncService", ignoreCase = true)
        if (isTrakt) {
            Log.w(TAG, "TraktSync eklentisi kara listede (engellendi): $pluginId ($className)")
            runCatching { dexFile.delete() }
            return@withLock CsApiResult.LoadFailed("TraktSyncDisabled", pluginId)
        }

        if (!dexFile.exists()) {
            val failure = CsApiResult.DexFileMissing(pluginId)
            Log.w(TAG, formatCsLog(failure))
            return@withLock failure
        }

        val cl = try {
            PathClassLoader(dexFile.absolutePath, context.classLoader)
        } catch (t: Throwable) {
            val failure = classifyDexError(t, pluginId)
            Log.w(TAG, formatCsLog(failure))
            return@withLock failure
        }

        val before = APIHolder.allProviders.toList()
        var plugin: BasePlugin? = null
        fun ownedApis(): List<MainAPI> = APIHolder.allProviders.filter { api -> before.none { it === api } }
        // Eklentinin kaydettiği çıkarıcılar (kendi sınıf yükleyicisinden): bırakılınca bunlar da listeden çıkar; yoksa genel liste
        // sınıf yükleyiciyi, dolayısıyla eklentinin bütün kodunu bellekte tutar (CloudStream'in eklenti kaldırması da böyle yapar).
        fun ownedExtractors(): List<ExtractorApi> = extractorApis.filter { it.javaClass.classLoader === cl }
        fun unloadOwned() {
            val owned = ownedApis()
            val extractors = ownedExtractors()
            try {
                plugin?.beforeUnload()
            } finally {
                APIHolder.allProviders.removeAll { api -> owned.any { it === api } }
                if (extractors.isNotEmpty()) extractorApis.removeAll(extractors)
            }
        }
        return@withLock try {
            val clazz = cl.loadClass(className)
            CommonActivity.context = context
            val apis = when {
                BasePlugin::class.java.isAssignableFrom(clazz) -> {
                    val instance = clazz.getDeclaredConstructor().newInstance() as BasePlugin
                    plugin = instance
                    if (instance is Plugin) {
                        try {
                            instance.load(context)
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (_: NoSuchMethodError) {
                            try {
                                instance.load()
                            } catch (cancelled: CancellationException) {
                                throw cancelled
                            } catch (error: Throwable) {
                                Log.w(TAG, "plugin.load() failed: ${error.javaClass.simpleName}")
                            }
                        } catch (error: Throwable) {
                            Log.w(TAG, "plugin.load(context) failed: ${error.javaClass.simpleName}")
                            try {
                                instance.load()
                            } catch (cancelled: CancellationException) {
                                throw cancelled
                            } catch (fallback: Throwable) {
                                Log.w(TAG, "plugin.load() fallback failed: ${fallback.javaClass.simpleName}")
                            }
                        }
                    } else {
                        try {
                            instance.load()
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (error: Throwable) {
                            Log.w(TAG, "plugin.load() failed: ${error.javaClass.simpleName}")
                        }
                    }
                    ownedApis().distinctBy { it.name }
                }
                MainAPI::class.java.isAssignableFrom(clazz) -> {
                    val api = clazz.getDeclaredConstructor().newInstance() as MainAPI
                    APIHolder.allProviders.add(api)
                    listOf(api)
                }
                else -> {
                    val failure = CsApiResult.LoadFailed("UnknownPluginType", pluginId)
                    Log.w(TAG, formatCsLog(failure))
                    return@withLock failure
                }
            }
            if (apis.isEmpty()) {
                val failure = CsApiResult.NoApiRegistered(pluginId)
                CsLoadedPlugin(unload = ::unloadOwned).unload()
                Log.w(TAG, formatCsLog(failure))
                failure
            } else {
                // Capture exactly this load's registrations, never a later plugin's global entries.
                val owned = ownedApis()
                val extractors = ownedExtractors()
                val instance = plugin
                val handle = CsLoadedPlugin(settings = (instance as? Plugin)?.openSettings) {
                    try {
                        instance?.beforeUnload()
                    } finally {
                        APIHolder.allProviders.removeAll { api -> owned.any { it === api } }
                        if (extractors.isNotEmpty()) extractorApis.removeAll(extractors)
                    }
                }
                Log.d(TAG, "$pluginId: Success (${apis.size} MainAPI)")
                CsApiResult.Success(apis, handle)
            }
        } catch (cancelled: CancellationException) {
            CsLoadedPlugin(unload = ::unloadOwned).unload()
            throw cancelled
        } catch (error: Throwable) {
            CsLoadedPlugin(unload = ::unloadOwned).unload()
            val failure = classifyDexError(error, pluginId)
            Log.w(TAG, formatCsLog(failure))
            failure
        }
    }

    companion object {
        private const val TAG = "PlayCs"
    }
}
