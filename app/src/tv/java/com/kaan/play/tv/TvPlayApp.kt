package com.kaan.play.tv

import android.app.Activity
import com.kaan.play.R
import android.os.Bundle
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * TV çeşidinin uygulama sınıfı: Play'in veri katmanını (AppContainer) kurar, eşleşmiş telefondan gelen "TV'de oynat" ve
 * kumanda isteklerini kabul eder. Eşli sunucu uygulamanın herhangi bir ekranı görünürken açıktır (ana ekrandan oynatıcıya
 * geçişte kapanmaz), son ekran kapanınca durur.
 */
class TvPlayApp : com.kaan.play.PlayApp() {
    /** Eşleşmiş telefondaki "TV'de oynat" istekleri TvMainActivity'de oynatıcıda açılır; kumanda/klavye [TvRemote]'ta. */
    override val acceptsRemotePlay: Boolean get() = true

    override val remoteStateSource: (() -> com.kaan.play.data.sync.local.RemoteState)? get() = { TvRemote.state(this) }

    /**
     * Telefonun eklenti numarası bu TV'de başka eklentiye denk gelebilir: önce eklentinin adıyla (yükleme yok), o yoksa alan
     * adıyla (eklentiler yüklenir) bu TV'deki eklentiye çevrilir. Eşleşme yoksa null → telefon doğrudan akış gönderir.
     * Eklenti TV'de kurulu ama kapalıysa yalnız bu izleme için açılır ve ekranda söylenir; oynatıcıdan çıkılınca (sonraki
     * bölümler arasında değil) ya da uygulama yarıda kapandıysa sonraki açılışta yeniden kapatılır ([restoreTempPlugins]).
     * Telefondan gönderilen her zaman açılsın, TV'deki ayar kalıcı değişmesin: kapalı eklentinin kodu çalıştırıcıda hiç
     * çalışmadığından geçici açmadan kullanılamaz.
     */
    override suspend fun resolveRemotePlay(request: com.kaan.play.data.sync.local.RemotePlay): com.kaan.play.data.stream.PlayTarget? {
        val target = request.target
        val id = target.request.pluginId?.takeIf { it.startsWith("cs:") } ?: return target
        val copies = request.pluginName?.let { name -> container.csRepos.plugins.first().filter { it.pluginId == name } }.orEmpty()
        val named = copies.sortedWith(compareBy({ !it.enabled }, { it.id })).firstOrNull()
        android.util.Log.i("PlayTv", "telefondan oynatma: ${request.pluginName} → ${copies.size} kopya " +
            copies.joinToString { "cs:${it.id}/repo${it.repoId}/${if (it.enabled) "açık" else "kapalı"}" })
        if (named != null && !named.enabled) {
            synchronized(this) { tempPrefs.edit().putStringSet(KEY_TEMP, tempIds() + named.id.toString()).commit() }
            container.csRepos.setEnabled(named.id, true)
            android.util.Log.i("PlayTv", "telefondan oynatma: kapalı eklenti bu izleme için açıldı (${named.pluginId})")
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                android.widget.Toast.makeText(this@TvPlayApp, getString(R.string.tv_remote_plugin_enabled_once, named.name), android.widget.Toast.LENGTH_LONG).show()
            }
        }
        val plugin = named?.let { "cs:${it.id}" }
            ?: target.request.contentUrl?.let { TvDetailLoader(container).csProviderOrNull(id, it) }
            ?: return null
        return target.copy(request = target.request.copy(pluginId = plugin))
    }

    private companion object {
        const val KEY_TEMP = "temp_enabled_plugins"
    }

    private var started = 0

    /** Açık oynatıcı ekranları (hazırlık + oynatıcı); sonraki bölümde yenisi eskisi kapanmadan açılır, sayı 0'a düşmez. */
    private var players = 0

    private val tempPrefs by lazy { getSharedPreferences("remote_play", MODE_PRIVATE) }

    private fun tempIds(): Set<String> = tempPrefs.getStringSet(KEY_TEMP, emptySet()).orEmpty()

    /** Telefondan oynatma için geçici açılan eklentiler yeniden kapatılır (izleme bitti ya da önceki süreç yarıda kaldı). */
    private fun restoreTempPlugins() {
        val c = containerOrNull ?: return
        val ids = synchronized(this) { tempIds().also { if (it.isNotEmpty()) tempPrefs.edit().remove(KEY_TEMP).commit() } }
        if (ids.isEmpty()) return
        c.appScope.launch {
            ids.mapNotNull { it.toLongOrNull() }.forEach { c.csRepos.setEnabled(it, false) }
            android.util.Log.i("PlayTv", "telefondan oynatma: geçici açılan ${ids.size} eklenti yeniden kapatıldı")
        }
    }

    private fun isPlayer(activity: Activity) = activity is TvPlayerActivity || activity is TvPlayStartActivity

    override fun onCreate() {
        super.onCreate()
        restoreTempPlugins()
        containerOrNull?.let { c -> TvRemote.start(this, c.localSync.remoteInputs, c.appScope) }
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityStarted(activity: Activity) {
                if (started++ == 0) containerOrNull?.localSync?.startPairedHosting()
            }

            override fun onActivityStopped(activity: Activity) {
                if (--started == 0) containerOrNull?.localSync?.stopPairedHosting()
            }

            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
                if (isPlayer(activity)) players++
            }
            override fun onActivityResumed(activity: Activity) = Unit
            override fun onActivityPaused(activity: Activity) = Unit
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
            override fun onActivityDestroyed(activity: Activity) {
                if (isPlayer(activity) && --players == 0) restoreTempPlugins()
            }
        })
    }
}
