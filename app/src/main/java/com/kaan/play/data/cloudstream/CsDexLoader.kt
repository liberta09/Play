package com.kaan.play.data.cloudstream

import android.content.Context
import android.util.Log
import com.kaan.play.data.net.PlayJson
import com.lagradost.cloudstream3.APIHolder
import com.lagradost.cloudstream3.CommonActivity
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.plugins.BasePlugin
import com.lagradost.cloudstream3.plugins.Plugin
import dalvik.system.PathClassLoader
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

class CsDexLoader(private val context: Context) : PluginDexLoader {

    private val loadLock = Mutex()

    override suspend fun loadPlugin(dexFile: File, className: String, pluginId: String): CsApiResult = loadLock.withLock {
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

        return@withLock try {
            val beforeCount = APIHolder.allProviders.size
            val clazz = cl.loadClass(className)
            CommonActivity.context = context

            val apis = when {
                BasePlugin::class.java.isAssignableFrom(clazz) -> {
                    val plugin = clazz.getDeclaredConstructor().newInstance() as BasePlugin
                    if (plugin is Plugin) {
                        try {
                            plugin.load(context)
                        } catch (_: NoSuchMethodError) {
                            try {
                                plugin.load()
                            } catch (t: Throwable) {
                                Log.w(TAG, "plugin.load() hatasi [${pluginId}]: ${t.javaClass.simpleName}: ${t.message}")
                            }
                        } catch (t: Throwable) {
                            Log.w(TAG, "plugin.load(context) hatasi [${pluginId}]: ${t.javaClass.simpleName}: ${t.message}")
                            try {
                                plugin.load()
                            } catch (t2: Throwable) {
                                Log.w(TAG, "plugin.load() fallback hatasi [${pluginId}]: ${t2.javaClass.simpleName}: ${t2.message}")
                            }
                        }
                    } else {
                        try {
                            plugin.load()
                        } catch (t: Throwable) {
                            Log.w(TAG, "plugin.load() hatasi [${pluginId}]: ${t.javaClass.simpleName}: ${t.message}")
                        }
                    }
                    APIHolder.allProviders.drop(beforeCount).distinctBy { it.name }
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
                Log.w(TAG, formatCsLog(failure))
                failure
            } else {
                Log.d(TAG, "$pluginId: Success (${apis.size} MainAPI)")
                CsApiResult.Success(apis)
            }
        } catch (t: Throwable) {
            val failure = classifyDexError(t, pluginId)
            Log.w(TAG, formatCsLog(failure))
            failure
        }
    }

    companion object {
        private const val TAG = "PlayCs"
    }
}
