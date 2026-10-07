package com.pashe.app.alarm

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.firestore
import com.pashe.app.Graph
import com.pashe.app.data.Fs
import com.pashe.app.data.toTimestamp
import com.pashe.app.domain.DoseStatus
import com.pashe.app.domain.PlannedDose
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import java.util.concurrent.TimeUnit

private val online = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

/** Uploads one confirmation. WorkManager holds it until the phone is online, across restarts. */
class ConfirmDoseWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        if (Firebase.auth.currentUser == null) return Result.retry()
        val parentId = inputData.getString(KEY_PARENT) ?: return Result.failure()
        val doseId = inputData.getString(KEY_DOSE) ?: return Result.failure()
        val fields = mapOf(
            "medId" to inputData.getString(KEY_MED),
            "scheduledAt" to inputData.getLong(KEY_SCHEDULED, 0).toTimestamp(),
            "status" to DoseStatus.TAKEN.key,
            "confirmedAt" to inputData.getLong(KEY_CONFIRMED, 0).toTimestamp(),
            "syncedAt" to FieldValue.serverTimestamp(),
        )
        return try {
            withTimeout(30_000) {
                Firebase.firestore.collection(Fs.PARENTS).document(parentId).collection(Fs.DOSES).document(doseId)
                    .set(fields, SetOptions.merge()).await()
            }
            Result.success()
        } catch (e: Exception) {
            if (runAttemptCount < 20) Result.retry() else Result.failure()
        }
    }

    companion object {
        private const val KEY_PARENT = "parentId"
        private const val KEY_DOSE = "doseId"
        private const val KEY_MED = "medId"
        private const val KEY_SCHEDULED = "scheduledAt"
        private const val KEY_CONFIRMED = "confirmedAt"

        fun enqueue(context: Context, parentId: String, dose: PlannedDose, confirmedAt: Long) {
            val request = OneTimeWorkRequestBuilder<ConfirmDoseWorker>()
                .setConstraints(online)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .setInputData(
                    workDataOf(
                        KEY_PARENT to parentId, KEY_DOSE to dose.doseId, KEY_MED to dose.medicine.id,
                        KEY_SCHEDULED to dose.scheduledAt, KEY_CONFIRMED to confirmedAt,
                    ),
                )
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork("confirm_${dose.doseId}", ExistingWorkPolicy.KEEP, request)
        }
    }
}

/** Refreshes the parent phone's schedule from Firestore and re-arms alarms. */
class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = if (Graph.parentRepo.sync()) Result.success() else Result.retry()

    companion object {
        fun schedulePeriodic(context: Context) {
            val request = PeriodicWorkRequestBuilder<SyncWorker>(6, TimeUnit.HOURS).setConstraints(online).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork("sync_periodic", ExistingPeriodicWorkPolicy.KEEP, request)
        }

        /** Used when a push says the schedule changed. */
        fun runNow(context: Context) {
            val request = OneTimeWorkRequestBuilder<SyncWorker>()
                .setConstraints(online)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork("sync_now", ExistingWorkPolicy.REPLACE, request)
        }

        fun cancelAll(context: Context) = WorkManager.getInstance(context).cancelUniqueWork("sync_periodic")
    }
}
