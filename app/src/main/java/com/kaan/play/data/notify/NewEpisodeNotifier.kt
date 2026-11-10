package com.kaan.play.data.notify

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.kaan.play.MainActivity
import com.kaan.play.R

/** Favori diziler için yeni bölüm bildirim kanalı ve bildirim oluşturucu. */
object NewEpisodeNotifier {
    const val CHANNEL_ID = "new_episodes"
    const val ACTION_OPEN_SERIES = "com.kaan.play.action.OPEN_NEW_EPISODE_SERIES"
    const val EXTRA_TMDB_ID = "tmdb_id"

    private const val NOTIFICATION_ID_BASE = 30_000

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.notify_channel_new_episodes),
                NotificationManager.IMPORTANCE_DEFAULT,
            ),
        )
    }

    fun canPostNotifications(context: Context): Boolean {
        val granted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        return granted && NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    fun notify(
        context: Context,
        tmdbId: Int,
        seriesTitle: String,
        season: Int,
        episode: Int,
    ) {
        if (tmdbId <= 0 || season <= 0 || episode <= 0) return
        ensureChannel(context)
        if (!canPostNotifications(context)) return

        val intent = Intent(context, MainActivity::class.java)
            .setAction(ACTION_OPEN_SERIES)
            .putExtra(EXTRA_TMDB_ID, tmdbId)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pending = PendingIntent.getActivity(
            context,
            tmdbId,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_play)
            .setContentTitle(context.getString(R.string.notify_new_episode_title, seriesTitle))
            .setContentText(context.getString(R.string.notify_new_episode_body, season, episode))
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()

        runCatching {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID_BASE + tmdbId, notification)
        }
    }
}
