package zhang.myapplication.receiver

import android.app.*
import android.content.*
import android.os.Build
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import zhang.myapplication.MainActivity
import java.time.ZonedDateTime
import zhang.myapplication.R
import zhang.myapplication.ScheduleApp
import zhang.myapplication.domain.ReminderScheduler
import zhang.myapplication.util.*

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val courseId = intent.getLongExtra("courseId", -1L)
        val title = intent.getStringExtra("title") ?: "Class"
        val location = intent.getStringExtra("location").orEmpty()
        val minutesBefore = intent.getIntExtra("minutesBefore", 10)
        val startZdt = intent.getStringExtra("startTime")?.let { ZonedDateTime.parse(it) }

        val channelId = "course_reminders"
        val nm = context.getSystemService(NotificationManager::class.java)

        nm.createNotificationChannel(
            NotificationChannel(channelId, "Course reminders", NotificationManager.IMPORTANCE_HIGH)
        )

        val prefs = runBlocking {
            context.dataStore.data.first()
        }

        val soundEnabled = prefs[NOTIF_SOUND_KEY] ?: true
        val vibrateEnabled = prefs[NOTIF_VIBRATE_KEY] ?: true
        val fullscreenEnabled = prefs[FULLSCREEN_ALERT_KEY] ?: false

        val text = buildString {
            append("Starts in $minutesBefore min")
            if (startZdt != null) append(" · ${startZdt.toLocalTime()}")
            if (location.isNotBlank()) append(" · $location")
        }

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_notifications_black_24dp)
            .setContentTitle(title)
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)

        if (soundEnabled) builder.setDefaults(NotificationCompat.DEFAULT_SOUND)
        if (vibrateEnabled) builder.setDefaults(NotificationCompat.DEFAULT_VIBRATE)

        if (fullscreenEnabled) {
            val intentFS = Intent(context, MainActivity::class.java)
            val pi = PendingIntent.getActivity(context, 0, intentFS, PendingIntent.FLAG_IMMUTABLE)
            builder.setFullScreenIntent(pi, true)
        }

        nm.notify(courseId.toInt(), builder.build())

        // Chain next reminder
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
