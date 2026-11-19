package com.example.util

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Global Date utilities standardizing all dates across the app to the Brazilian/NR-10 format: dd/MM/yyyy.
 */
object DateUtils {

    const val DATE_PATTERN = "dd/MM/yyyy"
    const val DATE_TIME_PATTERN = "dd/MM/yyyy HH:mm"
    const val FULL_DATE_TIME_PATTERN = "dd/MM/yyyy HH:mm:ss"

    private val supportedDatePatterns = arrayOf(
        "dd/MM/yyyy",
        "yyyy-MM-dd",
        "dd-MM-yyyy",
        "yyyy/MM/dd",
        "dd/MM/yy",
        "yyyy-MM-dd'T'HH:mm:ss",
        "yyyy-MM-dd HH:mm:ss",
        "dd/MM/yyyy HH:mm:ss",
        "dd/MM/yyyy HH:mm"
    )

    /**
     * Returns today's date formatted as dd/MM/yyyy.
     */
    fun today(): String {
        return SimpleDateFormat(DATE_PATTERN, Locale.getDefault()).format(Date())
    }

    /**
     * Returns current date and time formatted as dd/MM/yyyy HH:mm:ss.
     */
    fun now(): String {
        return SimpleDateFormat(FULL_DATE_TIME_PATTERN, Locale.getDefault()).format(Date())
    }

    /**
     * Returns current date and time formatted as dd/MM/yyyy HH:mm.
     */
    fun nowShort(): String {
        return SimpleDateFormat(DATE_TIME_PATTERN, Locale.getDefault()).format(Date())
    }

    /**
     * Parses any incoming date string (such as "yyyy-MM-dd" or "dd/MM/yyyy") into a java.util.Date.
     */
    fun parseDate(dateStr: String?): Date? {
        if (dateStr.isNullOrBlank()) return null
        val clean = dateStr.trim()
        for (pattern in supportedDatePatterns) {
            try {
                val sdf = SimpleDateFormat(pattern, Locale.getDefault())
                sdf.isLenient = false
                return sdf.parse(clean)
            } catch (_: Exception) {
                // Continue to next pattern
            }
        }
        return null
    }

    /**
     * Formats any date string into the standard "dd/MM/yyyy" pattern.
     * If already "dd/MM/yyyy", returns it directly. If invalid, returns the original trimmed string.
     */
    fun formatDate(dateStr: String?): String {
        if (dateStr.isNullOrBlank()) return ""
        val trimmed = dateStr.trim()

        // Quick check for already formatted dd/MM/yyyy
        if (trimmed.matches(Regex("^\\d{2}/\\d{2}/\\d{4}$"))) {
            return trimmed
        }

        // Quick conversion for ISO yyyy-MM-dd
        if (trimmed.matches(Regex("^(\\d{4})-(\\d{2})-(\\d{2})$"))) {
            val parts = trimmed.split("-")
            if (parts.size == 3) {
                return "${parts[2]}/${parts[1]}/${parts[0]}"
            }
        }

        val parsed = parseDate(trimmed) ?: return trimmed
        return SimpleDateFormat(DATE_PATTERN, Locale.getDefault()).format(parsed)
    }

    /**
     * Formats any datetime string into "dd/MM/yyyy HH:mm:ss".
     */
    fun formatDateTime(dateTimeStr: String?): String {
        if (dateTimeStr.isNullOrBlank()) return ""
        val trimmed = dateTimeStr.trim()
        val parsed = parseDate(trimmed) ?: return trimmed
        return SimpleDateFormat(FULL_DATE_TIME_PATTERN, Locale.getDefault()).format(parsed)
    }

    /**
     * Calculates the retest due date by adding [monthsToAdd] months to [baseDateStr].
     * Returns the resulting date formatted as "dd/MM/yyyy".
     */
    fun calculateRetestDueDate(baseDateStr: String?, monthsToAdd: Int): String {
        val baseDate = parseDate(baseDateStr) ?: Date()
        val cal = Calendar.getInstance().apply {
            time = baseDate
            add(Calendar.MONTH, monthsToAdd)
        }
        return SimpleDateFormat(DATE_PATTERN, Locale.getDefault()).format(cal.time)
    }

    /**
     * Checks if a due date has expired (strictly before today's start of day).
     */
    fun isExpired(dateStr: String?): Boolean {
        val date = parseDate(dateStr) ?: return false
        val todayCalendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val targetCalendar = Calendar.getInstance().apply {
            time = date
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return targetCalendar.before(todayCalendar)
    }

    /**
     * Checks if a due date is expiring soon (within [daysThreshold] days from today).
     */
    fun isExpiringSoon(dateStr: String?, daysThreshold: Int = 30): Boolean {
        val date = parseDate(dateStr) ?: return false
        val todayCalendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val targetCalendar = Calendar.getInstance().apply {
            time = date
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        if (targetCalendar.before(todayCalendar)) return false

        val maxCalendar = Calendar.getInstance().apply {
            time = todayCalendar.time
            add(Calendar.DAY_OF_YEAR, daysThreshold)
        }

        return !targetCalendar.after(maxCalendar)
    }
}
