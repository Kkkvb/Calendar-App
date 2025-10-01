package zhang.myapplication

import android.app.AlarmManager
import android.app.Application
import androidx.room.Room
import zhang.myapplication.data.AppDatabase
import androidx.work.*
import zhang.myapplication.domain.CalendarSyncWorker
import java.util.concurrent.TimeUnit

class ScheduleApp : Application() {
    lateinit var db: AppDatabase; private set
    lateinit var alarmManager: AlarmManager; private set

    override fun onCreate() {
        super.onCreate()
        db = Room.databaseBuilder(this, AppDatabase::class.java, "courses.db")
            .fallbackToDestructiveMigration()
            .build()
        alarmManager = getSystemService(AlarmManager::class.java)

        scheduleDailyCalendarSync()
    }
    private fun scheduleDailyCalendarSync() {
        val request = PeriodicWorkRequestBuilder<CalendarSyncWorker>(1, TimeUnit.DAYS)
            .setConstraints(
                Constraints.Builder()
                    .setRequiresBatteryNotLow(true)
                    .setRequiresDeviceIdle(false)
                    .build()
            )
            .build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "calendar_sync",
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }
}