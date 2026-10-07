package com.pashe.app.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.pashe.app.Graph
import com.pashe.app.MainActivity

/**
 * Registers exact alarms with the OS for every upcoming dose slot, from the local schedule only,
 * so reminders fire with no internet. Each slot has one "main" alarm and at most one follow-up
 * (repeat or snooze) alarm, kept under separate request codes.
 */
object AlarmScheduler {
    private const val HORIZON_MILLIS = 48L * 3600 * 1000

    const val EXTRA_SLOT = "slot"
    const val EXTRA_ATTEMPT = "attempt"
    const val EXTRA_FOLLOW_UP = "follow_up"

    @Synchronized
    fun rescheduleAll(context: Context) {
        val store = Graph.store
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        val slots = if (store.parentId == null) emptySet()
        else DoseAlarms.upcomingSlots(store, System.currentTimeMillis(), HORIZON_MILLIS).keys

        (store.scheduledSlots - slots).forEach { alarmManager.cancel(pendingIntent(context, it, 0, followUp = false)) }
        slots.forEach { slot -> setAlarm(context, alarmManager, slot, pendingIntent(context, slot, 0, followUp = false)) }
        store.scheduledSlots = slots
    }

    fun scheduleFollowUp(context: Context, slot: Long, at: Long, attempt: Int) {
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        setAlarm(context, alarmManager, at, pendingIntent(context, slot, attempt, followUp = true))
    }

    fun cancelFollowUp(context: Context, slot: Long) {
        context.getSystemService(AlarmManager::class.java)
            .cancel(pendingIntent(context, slot, 0, followUp = true))
    }

    fun cancelAll(context: Context) {
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        Graph.store.scheduledSlots.forEach { slot ->
            alarmManager.cancel(pendingIntent(context, slot, 0, followUp = false))
            alarmManager.cancel(pendingIntent(context, slot, 0, followUp = true))
        }
        Graph.store.scheduledSlots = emptySet()
    }

    fun canScheduleExact(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()

    private fun setAlarm(context: Context, alarmManager: AlarmManager, at: Long, operation: PendingIntent) {
        if (canScheduleExact(context)) {
            // Alarm-clock alarms are exempt from Doze and fire exactly on time.
            val show = PendingIntent.getActivity(
                context, 0, Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            alarmManager.setAlarmClock(AlarmManager.AlarmClockInfo(at, show), operation)
        } else {
            // Without the exact-alarm permission this may be delayed by a few minutes, but still rings.
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, operation)
        }
    }

    private fun requestCode(slot: Long, followUp: Boolean): Int {
        val minute = (slot / 60_000).toInt()
        return if (followUp) -minute else minute
    }

    private fun pendingIntent(context: Context, slot: Long, attempt: Int, followUp: Boolean): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java)
            .setAction(if (followUp) "com.pashe.app.FOLLOW_UP" else "com.pashe.app.DOSE_ALARM")
            .putExtra(EXTRA_SLOT, slot)
            .putExtra(EXTRA_ATTEMPT, attempt)
            .putExtra(EXTRA_FOLLOW_UP, followUp)
        return PendingIntent.getBroadcast(
            context, requestCode(slot, followUp), intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }
}
