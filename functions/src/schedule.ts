import { DateTime } from "luxon";
import { DEFAULT_TIMEZONE } from "./config";

/**
 * Mirrors app/src/main/java/com/pashe/app/domain/Schedule.kt. Both sides create dose documents with
 * these ids, so the id format and the time math must match exactly.
 */
export interface MedicineSchedule {
  id: string;
  times: string[]; // "HH:mm" in the parent's timezone
  startDate: string; // "yyyy-MM-dd"
  endDate?: string | null; // inclusive
  active?: boolean;
}

export interface PlannedDose {
  doseId: string;
  medId: string;
  scheduledAt: number; // epoch millis
}

const TIME = /^([01]\d|2[0-3]):([0-5]\d)$/;

export function validZone(timezone: string | undefined | null): string {
  return timezone && DateTime.local().setZone(timezone).isValid ? timezone : DEFAULT_TIMEZONE;
}

export function doseId(medId: string, date: string, time: string): string {
  return `${medId}_${date.replace(/-/g, "")}${time.replace(":", "")}`;
}

function isActiveOn(med: MedicineSchedule, date: string): boolean {
  if (med.active === false) return false;
  if (!med.startDate || date < med.startDate) return false;
  return !med.endDate || date <= med.endDate;
}

/** All doses with fromMs <= scheduledAt < toMs, in the parent's timezone. */
export function dosesBetween(meds: MedicineSchedule[], timezone: string, fromMs: number, toMs: number): PlannedDose[] {
  const zone = validZone(timezone);
  const result: PlannedDose[] = [];
  let day = DateTime.fromMillis(fromMs, { zone }).startOf("day");
  const last = DateTime.fromMillis(toMs, { zone }).startOf("day");
  while (day <= last) {
    const date = day.toISODate()!;
    for (const med of meds) {
      if (!isActiveOn(med, date)) continue;
      for (const time of new Set(med.times ?? [])) {
        const match = TIME.exec(time);
        if (!match) continue;
        // Luxon shifts times inside a DST gap forward, like java.time's ZonedDateTime.of.
        const at = day.set({ hour: Number(match[1]), minute: Number(match[2]) }).toMillis();
        if (at >= fromMs && at < toMs) result.push({ doseId: doseId(med.id, date, time), medId: med.id, scheduledAt: at });
      }
    }
    day = day.plus({ days: 1 });
  }
  return result.sort((a, b) => a.scheduledAt - b.scheduledAt);
}
