package com.kaan.play.data.notify

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.SystemClock

/** Ayar açıkken 12 saatte bir (ilk gecikme 2 dk) yeni bölüm denetimi kuran zamanlayıcı. */
object NewEpisodeScheduler {
    const val ACTION_CHECK_NEW_EPISODES = "com.kaan.play.action.CHECK_NEW_EPISODES"
    private const val REQUEST_CODE = 4101

    private fun pendingIntent(context: Context, create: Boolean): PendingIntent? {
        val flags = if (create) {
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        } else {
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_NO_CREATE
        }
        val intent = Intent(context, NewEpisodeReceiver::class.java).setAction(ACTION_CHECK_NEW_EPISODES)
        return PendingIntent.getBroadcast(context, REQUEST_CODE, intent, flags)
    }

    fun schedule(context: Context) {
        val appContext = context.applicationContext
        val alarm = appContext.getSystemService(AlarmManager::class.java) ?: return
        val pending = pendingIntent(appContext, create = true) ?: return
        runCatching {
            alarm.setInexactRepeating(
                AlarmManager.ELAPSED_REALTIME_WAKEUP,
                SystemClock.elapsedRealtime() + NewEpisodeRules.INITIAL_DELAY_MS,
                NewEpisodeRules.REPEAT_INTERVAL_MS,
                pending,
            )
        }
    }

    fun cancel(context: Context) {
        val appContext = context.applicationContext
        val alarm = appContext.getSystemService(AlarmManager::class.java) ?: return
        val existing = pendingIntent(appContext, create = false) ?: return
        runCatching {
            alarm.cancel(existing)
            existing.cancel()
        }
    }
}
