package com.pashe.app.data

import android.util.Log
import com.google.android.gms.tasks.Task
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.QuerySnapshot
import com.pashe.app.domain.DoseRecord
import com.pashe.app.domain.DoseStatus
import com.pashe.app.domain.MealTiming
import com.pashe.app.domain.Medicine
import com.pashe.app.domain.ParentProfile
import com.pashe.app.domain.Relation
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Date

/** Collection and field names shared with firestore.rules and the Cloud Functions. */
object Fs {
    const val USERS = "users"
    const val PARENTS = "parents"
    const val MEDICINES = "medicines"
    const val DOSES = "doses"
    const val PAIRING_CODES = "pairingCodes"
}

private const val TAG = "PasheFirestore"

fun Query.asFlow(): Flow<QuerySnapshot> = callbackFlow {
    val registration = addSnapshotListener { snapshot, error ->
        if (error != null) Log.w(TAG, "query listener failed", error)
        else if (snapshot != null) trySend(snapshot)
    }
    awaitClose { registration.remove() }
}

fun DocumentReference.asFlow(): Flow<DocumentSnapshot> = callbackFlow {
    val registration = addSnapshotListener { snapshot, error ->
        if (error != null) Log.w(TAG, "document listener failed", error)
        else if (snapshot != null) trySend(snapshot)
    }
    awaitClose { registration.remove() }
}

/**
 * Firestore only completes a write task once the server acknowledges it, which never happens while
 * offline. The write is already queued locally by then, so after a short wait we treat it as done.
 */
suspend fun <T> Task<T>.awaitOrQueued(timeoutMillis: Long = 8_000) {
    withTimeoutOrNull(timeoutMillis) { await() }
}

fun Long.toTimestamp() = Timestamp(Date(this))

@Suppress("UNCHECKED_CAST")
fun DocumentSnapshot.toParent(): ParentProfile? {
    if (!exists()) return null
    return ParentProfile(
        id = id,
        name = getString("name").orEmpty(),
        relation = Relation.fromKey(getString("relation")),
        timezone = getString("timezone") ?: ParentProfile.DEFAULT_TIMEZONE,
        childIds = (get("childIds") as? List<String>).orEmpty(),
        deviceUid = getString("deviceUid"),
        pairingCode = getString("pairingCode"),
        pairingCodeExpiresAt = getTimestamp("pairingCodeExpiresAt")?.toDate()?.time,
    )
}

@Suppress("UNCHECKED_CAST")
fun DocumentSnapshot.toMedicine(): Medicine? {
    if (!exists()) return null
    return Medicine(
        id = id,
        name = getString("name").orEmpty(),
        dose = getString("dose").orEmpty(),
        mealTiming = MealTiming.fromKey(getString("mealTiming")),
        times = (get("times") as? List<String>).orEmpty().sorted(),
        startDate = getString("startDate").orEmpty(),
        endDate = getString("endDate"),
        note = getString("note"),
        active = getBoolean("active") ?: true,
    )
}

fun DocumentSnapshot.toDose(): DoseRecord? {
    if (!exists()) return null
    return DoseRecord(
        id = id,
        medId = getString("medId").orEmpty(),
        scheduledAt = getTimestamp("scheduledAt")?.toDate()?.time ?: return null,
        status = DoseStatus.fromKey(getString("status")),
        confirmedAt = getTimestamp("confirmedAt")?.toDate()?.time,
    )
}

fun Medicine.toFirestore(): Map<String, Any?> = mapOf(
    "name" to name.trim(),
    "dose" to dose.trim(),
    "mealTiming" to mealTiming.key,
    "times" to times.sorted(),
    "startDate" to startDate,
    "endDate" to endDate?.takeIf { it.isNotBlank() },
    "note" to note?.trim()?.takeIf { it.isNotEmpty() },
    "active" to active,
)
