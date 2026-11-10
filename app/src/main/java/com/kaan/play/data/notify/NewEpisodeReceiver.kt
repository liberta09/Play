package com.kaan.play.data.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.kaan.play.PlayApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Zamanlanmış yeni bölüm denetimi ve cihaz açılışında alarm yenileme alıcısı (`exported="false"`). */
class NewEpisodeReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context?, intent: Intent?) {
        val appContext = context?.applicationContext ?: return
        val action = intent?.action ?: return
        val app = appContext as? PlayApp ?: return

        when (action) {
            Intent.ACTION_BOOT_COMPLETED -> {
                val pending = goAsync()
                app.container.appScope.launch(Dispatchers.IO) {
                    try {
                        if (app.container.settings.newEpisodeNotifications.first()) {
                            NewEpisodeScheduler.schedule(appContext)
                        } else {
                            NewEpisodeScheduler.cancel(appContext)
                        }
                    } finally {
                        pending.finish()
                    }
                }
            }
            NewEpisodeScheduler.ACTION_CHECK_NEW_EPISODES -> {
                val pending = goAsync()
                app.container.appScope.launch(Dispatchers.IO) {
                    try {
                        NewEpisodeChecker.run(appContext, app.container)
                    } finally {
                        pending.finish()
                    }
                }
            }
        }
    }
}
