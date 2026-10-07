package com.pashe.app.alarm

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.pashe.app.MainActivity
import com.pashe.app.R
import com.pashe.app.domain.BnFormat
import com.pashe.app.domain.MealTiming
import com.pashe.app.domain.PlannedDose

object Notifications {
    // Channel sound/vibration can't be changed after creation; bump the id to change them.
    const val CHANNEL_ALARMS = "dose_alarms_v1"
    const val CHANNEL_MISSED = "missed_doses"

    private val VIBRATION = longArrayOf(0, 800, 400, 800, 400, 800)

    fun createChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        val alarmSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
        val alarms = NotificationChannel(CHANNEL_ALARMS, context.getString(R.string.channel_alarms), NotificationManager.IMPORTANCE_HIGH).apply {
            description = context.getString(R.string.channel_alarms_desc)
            setSound(alarmSound, AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
            enableVibration(true)
            vibrationPattern = VIBRATION
            lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
        }
        val missed = NotificationChannel(CHANNEL_MISSED, context.getString(R.string.channel_missed), NotificationManager.IMPORTANCE_HIGH).apply {
            description = context.getString(R.string.channel_missed_desc)
        }
        manager.createNotificationChannels(listOf(alarms, missed))
    }

    private fun alarmId(slot: Long) = (slot / 60_000).toInt()

    fun mealText(context: Context, timing: MealTiming) =
        context.getString(if (timing == MealTiming.BEFORE) R.string.meal_before else R.string.meal_after)

    @SuppressLint("MissingPermission") // Checked via areNotificationsEnabled().
    fun showAlarm(context: Context, slot: Long, attempt: Int, doses: List<PlannedDose>) {
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return

        val fullScreen = PendingIntent.getActivity(
            context, alarmId(slot), AlarmActivity.intent(context, slot, attempt),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val lines = doses.map { "${it.medicine.name} — ${it.medicine.dose}, ${mealText(context, it.medicine.mealTiming)}" }
        val title = "${context.getString(R.string.alarm_title)} · ${BnFormat.time(doses.first().localTime, true)}"

        val notification = NotificationCompat.Builder(context, CHANNEL_ALARMS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(lines.joinToString("\n"))
            .setStyle(NotificationCompat.BigTextStyle().bigText(lines.joinToString("\n")))
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setFullScreenIntent(fullScreen, true)
            .setContentIntent(fullScreen)
            .setOngoing(true)
            .setAutoCancel(false)
            // Stop ringing before the next 5-minute repeat takes over.
            .setTimeoutAfter(DoseAlarms.REPEAT_MILLIS - 30_000)
            .addAction(0, context.getString(R.string.taken_btn), actionIntent(context, DoseActionReceiver.ACTION_CONFIRM, slot, attempt))
            .addAction(0, context.getString(R.string.snooze_btn), actionIntent(context, DoseActionReceiver.ACTION_SNOOZE, slot, attempt))
            .build()
        // Loop the alarm sound until the notification is answered or times out.
        notification.flags = notification.flags or android.app.Notification.FLAG_INSISTENT

        manager.cancel(alarmId(slot)) // Re-posting alone would not re-alert.
        manager.notify(alarmId(slot), notification)
    }

    fun cancelAlarm(context: Context, slot: Long) = NotificationManagerCompat.from(context).cancel(alarmId(slot))

    @SuppressLint("MissingPermission")
    fun showMissed(context: Context, title: String, body: String, parentId: String?) {
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return
        val open = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java).putExtra(MainActivity.EXTRA_PARENT_ID, parentId),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_MISSED)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        manager.notify((System.currentTimeMillis() % Int.MAX_VALUE).toInt(), notification)
    }

    fun canUseFullScreen(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE ||
            context.getSystemService(NotificationManager::class.java).canUseFullScreenIntent()

    private fun actionIntent(context: Context, action: String, slot: Long, attempt: Int): PendingIntent {
        val intent = Intent(context, DoseActionReceiver::class.java).setAction(action)
            .putExtra(AlarmScheduler.EXTRA_SLOT, slot)
            .putExtra(AlarmScheduler.EXTRA_ATTEMPT, attempt)
        return PendingIntent.getBroadcast(
            context, alarmId(slot), intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }
}
