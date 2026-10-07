import assert from "node:assert/strict";
import { test } from "node:test";
import { DateTime } from "luxon";
import { dosesBetween, doseId } from "../src/schedule";
import { missedDoseAlert } from "../src/messages";

const med = { id: "med1", times: ["20:00", "08:00"], startDate: "2026-10-01", endDate: "2026-10-10" };

test("dose id matches the Android format", () => {
  assert.equal(doseId("med1", "2026-10-07", "08:00"), "med1_202610070800");
});

test("doses are computed in the parent's timezone", () => {
  const from = DateTime.fromISO("2026-10-07T00:00", { zone: "Asia/Dhaka" }).toMillis();
  const doses = dosesBetween([med], "Asia/Dhaka", from, from + 24 * 3600_000);
  assert.deepEqual(doses.map((d) => d.doseId), ["med1_202610070800", "med1_202610072000"]);
  assert.equal(doses[0].scheduledAt, Date.parse("2026-10-07T02:00:00Z"));
});

test("start, end and active are respected", () => {
  const at = (iso: string) => DateTime.fromISO(iso, { zone: "Asia/Dhaka" }).toMillis();
  assert.equal(dosesBetween([med], "Asia/Dhaka", at("2026-09-30T00:00"), at("2026-10-01T00:00")).length, 0);
  assert.equal(dosesBetween([med], "Asia/Dhaka", at("2026-10-10T00:00"), at("2026-10-11T00:00")).length, 2);
  assert.equal(dosesBetween([med], "Asia/Dhaka", at("2026-10-11T00:00"), at("2026-10-12T00:00")).length, 0);
  assert.equal(dosesBetween([{ ...med, active: false }], "Asia/Dhaka", at("2026-10-07T00:00"), at("2026-10-08T00:00")).length, 0);
});

test("window spanning midnight", () => {
  const from = DateTime.fromISO("2026-10-07T12:00", { zone: "Asia/Dhaka" }).toMillis();
  const ids = dosesBetween([med], "Asia/Dhaka", from, from + 24 * 3600_000).map((d) => d.doseId);
  assert.deepEqual(ids, ["med1_202610072000", "med1_202610080800"]);
});

test("invalid timezone falls back to Dhaka", () => {
  const from = Date.parse("2026-10-07T00:00:00Z");
  assert.deepEqual(
    dosesBetween([med], "Not/AZone", from, from + 3600_000 * 6).map((d) => d.scheduledAt),
    [Date.parse("2026-10-07T02:00:00Z")],
  );
});

test("missed alert text in Bengali and English", () => {
  const at = Date.parse("2026-10-07T02:00:00Z"); // 08:00 in Dhaka
  const parent = { name: "রহিমা বেগম", relation: "mother", timezone: "Asia/Dhaka" };
  assert.equal(missedDoseAlert(parent, "প্রেশারের ওষুধ", at, "bn").body, "আম্মা ৮:০০টার প্রেশারের ওষুধ এখনো খাননি");
  assert.equal(missedDoseAlert(parent, "Amlodipine", at, "en").body, "Amma hasn't taken the 8:00 AM Amlodipine yet");
  assert.equal(missedDoseAlert({ ...parent, relation: "other" }, "X", at, "bn").body, "রহিমা বেগম ৮:০০টার X এখনো খাননি");
});
