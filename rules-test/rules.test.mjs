// Security rule tests. Run with `npm test` (starts the Firestore emulator).
import { after, before, beforeEach, test } from "node:test";
import { readFileSync } from "node:fs";
import { assertFails, assertSucceeds, initializeTestEnvironment } from "@firebase/rules-unit-testing";
import { Timestamp, deleteDoc, doc, getDoc, setDoc, updateDoc, collection, query, where, getDocs } from "firebase/firestore";

let env;
const HOUR = 3600_000;

before(async () => {
  env = await initializeTestEnvironment({
    projectId: "demo-pashe",
    firestore: { rules: readFileSync(new URL("../firestore.rules", import.meta.url), "utf8") },
  });
});
after(() => env.cleanup());

beforeEach(async () => {
  await env.clearFirestore();
  await env.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await setDoc(doc(db, "parents/p1"), { name: "Rahima", relation: "mother", timezone: "Asia/Dhaka", childIds: ["child"], deviceUid: "device" });
    await setDoc(doc(db, "parents/p1/medicines/m1"), { name: "Med", times: ["08:00"], startDate: "2026-10-01", active: true });
    await setDoc(doc(db, "parents/p1/doses/d1"), { medId: "m1", scheduledAt: Timestamp.fromMillis(Date.now()), status: "pending", confirmedAt: null });
    await setDoc(doc(db, "parents/p2"), { name: "Other", relation: "father", timezone: "Asia/Dhaka", childIds: ["stranger"] });
  });
});

const as = (uid) => env.authenticatedContext(uid).firestore();

test("child reads and edits only linked parents", async () => {
  await assertSucceeds(getDoc(doc(as("child"), "parents/p1")));
  await assertFails(getDoc(doc(as("child"), "parents/p2")));
  await assertSucceeds(getDocs(query(collection(as("child"), "parents"), where("childIds", "array-contains", "child"))));
  await assertSucceeds(updateDoc(doc(as("child"), "parents/p1"), { name: "Rahima Begum" }));
  await assertFails(updateDoc(doc(as("child"), "parents/p2"), { name: "x" }));
  await assertSucceeds(setDoc(doc(as("child"), "parents/p1/medicines/m2"), { name: "New", times: ["09:00"] }));
  await assertFails(setDoc(doc(as("child"), "parents/p2/medicines/m2"), { name: "New" }));
});

test("child cannot link a device or create a parent for someone else", async () => {
  await assertFails(updateDoc(doc(as("child"), "parents/p1"), { deviceUid: "evil" }));
  await assertFails(setDoc(doc(as("child"), "parents/p3"), { name: "x", childIds: ["child"], deviceUid: "evil" }));
  await assertFails(setDoc(doc(as("child"), "parents/p3"), { name: "x", childIds: ["someone"] }));
  await assertSucceeds(setDoc(doc(as("child"), "parents/p3"), { name: "x", childIds: ["child"] }));
});

test("parent device reads its schedule and only marks doses taken", async () => {
  const device = as("device");
  await assertSucceeds(getDoc(doc(device, "parents/p1")));
  await assertSucceeds(getDocs(collection(device, "parents/p1/medicines")));
  await assertFails(setDoc(doc(device, "parents/p1/medicines/m1"), { name: "hacked" }));
  await assertFails(updateDoc(doc(device, "parents/p1"), { name: "hacked" }));
  await assertSucceeds(updateDoc(doc(device, "parents/p1"), { deviceToken: "tok" }));
  await assertSucceeds(setDoc(doc(device, "parents/p1/doses/d1"),
    { status: "taken", confirmedAt: Timestamp.now() }, { merge: true }));
  await assertFails(setDoc(doc(device, "parents/p1/doses/d1"), { status: "missed" }, { merge: true }));
  await assertSucceeds(setDoc(doc(device, "parents/p1/doses/d2"),
    { medId: "m1", scheduledAt: Timestamp.now(), status: "taken", confirmedAt: Timestamp.now() }));
  await assertFails(setDoc(doc(device, "parents/p1/doses/d3"),
    { medId: "m1", scheduledAt: Timestamp.now(), status: "taken", confirmedAt: Timestamp.now(), alertSent: true }));
  await assertFails(getDoc(doc(device, "parents/p2")));
  await assertFails(getDocs(collection(device, "parents/p2/medicines")));
});

test("pairing codes: create-only by a linked child, valid at most 24h", async () => {
  const child = as("child");
  const ok = { parentId: "p1", expiresAt: Timestamp.fromMillis(Date.now() + 23 * HOUR), createdBy: "child" };
  await assertSucceeds(setDoc(doc(child, "pairingCodes/123456"), ok));
  await assertFails(setDoc(doc(child, "pairingCodes/123456"), ok)); // collision = update
  await assertFails(setDoc(doc(child, "pairingCodes/654321"), { ...ok, expiresAt: Timestamp.fromMillis(Date.now() + 48 * HOUR) }));
  await assertFails(setDoc(doc(child, "pairingCodes/111111"), { ...ok, parentId: "p2" }));
  await assertFails(setDoc(doc(child, "pairingCodes/abc"), ok));
  await assertFails(getDoc(doc(as("device"), "pairingCodes/123456")));
  await assertSucceeds(deleteDoc(doc(child, "pairingCodes/123456")));
});

test("users are private", async () => {
  await assertSucceeds(setDoc(doc(as("child"), "users/child"), { name: "Me", language: "bn" }));
  await assertFails(getDoc(doc(as("device"), "users/child")));
  await assertFails(getDoc(doc(env.unauthenticatedContext().firestore(), "parents/p1")));
});
