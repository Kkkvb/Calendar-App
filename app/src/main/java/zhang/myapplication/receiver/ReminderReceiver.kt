
package zhang.myapplication.receiver

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import zhang.myapplication.MainActivity
import zhang.myapplication.R
import zhang.myapplication.ScheduleApp
import zhang.myapplication.domain.ReminderScheduler
import java.time.ZonedDateTime

/**
 * Shows the reminder notification and chains the next reminder.
 * Now supports:
 *  - Loud/silent channels (sound/vibration controlled by channel choice)
 *  - Full-screen intent on Android 14+ when user has granted the special access
 */
class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val courseId   = intent.getLongExtra("courseId", -1L)
        val title      = intent.getStringExtra("title") ?: "Class"
        val location   = intent.getStringExtra("location").orEmpty()
        val minutesBef = intent.getIntExtra("minutesBefore", 10)
        val startZdt   = intent.getStringExtra("startTime")?.let { ZonedDateTime.parse(it) }

        val app = context.applicationContext as ScheduleApp
        val dataStore = app.dataStoreManager

        // Read settings synchronously for simplicity in a receiver
        val soundEnabled      = runBlocking { dataStore.notificationSoundEnabled.first() }
        val vibrateEnabled    = runBlocking { dataStore.notificationVibrateEnabled.first() }
        val fullscreenEnabled = runBlocking { dataStore.fullscreenAlertEnabled.first() }

        val nm = context.getSystemService(NotificationManager::class.java)

        // Decide channel by settings (channels are immutable; choose the right one at post time)
        val channelId = if (soundEnabled || vibrateEnabled || fullscreenEnabled) {
            CHANNEL_ID_LOUD
        } else {
            CHANNEL_ID_SILENT
        }
        ensureChannel(nm, channelId)

        val text = buildString {
            append("Starts in $minutesBef min")
            if (startZdt != null) append(" · ${startZdt.toLocalTime()}")
            if (location.isNotBlank()) append(" · $location")
        }

        // Content tap → open app
        val contentIntent = Intent(context, MainActivity::class.java)
        val contentPi = PendingIntent.getActivity(
            context, 0, contentIntent, PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_notifications_black_24dp)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(contentPi)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)

        // --- Full-screen intent (Android 14+ requires special user grant) ---
        val canFSI = if (Build.VERSION.SDK_INT >= 34) nm.canUseFullScreenIntent() else true
        if (fullscreenEnabled && canFSI) {
            val fsIntent = Intent(context, MainActivity::class.java)
            val fsPi = PendingIntent.getActivity(context, 1, fsIntent, PendingIntent.FLAG_IMMUTABLE)
            builder.setFullScreenIntent(fsPi, true)
        } else if (Build.VERSION.SDK_INT >= 34 && fullscreenEnabled) {
            // Offer an action to open the OS setting so the user can grant it
            val settingsIntent =
                Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            val settingsPi = PendingIntent.getActivity(
                context, 2, settingsIntent, PendingIntent.FLAG_IMMUTABLE
            )
            builder.addAction(0, "Enable full-screen", settingsPi)
        }

        nm.notify(courseId.toInt(), builder.build())

        // Chain next reminder safely on a background thread
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
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

    // region Channels

    private fun ensureChannel(nm: NotificationManager, id: String) {
        if (nm.getNotificationChannel(id) != null) return

        val channel = when (id) {
            CHANNEL_ID_LOUD -> NotificationChannel(
                CHANNEL_ID_LOUD,
                "Course reminders (Loud)",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Alerts before each class (sound + vibration)"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 400, 200, 600)
                setSound(
                    RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }

            CHANNEL_ID_SILENT -> NotificationChannel(
                CHANNEL_ID_SILENT,
                "Course reminders (Silent)",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Alerts without sound/vibration"
                enableVibration(false)
                setSound(null, null)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }

            else -> null
        }

        if (channel != null) nm.createNotificationChannel(channel)
    }

    companion object {
        private const val CHANNEL_ID_LOUD = "course_reminders_loud_v2"
        private const val CHANNEL_ID_SILENT = "course_reminders_silent_v1"
    }
    // endregion
}