package com.pashe.app.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.pashe.app.Graph

/** Fires at dose time (and for repeats/snoozes) and raises the full-screen alarm notification. */
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val slot = intent.getLongExtra(AlarmScheduler.EXTRA_SLOT, -1L)
        if (slot <= 0) return
        val attempt = intent.getIntExtra(AlarmScheduler.EXTRA_ATTEMPT, 0)
        val store = Graph.store

        val pending = DoseAlarms.pendingAt(store, slot)
        if (pending.isNotEmpty()) {
            Notifications.showAlarm(context, slot, attempt, pending)
            if (attempt < DoseAlarms.MAX_REPEATS) {
                AlarmScheduler.scheduleFollowUp(context, slot, System.currentTimeMillis() + DoseAlarms.REPEAT_MILLIS, attempt + 1)
            }
        } else {
            Notifications.cancelAlarm(context, slot)
        }
        // Keep the 48-hour window of main alarms rolling forward.
        if (!intent.getBooleanExtra(AlarmScheduler.EXTRA_FOLLOW_UP, false)) AlarmScheduler.rescheduleAll(context)
    }
}
