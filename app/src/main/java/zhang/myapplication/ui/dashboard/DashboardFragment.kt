package zhang.myapplication.ui.dashboard

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.*
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import zhang.myapplication.ScheduleApp
import zhang.myapplication.databinding.FragmentDashboardBinding
import zhang.myapplication.domain.CalendarSyncHelper
import zhang.myapplication.ui.common.CourseListFragment

class DashboardFragment : Fragment() {
    private var _binding: FragmentDashboardBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDashboardBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Load the reusable course list
        childFragmentManager.beginTransaction()
            .replace(binding.dashboardContainer.id, CourseListFragment())
            .commit()

        // Handle sync button click
        binding.btnSyncCalendar.setOnClickListener {
            val context = requireContext()
            val app = requireActivity().application as ScheduleApp
            val dao = app.db.courseDao()

            // Request permission if not granted
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_CALENDAR)
                != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(requireActivity(), arrayOf(Manifest.permission.WRITE_CALENDAR), 1001)
                return@setOnClickListener
            }

            // Perform sync
            viewLifecycleOwner.lifecycleScope.launch {
                val courses = dao.getAll()
                CalendarSyncHelper(context).syncCoursesToCalendar(courses)
                Toast.makeText(context, "Synced to calendar", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
