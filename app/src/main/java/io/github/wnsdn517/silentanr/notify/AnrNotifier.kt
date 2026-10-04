package io.github.wnsdn517.silentanr.notify

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import io.github.wnsdn517.silentanr.R
import io.github.wnsdn517.silentanr.common.Contract
import io.github.wnsdn517.silentanr.data.AnrRecord
import io.github.wnsdn517.silentanr.receiver.NotificationActionReceiver
import io.github.wnsdn517.silentanr.ui.MainActivity
import io.github.wnsdn517.silentanr.util.AppInfoCache

object AnrNotifier {
    private const val CHANNEL_ANR = "anr"
    private const val CHANNEL_AUTO = "anr_auto"
    private const val GROUP = "io.github.wnsdn517.silentanr.ANR"

    fun createChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannels(
            listOf(
                NotificationChannel(CHANNEL_ANR, context.getString(R.string.channel_anr), NotificationManager.IMPORTANCE_HIGH)
                    .apply { description = context.getString(R.string.channel_anr_desc) },
                NotificationChannel(CHANNEL_AUTO, context.getString(R.string.channel_auto), NotificationManager.IMPORTANCE_LOW)
                    .apply { description = context.getString(R.string.channel_auto_desc) },
            )
        )
    }

    fun canNotify(context: Context) =
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED &&
            NotificationManagerCompat.from(context).areNotificationsEnabled()

    /** One notification per package, updated in place when the same app ANRs again. */
    fun show(context: Context, record: AnrRecord, recentCount: Int) {
        if (!canNotify(context)) return
        val label = AppInfoCache.label(context, record.packageName)
        val notificationId = (record.packageName + "#" + record.userId).hashCode()
        val (channel, titleRes) = when (record.action) {
            Contract.ACT_AUTO_WAIT -> CHANNEL_AUTO to R.string.notif_title_wait
            Contract.ACT_AUTO_KILL -> CHANNEL_AUTO to R.string.notif_title_kill
            else -> CHANNEL_ANR to R.string.notif_title_notified
        }
        val text = buildString {
            append(record.reason ?: record.processName ?: record.packageName)
            if (recentCount > 1) append(" · ").append(context.getString(R.string.notif_repeat, recentCount))
        }

        val open = PendingIntent.getActivity(
            context, notificationId,
            Intent(context, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_RECORD_ID, record.id)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val builder = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_stat_anr)
            .setContentTitle(context.getString(titleRes, label))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setCategory(NotificationCompat.CATEGORY_ERROR)
            .setWhen(record.timestamp)
            .setShowWhen(true)
            .setAutoCancel(true)
            .setOnlyAlertOnce(false)
            .setGroup(GROUP)
            .setContentIntent(open)
            .setPriority(if (channel == CHANNEL_ANR) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_LOW)

        if (record.action == Contract.ACT_NOTIFIED || record.action == Contract.ACT_AUTO_WAIT) {
            builder.addAction(0, context.getString(R.string.notif_action_close),
                action(context, NotificationActionReceiver.ACTION_CLOSE, record, notificationId))
        }
        if (record.action == Contract.ACT_NOTIFIED) {
            builder.addAction(0, context.getString(R.string.notif_action_wait),
                action(context, NotificationActionReceiver.ACTION_WAIT, record, notificationId))
            builder.addAction(0, context.getString(R.string.notif_action_always_wait),
                action(context, NotificationActionReceiver.ACTION_ALWAYS_WAIT, record, notificationId))
        }

        try {
            NotificationManagerCompat.from(context).notify(notificationId, builder.build())
        } catch (e: SecurityException) {
            // Permission revoked between check and post.
        }
    }

    private fun action(context: Context, action: String, record: AnrRecord, notificationId: Int): PendingIntent {
        val intent = Intent(context, NotificationActionReceiver::class.java)
            .setAction(action)
            .putExtra(NotificationActionReceiver.EXTRA_RECORD_ID, record.id)
            .putExtra(NotificationActionReceiver.EXTRA_NOTIFICATION_ID, notificationId)
            .putExtra(Contract.EXTRA_PACKAGE, record.packageName)
            .putExtra(Contract.EXTRA_PID, record.pid)
            .putExtra(Contract.EXTRA_USER_ID, record.userId)
        return PendingIntent.getBroadcast(
            context, (action + notificationId).hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
