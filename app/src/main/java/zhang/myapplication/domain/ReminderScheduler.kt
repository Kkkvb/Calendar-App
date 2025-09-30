package zhang.myapplication.domain

import android.app.AlarmManager
import android.app.PendingIntent
import android.net.Uri
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import zhang.myapplication.data.Course
import zhang.myapplication.receiver.ReminderReceiver
import java.time.ZonedDateTime


class ReminderScheduler(
    private val context: Context,
    private val alarmManager: AlarmManager
) {
    fun scheduleNext(course: Course) {
        val now = ZonedDateTime.now()
        val next = nextOccurrence(course, now) ?: return
        val triggerAt = next.minusMinutes(course.reminderMinutesBefore.toLong())

        // --- Lint-friendly guard for Android 12+ ---
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val can = try {
                alarmManager.canScheduleExactAlarms()
            } catch (_: SecurityException) {
                false
            }
            if (!can) {
                // Optional: deep-link to your app’s Alarms & reminders screen
                context.startActivity(
                Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                .setData(Uri.parse("package:${context.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
                return // gracefully skip until user grants access
            }
        }

        val intent = Intent(context, ReminderReceiver::class.java).apply {
            putExtra("courseId", course.id)
            putExtra("title", course.title)
            putExtra("location", course.location ?: "")
            putExtra("startTime", next.toString())          // ZonedDateTime ISO
            putExtra("minutesBefore", course.reminderMinutesBefore)
        }
        val pi = PendingIntent.getBroadcast(
            context,
            course.id.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        try {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerAt.toInstant().toEpochMilli(),
                pi
            )
        } catch (_: SecurityException) {
            // Permission not granted at runtime on Android 14+; skip or notify user
        }
    }

    fun cancel(course: Course) {
        val pi = PendingIntent.getBroadcast(
            context,
            course.id.toInt(),
            Intent(context, ReminderReceiver::class.java),
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        ) ?: return
        alarmManager.cancel(pi)
        pi.cancel()
    }
}
