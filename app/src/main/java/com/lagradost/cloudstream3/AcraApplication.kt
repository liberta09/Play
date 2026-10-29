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

open class CloudStreamApp : AcraApplication() {
    companion object {
        private var _context: WeakReference<Context>? = null

        @JvmStatic
        var context: Context?
            get() = _context?.get() ?: AcraApplication.context
            set(value) {
                _context = value?.let { WeakReference(it) }
                AcraApplication.context = value
                try {
                    setContext(value)
                } catch (_: Throwable) {}
            }

        @JvmStatic
        var cfUserAgent: String? = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

        @JvmStatic
        fun <T> setKey(path: String, value: T) = DataStore.setKey(path, value)

        @JvmStatic
        fun <T> setKey(folder: String, path: String, value: T) = DataStore.setKey(DataStore.getFolderName(folder, path), value)

        @JvmStatic
        fun <T> getKey(path: String, defVal: T? = null): T? = DataStore.getKey(path, defVal)

        @JvmStatic
        fun <T> getKey(folder: String, path: String, defVal: T? = null): T? = DataStore.getKey(DataStore.getFolderName(folder, path), defVal)

        @JvmStatic
        fun removeKey(path: String) = DataStore.removeKey(path)

        @JvmStatic
        fun removeKey(folder: String, path: String) = DataStore.removeKey(DataStore.getFolderName(folder, path))
    }
}
