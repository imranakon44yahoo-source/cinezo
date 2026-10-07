package com.pashe.app.ui.child

import com.pashe.app.Graph
import com.pashe.app.domain.DoseRecord
import com.pashe.app.domain.Medicine
import com.pashe.app.domain.ParentProfile
import com.pashe.app.domain.PlannedDose
import com.pashe.app.domain.ResolvedDose
import com.pashe.app.domain.Schedule
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import java.time.Instant
import java.time.LocalDate

/** Emits the current time every minute so "pending" turns into "missed" on screen. */
fun minuteTicker(): Flow<Long> = flow {
    while (true) {
        emit(System.currentTimeMillis())
        delay(60_000)
    }
}

data class ParentDay(val parent: ParentProfile, val doses: List<ResolvedDose>)

private const val DAY = 24L * 3600 * 1000

/** Today's doses for one parent, computed in the parent's timezone and merged with server status. */
fun parentToday(parent: ParentProfile): Flow<ParentDay> {
    val now = System.currentTimeMillis()
    return combine(
        Graph.childRepo.medicines(parent.id),
        Graph.childRepo.doses(parent.id, now - 2 * DAY, now + 2 * DAY),
        minuteTicker(),
    ) { medicines, records, tick ->
        val zone = Schedule.zoneOf(parent.timezone)
        ParentDay(parent, resolveDay(medicines, records, zone, Schedule.today(zone, tick), tick))
    }
}

/**
 * Doses planned for [date] plus any recorded doses of known medicines that no longer match the
 * current schedule (e.g. a time was changed), so history is not lost.
 */
fun resolveDay(
    medicines: List<Medicine>, records: List<DoseRecord>, zone: java.time.ZoneId, date: LocalDate, now: Long,
): List<ResolvedDose> {
    val byId = records.associateBy { it.id }
    val planned = Schedule.dosesOn(medicines, zone, date)
    val plannedIds = planned.map { it.doseId }.toSet()
    val medicinesById = medicines.associateBy { it.id }
    val extra = records.filter { it.id !in plannedIds }.mapNotNull { record ->
        val medicine = medicinesById[record.medId] ?: return@mapNotNull null
        val at = Instant.ofEpochMilli(record.scheduledAt).atZone(zone)
        if (at.toLocalDate() != date) return@mapNotNull null
        PlannedDose(record.id, medicine, record.scheduledAt, date, at.toLocalTime())
    }
    return (planned + extra).sortedBy { it.scheduledAt }
        .map { Schedule.resolve(it, byId[it.doseId], takenLocally = false, nowMillis = now) }
}
