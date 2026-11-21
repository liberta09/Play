package com.kaan.play.data.cloudstream

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.util.Log
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner

/** A plugin callback never owns an Activity; the current UI context is passed only on click. */
class CsLoadedPlugin(
    settings: ((Context) -> Unit)? = null,
    unload: () -> Unit = {},
) {
    private val lock = Any()
    private var settingsAction = settings
    private var unloadAction: (() -> Unit)? = unload

    fun openSettings(context: Context): Boolean = synchronized(lock) {
        val action = settingsAction ?: return@synchronized false
        action(context)
        true
    }

    fun unload() {
        val action = synchronized(lock) {
            settingsAction = null
            val previous = unloadAction
            unloadAction = null
            previous
        }
        try {
            action?.invoke()
        } catch (error: Throwable) {
            Log.w("PlayCs", "Plugin unload failed: ${error.javaClass.simpleName}")
        }
    }
}

sealed interface CsPluginSettingsResult {
    data object Opened : CsPluginSettingsResult
    data object NotSupported : CsPluginSettingsResult
    data object Disabled : CsPluginSettingsResult
    data object Unavailable : CsPluginSettingsResult
    data class Failed(val causeClass: String) : CsPluginSettingsResult
}

internal fun settingsActivity(context: Context): Activity? {
    var current = context
    repeat(16) {
        if (current is Activity) {
            return (current as Activity).takeUnless {
                it.isFinishing || it.isDestroyed ||
                    (it is LifecycleOwner && !it.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))
            }
        }
        val wrapper = current as? ContextWrapper ?: return null
        val next = wrapper.baseContext
        if (next === current) return null
        current = next
    }
    return null
}

internal data class CsRuntimeIdentity(
    val rowId: Long,
    val repoId: Long,
    val pluginId: String,
    val version: String?,
    val artifactHash: String,
    val dexPath: String,
    val dexSize: Long,
    val dexModified: Long,
    val dexFileKey: String?,
    val manifest: String?,
)

/** Access is serialized by CsRepository; invalidation unloads only the retained plugin. */
internal class CsPluginRuntimeCache {
    private data class Entry(val identity: CsRuntimeIdentity, val result: CsApiResult.Success)
    private val entries = mutableMapOf<Long, Entry>()

    fun get(identity: CsRuntimeIdentity): CsApiResult.Success? {
        val entry = entries[identity.rowId] ?: return null
        if (entry.identity != identity) {
            invalidate(identity.rowId)
            return null
        }
        return entry.result
    }

    fun put(identity: CsRuntimeIdentity, result: CsApiResult.Success) {
        invalidate(identity.rowId)
        entries[identity.rowId] = Entry(identity, result)
    }

    fun invalidate(rowId: Long) {
        entries.remove(rowId)?.result?.plugin?.unload()
    }

    fun contains(rowId: Long): Boolean = entries.containsKey(rowId)

    /** Satır hâlâ [result] ise bırakır (bu arada yeniden yüklenmiş daha yeni örneğe dokunmaz). */
    fun releaseIfSame(rowId: Long, result: CsApiResult.Success) {
        if (entries[rowId]?.result === result) invalidate(rowId)
    }

    fun invalidateUnless(keep: (CsRuntimeIdentity) -> Boolean) {
        entries.values.filterNot { keep(it.identity) }.map { it.identity.rowId }.forEach(::invalidate)
    }

    fun clear() = invalidateUnless { false }
}
