package com.lagradost.cloudstream3

import android.app.Application
import android.content.Context
import com.lagradost.api.setContext
import java.lang.ref.WeakReference

open class AcraApplication : Application() {
    companion object {
        private var _context: WeakReference<Context>? = null
        @JvmStatic
        var context: Context?
            get() = _context?.get()
            set(value) {
                _context = value?.let { WeakReference(it) }
                try {
                    setContext(value)
                } catch (_: Throwable) {}
            }
    }
}

object CloudStreamApp {
    private var _context: WeakReference<Context>? = null
    @JvmStatic
    var context: Context?
        get() = _context?.get()
        set(value) {
            _context = value?.let { WeakReference(it) }
            try {
                setContext(value)
            } catch (_: Throwable) {}
        }

    fun <T> setKey(path: String, value: T) = DataStore.setKey(path, value)
    fun <T> setKey(folder: String, path: String, value: T) = DataStore.setKey(DataStore.getFolderName(folder, path), value)
    fun <T> getKey(path: String, defVal: T? = null): T? = DataStore.getKey(path, defVal)
    fun <T> getKey(folder: String, path: String, defVal: T? = null): T? = DataStore.getKey(DataStore.getFolderName(folder, path), defVal)
    fun removeKey(path: String) = DataStore.removeKey(path)
    fun removeKey(folder: String, path: String) = DataStore.removeKey(DataStore.getFolderName(folder, path))
}
