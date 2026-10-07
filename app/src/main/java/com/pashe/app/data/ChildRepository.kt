package com.pashe.app.data

import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.firestore
import com.pashe.app.domain.DoseRecord
import com.pashe.app.domain.MealTiming
import com.pashe.app.domain.Medicine
import com.pashe.app.domain.ParentProfile
import com.pashe.app.domain.Relation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.security.SecureRandom
import java.time.LocalDate
import java.time.ZoneId

/** Firestore access for the child (caregiver) side of the app. */
class ChildRepository(private val db: FirebaseFirestore = Firebase.firestore) {
    private val uid: String get() = Firebase.auth.currentUser?.uid ?: error("Not signed in")

    private fun parentDoc(parentId: String) = db.collection(Fs.PARENTS).document(parentId)

    fun parents(): Flow<List<ParentProfile>> =
        db.collection(Fs.PARENTS).whereArrayContains("childIds", uid).asFlow()
            .map { snap -> snap.documents.mapNotNull { it.toParent() }.sortedBy { it.name } }

    fun parent(parentId: String): Flow<ParentProfile?> = parentDoc(parentId).asFlow().map { it.toParent() }

    fun medicines(parentId: String): Flow<List<Medicine>> =
        parentDoc(parentId).collection(Fs.MEDICINES).asFlow()
            .map { snap -> snap.documents.mapNotNull { it.toMedicine() }.sortedBy { it.times.firstOrNull() } }

    fun doses(parentId: String, fromMillis: Long, toMillis: Long): Flow<List<DoseRecord>> =
        parentDoc(parentId).collection(Fs.DOSES)
            .whereGreaterThanOrEqualTo("scheduledAt", fromMillis.toTimestamp())
            .whereLessThan("scheduledAt", toMillis.toTimestamp())
            .asFlow()
            .map { snap -> snap.documents.mapNotNull { it.toDose() } }

    suspend fun getParent(parentId: String): ParentProfile? = parentDoc(parentId).get().await().toParent()

    suspend fun getMedicine(parentId: String, medId: String): Medicine? =
        parentDoc(parentId).collection(Fs.MEDICINES).document(medId).get().await().toMedicine()

    /** Creates a parent when [parentId] is null, otherwise updates it. Returns the parent id. */
    suspend fun saveParent(parentId: String?, name: String, relation: Relation, timezone: String): String {
        val fields = mapOf("name" to name.trim(), "relation" to relation.key, "timezone" to timezone)
        return if (parentId == null) {
            val ref = db.collection(Fs.PARENTS).document()
            ref.set(fields + mapOf("childIds" to listOf(uid), "createdAt" to FieldValue.serverTimestamp()))
                .awaitOrQueued()
            ref.id
        } else {
            parentDoc(parentId).update(fields).awaitOrQueued()
            parentId
        }
    }

    suspend fun deleteParent(parent: ParentProfile) {
        val ref = parentDoc(parent.id)
        for (sub in listOf(Fs.MEDICINES, Fs.DOSES)) {
            val docs = ref.collection(sub).get().await().documents
            docs.chunked(400).forEach { chunk ->
                db.batch().apply { chunk.forEach { delete(it.reference) } }.commit().await()
            }
        }
        db.batch().apply {
            parent.pairingCode?.let { delete(db.collection(Fs.PAIRING_CODES).document(it)) }
            delete(ref)
        }.commit().await()
    }

    suspend fun saveMedicine(parentId: String, medicine: Medicine): String {
        val medicines = parentDoc(parentId).collection(Fs.MEDICINES)
        val ref = if (medicine.id.isBlank()) medicines.document() else medicines.document(medicine.id)
        ref.set(medicine.toFirestore() + ("updatedAt" to FieldValue.serverTimestamp())).awaitOrQueued()
        return ref.id
    }

    suspend fun deleteMedicine(parentId: String, medId: String) {
        parentDoc(parentId).collection(Fs.MEDICINES).document(medId).delete().awaitOrQueued()
    }

