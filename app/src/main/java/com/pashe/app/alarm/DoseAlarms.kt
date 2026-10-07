package com.pashe.app.alarm

import com.pashe.app.data.LocalStore
import com.pashe.app.data.ParentDeviceRepository
import com.pashe.app.domain.DoseRecord
import com.pashe.app.domain.DoseStatus
import com.pashe.app.domain.PlannedDose
import com.pashe.app.domain.ResolvedDose
import com.pashe.app.domain.Schedule

/** Schedule queries over the parent phone's offline copy. */
object DoseAlarms {
    /** Alarms for one time slot: the first ring plus up to this many repeats. */
    const val MAX_REPEATS = 3
    const val REPEAT_MILLIS = 5L * 60 * 1000
    const val SNOOZE_MILLIS = 10L * 60 * 1000

    fun isDone(store: LocalStore, doseId: String): Boolean =
        doseId in store.takenLocally || store.remoteStatuses[doseId] == DoseStatus.TAKEN

    /** Unconfirmed doses that are due at [slot] (several medicines can share one time). */
    fun pendingAt(store: LocalStore, slot: Long): List<PlannedDose> =
        Schedule.dosesBetween(store.medicines, ParentDeviceRepository.zoneFor(store), slot, slot + 1)
            .filterNot { isDone(store, it.doseId) }

    /** Upcoming unconfirmed doses grouped by their exact time. */
    fun upcomingSlots(store: LocalStore, now: Long, horizonMillis: Long): Map<Long, List<PlannedDose>> =
        Schedule.dosesBetween(store.medicines, ParentDeviceRepository.zoneFor(store), now, now + horizonMillis)
            .filterNot { isDone(store, it.doseId) }
            .groupBy { it.scheduledAt }

    fun today(store: LocalStore, now: Long): List<ResolvedDose> {
        val zone = ParentDeviceRepository.zoneFor(store)
        val taken = store.takenLocally
        val remote = store.remoteStatuses
        return Schedule.dosesOn(store.medicines, zone, Schedule.today(zone, now)).map { planned ->
            val record = remote[planned.doseId]?.let {
                DoseRecord(planned.doseId, planned.medicine.id, planned.scheduledAt, it)
            }
            Schedule.resolve(planned, record, planned.doseId in taken, now)
        }
    }
}
