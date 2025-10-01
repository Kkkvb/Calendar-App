package zhang.myapplication.ui.home

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import zhang.myapplication.MainActivity
import zhang.myapplication.ScheduleApp
import zhang.myapplication.databinding.FragmentHomeBinding
import zhang.myapplication.domain.nextOccurrence
import java.time.*
import java.time.format.DateTimeFormatter
import java.util.Locale

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!
    private val timeFmt: DateTimeFormatter by lazy {
        DateTimeFormatter.ofPattern("EEE, MMM d • HH:mm", Locale.getDefault())
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }


    @SuppressLint("SetTextI18n")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Quick "Add course" uses your existing dialog method in MainActivity
        binding.btnAddCourse.setOnClickListener {
            (requireActivity() as? MainActivity)?.showAddCourseDialog()
        }

        val app = requireActivity().application as ScheduleApp
        val dao = app.db.courseDao()

        // Observe the database and display the next upcoming class
        viewLifecycleOwner.lifecycleScope.launch {
            dao.observeAll().collectLatest { list ->
                val now = ZonedDateTime.now()

                // For each course, compute the next start; choose the earliest
                val nextPair = list.mapNotNull { course ->
                    val next = nextOccurrence(course, now)
                    if (next != null) course to next else null
                }.minByOrNull { it.second.toInstant() }

                if (nextPair == null) {
                    // No upcoming class
                    binding.tvNextTitle.text = "No upcoming classes"
                    binding.tvNextMeta.text = "Add a course to get reminders"
                    binding.tvNextCountdown.text = ""
                    return@collectLatest
                }

                val (course, startZdt) = nextPair
                val minutesBefore = course.reminderMinutesBefore
                val triggerAt = startZdt.minusMinutes(minutesBefore.toLong())

                // UI texts
                binding.tvNextTitle.text = course.title
                val locPart = if (course.location.isNullOrBlank()) "" else " • ${course.location}"
                binding.tvNextMeta.text = "${startZdt.format(timeFmt)}$locPart"

                // Countdown (to reminder time if it’s still ahead; otherwise to start)
                val target = if (triggerAt.isAfter(now)) triggerAt else startZdt
                val dur = Duration.between(now, target)
                val mins = dur.toMinutes()
                val hours = mins / 60
                val remMins = mins % 60
                binding.tvNextCountdown.text = when {
                    mins <= 0 -> "Starting now"
                    hours <= 0 -> "Starts in $mins min"
                    else -> "Starts in $hours h $remMins min"
                }
            }
        }
    }


    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
