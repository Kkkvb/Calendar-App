package zhang.myapplication.domain

import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import android.provider.CalendarContract.Calendars
import android.provider.CalendarContract.Events
import android.util.Log
import androidx.core.content.ContextCompat
import zhang.myapplication.data.Course
import java.time.ZonedDateTime

/**
 * Pushes the next few occurrences of courses into the user's calendar.
 * Picks a *visible, writable* calendar (OEM-safe) instead of relying on IS_PRIMARY.
 */
class CalendarSyncHelper(private val context: Context) {

    /**
     * Sync the next occurrences within [lookaheadDays] for the given [courses].
     * Returns the number of events inserted.
     */
    fun syncCoursesToCalendar(courses: List<Course>, lookaheadDays: Long = 3): Int {
        // Double-check permissions (caller should have asked already).
        val hasRead = ContextCompat.checkSelfPermission(
            context, android.Manifest.permission.READ_CALENDAR
        ) == PackageManager.PERMISSION_GRANTED
        val hasWrite = ContextCompat.checkSelfPermission(
            context, android.Manifest.permission.WRITE_CALENDAR
        ) == PackageManager.PERMISSION_GRANTED
        if (!hasRead || !hasWrite) {
            Log.w(TAG, "Missing calendar permissions; aborting sync")
            return 0
        }

        val calendarId = pickWritableCalendarId() ?: run {
            Log.w(TAG, "No visible writable calendar found")
            return 0
        }

        val now = ZonedDateTime.now()
        val cutoff = now.plusDays(lookaheadDays)

        // Delete our old upcoming events to avoid duplicates (tag via DESCRIPTION)
        try {
            val deleteWhere =
                "${Events.DTSTART} >= ? AND ${Events.CALENDAR_ID} = ? AND ${Events.DESCRIPTION} = ?"
            val deleteArgs = arrayOf(
                now.toInstant().toEpochMilli().toString(),
                calendarId.toString(),
                TAG_DESC
            )
            val del = context.contentResolver.delete(Events.CONTENT_URI, deleteWhere, deleteArgs)
            Log.d(TAG, "Deleted $del old events")
        } catch (se: SecurityException) {
            Log.e(TAG, "SecurityException on delete", se)
        }

        var inserted = 0
        for (course in courses) {
            val next = nextOccurrence(course, now) ?: continue
            if (next.isAfter(cutoff)) continue

            // Use the real course duration; if not stored, fall back to 50 minutes
            val end = next.withHour(course.endTime.hour).withMinute(course.endTime.minute)
            val endZdt = if (end.isAfter(next)) end else next.plusMinutes(50)

            val startMillis = next.toInstant().toEpochMilli()
            val endMillis = endZdt.toInstant().toEpochMilli()

            val values = ContentValues().apply {
                put(Events.CALENDAR_ID, calendarId)
                put(Events.DTSTART, startMillis)
                put(Events.DTEND, endMillis)
                put(Events.TITLE, course.title)
                put(Events.EVENT_LOCATION, course.location ?: "")
                put(Events.EVENT_TIMEZONE, next.zone.id)
                put(Events.DESCRIPTION, TAG_DESC)
            }

            try {
                val uri = context.contentResolver.insert(Events.CONTENT_URI, values)
                if (uri != null) {
                    inserted++
                }
            } catch (se: SecurityException) {
                Log.e(TAG, "SecurityException on insert", se)
            }
        }

        Log.i(TAG, "Inserted $inserted events into calendarId=$calendarId")
        return inserted
    }

    /**
     * Pick a *visible, writable* calendar. Prefer calendars whose access level
     * is >= CAL_ACCESS_CONTRIBUTOR; otherwise fall back to any visible calendar.
     */
    private fun pickWritableCalendarId(): Long? {
        // First pass: visible + writable (access level >= contributor)
        val writable = queryCalendars(
            selection = "${Calendars.VISIBLE}=? AND ${Calendars.CALENDAR_ACCESS_LEVEL}>=?",
            args = arrayOf("1", Calendars.CAL_ACCESS_CONTRIBUTOR.toString())
        )
        if (writable != null) return writable

        // Fallback: any visible calendar
        return queryCalendars(
            selection = "${Calendars.VISIBLE}=?",
            args = arrayOf("1")
        )
    }

    private fun queryCalendars(selection: String, args: Array<String>): Long? {
        val projection = arrayOf(
            Calendars._ID,
            Calendars.CALENDAR_DISPLAY_NAME,
            Calendars.CALENDAR_ACCESS_LEVEL,
            Calendars.VISIBLE
        )
        val cursor = context.contentResolver.query(
            Calendars.CONTENT_URI,
            projection,
            selection,
            args,
            null
        )
        cursor?.use {
            while (it.moveToNext()) {
                val id = it.getLong(0)
                val name = it.getString(1) ?: ""
                val level = it.getInt(2)
                val vis = it.getInt(3)
                Log.d(TAG, "Calendar candidate: id=$id, name=$name, level=$level, visible=$vis")
                return id
            }
        }
        return null
    }

    companion object {
        private const val TAG = "CalendarSync"
        private const val TAG_DESC = "CourseReminder"
    }
}