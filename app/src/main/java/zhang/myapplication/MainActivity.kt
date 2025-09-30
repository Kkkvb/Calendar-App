package zhang.myapplication

import android.Manifest
import android.app.AlarmManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import com.google.android.material.bottomnavigation.BottomNavigationView
import zhang.myapplication.databinding.ActivityMainBinding
import zhang.myapplication.BuildConfig

import kotlinx.coroutines.launch
import java.time.*
import zhang.myapplication.domain.ReminderScheduler
import zhang.myapplication.data.Course
import zhang.myapplication.data.WeekFilter

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    // We’ll ask for notifications on Android 13+ when the app starts
    private val requestNotifPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* handle result if you want */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val navView: BottomNavigationView = binding.navView
        val navController = findNavController(R.id.nav_host_fragment_activity_main)

        val appBarConfiguration = AppBarConfiguration(
            setOf(
                R.id.navigation_home,
                R.id.navigation_dashboard,
                R.id.navigation_notifications
            )
        )
        setupActionBarWithNavController(navController, appBarConfiguration)
        navView.setupWithNavController(navController)

        // === NEW: Access singletons from ScheduleApp ===
        val app = application as ScheduleApp
        val dao = app.db.courseDao()          // you can use this in your fragments
        val alarmManager = app.alarmManager   // we'll use this once reminders are added
        val scheduler = ReminderScheduler(this, alarmManager) // <-- define scheduler

        // === Permissions ===
        // Notifications (Android 13+)
        if (Build.VERSION.SDK_INT >= 33) {
            requestNotifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        // Exact alarms (Android 12+): deep-link to the app-specific page if needed
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (!alarmManager.canScheduleExactAlarms()) {
                startActivity(
                    Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                        .setData(android.net.Uri.parse("package:$packageName"))
                )
            }
        }


        // DEBUG-ONLY seed to see a reminder soon
        if (BuildConfig.DEBUG) {
            lifecycleScope.launch {
                val now = ZonedDateTime.now()
                val start = now.toLocalTime().plusMinutes(3).withSecond(0).withNano(0)
                val end = start.plusMinutes(50)

                val course = Course(
                    title = "Test Class",
                    location = "Room 101",
                    dayOfWeek = now.dayOfWeek,
                    startTime = start,
                    endTime = end,
                    semesterStart = LocalDate.now()
                        .with(java.time.temporal.TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)),
                    totalWeeks = 16,
                    weekFilter = WeekFilter.ALL,
                    reminderMinutesBefore = 2
                )
                val id = dao.upsert(course)
                val saved = course.copy(id = id)
                scheduler.cancel(saved)     // avoid duplicates on repeated runs
                scheduler.scheduleNext(saved)
            }
        }
    }
}

