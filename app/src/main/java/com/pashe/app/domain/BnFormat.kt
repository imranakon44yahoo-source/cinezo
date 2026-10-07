package com.pashe.app.domain

import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/** Formatting helpers that render Bengali digits and day periods ("সকাল ৮:০০"). */
object BnFormat {
    private const val BN_DIGITS = "০১২৩৪৫৬৭৮৯"
    private val BN_MONTHS = listOf(
        "জানুয়ারি", "ফেব্রুয়ারি", "মার্চ", "এপ্রিল", "মে", "জুন",
        "জুলাই", "আগস্ট", "সেপ্টেম্বর", "অক্টোবর", "নভেম্বর", "ডিসেম্বর",
    )
    private val BN_WEEKDAYS = listOf("সোমবার", "মঙ্গলবার", "বুধবার", "বৃহস্পতিবার", "শুক্রবার", "শনিবার", "রবিবার")

    fun digits(text: String): String = buildString {
        text.forEach { c -> append(if (c in '0'..'9') BN_DIGITS[c - '0'] else c) }
    }

    fun number(value: Int, bengali: Boolean): String = if (bengali) digits(value.toString()) else value.toString()

    /** "৮:০০" in 12-hour form, without a period. */
    fun clock(time: LocalTime, bengali: Boolean): String {
        val hour12 = if (time.hour % 12 == 0) 12 else time.hour % 12
        val text = "%d:%02d".format(hour12, time.minute)
        return if (bengali) digits(text) else text
    }

    fun period(time: LocalTime): String = when (time.hour) {
        in 4..11 -> "সকাল"
        in 12..14 -> "দুপুর"
        in 15..17 -> "বিকাল"
        in 18..19 -> "সন্ধ্যা"
        else -> "রাত"
    }

    /** "সকাল ৮:০০" or "8:00 AM". */
    fun time(time: LocalTime, bengali: Boolean): String =
        if (bengali) "${period(time)} ${clock(time, true)}"
        else time.format(DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH))

    /** "৭ অক্টোবর, বুধবার" or "Wed, 7 Oct". */
    fun date(date: LocalDate, bengali: Boolean): String =
        if (bengali) "${digits(date.dayOfMonth.toString())} ${BN_MONTHS[date.monthValue - 1]}, ${BN_WEEKDAYS[date.dayOfWeek.value - 1]}"
        else "${date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.ENGLISH)}, ${date.dayOfMonth} " +
            date.month.getDisplayName(TextStyle.SHORT, Locale.ENGLISH)

    /** "৭ অক্টো" style short date for compact rows. */
    fun shortDate(date: LocalDate, bengali: Boolean): String =
        if (bengali) "${digits(date.dayOfMonth.toString())} ${BN_MONTHS[date.monthValue - 1]}"
        else "${date.dayOfMonth} ${date.month.getDisplayName(TextStyle.SHORT, Locale.ENGLISH)}"
}
