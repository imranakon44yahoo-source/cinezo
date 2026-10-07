package com.pashe.app.data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.pashe.app.domain.AppMode
import com.pashe.app.domain.DoseStatus
import com.pashe.app.domain.Medicine
import com.pashe.app.domain.ParentProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/**
 * Device-local state. On a parent phone this is the offline copy of the schedule that alarms are
 * computed from, so reminders keep working without internet. SharedPreferences is used because
 * broadcast receivers need synchronous reads.
 */
class LocalStore(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("pashe", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    private val _version = MutableStateFlow(0)

    /** Bumps on every change so UI can recompute from the store. */
    val version: StateFlow<Int> = _version

    // Held in a field: SharedPreferences keeps listeners only weakly.
    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> _version.value++ }

    init {
        prefs.registerOnSharedPreferenceChangeListener(listener)
    }

    var disclaimerAccepted: Boolean
        get() = prefs.getBoolean(KEY_DISCLAIMER, false)
        set(value) = prefs.edit { putBoolean(KEY_DISCLAIMER, value) }

    var appMode: AppMode?
        get() = prefs.getString(KEY_MODE, null)?.let { runCatching { AppMode.valueOf(it) }.getOrNull() }
        set(value) = prefs.edit { putString(KEY_MODE, value?.name) }

    var parentId: String?
        get() = prefs.getString(KEY_PARENT_ID, null)
        set(value) = prefs.edit { putString(KEY_PARENT_ID, value) }

    var parentProfile: ParentProfile?
        get() = prefs.getString(KEY_PARENT_PROFILE, null)?.let {
            runCatching { json.decodeFromString(ParentProfile.serializer(), it) }.getOrNull()
        }
        set(value) = prefs.edit {
            putString(KEY_PARENT_PROFILE, value?.let { json.encodeToString(ParentProfile.serializer(), it) })
        }

    var medicines: List<Medicine>
        get() = prefs.getString(KEY_MEDICINES, null)?.let {
            runCatching { json.decodeFromString(medicineList, it) }.getOrNull()
        } ?: emptyList()
        set(value) = prefs.edit { putString(KEY_MEDICINES, json.encodeToString(medicineList, value)) }

    /** Last known server status per dose id (recent days only). */
    var remoteStatuses: Map<String, DoseStatus>
        get() = readMap(KEY_REMOTE).mapValues { DoseStatus.fromKey(it.value) }
        set(value) = writeMap(KEY_REMOTE, value.mapValues { it.value.key })

    /** Doses confirmed on this phone: dose id -> confirmation time. Survives until synced and beyond. */
    val takenLocally: Map<String, Long>
        get() = readMap(KEY_TAKEN).mapNotNull { (k, v) -> v.toLongOrNull()?.let { k to it } }.toMap()

    fun markTaken(doseIds: Collection<String>, at: Long) {
        val cutoff = at - RETENTION_MILLIS
        val updated = takenLocally.filterValues { it >= cutoff } + doseIds.associateWith { at }
        writeMap(KEY_TAKEN, updated.mapValues { it.value.toString() })
    }

    /** Slot times (epoch millis) that currently have a main alarm registered. */
    var scheduledSlots: Set<Long>
        get() = prefs.getStringSet(KEY_SLOTS, emptySet())!!.mapNotNull { it.toLongOrNull() }.toSet()
        set(value) = prefs.edit { putStringSet(KEY_SLOTS, value.map { it.toString() }.toSet()) }

    fun clearParentData() = prefs.edit {
        remove(KEY_PARENT_ID); remove(KEY_PARENT_PROFILE); remove(KEY_MEDICINES)
        remove(KEY_REMOTE); remove(KEY_TAKEN); remove(KEY_SLOTS)
    }

    private fun readMap(key: String): Map<String, String> = prefs.getString(key, null)?.let {
        runCatching { json.decodeFromString(stringMap, it) }.getOrNull()
    } ?: emptyMap()

    private fun writeMap(key: String, value: Map<String, String>) =
        prefs.edit { putString(key, json.encodeToString(stringMap, value)) }

    private companion object {
        const val KEY_DISCLAIMER = "disclaimer_accepted"
        const val KEY_MODE = "app_mode"
        const val KEY_PARENT_ID = "parent_id"
        const val KEY_PARENT_PROFILE = "parent_profile"
        const val KEY_MEDICINES = "medicines"
        const val KEY_REMOTE = "remote_statuses"
        const val KEY_TAKEN = "taken_locally"
        const val KEY_SLOTS = "scheduled_slots"
        const val RETENTION_MILLIS = 8L * 24 * 3600 * 1000
        val medicineList = ListSerializer(Medicine.serializer())
        val stringMap = MapSerializer(String.serializer(), String.serializer())
    }
}
