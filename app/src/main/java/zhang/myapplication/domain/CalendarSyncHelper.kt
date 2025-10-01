package zhang.myapplication.domain

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.provider.CalendarContract
import android.provider.CalendarContract.Events
import android.provider.CalendarContract.Calendars
import android.util.Log
import zhang.myapplication.data.Course
import java.time.ZonedDateTime
import java.util.*

class CalendarSyncHelper(private val context: Context) {

    private fun getPrimaryCalendarId(): Long? {
        val projection = arrayOf(Calendars._ID, Calendars.IS_PRIMARY)
        val uri = Calendars.CONTENT_URI
        val cursor = context.contentResolver.query(uri, projection, null, null, null)
        cursor?.use {
            while (it.moveToNext()) {
                val id = it.getLong(0)
                val isPrimary = it.getInt(1) == 1
                if (isPrimary) return id
            }
        }
        return null
    }

    fun syncCoursesToCalendar(courses: List<Course>) {
        val calendarId = getPrimaryCalendarId() ?: return
        val now = ZonedDateTime.now()
        val threeDaysLater = now.plusDays(3)

        // Delete old events
        val deleteWhere = "${Events.DTSTART} < ? AND ${Events.CALENDAR_ID} = ? AND ${Events.DESCRIPTION} = ?"
        val deleteArgs = arrayOf(now.toInstant().toEpochMilli().toString(), calendarId.toString(), "CourseReminder")
        context.contentResolver.delete(Events.CONTENT_URI, deleteWhere, deleteArgs)

        for (course in courses) {
            val next = nextOccurrence(course, now) ?: continue
            if (next.isAfter(threeDaysLater)) continue

            val startMillis = next.toInstant().toEpochMilli()
            val endMillis = next.plusMinutes(50).toInstant().toEpochMilli()

            val values = ContentValues().apply {
                put(Events.DTSTART, startMillis)
                put(Events.DTEND, endMillis)
                put(Events.TITLE, course.title)
                put(Events.EVENT_LOCATION, course.location ?: "")
                put(Events.CALENDAR_ID, calendarId)
                put(Events.EVENT_TIMEZONE, next.zone.id)
                put(Events.DESCRIPTION, "CourseReminder")
            }

            context.contentResolver.insert(Events.CONTENT_URI, values)
        }
    }
}
