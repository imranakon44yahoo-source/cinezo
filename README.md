# পাশে · Pashe

**দূরে থেকেও বাবা-মায়ের পাশে**

Play Store title: **Pashe – Parents Medicine Reminder** · Package: `com.pashe.app`

Children (often living abroad) set up medicine schedules for their elderly parents in Bangladesh.
The parent's phone rings at dose time with a full-screen Bengali alarm and one big
**"খেয়েছি ✓"** button. If a dose is still unconfirmed 30 minutes later, every linked child gets a push:
*"আম্মা ৮:০০টার প্রেশারের ওষুধ এখনো খাননি"*.

Kotlin + Jetpack Compose, Firebase Auth / Firestore / Cloud Messaging / Cloud Functions. No ads, no payments.

## Repository layout

| Path | What |
|---|---|
| `app/` | Android app (both modes in one APK) |
| `app/src/main/java/com/pashe/app/domain/` | Pure-Kotlin schedule math, models, Bengali formatting (unit tested) |
| `app/src/main/java/com/pashe/app/alarm/` | Exact alarms, full-screen alarm activity, boot re-scheduling, sync workers |
| `functions/` | Cloud Functions (TypeScript): pairing, dose generation, missed-dose alerts, seeding |
| `firestore.rules`, `firestore.indexes.json` | Security rules and the collection-group index the missed-dose job needs |
| `rules-test/` | Security-rule tests against the Firestore emulator |

## How it works

**Child mode** — sign in with phone number or Google, add parents (name, relation, timezone), add
medicines (name, dose, before/after meal, several times a day, start/end date, note). The dashboard
shows today's doses as খেয়েছেন ✅ / বাকি ⏳ / মিস হয়েছে ❌, with times in the parent's timezone and the
child's own. A 7-day history shows adherence. Bengali by default; English in Settings.

**Parent mode** — no account. The parent enters the child's 6-digit code once; the phone signs in
anonymously and the `redeemPairingCode` function binds that anonymous uid to the parent profile
(`parents/{id}.deviceUid`). The home screen shows only today's medicines in large (≥24sp) Bengali text.

**Reminders work offline.** The parent phone keeps a local copy of the schedule and registers
`AlarmManager.setAlarmClock` alarms for the next 48 hours. Medicines sharing a time ring together.
If not confirmed, the alarm repeats every 5 minutes, up to 3 times; "১০ মিনিট পরে মনে করাও" snoozes
10 minutes. Confirmations are written locally first and uploaded by WorkManager once online.
Alarms are re-armed after reboot, app update and clock/timezone changes. When the child edits a
medicine, a silent FCM push makes the parent phone re-sync (a 6-hourly sync is the fallback).

**Missed doses.** `materializeDoses` (hourly) and `onMedicineWritten` keep pending dose documents
for the next 36 hours. `checkMissedDoses` runs every 5 minutes, flips doses still pending 30 minutes
after their time to `missed` inside a transaction that also sets `alertSent`, so each dose alerts
once, and pushes to every child in `childIds` in that child's language. A late "খেয়েছি" from an
offline phone still turns a missed dose into taken.

Dose ids are deterministic (`{medId}_{yyyyMMddHHmm}` in the parent's timezone), so the phone and the
server write the same document. `domain/Schedule.kt` and `functions/src/schedule.ts` must stay in sync.
Both have tests for the same cases.

## Data model

```
users/{childId}                    name, phone, language, fcmTokens[]
parents/{parentId}                 name, relation (mother|father|other), timezone, childIds[],
                                   deviceUid, deviceToken, pairingCode, pairingCodeExpiresAt
parents/{parentId}/medicines/{id}  name, dose, mealTiming (before|after), times["HH:mm"],
                                   startDate, endDate, note, active
parents/{parentId}/doses/{doseId}  medId, scheduledAt, status (pending|taken|missed), confirmedAt, alertSent
pairingCodes/{code}                parentId, expiresAt, createdBy
```

Security rules: a child reads/writes only parents whose `childIds` contain them (and their
subcollections); a parent device reads only its own profile and medicines, may update only its push
token, and may only set its own doses to `taken`. Pairing codes are create-only, at most 24 hours,
and are redeemed only by the function (rate limited to 10 tries per device per hour).

## Setup

1. Create a Firebase project. Enable **Authentication** → Phone, Google and Anonymous providers;
   **Firestore**; **Cloud Messaging**. Cloud Functions with scheduled jobs need the Blaze plan.
2. Add an Android app with package `com.pashe.app` and your debug/release SHA-1 and SHA-256
   (required for Phone auth and Google sign-in). Download `google-services.json` into `app/`.
   The Gradle build applies the google-services plugin only when that file exists; without it the
   app starts on a "Firebase is not configured" screen.
3. Set your project id in `.firebaserc`, then deploy:
   ```sh
   cd functions && npm ci && cd ..
   firebase deploy --only firestore:rules,firestore:indexes,functions
   ```
   Functions run in `asia-south1` (`functions/src/config.ts`, matching `Graph.FUNCTIONS_REGION`).
4. Build the app: `./gradlew assembleDebug` (Android Studio Ladybug or newer, JDK 17).
5. Replace `Graph.PRIVACY_POLICY_URL` with your real privacy policy before publishing.

### Sample data

- **In the app:** Child mode → Settings → *টেস্টের জন্য নমুনা তথ্য যোগ করুন* adds one mother with
  three medicines to the signed-in account. Generate a pairing code on her page and enter it on a
  second phone in parent mode.
- **From the command line** (one child, one parent, three medicines, pairing code `123456`):
  ```sh
  cd functions
  # emulator
  FIRESTORE_EMULATOR_HOST=127.0.0.1:8080 GCLOUD_PROJECT=demo-pashe npm run seed
  # real project; SEED_CHILD_UID attaches the parent to an account you already signed in with
  GOOGLE_APPLICATION_CREDENTIALS=service-account.json GCLOUD_PROJECT=<project-id> SEED_CHILD_UID=<uid> npm run seed
  ```

## Tests

```sh
./gradlew testDebugUnitTest                      # schedule math, Bengali formatting
cd functions && npm test                          # schedule mirror + alert wording
cd rules-test && npm ci && npm test               # security rules (Firestore emulator, needs Java)
cd functions && npm run test:emulator             # pairing, dose generation, missed-dose job (needs rules-test deps)
```

CI (`.github/workflows/ci.yml`) runs all of these.

## Before release

- Turn on **App Check** (Play Integrity) and enforce it on `redeemPairingCode` and Firestore.
- Play Console declarations: `SCHEDULE_EXACT_ALARM` (medication reminders), `USE_FULL_SCREEN_INTENT`
  (alarm), `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` (reminders must not be killed), and the health-app
  declaration. The in-app disclaimer states the app is a reminder tool, not medical advice.
- Some manufacturers (Xiaomi, Oppo, Vivo, Samsung) have their own auto-start/battery settings that can
  still block alarms; consider an in-app guide for the brands common in Bangladesh.

Fonts: Hind Siliguri, SIL Open Font License (`licenses/OFL-HindSiliguri.txt`).
