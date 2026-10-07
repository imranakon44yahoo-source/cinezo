/** Region the app calls; keep in sync with Graph.FUNCTIONS_REGION in the Android app. */
export const REGION = "asia-south1";

/** A dose still pending this long after its time is marked missed. */
export const MISSED_AFTER_MS = 30 * 60 * 1000;

/** Doses missed longer ago than this are marked silently (e.g. a backlog after an outage). */
export const MAX_ALERT_AGE_MS = 6 * 60 * 60 * 1000;

/** How far ahead pending dose documents are created. */
export const HORIZON_MS = 36 * 60 * 60 * 1000;

export const DEFAULT_TIMEZONE = "Asia/Dhaka";