    /**
     * Creates a fresh 6-digit pairing code valid for 24 hours, replacing the parent's previous code.
     * Security rules reject a code that already exists, so collisions are retried.
     */
    suspend fun generatePairingCode(parent: ParentProfile): Pair<String, Long> {
        val random = SecureRandom()
        var lastError: Exception? = null
        repeat(5) {
            val code = (100_000 + random.nextInt(900_000)).toString()
            val expiresAt = System.currentTimeMillis() + PAIRING_CODE_VALIDITY_MILLIS
            try {
                db.batch().apply {
                    parent.pairingCode?.let { delete(db.collection(Fs.PAIRING_CODES).document(it)) }
                    set(
                        db.collection(Fs.PAIRING_CODES).document(code),
                        mapOf("parentId" to parent.id, "expiresAt" to expiresAt.toTimestamp(), "createdBy" to uid),
                    )
                    update(parentDoc(parent.id), mapOf("pairingCode" to code, "pairingCodeExpiresAt" to expiresAt.toTimestamp()))
                }.commit().await()
                return code to expiresAt
            } catch (e: Exception) {
                lastError = e
            }
        }
        throw lastError ?: IllegalStateException("Could not create pairing code")
    }

    // --- Child's own profile ---

    data class UserProfile(val name: String, val phone: String?, val language: String)

    fun userProfile(): Flow<UserProfile> = db.collection(Fs.USERS).document(uid).asFlow().map {
        UserProfile(it.getString("name").orEmpty(), it.getString("phone"), it.getString("language") ?: "bn")
    }

    /** Creates users/{uid} on first sign-in without overwriting an existing name or language. */
    suspend fun ensureUserProfile(name: String?, phone: String?, language: String) {
        val ref = db.collection(Fs.USERS).document(uid)
        val existing = runCatching { ref.get().await() }.getOrNull()
        val fields = buildMap<String, Any> {
            if (existing?.getString("name").isNullOrBlank() && !name.isNullOrBlank()) put("name", name)
            if (!phone.isNullOrBlank()) put("phone", phone)
            if (existing?.getString("language") == null) put("language", language)
        }
        if (fields.isNotEmpty()) ref.set(fields, SetOptions.merge()).awaitOrQueued()
    }

    suspend fun updateUser(fields: Map<String, Any>) {
        db.collection(Fs.USERS).document(uid).set(fields, SetOptions.merge()).awaitOrQueued()
    }

    suspend fun registerFcmToken(token: String) =
        updateUser(mapOf("fcmTokens" to FieldValue.arrayUnion(token)))

    suspend fun unregisterFcmToken(token: String) =
        updateUser(mapOf("fcmTokens" to FieldValue.arrayRemove(token)))

    /** Adds one parent with three medicines so the app can be tried end to end. */
    suspend fun seedSampleData(): String {
        val parentId = saveParent(null, "রহিমা বেগম", Relation.MOTHER, ParentProfile.DEFAULT_TIMEZONE)
        val today = LocalDate.now(ZoneId.of(ParentProfile.DEFAULT_TIMEZONE)).toString()
        SAMPLE_MEDICINES.forEach { saveMedicine(parentId, it.copy(startDate = today)) }
        return parentId
    }

    companion object {
        const val PAIRING_CODE_VALIDITY_MILLIS = 24L * 3600 * 1000

        val SAMPLE_MEDICINES = listOf(
            Medicine("", "প্রেশারের ওষুধ (Amlodipine 5mg)", "১টা ট্যাবলেট", MealTiming.AFTER, listOf("08:00", "20:00"), ""),
            Medicine("", "ডায়াবেটিসের ওষুধ (Metformin 500mg)", "১টা ট্যাবলেট", MealTiming.BEFORE, listOf("07:30", "13:30", "19:30"), "",
                note = "খাওয়ার ১৫ মিনিট আগে"),
            Medicine("", "গ্যাসের ওষুধ (Omeprazole 20mg)", "১টা ক্যাপসুল", MealTiming.BEFORE, listOf("07:00"), ""),
        )
    }
}
