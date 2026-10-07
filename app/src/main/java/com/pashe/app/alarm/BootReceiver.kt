package com.pashe.app.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.pashe.app.Graph
import com.pashe.app.domain.AppMode

/** Alarms are wiped on reboot, app update and clock changes; re-arm them from the local schedule. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val store = Graph.store
        if (store.appMode != AppMode.PARENT || store.parentId == null) return
        // Slots registered before the reboot no longer exist in AlarmManager.
        if (intent.action != Intent.ACTION_TIME_CHANGED && intent.action != Intent.ACTION_TIMEZONE_CHANGED) {
            store.scheduledSlots = emptySet()
        }
        AlarmScheduler.rescheduleAll(context)
        SyncWorker.schedulePeriodic(context)
        SyncWorker.runNow(context)
    }
}
