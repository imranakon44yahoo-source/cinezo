package com.pashe.app.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.pashe.app.Graph
import com.pashe.app.domain.PlannedDose

/** What happens when the parent taps "খেয়েছি" or "১০ মিনিট পরে". */
object DoseActions {
    fun confirm(context: Context, doses: List<PlannedDose>) {
        if (doses.isEmpty()) return
        val now = System.currentTimeMillis()
        Graph.store.markTaken(doses.map { it.doseId }, now)
        val parentId = Graph.store.parentId
        if (parentId != null) doses.forEach { ConfirmDoseWorker.enqueue(context, parentId, it, now) }
        doses.map { it.scheduledAt }.distinct().forEach { slot ->
            if (DoseAlarms.pendingAt(Graph.store, slot).isEmpty()) {
                AlarmScheduler.cancelFollowUp(context, slot)
                Notifications.cancelAlarm(context, slot)
            }
        }
    }

    fun confirmSlot(context: Context, slot: Long) = confirm(context, DoseAlarms.pendingAt(Graph.store, slot))

    /** Silences the alarm now and rings again in 10 minutes, replacing any pending 5-minute repeat. */
    fun snooze(context: Context, slot: Long, attempt: Int) {
        Notifications.cancelAlarm(context, slot)
        AlarmScheduler.scheduleFollowUp(context, slot, System.currentTimeMillis() + DoseAlarms.SNOOZE_MILLIS, attempt)
    }
}

/** Handles the action buttons on the alarm notification. */
class DoseActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val slot = intent.getLongExtra(AlarmScheduler.EXTRA_SLOT, -1L)
        if (slot <= 0) return
        when (intent.action) {
            ACTION_CONFIRM -> DoseActions.confirmSlot(context, slot)
            ACTION_SNOOZE -> DoseActions.snooze(context, slot, intent.getIntExtra(AlarmScheduler.EXTRA_ATTEMPT, 0))
        }
    }

    companion object {
        const val ACTION_CONFIRM = "com.pashe.app.CONFIRM"
        const val ACTION_SNOOZE = "com.pashe.app.SNOOZE"
    }
}
