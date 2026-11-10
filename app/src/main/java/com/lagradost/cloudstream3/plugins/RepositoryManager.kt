package com.lagradost.cloudstream3.plugins

import android.os.Looper
import android.util.Log
import com.kaan.play.BuildConfig
import com.kaan.play.PlayApp
import com.kaan.play.data.cloudstream.CsRepoManifestParser
import com.kaan.play.data.cloudstream.PlayNetworkGuard
import com.kaan.play.data.db.AddonType
import com.lagradost.cloudstream3.CommonActivity
import com.lagradost.cloudstream3.ui.settings.extensions.RepositoryData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.net.URL

object RepositoryManager {

    fun getRepositories(): Array<RepositoryData> {
        if (BuildConfig.DEBUG) {
            Log.d("PlayCs", "RepositoryManager.getRepositories cagirildi")
        }
        val context = CommonActivity.context ?: PlayApp.appContext
        val app = (context.applicationContext as? PlayApp) ?: run {
            Log.w("PlayCs", "getRepositories: app null")
            return emptyArray()
        }
        val dao = app.container.database.addonDao()
        val list = try {
            if (Looper.myLooper() == Looper.getMainLooper()) {
                runBlocking(Dispatchers.IO) {
                    dao.observeOfType(AddonType.CLOUDSTREAM_REPO).first()
                }
            } else {
                runBlocking {
                    dao.observeOfType(AddonType.CLOUDSTREAM_REPO).first()
                }
            }
        } catch (e: Throwable) {
            Log.w("PlayCs", "getRepositories hatasi: ${e.javaClass.simpleName}")
            emptyList()
        }
        if (BuildConfig.DEBUG) {
            Log.d("PlayCs", "getRepositories donuyor: ${list.size} repo")
        }
        return list.map {
            RepositoryData(iconUrl = it.logoUrl, name = it.name, url = it.sourceUrl)
        }.toTypedArray()
    }

    suspend fun parseRepository(url: String): Repository? {
        if (BuildConfig.DEBUG) {
            Log.d("PlayCs", "RepositoryManager.parseRepository cagirildi")
        }
        return try {
            val lower = url.lowercase().trim()
            if (!lower.startsWith("http://") && !lower.startsWith("https://")) {
                Log.w("PlayCs", "parseRepository: gecersiz sema")
                return null
            }
            val parsedUrl = URL(url)
            val host = parsedUrl.host ?: return null
            val port = if (parsedUrl.port != -1) parsedUrl.port else parsedUrl.defaultPort
            if (PlayNetworkGuard.isBlockedHost(host, port)) {
                Log.w("PlayCs", "parseRepository: PlayNetworkGuard engelledi")
                return null
            }

            val context = CommonActivity.context ?: PlayApp.appContext
            val app = (context.applicationContext as? PlayApp) ?: run {
                Log.w("PlayCs", "parseRepository: app null")
                return null
            }
            val raw = app.container.manifestFetcher.fetch(url)
            val root = CsRepoManifestParser.parseRoot(raw)
            if (BuildConfig.DEBUG) {
                Log.d("PlayCs", "parseRepository basarili")
            }
            Repository(
                name = root.name,
                url = url,
                description = root.description,
                manifestVersion = 1,
                pluginLists = root.pluginLists,
                iconUrl = null,
            )
        } catch (t: Throwable) {
            Log.w("PlayCs", "parseRepository hatasi: ${t.javaClass.simpleName}")
            null
        }
    }

    suspend fun addRepository(data: RepositoryData): Boolean {
        if (BuildConfig.DEBUG) {
            Log.d("PlayCs", "RepositoryManager.addRepository cagirildi")
        }
        val context = CommonActivity.context ?: PlayApp.appContext
        val app = (context.applicationContext as? PlayApp) ?: run {
            Log.w("PlayCs", "addRepository: app null")
            return false
        }
        app.container.pluginRepoRequests.enqueue(data.name, data.url)
        return true
    }
}
