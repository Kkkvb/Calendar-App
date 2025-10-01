package zhang.myapplication.ui.settings

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.*
import android.widget.CompoundButton
import android.widget.Toast
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import zhang.myapplication.ScheduleApp
import zhang.myapplication.data.Course
import zhang.myapplication.databinding.FragmentSettingsBinding
import zhang.myapplication.util.dataStore
import zhang.myapplication.util.AUTO_SYNC_KEY
import zhang.myapplication.util.FULLSCREEN_ALERT_KEY
import zhang.myapplication.util.LAST_SYNC_KEY
import zhang.myapplication.util.NOTIF_SOUND_KEY
import zhang.myapplication.util.NOTIF_VIBRATE_KEY
import java.io.File
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.temporal.TemporalAdjusters

class SettingsFragment : Fragment() {
    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    @SuppressLint("SetTextI18n")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val context = requireContext()

        lifecycleScope.launch {
            val prefs = context.dataStore.data.first()

            binding.switchAutoSync.isChecked = prefs[AUTO_SYNC_KEY] ?: true
            binding.switchFullscreen.isChecked = prefs[FULLSCREEN_ALERT_KEY] ?: false
            binding.switchSound.isChecked = prefs[NOTIF_SOUND_KEY] ?: true
            binding.switchVibrate.isChecked = prefs[NOTIF_VIBRATE_KEY] ?: true

            val lastSync = prefs[LAST_SYNC_KEY] ?: "Never"
            binding.tvLastSync.text = "Last sync: $lastSync"
        }

        binding.switchAutoSync.setOnCheckedChangeListener { _, isChecked ->
            lifecycleScope.launch {
                context.dataStore.edit { it[AUTO_SYNC_KEY] = isChecked }
            }
        }

        binding.switchFullscreen.setOnCheckedChangeListener { _, isChecked ->
            lifecycleScope.launch {
                context.dataStore.edit { it[FULLSCREEN_ALERT_KEY] = isChecked }
            }
        }

        binding.switchSound.setOnCheckedChangeListener { _, isChecked ->
            lifecycleScope.launch {
                context.dataStore.edit { it[NOTIF_SOUND_KEY] = isChecked }
            }
        }

        binding.switchVibrate.setOnCheckedChangeListener { _, isChecked ->
            lifecycleScope.launch {
                context.dataStore.edit { it[NOTIF_VIBRATE_KEY] = isChecked }
            }
        }

        binding.switchTheme.setOnCheckedChangeListener { _, isChecked ->
            lifecycleScope.launch {
                context.dataStore.edit { it[booleanPreferencesKey("dark_theme_enabled")] = isChecked }
            }
            AppCompatDelegate.setDefaultNightMode(
                if (isChecked) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
            )
        }

        binding.btnExport.setOnClickListener {
            val app = requireActivity().application as ScheduleApp
            val dao = app.db.courseDao()

            lifecycleScope.launch {
                val courses = dao.getAll()
                val exportText = courses.joinToString("\n") { course ->
                    "${course.title}, ${course.dayOfWeek}, ${course.startTime}–${course.endTime}, ${course.location}"
                }

                val fileName = "courses_export_${System.currentTimeMillis()}.txt"
                val file = File(context.getExternalFilesDir(null), fileName)
                file.writeText(exportText)

                Toast.makeText(context, "Exported to ${file.absolutePath}", Toast.LENGTH_LONG).show()
            }
        }

        binding.btnImport.setOnClickListener {
            val app = requireActivity().application as ScheduleApp
            val dao = app.db.courseDao()

            val dir = context.getExternalFilesDir(null)
            val files = dir?.listFiles()?.filter { it.name.startsWith("courses_export_") }?.sortedByDescending { it.lastModified() }

            if (files.isNullOrEmpty()) {
                Toast.makeText(context, "No export file found", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val file = files.first()
            val lines = file.readLines()
            lifecycleScope.launch {
                for (line in lines) {
                    val parts = line.split(", ")
                    if (parts.size == 4) {
                        val title = parts[0]
                        val dayOfWeek = DayOfWeek.valueOf(parts[1].uppercase())
                        val times = parts[2].split("-")
                        val location = parts[3]
                        if (times.size == 2) {
                            val course = Course(
                                title = title,
                                dayOfWeek = dayOfWeek,
                                startTime = LocalTime.parse(times[0]),
                                endTime = LocalTime.parse(times[1]),
                                location = location,
                                semesterStart = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)),
                                totalWeeks = 16
                            )
                            dao.upsert(course)
                        }
                    }
                }
                Toast.makeText(context, "Courses imported", Toast.LENGTH_SHORT).show()
            }
        }

        // Request calendar permission if not granted
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_CALENDAR)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(requireActivity(), arrayOf(Manifest.permission.WRITE_CALENDAR), 1002)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
