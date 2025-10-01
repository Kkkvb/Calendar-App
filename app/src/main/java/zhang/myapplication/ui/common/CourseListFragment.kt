package zhang.myapplication.ui.common

import android.os.Bundle
import android.view.*
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import zhang.myapplication.ScheduleApp
import zhang.myapplication.data.Course
import zhang.myapplication.databinding.FragmentCourseListBinding
import zhang.myapplication.domain.ReminderScheduler
import zhang.myapplication.ui.notifications.CoursesAdapter

class CourseListFragment : Fragment() {
    private var _binding: FragmentCourseListBinding? = null
    private val binding get() = _binding!!
    private lateinit var adapter: CoursesAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentCourseListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val app = requireActivity().application as ScheduleApp
        val dao = app.db.courseDao()
        val scheduler = ReminderScheduler(requireContext(), app.alarmManager)

        adapter = CoursesAdapter(
            onClick = { /* Optional: handle click */ },
            onLongClick = { course -> confirmDelete(course, dao, scheduler) }
        )

        binding.rvCourses.layoutManager = LinearLayoutManager(requireContext())
        binding.rvCourses.adapter = adapter

        viewLifecycleOwner.lifecycleScope.launch {
            dao.observeAll().collectLatest { list ->
                adapter.submitList(list.sortedWith(compareBy({ it.dayOfWeek.value }, { it.startTime })))
            }
        }
    }

    private fun confirmDelete(course: Course, dao: zhang.myapplication.data.CourseDao, scheduler: ReminderScheduler) {
        AlertDialog.Builder(requireContext())
            .setTitle("Delete course?")
            .setMessage("This will cancel reminders for “${course.title}”.")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Delete") { _, _ ->
                viewLifecycleOwner.lifecycleScope.launch {
                    scheduler.cancel(course)
                    dao.delete(course)
                    Toast.makeText(requireContext(), "Deleted", Toast.LENGTH_SHORT).show()
                }
            }
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
