package com.pashe.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

class ScheduleTest {
    private val dhaka = ZoneId.of("Asia/Dhaka")
    private val med = Medicine(
        id = "med1", name = "Amlodipine", dose = "১টা ট্যাবলেট", mealTiming = MealTiming.AFTER,
        times = listOf("20:00", "08:00"), startDate = "2026-10-01", endDate = "2026-10-10",
    )

    @Test fun doseIdMatchesServerFormat() {
        assertEquals("med1_202610070800", Schedule.doseId("med1", LocalDate.of(2026, 10, 7), LocalTime.of(8, 0)))
    }

    @Test fun dosesOnUsesParentTimezoneAndSorts() {
        val doses = Schedule.dosesOn(listOf(med), dhaka, LocalDate.of(2026, 10, 7))
        assertEquals(listOf("08:00", "20:00"), doses.map { Schedule.formatTime(it.localTime) })
        // 08:00 in Dhaka (UTC+6) is 02:00 UTC.
        assertEquals(ZonedDateTime.parse("2026-10-07T02:00Z").toInstant().toEpochMilli(), doses.first().scheduledAt)
    }

    @Test fun respectsStartEndAndActive() {
        assertTrue(Schedule.dosesOn(listOf(med), dhaka, LocalDate.of(2026, 9, 30)).isEmpty())
        assertEquals(2, Schedule.dosesOn(listOf(med), dhaka, LocalDate.of(2026, 10, 10)).size)
        assertTrue(Schedule.dosesOn(listOf(med), dhaka, LocalDate.of(2026, 10, 11)).isEmpty())
        assertTrue(Schedule.dosesOn(listOf(med.copy(active = false)), dhaka, LocalDate.of(2026, 10, 7)).isEmpty())
    }

    @Test fun dosesBetweenSpansDays() {
        val from = ZonedDateTime.of(2026, 10, 7, 12, 0, 0, 0, dhaka).toInstant().toEpochMilli()
        val to = from + 24 * 3600_000L
        val ids = Schedule.dosesBetween(listOf(med), dhaka, from, to).map { it.doseId }
        assertEquals(listOf("med1_202610072000", "med1_202610080800"), ids)
    }

    @Test fun resolveAppliesGracePeriod() {
        val planned = Schedule.dosesOn(listOf(med), dhaka, LocalDate.of(2026, 10, 7)).first()
        val at = planned.scheduledAt
        assertEquals(DoseStatus.PENDING, Schedule.resolve(planned, null, false, at + 29 * 60_000).status)
        assertEquals(DoseStatus.MISSED, Schedule.resolve(planned, null, false, at + 30 * 60_000).status)
        assertEquals(DoseStatus.TAKEN, Schedule.resolve(planned, null, true, at + 60 * 60_000).status)
        val missed = DoseRecord(planned.doseId, "med1", at, DoseStatus.MISSED)
        assertEquals(DoseStatus.TAKEN, Schedule.resolve(planned, missed, true, at).status)
    }

    @Test fun adherence() {
        val p = Schedule.dosesOn(listOf(med), dhaka, LocalDate.of(2026, 10, 7))
        val doses = listOf(ResolvedDose(p[0], DoseStatus.TAKEN), ResolvedDose(p[1], DoseStatus.MISSED))
        assertEquals(50, Schedule.adherencePercent(doses))
        assertNull(Schedule.adherencePercent(listOf(ResolvedDose(p[0], DoseStatus.PENDING))))
    }

    @Test fun bengaliFormatting() {
        assertEquals("সকাল ৮:০০", BnFormat.time(LocalTime.of(8, 0), true))
        assertEquals("রাত ১০:৩০", BnFormat.time(LocalTime.of(22, 30), true))
        assertEquals("8:00 PM", BnFormat.time(LocalTime.of(20, 0), false))
        assertEquals("৭ অক্টোবর, বুধবার", BnFormat.date(LocalDate.of(2026, 10, 7), true))
    }
}
