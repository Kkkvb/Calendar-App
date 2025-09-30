
package zhang.myapplication

import android.Manifest
import android.app.AlarmManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import com.google.android.material.bottomnavigation.BottomNavigationView
import zhang.myapplication.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    // We’ll ask for notifications on Android 13+ when the app starts
    private val requestNotifPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* you can check granted/denied here if needed */ }

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

        // === NEW: Request the permissions we will need for reminders ===
        requestPostNotificationsIfNeeded()
        ensureExactAlarmPermissionIfNeeded(alarmManager)

        // (Optional sanity check) Read DB — uncomment if you want to test
        // lifecycleScope.launch {
        //     val courses = dao.getAll()
        //     Log.d("MainActivity", "Courses in DB: ${courses.size}")
        // }
    }

    private fun requestPostNotificationsIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33) {
            requestNotifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun ensureExactAlarmPermissionIfNeeded(alarmManager: AlarmManager) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (!alarmManager.canScheduleExactAlarms()) {
                // Opens the system Settings screen where user can allow exact alarms
                startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM))
            }
        }
    }
}
