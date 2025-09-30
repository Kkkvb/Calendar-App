package zhang.myapplication

import android.app.AlarmManager
import android.app.Application
import androidx.room.Room
import zhang.myapplication.data.AppDatabase

class ScheduleApp : Application() {
    lateinit var db: AppDatabase; private set
    lateinit var alarmManager: AlarmManager; private set

    override fun onCreate() {
        super.onCreate()
        db = Room.databaseBuilder(this, AppDatabase::class.java, "courses.db")
            .fallbackToDestructiveMigration()
            .build()
        alarmManager = getSystemService(AlarmManager::class.java)
    }
}