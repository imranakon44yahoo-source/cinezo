// Runs the real function handlers against the Firestore emulator:
//   npm run test:emulator
import assert from "node:assert/strict";
import { before, test } from "node:test";
import { Timestamp, getFirestore } from "firebase-admin/firestore";

const enabled = !!process.env.FIRESTORE_EMULATOR_HOST;
// eslint-disable-next-line @typescript-eslint/no-require-imports
const fns = enabled ? require("../src/index") : null;
const db = () => getFirestore();

before(async () => {
  if (!enabled) return;
  const today = new Date().toISOString().slice(0, 10);
  await db().doc("parents/p1").set({ name: "Rahima", relation: "mother", timezone: "Asia/Dhaka", childIds: ["child"] });
  await db().doc("parents/p1/medicines/m1").set({ name: "Med", times: ["08:00", "20:00"], startDate: today, active: true });
  await db().doc("pairingCodes/123456").set({ parentId: "p1", expiresAt: Timestamp.fromMillis(Date.now() + 3600_000), createdBy: "child" });
  await db().doc("pairingCodes/999999").set({ parentId: "p1", expiresAt: Timestamp.fromMillis(Date.now() - 1000), createdBy: "child" });
});

test("redeemPairingCode links the device and consumes the code", { skip: !enabled }, async () => {
  const result = await fns.redeemPairingCode.run({ data: { code: "123456" }, auth: { uid: "device1" } });
  assert.equal(result.parentId, "p1");
  assert.equal((await db().doc("parents/p1").get()).get("deviceUid"), "device1");
  assert.equal((await db().doc("pairingCodes/123456").get()).exists, false);
  await assert.rejects(fns.redeemPairingCode.run({ data: { code: "123456" }, auth: { uid: "device2" } }), /Unknown code/);
  await assert.rejects(fns.redeemPairingCode.run({ data: { code: "999999" }, auth: { uid: "device2" } }), /expired/);
  await assert.rejects(fns.redeemPairingCode.run({ data: { code: "12" }, auth: { uid: "device2" } }), /6 digits/);
});

test("materializeDoses creates the upcoming pending doses", { skip: !enabled }, async () => {
  await fns.materializeDoses.run({});
  const doses = await db().collection("parents/p1/doses").get();
  assert.ok(doses.size >= 2 && doses.size <= 4, `expected 2-4 doses in 36h, got ${doses.size}`);
  assert.ok(doses.docs.every((d) => d.get("status") === "pending" && d.id.startsWith("m1_")));
  assert.equal((await db().doc("pairingCodes/999999").get()).exists, false, "expired code cleaned up");
});

test("medicine change reconciles pending doses but keeps taken ones", { skip: !enabled }, async () => {
  const doses = await db().collection("parents/p1/doses").orderBy("scheduledAt").get();
  const taken = doses.docs[0];
  await taken.ref.update({ status: "taken", confirmedAt: Timestamp.now() });
  await db().doc("parents/p1/medicines/m1").update({ active: false });
  await fns.onMedicineWritten.run({ params: { parentId: "p1", medId: "m1" } });
  const after = await db().collection("parents/p1/doses").get();
  assert.deepEqual(after.docs.map((d) => d.id), [taken.id]);
});

test("checkMissedDoses marks overdue doses missed exactly once", { skip: !enabled }, async () => {
  const overdue = db().doc("parents/p1/doses/m1_old");
  const recent = db().doc("parents/p1/doses/m1_recent");
  await overdue.set({ medId: "m1", scheduledAt: Timestamp.fromMillis(Date.now() - 31 * 60_000), status: "pending", confirmedAt: null });
  await recent.set({ medId: "m1", scheduledAt: Timestamp.fromMillis(Date.now() - 10 * 60_000), status: "pending", confirmedAt: null });
  await fns.checkMissedDoses.run({});
  const o = await overdue.get();
  assert.equal(o.get("status"), "missed");
  assert.equal(o.get("alertSent"), true);
  assert.equal((await recent.get()).get("status"), "pending");
  // A late confirmation from the phone still wins.
  await overdue.update({ status: "taken" });
  await fns.checkMissedDoses.run({});
  assert.equal((await overdue.get()).get("status"), "taken");
});
