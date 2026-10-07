/**
 * Seeds one child, one parent and three medicines for testing.
 *
 *   # against the local emulator
 *   FIRESTORE_EMULATOR_HOST=127.0.0.1:8080 GCLOUD_PROJECT=demo-pashe npm run seed
 *   # against a real project (service account with Firestore access)
 *   GOOGLE_APPLICATION_CREDENTIALS=service-account.json GCLOUD_PROJECT=<project-id> npm run seed
 *
 * Pass SEED_CHILD_UID=<uid> to attach the sample parent to an account you already signed in with,
 * so it shows up on that phone's dashboard. The printed pairing code links a parent phone.
 */
import { initializeApp } from "firebase-admin/app";
import { Timestamp, getFirestore } from "firebase-admin/firestore";
import { DateTime } from "luxon";

initializeApp({ projectId: process.env.GCLOUD_PROJECT });
const db = getFirestore();

async function main() {
  const childId = process.env.SEED_CHILD_UID ?? "seed-child";
  const parentId = "seed-parent";
  const today = DateTime.now().setZone("Asia/Dhaka").toISODate()!;
  const pairingCode = "123456";

  await db.doc(`users/${childId}`).set({ name: "সাকিব (টেস্ট)", phone: "+447700900123", language: "bn" }, { merge: true });
  await db.doc(`parents/${parentId}`).set({
    name: "রহিমা বেগম",
    relation: "mother",
    timezone: "Asia/Dhaka",
    childIds: [childId],
    pairingCode,
    pairingCodeExpiresAt: Timestamp.fromMillis(Date.now() + 24 * 3600_000),
    createdAt: Timestamp.now(),
  }, { merge: true });

  const medicines = [
    { id: "seed-med-pressure", name: "প্রেশারের ওষুধ (Amlodipine 5mg)", dose: "১টা ট্যাবলেট", mealTiming: "after", times: ["08:00", "20:00"], note: null },
    { id: "seed-med-diabetes", name: "ডায়াবেটিসের ওষুধ (Metformin 500mg)", dose: "১টা ট্যাবলেট", mealTiming: "before", times: ["07:30", "13:30", "19:30"], note: "খাওয়ার ১৫ মিনিট আগে" },
    { id: "seed-med-gastric", name: "গ্যাসের ওষুধ (Omeprazole 20mg)", dose: "১টা ক্যাপসুল", mealTiming: "before", times: ["07:00"], note: null },
  ];
  for (const { id, ...med } of medicines) {
    await db.doc(`parents/${parentId}/medicines/${id}`).set({ ...med, startDate: today, endDate: null, active: true });
  }

  await db.doc(`pairingCodes/${pairingCode}`).set({
    parentId,
    expiresAt: Timestamp.fromMillis(Date.now() + 24 * 3600_000),
    createdBy: childId,
  });

  console.log(`Seeded child ${childId}, parent ${parentId} with ${medicines.length} medicines.`);
  console.log(`Pairing code for the parent phone: ${pairingCode} (valid 24 hours)`);
}

main().catch((e) => {
  console.error(e);
  process.exit(1);
});
