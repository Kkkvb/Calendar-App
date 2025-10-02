package zhang.myapplication.ui.settings
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import zhang.myapplication.ScheduleApp
import zhang.myapplication.databinding.FragmentSettingsBinding
import zhang.myapplication.domain.ReminderScheduler
import zhang.myapplication.data.Course
import zhang.myapplication.data.WeekFilter
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeParseException
import android.view.WindowManager
import androidx.drawerlayout.widget.DrawerLayout
import zhang.myapplication.R

class SettingsFragmentOriginal : Fragment() {
    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    // --- Storage Access Framework launchers ---
    private val exportLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) exportCoursesTo(uri) else
            Toast.makeText(requireContext(), "Export canceled", Toast.LENGTH_SHORT).show()
    }

    private val importLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) importCoursesFrom(uri) else
            Toast.makeText(requireContext(), "Import canceled", Toast.LENGTH_SHORT).show()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val app = requireActivity().application as ScheduleApp
        val dataStore = app.dataStoreManager

        // --- Load current settings from DataStore and set UI switches ---
        viewLifecycleOwner.lifecycleScope.launch {
            binding.switchDarkTheme.isChecked =
                dataStore.darkThemeEnabled.first()
            binding.switchNotifSound.isChecked =
                dataStore.notificationSoundEnabled.first()
            binding.switchNotifVibrate.isChecked =
                dataStore.notificationVibrateEnabled.first()
            binding.switchFullscreen.isChecked =
                dataStore.fullscreenAlertEnabled.first()
        }

        // --- Switch listeners (persist to DataStore immediately) ---
        binding.switchDarkTheme.setOnCheckedChangeListener { _, isChecked ->
            viewLifecycleOwner.lifecycleScope.launch {
                dataStore.setDarkThemeEnabled(isChecked)
            }
        }
        binding.switchNotifSound.setOnCheckedChangeListener { _, isChecked ->
            viewLifecycleOwner.lifecycleScope.launch {
                dataStore.setNotificationSoundEnabled(isChecked)
                Toast.makeText(requireContext(), "Sound ${if (isChecked) "On" else "Off"}", Toast.LENGTH_SHORT).show()
            }
        }
        binding.switchNotifVibrate.setOnCheckedChangeListener { _, isChecked ->
            viewLifecycleOwner.lifecycleScope.launch {
                dataStore.setNotificationVibrateEnabled(isChecked)
                Toast.makeText(requireContext(), "Vibration ${if (isChecked) "On" else "Off"}", Toast.LENGTH_SHORT).show()
            }
        }
        binding.switchFullscreen.setOnCheckedChangeListener { _, isChecked ->
            viewLifecycleOwner.lifecycleScope.launch {
                dataStore.setFullscreenAlertEnabled(isChecked)
                if (isChecked && Build.VERSION.SDK_INT >= 34) {
                    // Offer quick jump to the OS permission page for Full-screen intents
                    startActivity(
                        Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT)
                            .putExtra(Settings.EXTRA_APP_PACKAGE, requireContext().packageName)
                    )
                }
            }
        }

        // --- Export / Import buttons ---
        binding.btnExportCourses.setOnClickListener {
            exportLauncher.launch("courses.uni.json")
        }
        binding.btnImportCourses.setOnClickListener {
            importLauncher.launch(arrayOf("application/json"))
        }

        // --- Optional: Exact alarms settings deep-link ---
        binding.btnManageExactAlarms.setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                startActivity(
                    Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                        .setData(Uri.parse("package:${requireContext().packageName}"))
                )
            } else {
                Toast.makeText(requireContext(), "Not required on this Android version", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // region Export / Import using SAF (JSON)
    private fun exportCoursesTo(uri: Uri) {
        val app = requireActivity().application as ScheduleApp
        val dao = app.db.courseDao()
        viewLifecycleOwner.lifecycleScope.launch {
            val courses = dao.getAll()
            val json = JSONArray()
            courses.forEach { c ->
                val obj = JSONObject().apply {
                    put("title", c.title)
                    put("location", c.location)
                    put("color", c.color)
                    put("dayOfWeek", c.dayOfWeek.value) // 1..7
                    put("startTime", c.startTime.toString()) // HH:MM:SS
                    put("endTime", c.endTime.toString()) // HH:MM:SS
                    put("semesterStart", c.semesterStart.toString()) // yyyy-MM-dd
                    put("totalWeeks", c.totalWeeks)
                    put("weekFilter", c.weekFilter.name)
                    put("includedWeeks", JSONArray(c.includedWeeks.toList()))
                    put("reminderMinutesBefore", c.reminderMinutesBefore)
                }
                json.put(obj)
            }
            try {
                requireContext().contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(json.toString(2).toByteArray())
                    out.flush()
                }
                Toast.makeText(requireContext(), "Exported to: $uri", Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Export failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun importCoursesFrom(uri: Uri) {
        // Persist read access if the user picks from a cloud provider
        try {
            requireContext().contentResolver.takePersistableUriPermission(
                uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        } catch (_: SecurityException) { /* best effort */ }

        val app = requireActivity().application as ScheduleApp
        val dao = app.db.courseDao()
        val scheduler = ReminderScheduler(requireContext(), app.alarmManager)

        viewLifecycleOwner.lifecycleScope.launch {
            // --- SAFELY READ TEXT & CLOSE STREAMS ---
            val text = try {
                val cr = requireContext().contentResolver
                cr.openInputStream(uri)?.use { input ->
                    input.bufferedReader().use { reader ->
                        reader.readText()
                    }
                }
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Import failed: ${e.message}", Toast.LENGTH_LONG).show()
                null
            } ?: return@launch

            // Minimal validation (ensure .uni.json or a JSON array)
            if (!uri.toString().endsWith(".uni.json") && !text.trim().startsWith("[")) {
                Toast.makeText(requireContext(), "Invalid file format", Toast.LENGTH_LONG).show()
                return@launch
            }

            // --- parsing & replace logic ---
            val list = mutableListOf<Course>()
            try {
                val arr = org.json.JSONArray(text)
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    val title = o.optString("title")
                    if (title.isBlank()) continue
                    val location: String? =
                        if (!o.has("location") || o.isNull("location")) null
                        else o.optString("location").takeIf { it.isNotBlank() }
                    val color = o.optInt("color")
                    val day = DayOfWeek.of(o.optInt("dayOfWeek", 1).coerceIn(1, 7))
                    val start = parseLocalTime(o.optString("startTime"))
                    val end = parseLocalTime(o.optString("endTime"))
                    val startDate = parseLocalDate(o.optString("semesterStart"))
                    val totalWeeks = o.optInt("totalWeeks", 16)
                    val weekFilter = runCatching { WeekFilter.valueOf(o.optString("weekFilter", "ALL")) }
                        .getOrDefault(WeekFilter.ALL)
                    val includedWeeks = o.optJSONArray("includedWeeks")?.let { ja ->
                        (0 until ja.length()).mapNotNull { idx -> ja.optInt(idx).takeIf { it > 0 } }.toSet()
                    } ?: emptySet()
                    val reminderMin = o.optInt("reminderMinutesBefore", 10)

                    if (start != null && end != null && startDate != null && end.isAfter(start)) {
                        list.add(
                            Course(
                                title = title,
                                location = location,
                                color = color,
                                dayOfWeek = day,
                                startTime = start,
                                endTime = end,
                                semesterStart = startDate,
                                totalWeeks = totalWeeks,
                                weekFilter = weekFilter,
                                includedWeeks = includedWeeks,
                                reminderMinutesBefore = reminderMin
                            )
                        )
                    }
                }
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Import parse error: ${e.message}", Toast.LENGTH_LONG).show()
                return@launch
            }

            if (list.isEmpty()) {
                Toast.makeText(requireContext(), "No courses found in file", Toast.LENGTH_SHORT).show()
                return@launch
            }

            app.db.runInTransaction {
                runBlockingUnit {
                    dao.deleteAll()
                    list.forEach { c ->
                        val id = dao.upsert(c)
                        val saved = c.copy(id = id)
                        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S
                            || app.alarmManager.canScheduleExactAlarms()
                        ) {
                            scheduler.scheduleNext(saved)
                        }
                    }
                }
            }
            Toast.makeText(requireContext(), "Imported ${list.size} courses", Toast.LENGTH_LONG).show()
        }
    }

    private fun parseLocalTime(s: String?): LocalTime? = try {
        if (s.isNullOrBlank()) null else LocalTime.parse(s)
    } catch (_: DateTimeParseException) { null }

    private fun parseLocalDate(s: String?): LocalDate? = try {
        if (s.isNullOrBlank()) null else LocalDate.parse(s)
    } catch (_: DateTimeParseException) { null }

    private inline fun runBlockingUnit(crossinline block: suspend () -> Unit) {
        kotlinx.coroutines.runBlocking { block() }
    }
    // endregion

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onResume() {
        super.onResume()
        // Ensure window is touchable
        requireActivity().window.clearFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE)

        // Lock and close drawer while in Settings
        requireActivity().findViewById<DrawerLayout?>(R.id.drawer_layout)?.apply {
            closeDrawers()
            setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED)
        }

        // Explicitly enable main containers and switches
        listOf(
            binding.settingsScroll,
            binding.settingsContainer,
            binding.switchDarkTheme,
            binding.switchNotifSound,
            binding.switchNotifVibrate,
            binding.switchFullscreen,
            binding.btnManageExactAlarms,
            binding.btnExportCourses,
            binding.btnImportCourses
        ).forEach { v ->
            v.isEnabled = true
            v.isClickable = true
            v.isFocusable = true
            v.parent?.requestDisallowInterceptTouchEvent(true)
        }
    }

    override fun onPause() {
        super.onPause()
        // Unlock drawer when leaving
        requireActivity().findViewById<DrawerLayout?>(R.id.drawer_layout)
            ?.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED)
    }
}
