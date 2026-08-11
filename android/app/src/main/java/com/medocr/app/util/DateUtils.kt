package com.medocr.app.util

import com.medocr.app.data.model.DateGroup
import java.time.LocalDate
import java.time.Year

/**
 * Date parsing/merging helpers. Dates in this app are always written by the
 * lab in DD/MM/YY(YY) order — never MM/DD — matching `static/app.js`'s
 * `parseDateForSort` / `normaliseDateKey` and `sheets_writer.py`'s `format_date`.
 */
object DateUtils {

    private val MONTH_NAMES = mapOf(
        1 to "Jan", 2 to "Feb", 3 to "Mar", 4 to "Apr", 5 to "May", 6 to "Jun",
        7 to "Jul", 8 to "Aug", 9 to "Sep", 10 to "Oct", 11 to "Nov", 12 to "Dec",
    )

    private data class Parsed(val day: Int, val month: Int, val year: Int)

    private fun parse(dateStr: String): Parsed? {
        if (dateStr.isBlank()) return null
        val parts = dateStr.trim().replace(Regex("[-.\\s]"), "/").split("/")
        if (parts.size < 2) return null
        return try {
            val day = parts[0].trim().toInt()
            val month = parts[1].trim().toInt()
            var year = if (parts.size > 2) parts[2].trim().toInt() else Year.now().value % 100
            if (year < 100) year += 2000
            Parsed(day, month, year)
        } catch (e: NumberFormatException) {
            null
        }
    }

    /** Sortable key for a date string; unparseable dates sort to the end. */
    fun parseDateForSort(dateStr: String): Long {
        val p = parse(dateStr) ?: return Long.MAX_VALUE
        return try {
            LocalDate.of(p.year, p.month, p.day).toEpochDay()
        } catch (e: Exception) {
            Long.MAX_VALUE
        }
    }

    /** Canonical `D/M/YYYY` key (no leading zeros) used to merge same-day groups. */
    fun normalizeDateKey(dateStr: String): String {
        val p = parse(dateStr) ?: return dateStr.trim()
        return "${p.day}/${p.month}/${p.year}"
    }

    /** `7 Mar 2026` style, matching the format written into the Sheet. */
    fun formatDateForSheet(dateStr: String): String {
        val p = parse(dateStr) ?: return dateStr
        val monthName = MONTH_NAMES[p.month] ?: p.month.toString()
        return "${p.day} $monthName ${p.year}"
    }

    fun formatNameAgeGender(name: String, age: String, gender: String): String {
        val namePart = name.trim().uppercase()
        val agePart = age.trim()
        val genderPart = gender.trim().uppercase()
        return when {
            agePart.isNotEmpty() && genderPart.isNotEmpty() -> "$namePart $agePart/$genderPart"
            agePart.isNotEmpty() -> "$namePart $agePart"
            else -> namePart
        }
    }

    /**
     * Merges groups that share the same calendar date (across multiple scanned
     * images) and returns them sorted chronologically, earliest first.
     */
    fun mergeAndSortDateGroups(groups: List<DateGroup>): List<DateGroup> {
        val merged = LinkedHashMap<String, DateGroup>()
        for (g in groups) {
            val key = normalizeDateKey(g.date)
            val existing = merged[key]
            if (existing != null) {
                merged[key] = existing.copy(
                    patients = existing.patients + g.patients,
                    dateConfidence = maxOf(existing.dateConfidence, g.dateConfidence),
                )
            } else {
                merged[key] = g
            }
        }
        return merged.values.sortedBy { parseDateForSort(it.date) }
    }
}
