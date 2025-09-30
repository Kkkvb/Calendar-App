package zhang.myapplication

import android.app.AlarmManager
import android.app.Application
import androidx.room.Room
import zhang.myapplication.data.AppDatabase

class ScheduleApp : Application() {

    lateinit var db: AppDatabase
        private set

    lateinit var alarmManager: AlarmManager
        private set

    override fun onCreate() {
        super.onCreate()

        // Initialize Room DB (single instance)
        db = Room.databaseBuilder(
            applicationContext,
            AppDatabase::class.java,
            "courses.db"
        )
            .fallbackToDestructiveMigration() // ok for dev; remove when you add migrations
            .build()

        // Grab AlarmManager system service once
        alarmManager = getSystemService(AlarmManager::class.java)
    }
}