package com.pashe.app.domain

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/** One planned intake of one medicine. */
data class PlannedDose(
    val doseId: String,
    val medicine: Medicine,
    val scheduledAt: Long,
    val localDate: LocalDate,
    val localTime: LocalTime,
)

data class ResolvedDose(
    val planned: PlannedDose,
    val status: DoseStatus,
    val confirmedAt: Long? = null,
)

/**
 * Turns medicine schedules into concrete doses. The dose id format and the time math must stay in
 * sync with functions/src/schedule.ts, because the phone and the server both create dose documents.
 */
object Schedule {
    /** A dose still unconfirmed this long after its time counts as missed. */
    const val MISSED_AFTER_MILLIS = 30L * 60 * 1000

    private val TIME = DateTimeFormatter.ofPattern("HH:mm")
    private val ID_DATE = DateTimeFormatter.BASIC_ISO_DATE

    fun parseTime(value: String): LocalTime? = runCatching { LocalTime.parse(value, TIME) }.getOrNull()

    fun formatTime(value: LocalTime): String = value.format(TIME)

    fun zoneOf(timezone: String): ZoneId = runCatching { ZoneId.of(timezone) }
        .getOrDefault(ZoneId.of(ParentProfile.DEFAULT_TIMEZONE))

    fun doseId(medId: String, date: LocalDate, time: LocalTime): String =
        "${medId}_${date.format(ID_DATE)}${"%02d%02d".format(time.hour, time.minute)}"

    fun isActiveOn(medicine: Medicine, date: LocalDate): Boolean {
        if (!medicine.active) return false
        val start = runCatching { LocalDate.parse(medicine.startDate) }.getOrNull() ?: return false
        if (date.isBefore(start)) return false
        val end = medicine.endDate?.takeIf { it.isNotBlank() }?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        return end == null || !date.isAfter(end)
    }

    fun dosesOn(medicines: List<Medicine>, zone: ZoneId, date: LocalDate): List<PlannedDose> =
        medicines.filter { isActiveOn(it, date) }.flatMap { med ->
            med.times.mapNotNull(::parseTime).distinct().map { time ->
                PlannedDose(
                    doseId = doseId(med.id, date, time),
                    medicine = med,
                    scheduledAt = ZonedDateTime.of(date, time, zone).toInstant().toEpochMilli(),
                    localDate = date,
                    localTime = time,
                )
            }
        }.sortedWith(compareBy({ it.scheduledAt }, { it.medicine.name }))

    /** All doses with fromMillis <= scheduledAt < toMillis. */
    fun dosesBetween(medicines: List<Medicine>, zone: ZoneId, fromMillis: Long, toMillis: Long): List<PlannedDose> {
        var day = Instant.ofEpochMilli(fromMillis).atZone(zone).toLocalDate()
        val lastDay = Instant.ofEpochMilli(toMillis).atZone(zone).toLocalDate()
        val result = mutableListOf<PlannedDose>()
        while (!day.isAfter(lastDay)) {
            result += dosesOn(medicines, zone, day).filter { it.scheduledAt in fromMillis until toMillis }
            day = day.plusDays(1)
        }
        return result
    }

    fun today(zone: ZoneId, nowMillis: Long): LocalDate = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()

    fun startOfDay(zone: ZoneId, date: LocalDate): Long = date.atStartOfDay(zone).toInstant().toEpochMilli()

    /**
     * The status shown in the app. A local confirmation or a server "taken" wins; a dose the server
     * has not marked yet is shown as missed once the 30 minute grace period is over.
     */
    fun resolve(planned: PlannedDose, record: DoseRecord?, takenLocally: Boolean, nowMillis: Long): ResolvedDose {
        val status = when {
            takenLocally || record?.status == DoseStatus.TAKEN -> DoseStatus.TAKEN
            record?.status == DoseStatus.MISSED -> DoseStatus.MISSED
            nowMillis >= planned.scheduledAt + MISSED_AFTER_MILLIS -> DoseStatus.MISSED
            else -> DoseStatus.PENDING
        }
        return ResolvedDose(planned, status, record?.confirmedAt)
    }

    /** Percentage of decided doses (taken or missed) that were taken; null when none are decided. */
    fun adherencePercent(doses: List<ResolvedDose>): Int? {
        val decided = doses.filter { it.status != DoseStatus.PENDING }
        if (decided.isEmpty()) return null
        return Math.round(decided.count { it.status == DoseStatus.TAKEN } * 100f / decided.size)
    }
}
