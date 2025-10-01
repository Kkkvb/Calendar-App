package zhang.myapplication

import android.app.AlarmManager
import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.room.Room
import zhang.myapplication.data.AppDatabase
import androidx.work.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import zhang.myapplication.domain.CalendarSyncWorker
import zhang.myapplication.domain.dataStore
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

        // Apply theme from DataStore
        CoroutineScope(Dispatchers.Default).launch {
            val darkMode = dataStore.data
                .map { it[booleanPreferencesKey("dark_theme_enabled")] ?: false }
                .first()
            AppCompatDelegate.setDefaultNightMode(
                if (darkMode) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
            )
        }

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