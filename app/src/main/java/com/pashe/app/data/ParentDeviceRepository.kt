package com.pashe.app.data

import android.content.Context
import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.firestore
import com.google.firebase.functions.FirebaseFunctionsException
import com.google.firebase.functions.functions
import com.google.firebase.messaging.FirebaseMessaging
import com.pashe.app.Graph
import com.pashe.app.alarm.AlarmScheduler
import com.pashe.app.alarm.SyncWorker
import com.pashe.app.domain.DoseRecord
import com.pashe.app.domain.ParentProfile
import com.pashe.app.domain.Relation
import com.pashe.app.domain.Schedule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/** Firestore access for a linked parent phone. Everything is mirrored into [LocalStore]. */
class ParentDeviceRepository(
    private val context: Context,
    private val store: LocalStore,
    private val db: FirebaseFirestore = Firebase.firestore,
) {
    sealed interface PairResult {
        data class Success(val parent: ParentProfile) : PairResult
        data object InvalidCode : PairResult
        data class Failed(val error: Exception) : PairResult
    }

    /** Links this phone to a parent profile using the child's 6-digit code. */
    suspend fun redeemPairingCode(code: String): PairResult = try {
        val auth = Firebase.auth
        if (auth.currentUser == null) auth.signInAnonymously().await()
        val result = Firebase.functions(Graph.FUNCTIONS_REGION)
            .getHttpsCallable("redeemPairingCode")
            .call(mapOf("code" to code))
            .await()
        @Suppress("UNCHECKED_CAST")
        val data = result.getData() as Map<String, Any?>
        val parent = ParentProfile(
            id = data["parentId"] as String,
            name = data["name"] as? String ?: "",
            relation = Relation.fromKey(data["relation"] as? String),
            timezone = data["timezone"] as? String ?: ParentProfile.DEFAULT_TIMEZONE,
            deviceUid = auth.currentUser?.uid,
        )
        store.clearParentData()
        store.parentId = parent.id
        store.parentProfile = parent
        runCatching { updateDeviceToken(FirebaseMessaging.getInstance().token.await()) }
        sync()
        SyncWorker.schedulePeriodic(context)
        PairResult.Success(parent)
    } catch (e: FirebaseFunctionsException) {
        if (e.code == FirebaseFunctionsException.Code.NOT_FOUND ||
            e.code == FirebaseFunctionsException.Code.FAILED_PRECONDITION ||
            e.code == FirebaseFunctionsException.Code.INVALID_ARGUMENT
        ) PairResult.InvalidCode else PairResult.Failed(e)
    } catch (e: Exception) {
        PairResult.Failed(e)
    }

    suspend fun updateDeviceToken(token: String) {
        val parentId = store.parentId ?: return
        db.collection(Fs.PARENTS).document(parentId)
            .update(mapOf("deviceToken" to token, "deviceUpdatedAt" to FieldValue.serverTimestamp()))
            .awaitOrQueued()
    }

    /**
     * Pulls the profile, medicines and recent doses into the local store and re-arms alarms.
     * Alarms are re-armed from the cached copy even if the network fetch fails.
     */
    suspend fun sync(): Boolean {
        val parentId = store.parentId ?: return false
        val ok = try {
            val parentRef = db.collection(Fs.PARENTS).document(parentId)
            parentRef.get().await().toParent()?.let { store.parentProfile = it }
            store.medicines = parentRef.collection(Fs.MEDICINES).get().await().documents.mapNotNull { it.toMedicine() }
            store.remoteStatuses = recentDoses(parentId).associate { it.id to it.status }
            true
        } catch (e: Exception) {
            Log.w(TAG, "sync failed, using cached schedule", e)
            false
        }
        AlarmScheduler.rescheduleAll(context)
        return ok
    }

    private suspend fun recentDoses(parentId: String): List<DoseRecord> {
        val now = System.currentTimeMillis()
        return db.collection(Fs.PARENTS).document(parentId).collection(Fs.DOSES)
            .whereGreaterThanOrEqualTo("scheduledAt", (now - DAY).toTimestamp())
            .whereLessThan("scheduledAt", (now + 2 * DAY).toTimestamp())
            .get().await().documents.mapNotNull { it.toDose() }
    }

    /** Keeps the local store live while the home screen is visible. Cancel the job to stop. */
    fun listen(scope: CoroutineScope): Job? {
        val parentId = store.parentId ?: return null
        val parentRef = db.collection(Fs.PARENTS).document(parentId)
        val now = System.currentTimeMillis()
        val doses = parentRef.collection(Fs.DOSES)
            .whereGreaterThanOrEqualTo("scheduledAt", (now - DAY).toTimestamp())
            .whereLessThan("scheduledAt", (now + 2 * DAY).toTimestamp())
        return combine(parentRef.asFlow(), parentRef.collection(Fs.MEDICINES).asFlow(), doses.asFlow()) { p, m, d ->
            Triple(p.toParent(), m.documents.mapNotNull { it.toMedicine() }, d.documents.mapNotNull { it.toDose() })
        }.onEach { (parent, medicines, doseRecords) ->
            parent?.let { store.parentProfile = it }
            val scheduleChanged = medicines != store.medicines
            store.medicines = medicines
            store.remoteStatuses = doseRecords.associate { it.id to it.status }
            if (scheduleChanged) scope.launch { AlarmScheduler.rescheduleAll(context) }
        }.launchIn(scope)
    }

    companion object {
        private const val TAG = "PasheParentRepo"
        private const val DAY = 24L * 3600 * 1000

        /** The parent's timezone; all dose times are computed in it, not in the phone's timezone. */
        fun zoneFor(store: LocalStore) = Schedule.zoneOf(store.parentProfile?.timezone ?: ParentProfile.DEFAULT_TIMEZONE)
    }
}
