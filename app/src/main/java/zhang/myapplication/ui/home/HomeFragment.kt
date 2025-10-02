package zhang.myapplication.ui.home

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.*
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import zhang.myapplication.MainActivity
import zhang.myapplication.ScheduleApp
import zhang.myapplication.databinding.FragmentHomeBinding
import zhang.myapplication.domain.nextOccurrence
import java.time.*
import java.time.format.DateTimeFormatter
import java.util.*

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
        val app = requireActivity().application as ScheduleApp
        val dao = app.db.courseDao()
        val dataStore = app.dataStoreManager

        binding.btnAddCourse.setOnClickListener {
            (requireActivity() as? MainActivity)?.showAddCourseDialog()
        }

        // Show theme and notification settings
        lifecycleScope.launch {
            val darkTheme = dataStore.darkThemeEnabled.first()
            val sound = dataStore.notificationSoundEnabled.first()
            val vibrate = dataStore.notificationVibrateEnabled.first()
            val fullscreen = dataStore.fullscreenAlertEnabled.first()

            binding.tvNextTitle.text = "Theme: ${if (darkTheme) "Dark" else "Light"}"
            binding.tvNextMeta.text = "Sound: ${if (sound) "On" else "Off"}, Vibration: ${if (vibrate) "On" else "Off"}"
            binding.tvNextCountdown.text = "Fullscreen Alerts: ${if (fullscreen) "Enabled" else "Disabled"}"
        }

        // Show next upcoming class
        viewLifecycleOwner.lifecycleScope.launch {
            dao.observeAll().collect { list ->
                val now = ZonedDateTime.now()
                val nextPair = list.mapNotNull { course ->
                    val next = nextOccurrence(course, now)
                    if (next != null) course to next else null
                }.minByOrNull { it.second.toInstant() }

                if (nextPair == null) return@collect

                val (course, startZdt) = nextPair
                val minutesBefore = course.reminderMinutesBefore
                val triggerAt = startZdt.minusMinutes(minutesBefore.toLong())
                val target = if (triggerAt.isAfter(now)) triggerAt else startZdt
                val dur = Duration.between(now, target)
                val mins = dur.toMinutes()
                val hours = mins / 60
                val remMins = mins % 60

                binding.tvNextTitle.text = course.title
                val locPart = if (course.location.isNullOrBlank()) "" else " • ${course.location}"
                binding.tvNextMeta.text = "${startZdt.format(timeFmt)}$locPart"
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