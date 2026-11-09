package com.lagradost.cloudstream3.plugins

import android.os.Looper
import android.util.Log
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
        Log.i("PlayCs", "RepositoryManager.getRepositories() cagirildi")
        val context = CommonActivity.context ?: PlayApp.appContext
        val app = (context.applicationContext as? PlayApp) ?: run {
            Log.w("PlayCs", "getRepositories: app null!")
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
            Log.w("PlayCs", "getRepositories hatasi: ${e.message}", e)
            emptyList()
        }
        Log.i("PlayCs", "getRepositories donuyor: ${list.size} repo")
        return list.map {
            RepositoryData(iconUrl = it.logoUrl, name = it.name, url = it.sourceUrl)
        }.toTypedArray()
    }

    suspend fun parseRepository(url: String): Repository? {
        Log.i("PlayCs", "RepositoryManager.parseRepository cagirildi: $url")
        return try {
            val lower = url.lowercase().trim()
            if (!lower.startsWith("http://") && !lower.startsWith("https://")) {
                Log.w("PlayCs", "parseRepository: http(s) degil: $url")
                return null
            }
            val parsedUrl = URL(url)
            val host = parsedUrl.host ?: return null
            val port = if (parsedUrl.port != -1) parsedUrl.port else parsedUrl.defaultPort
            if (PlayNetworkGuard.isBlockedHost(host, port)) {
                Log.w("PlayCs", "parseRepository: PlayNetworkGuard engelledi: $host:$port")
                return null
            }

            val context = CommonActivity.context ?: PlayApp.appContext
            val app = (context.applicationContext as? PlayApp) ?: run {
                Log.w("PlayCs", "parseRepository: app null!")
                return null
            }
            val raw = app.container.manifestFetcher.fetch(url)
            val root = CsRepoManifestParser.parseRoot(raw)
            Log.i("PlayCs", "parseRepository basarili: ${root.name} ($url)")
            Repository(
                name = root.name,
                url = url,
                description = root.description,
                manifestVersion = 1,
                pluginLists = root.pluginLists,
                iconUrl = null,
            )
        } catch (t: Throwable) {
            Log.w("PlayCs", "parseRepository hatasi [$url]: ${t.message}", t)
            null
        }
    }

    suspend fun addRepository(data: RepositoryData): Boolean {
        Log.i("PlayCs", "RepositoryManager.addRepository cagirildi: name='${data.name}' url='${data.url}'")
        val context = CommonActivity.context ?: PlayApp.appContext
        val app = (context.applicationContext as? PlayApp) ?: run {
            Log.w("PlayCs", "addRepository: app null!")
            return false
        }
        app.container.pluginRepoRequests.enqueue(data.name, data.url)
        return true
    }
}
