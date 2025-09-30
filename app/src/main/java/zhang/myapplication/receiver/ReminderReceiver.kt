package zhang.myapplication.receiver

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*
import java.time.ZonedDateTime
import zhang.myapplication.R
import zhang.myapplication.ScheduleApp
import zhang.myapplication.domain.ReminderScheduler

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val courseId = intent.getLongExtra("courseId", -1L)
        val title = intent.getStringExtra("title") ?: "Class"
        val location = intent.getStringExtra("location").orEmpty()
        val minutesBefore = intent.getIntExtra("minutesBefore", 10)
        val startZdt = intent.getStringExtra("startTime")?.let { ZonedDateTime.parse(it) }

        val channelId = "course_reminders"
        val nm = context.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(
                NotificationChannel(channelId, "Course reminders", NotificationManager.IMPORTANCE_HIGH)
            )
        }

        val text = buildString {
            append("Starts in $minutesBefore min")
            if (startZdt != null) append(" · ${startZdt.toLocalTime()}")
            if (location.isNotBlank()) append(" · $location")
        }

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_notifications_black_24dp) // <-- use existing drawable
            .setContentTitle(title)
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        nm.notify(courseId.toInt(), notification)

        // Chain: schedule the NEXT reminder for this course
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val app = context.applicationContext as ScheduleApp
                val dao = app.db.courseDao()
                val course = dao.getById(courseId)
                if (course != null) {
                    ReminderScheduler(context, app.alarmManager).scheduleNext(course)
                }
            } finally {
                pending.finish()
            }
        }
    }
}
