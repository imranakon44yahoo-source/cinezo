import { initializeApp } from "firebase-admin/app";
import { DocumentReference, FieldValue, Timestamp, getFirestore } from "firebase-admin/firestore";
import { getMessaging } from "firebase-admin/messaging";
import { logger, setGlobalOptions } from "firebase-functions/v2";
import { onDocumentUpdated, onDocumentWritten } from "firebase-functions/v2/firestore";
import { HttpsError, onCall } from "firebase-functions/v2/https";
import { onSchedule } from "firebase-functions/v2/scheduler";
import { HORIZON_MS, MAX_ALERT_AGE_MS, MISSED_AFTER_MS, REGION } from "./config";
import { missedDoseAlert } from "./messages";
import { MedicineSchedule, dosesBetween } from "./schedule";

initializeApp();
setGlobalOptions({ region: REGION, maxInstances: 10 });

const db = getFirestore();

const MAX_PAIRING_ATTEMPTS_PER_HOUR = 10;

// ---------------------------------------------------------------------------------------------
// Pairing
// ---------------------------------------------------------------------------------------------

/**
 * Links an anonymously signed-in parent phone to a parent profile. The 6-digit code is single-use
 * and expires after 24 hours; attempts are rate limited per device to stop guessing.
 */
export const redeemPairingCode = onCall(async (request) => {
  const uid = request.auth?.uid;
  if (!uid) throw new HttpsError("unauthenticated", "Sign in first");
  const code = String(request.data?.code ?? "").trim();
  if (!/^\d{6}$/.test(code)) throw new HttpsError("invalid-argument", "Code must be 6 digits");

  const attemptsRef = db.collection("pairingAttempts").doc(uid);
  await db.runTransaction(async (tx) => {
    const snap = await tx.get(attemptsRef);
    const windowStart = snap.get("windowStart")?.toMillis?.() ?? 0;
    const fresh = Date.now() - windowStart > 3600_000;
    const count = fresh ? 0 : (snap.get("count") ?? 0);
    if (count >= MAX_PAIRING_ATTEMPTS_PER_HOUR) throw new HttpsError("resource-exhausted", "Too many attempts");
    tx.set(attemptsRef, { count: count + 1, windowStart: fresh ? Timestamp.now() : snap.get("windowStart") });
  });

  const codeRef = db.collection("pairingCodes").doc(code);
  return db.runTransaction(async (tx) => {
    const codeSnap = await tx.get(codeRef);
    if (!codeSnap.exists) throw new HttpsError("not-found", "Unknown code");
    const expiresAt: Timestamp | undefined = codeSnap.get("expiresAt");
    if (!expiresAt || expiresAt.toMillis() < Date.now()) {
      tx.delete(codeRef);
      throw new HttpsError("failed-precondition", "Code expired");
    }
    const parentRef = db.collection("parents").doc(codeSnap.get("parentId"));
    const parent = await tx.get(parentRef);
    if (!parent.exists) throw new HttpsError("not-found", "Parent not found");

    tx.update(parentRef, {
      deviceUid: uid,
      pairedAt: FieldValue.serverTimestamp(),
      pairingCode: FieldValue.delete(),
      pairingCodeExpiresAt: FieldValue.delete(),
    });
    tx.delete(codeRef);
    tx.delete(attemptsRef);
    return {
      parentId: parent.id,
      name: parent.get("name") ?? "",
      relation: parent.get("relation") ?? "other",
      timezone: parent.get("timezone") ?? "Asia/Dhaka",
    };
  });
});

// ---------------------------------------------------------------------------------------------
// Dose documents
// ---------------------------------------------------------------------------------------------

function toSchedule(id: string, data: FirebaseFirestore.DocumentData): MedicineSchedule {
  return { id, times: data.times ?? [], startDate: data.startDate ?? "", endDate: data.endDate ?? null, active: data.active !== false };
}

/**
 * Makes the parent's upcoming pending dose documents match the current schedule: creates missing
 * ones and removes pending ones that no longer belong (time changed, medicine deleted or paused).
 * Taken and missed doses are never touched.
 */
async function reconcileUpcomingDoses(parentRef: DocumentReference): Promise<void> {
  const parent = await parentRef.get();
  if (!parent.exists) return;
  const now = Date.now();
  const meds = await parentRef.collection("medicines").get();
  const planned = dosesBetween(meds.docs.map((d) => toSchedule(d.id, d.data())), parent.get("timezone"), now, now + HORIZON_MS);
  const plannedIds = new Set(planned.map((d) => d.doseId));

  const existing = await parentRef.collection("doses").where("scheduledAt", ">", Timestamp.fromMillis(now)).get();
  const existingIds = new Set(existing.docs.map((d) => d.id));

  const writer = db.bulkWriter();
  // ALREADY_EXISTS (6) means the phone confirmed the dose first, which is fine; don't retry it.
  writer.onWriteError((error) => error.code !== 6 && error.failedAttempts < 3);
  for (const doc of existing.docs) {
    if (!plannedIds.has(doc.id) && doc.get("status") === "pending") {
      writer.delete(doc.ref).catch((e) => logger.warn("dose delete failed", { dose: doc.id, error: String(e) }));
    }
  }
  for (const dose of planned) {
    if (existingIds.has(dose.doseId)) continue;
    writer.create(parentRef.collection("doses").doc(dose.doseId), {
      medId: dose.medId,
      scheduledAt: Timestamp.fromMillis(dose.scheduledAt),
      status: "pending",
      confirmedAt: null,
    }).catch((e) => {
      if (e.code !== 6) logger.warn("dose create failed", { dose: dose.doseId, error: String(e) });
    });
  }
  await writer.close();
}

