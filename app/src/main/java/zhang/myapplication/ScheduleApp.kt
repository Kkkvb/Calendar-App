package zhang.myapplication

import android.app.AlarmManager
import android.app.Application
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.getSystemService
import androidx.room.Room
import androidx.work.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import zhang.myapplication.data.AppDatabase
import zhang.myapplication.domain.CalendarSyncWorker
import zhang.myapplication.util.DataStoreManager
import java.util.concurrent.TimeUnit

class ScheduleApp : Application() {

    lateinit var db: AppDatabase; private set
    lateinit var alarmManager: AlarmManager; private set
    lateinit var dataStoreManager: DataStoreManager; private set

    override fun onCreate() {
        super.onCreate()

        // Room database
        db = Room.databaseBuilder(this, AppDatabase::class.java, "courses.db")
            .fallbackToDestructiveMigration()
            .build()

        // System services
        alarmManager = getSystemService(AlarmManager::class.java)
        dataStoreManager = DataStoreManager(this)

        // Create notification channels once (O+). Channels are immutable afterwards.
        createNotificationChannels()

        // Apply theme from DataStore
        CoroutineScope(Dispatchers.Default).launch {
            val dark = dataStoreManager.darkThemeEnabled.first()
            AppCompatDelegate.setDefaultNightMode(
                if (dark) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
            )
        }

        // Periodic calendar sync (user can toggle in Settings; worker checks before doing work)
        scheduleDailyCalendarSync()
    }

    private fun scheduleDailyCalendarSync() {
        val request = PeriodicWorkRequestBuilder<CalendarSyncWorker>(1, TimeUnit.DAYS)
            .setConstraints(
                Constraints.Builder()
                    .setRequiresBatteryNotLow(true)
                    .build()
            )
            .build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "calendar_sync",
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }

    private fun createNotificationChannels() {
        val nm = getSystemService<NotificationManager>() ?: return
        if (android.os.Build.VERSION.SDK_INT < 26) return

        // --- LOUD channel (alarm sound + vibration) ---
        if (nm.getNotificationChannel(CH_REMINDERS_LOUD) == null) {
            val ch = NotificationChannel(
                CH_REMINDERS_LOUD,
                "Course reminders (Loud)",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Alerts before each class (sound + vibration)"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 400, 200, 600)
                setSound(
                    android.media.RingtoneManager.getDefaultUri(
                        android.media.RingtoneManager.TYPE_ALARM
                    ),
                    android.media.AudioAttributes.Builder()
                        .setUsage(android.media.AudioAttributes.USAGE_ALARM)
                        .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            nm.createNotificationChannel(ch)
        }

        // --- SILENT channel (no sound/vibration) ---
        if (nm.getNotificationChannel(CH_REMINDERS_SILENT) == null) {
            val ch = NotificationChannel(
                CH_REMINDERS_SILENT,
                "Course reminders (Silent)",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Alerts without sound/vibration"
                enableVibration(false)
                setSound(null, null)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            nm.createNotificationChannel(ch)
        }
    }

    companion object {
        // Keep these in sync with ReminderReceiver
        const val CH_REMINDERS_LOUD = "course_reminders_loud_v2"
        const val CH_REMINDERS_SILENT = "course_reminders_silent_v1"
    }
}