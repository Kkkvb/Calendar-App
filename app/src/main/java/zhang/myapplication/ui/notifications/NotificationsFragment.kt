package zhang.myapplication.ui.notifications

import android.app.TimePickerDialog
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import zhang.myapplication.ScheduleApp
import zhang.myapplication.R
import zhang.myapplication.data.Course
import zhang.myapplication.data.WeekFilter
import zhang.myapplication.databinding.DialogAddCourseBinding
import zhang.myapplication.databinding.FragmentNotificationsBinding
import zhang.myapplication.domain.ReminderScheduler
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

class NotificationsFragment : Fragment() {

    private var _binding: FragmentNotificationsBinding? = null
    private val binding get() = _binding!!

    private lateinit var adapter: CoursesAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentNotificationsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        adapter = CoursesAdapter(
            onClick = { course -> showAddOrEditDialog(course) },
            onLongClick = { course -> confirmDelete(course) }
        )
        binding.rvCourses.layoutManager = LinearLayoutManager(requireContext())
        binding.rvCourses.adapter = adapter

        val app = requireActivity().application as ScheduleApp
        val dao = app.db.courseDao()

        // Observe all courses and display (sorted by day, then start time)
        viewLifecycleOwner.lifecycleScope.launch {
            dao.observeAll().collectLatest { list ->
                val sorted = list.sortedWith(
                    compareBy<Course> { it.dayOfWeek.value }.thenBy { it.startTime }
                )
                adapter.submitList(sorted)
            }
        }
    }

    private fun confirmDelete(course: Course) {
        AlertDialog.Builder(requireContext())
            .setTitle("Delete course?")
            .setMessage("This will cancel upcoming reminders for “${course.title}”.")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Delete") { _, _ ->
                val app = requireActivity().application as ScheduleApp
                val scheduler = ReminderScheduler(requireContext(), app.alarmManager)
                viewLifecycleOwner.lifecycleScope.launch {
                    scheduler.cancel(course)
                    app.db.courseDao().delete(course)
                    Toast.makeText(requireContext(), "Deleted", Toast.LENGTH_SHORT).show()
                }
            }
            .show()
    }

    /** If [existing] is null → Add; else Edit (prefilled). */
    private fun showAddOrEditDialog(existing: Course? = null) {
        val app = requireActivity().application as ScheduleApp
        val dao = app.db.courseDao()
        val scheduler = ReminderScheduler(requireContext(), app.alarmManager)

        val b = DialogAddCourseBinding.inflate(layoutInflater)

        // Prepare day spinner
        val days = resources.getStringArray(R.array.days_of_week).toList()
        b.spDay.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, days)

        // Prefill if editing
        var startTime: LocalTime? = existing?.startTime
        var endTime: LocalTime? = existing?.endTime

        if (existing != null) {
            b.etTitle.setText(existing.title)
            b.etLocation.setText(existing.location ?: "")
            b.etReminder.setText(existing.reminderMinutesBefore.toString())
            b.spDay.setSelection(existing.dayOfWeek.value - 1)
            b.btnStartTime.text = "Start: ${existing.startTime}"
            b.btnEndTime.text = "End: ${existing.endTime}"
        }

        // Time pickers
        b.btnStartTime.setOnClickListener {
            val now = startTime ?: LocalTime.now()
            TimePickerDialog(requireContext(), { _, h, m ->
                startTime = LocalTime.of(h, m)
                b.btnStartTime.text = "Start: %02d:%02d".format(h, m)
            }, now.hour, now.minute, true).show()
        }
        b.btnEndTime.setOnClickListener {
            val now = endTime ?: (startTime ?: LocalTime.now()).plusMinutes(50)
            TimePickerDialog(requireContext(), { _, h, m ->
                endTime = LocalTime.of(h, m)
                b.btnEndTime.text = "End: %02d:%02d".format(h, m)
            }, now.hour, now.minute, true).show()
        }

        AlertDialog.Builder(requireContext())
            .setTitle(if (existing == null) "Add course" else "Edit course")
            .setView(b.root)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Save") { _, _ ->
                val title = b.etTitle.text.toString().trim()
                val location = b.etLocation.text.toString().trim().ifBlank { null }
                val reminderMin = b.etReminder.text.toString().toIntOrNull() ?: 10
                val dayIdx = b.spDay.selectedItemPosition
                val dayOfWeek = DayOfWeek.of(dayIdx + 1)

                // Non-null locals with guards
                val st = startTime ?: run {
                    Toast.makeText(requireContext(), "Pick a start time", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                val et = endTime ?: run {
                    Toast.makeText(requireContext(), "Pick an end time", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                if (title.isBlank()) {
                    Toast.makeText(requireContext(), "Please enter a title", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                if (!et.isAfter(st)) {
                    Toast.makeText(requireContext(), "End time must be after start time", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                viewLifecycleOwner.lifecycleScope.launch {
                    val semesterStart = existing?.semesterStart ?: LocalDate.now()
                        .with(java.time.temporal.TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

                    val updated = (existing ?: Course(
                        title = title,
                        location = location,
                        dayOfWeek = dayOfWeek,
                        startTime = st,
                        endTime = et,
                        semesterStart = semesterStart,
                        totalWeeks = 16,
                        weekFilter = WeekFilter.ALL,
                        includedWeeks = emptySet(),
                        reminderMinutesBefore = reminderMin
                    )).copy(
                        title = title,
                        location = location,
                        dayOfWeek = dayOfWeek,
                        startTime = st,
                        endTime = et,
                        reminderMinutesBefore = reminderMin
                    )

                    // Persist
                    val id = if (existing == null) dao.upsert(updated) else {
                        dao.upsert(updated.copy(id = existing.id))
                    }
                    val saved = updated.copy(id = if (existing == null) id else existing.id)

                    // Schedule/cancel
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S
                        || app.alarmManager.canScheduleExactAlarms()
                    ) {
                        scheduler.cancel(saved)    // prevent duplicates
                        scheduler.scheduleNext(saved)
                        Toast.makeText(requireContext(), "Saved & scheduled", Toast.LENGTH_SHORT).show()
                    } else {
                        // Optional: deep link to exact-alarms settings
                        startActivity(
                            android.content.Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                                .setData(android.net.Uri.parse("package:${requireContext().packageName}"))
                        )
                        Toast.makeText(requireContext(), "Saved. Enable exact alarms to schedule.", Toast.LENGTH_LONG).show()
                    }
                }
            }
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}