/** Tells the parent phone to re-sync its local schedule. Alarms never depend on this push. */
async function pingParentDevice(parentRef: DocumentReference): Promise<void> {
  const token = (await parentRef.get()).get("deviceToken");
  if (!token) return;
  try {
    await getMessaging().send({ token, data: { type: "sync" }, android: { priority: "high" } });
  } catch (e) {
    logger.warn("sync ping failed", { parentId: parentRef.id, error: String(e) });
  }
}

export const onMedicineWritten = onDocumentWritten("parents/{parentId}/medicines/{medId}", async (event) => {
  const parentRef = db.collection("parents").doc(event.params.parentId);
  await reconcileUpcomingDoses(parentRef);
  await pingParentDevice(parentRef);
});

export const onParentUpdated = onDocumentUpdated("parents/{parentId}", async (event) => {
  const before = event.data?.before.data();
  const after = event.data?.after.data();
  if (!before || !after || before.timezone === after.timezone) return;
  const parentRef = db.collection("parents").doc(event.params.parentId);
  await reconcileUpcomingDoses(parentRef);
  await pingParentDevice(parentRef);
});

/** Keeps the rolling window of pending doses filled, and clears out expired pairing codes. */
export const materializeDoses = onSchedule({ schedule: "every 60 minutes", timeoutSeconds: 540 }, async () => {
  const parents = await db.collection("parents").select().get();
  for (const parent of parents.docs) {
    try {
      await reconcileUpcomingDoses(parent.ref);
    } catch (e) {
      logger.error("reconcile failed", { parentId: parent.id, error: String(e) });
    }
  }
  const expired = await db.collection("pairingCodes").where("expiresAt", "<", Timestamp.now()).get();
  await Promise.all(expired.docs.map((d) => d.ref.delete()));
});

// ---------------------------------------------------------------------------------------------
// Missed doses
// ---------------------------------------------------------------------------------------------

const INVALID_TOKEN_CODES = new Set([
  "messaging/registration-token-not-registered",
  "messaging/invalid-registration-token",
  "messaging/invalid-argument",
]);

async function alertChildren(parentRef: DocumentReference, medId: string, scheduledAt: number): Promise<void> {
  const parent = await parentRef.get();
  if (!parent.exists) return;
  const medicine = await parentRef.collection("medicines").doc(medId).get();
  const medicineName: string = medicine.get("name") ?? "";
  const childIds: string[] = parent.get("childIds") ?? [];
  const profile = { name: parent.get("name") ?? "", relation: parent.get("relation"), timezone: parent.get("timezone") };

  for (const childId of childIds) {
    const userRef = db.collection("users").doc(childId);
    const user = await userRef.get();
    const tokens: string[] = user.get("fcmTokens") ?? [];
    if (tokens.length === 0) continue;
    const alert = missedDoseAlert(profile, medicineName, scheduledAt, user.get("language") ?? "bn");
    const response = await getMessaging().sendEachForMulticast({
      tokens,
      notification: alert,
      data: { type: "missed", parentId: parent.id, medId },
      android: { priority: "high", notification: { channelId: "missed_doses" } },
    });
    const stale = response.responses
      .map((r, i) => (!r.success && INVALID_TOKEN_CODES.has(r.error?.code ?? "") ? tokens[i] : null))
      .filter((t): t is string => t !== null);
    if (stale.length > 0) await userRef.update({ fcmTokens: FieldValue.arrayRemove(...stale) });
  }
}

/**
 * Every 5 minutes: any dose still pending 30 minutes after its time becomes "missed" and every
 * linked child gets one push. The transaction's alertSent flag guarantees a single alert per dose
 * even if two runs overlap.
 */
export const checkMissedDoses = onSchedule({ schedule: "every 5 minutes", timeoutSeconds: 240 }, async () => {
  const now = Date.now();
  const due = await db.collectionGroup("doses")
    .where("status", "==", "pending")
    .where("scheduledAt", "<=", Timestamp.fromMillis(now - MISSED_AFTER_MS))
    .limit(500)
    .get();

  for (const doc of due.docs) {
    const claimed = await db.runTransaction(async (tx) => {
      const fresh = await tx.get(doc.ref);
      if (!fresh.exists || fresh.get("status") !== "pending" || fresh.get("alertSent") === true) return false;
      tx.update(doc.ref, { status: "missed", missedAt: FieldValue.serverTimestamp(), alertSent: true });
      return true;
    });
    if (!claimed) continue;

    const scheduledAt = (doc.get("scheduledAt") as Timestamp).toMillis();
    if (now - scheduledAt > MAX_ALERT_AGE_MS) continue;
    const parentRef = doc.ref.parent.parent;
    if (!parentRef) continue;
    try {
      await alertChildren(parentRef, doc.get("medId"), scheduledAt);
    } catch (e) {
      logger.error("missed-dose alert failed", { dose: doc.ref.path, error: String(e) });
    }
  }
  if (due.size > 0) logger.info(`processed ${due.size} overdue doses`);
});
