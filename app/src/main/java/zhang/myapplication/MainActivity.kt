package zhang.myapplication

import android.Manifest
import android.app.TimePickerDialog
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Menu
import android.view.MenuItem
import android.widget.ArrayAdapter
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.lifecycleScope
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.navigation.NavigationView
import kotlinx.coroutines.launch
import zhang.myapplication.databinding.ActivityMainBinding
import zhang.myapplication.databinding.DialogAddCourseBinding
import zhang.myapplication.domain.ReminderScheduler
import java.time.LocalTime

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

        // Sidebar View
        val drawerLayout = binding.root.findViewById<DrawerLayout>(R.id.drawer_layout)
        val navViewSide = findViewById<NavigationView>(R.id.nav_view_side)

        navViewSide.setNavigationItemSelectedListener { menuItem ->
            when (menuItem.itemId) {
                R.id.nav_settings -> {
                    findNavController(R.id.nav_host_fragment_activity_main)
                        .navigate(R.id.action_global_settingsFragment)
                    drawerLayout.closeDrawers()
                    true
                }
                else -> false
            }
        }

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
        /* if (BuildConfig.DEBUG) {
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
        } */
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_actions, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_add_course -> {
                showAddCourseDialog()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    fun showAddCourseDialog() {
        val app = application as ScheduleApp
        val dao = app.db.courseDao()
        val scheduler = ReminderScheduler(this, app.alarmManager)

        val binding = DialogAddCourseBinding.inflate(layoutInflater)

        // Day spinner
        val days = resources.getStringArray(R.array.days_of_week).toList()
        binding.spDay.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, days)

        var startTime: LocalTime? = null
        var endTime: LocalTime? = null

        binding.btnStartTime.setOnClickListener {
            val now = LocalTime.now()
            TimePickerDialog(this, { _, h, m ->
                startTime = LocalTime.of(h, m)
                binding.btnStartTime.text = "Start: %02d:%02d".format(h, m)
            }, now.hour, now.minute, true).show()
        }
        binding.btnEndTime.setOnClickListener {
            val now = LocalTime.now().plusMinutes(50)
            TimePickerDialog(this, { _, h, m ->
                endTime = LocalTime.of(h, m)
                binding.btnEndTime.text = "End: %02d:%02d".format(h, m)
            }, now.hour, now.minute, true).show()
        }

        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Add course")
            .setView(binding.root)
            .setNegativeButton("Cancel", null)

            .setPositiveButton("Save") { _, _ ->
                val title = binding.etTitle.text.toString().trim()
                val location = binding.etLocation.text.toString().trim().ifBlank { null }
                val reminderMin = binding.etReminder.text.toString().toIntOrNull() ?: 10

                val dayIdx = binding.spDay.selectedItemPosition // 0=Mon … 6=Sun
                val dayOfWeek = java.time.DayOfWeek.of(((dayIdx + 1 - 1) % 7) + 1)

                // Make them non-null locals with guard clauses
                val st = startTime ?: run {
                    android.widget.Toast.makeText(this, "Pick a start time", android.widget.Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                val et = endTime ?: run {
                    android.widget.Toast.makeText(this, "Pick an end time", android.widget.Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                if (title.isBlank()) {
                    android.widget.Toast.makeText(this, "Please enter a title", android.widget.Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                if (!et.isAfter(st)) {
                    android.widget.Toast.makeText(this, "End time must be after start time", android.widget.Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                val semesterStart = java.time.LocalDate.now()
                    .with(java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY))

                lifecycleScope.launch {
                    val course = zhang.myapplication.data.Course(
                        title = title,
                        location = location,
                        dayOfWeek = dayOfWeek,
                        startTime = st,   // now LocalTime, not LocalTime?
                        endTime = et,     // now LocalTime, not LocalTime?
                        semesterStart = semesterStart,
                        totalWeeks = 16,
                        weekFilter = zhang.myapplication.data.WeekFilter.ALL,
                        includedWeeks = emptySet(),
                        reminderMinutesBefore = reminderMin
                    )
                    val app = application as zhang.myapplication.ScheduleApp
                    val dao = app.db.courseDao()
                    val id = dao.upsert(course)
                    val saved = course.copy(id = id)

                    if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.S
                        || app.alarmManager.canScheduleExactAlarms()
                    ) {
                        val scheduler = zhang.myapplication.domain.ReminderScheduler(this@MainActivity, app.alarmManager)
                        scheduler.cancel(saved)
                        scheduler.scheduleNext(saved)
                        android.widget.Toast.makeText(this@MainActivity, "Saved & scheduled", android.widget.Toast.LENGTH_SHORT).show()
                    } else {
                        android.widget.Toast.makeText(
                            this@MainActivity,
                            "Saved. Enable exact alarms to schedule reminders.",
                            android.widget.Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }
            .show()
    }

}

