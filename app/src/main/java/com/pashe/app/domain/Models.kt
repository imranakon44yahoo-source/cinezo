package com.pashe.app.domain

import kotlinx.serialization.Serializable

/** Which side of the family this install belongs to. Chosen once on first launch. */
enum class AppMode { CHILD, PARENT }

@Serializable
enum class Relation(val key: String) {
    MOTHER("mother"), FATHER("father"), OTHER("other");

    companion object {
        fun fromKey(key: String?): Relation = entries.firstOrNull { it.key == key } ?: OTHER
    }
}

@Serializable
enum class MealTiming(val key: String) {
    BEFORE("before"), AFTER("after");

    companion object {
        fun fromKey(key: String?): MealTiming = entries.firstOrNull { it.key == key } ?: AFTER
    }
}

enum class DoseStatus(val key: String) {
    PENDING("pending"), TAKEN("taken"), MISSED("missed");

    companion object {
        fun fromKey(key: String?): DoseStatus = entries.firstOrNull { it.key == key } ?: PENDING
    }
}

@Serializable
data class ParentProfile(
    val id: String,
    val name: String,
    val relation: Relation,
    val timezone: String = DEFAULT_TIMEZONE,
    val childIds: List<String> = emptyList(),
    val deviceUid: String? = null,
    val pairingCode: String? = null,
    val pairingCodeExpiresAt: Long? = null,
) {
    /** What the alarm and alerts call this person: "আম্মা", "আব্বা" or their name. */
    fun displayName(bengali: Boolean = true): String = when (relation) {
        Relation.MOTHER -> if (bengali) "আম্মা" else "Amma"
        Relation.FATHER -> if (bengali) "আব্বা" else "Abba"
        Relation.OTHER -> name
    }

    companion object {
        const val DEFAULT_TIMEZONE = "Asia/Dhaka"
    }
}

/**
 * A medicine schedule. [times] are "HH:mm" in the parent's timezone; [startDate]/[endDate] are
 * ISO "yyyy-MM-dd" dates in the parent's timezone, end date inclusive.
 */
@Serializable
data class Medicine(
    val id: String,
    val name: String,
    val dose: String,
    val mealTiming: MealTiming,
    val times: List<String>,
    val startDate: String,
    val endDate: String? = null,
    val note: String? = null,
    val active: Boolean = true,
)

/** A dose document as stored under parents/{parentId}/doses/{doseId}. */
data class DoseRecord(
    val id: String,
    val medId: String,
    val scheduledAt: Long,
    val status: DoseStatus,
    val confirmedAt: Long? = null,
)
