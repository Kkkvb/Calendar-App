package zhang.myapplication.ui.dashboard

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import zhang.myapplication.ScheduleApp
import zhang.myapplication.databinding.FragmentDashboardBinding
import zhang.myapplication.domain.CalendarSyncHelper
import zhang.myapplication.ui.common.CourseListFragment
import java.time.ZonedDateTime

class DashboardFragment : Fragment() {

    private var _binding: FragmentDashboardBinding? = null
    private val binding get() = _binding!!

    private val requestCalendarPerms =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
            val read = result[Manifest.permission.READ_CALENDAR] == true
            val write = result[Manifest.permission.WRITE_CALENDAR] == true
            if (read && write) {
                doManualSync()
            } else {
                Toast.makeText(requireContext(), "Calendar permission denied", Toast.LENGTH_SHORT).show()
            }
        }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDashboardBinding.inflate(inflater, container, false)
        return binding.root
    }

    @SuppressLint("SetTextI18n")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val ctx = requireContext()
        val app = requireActivity().application as ScheduleApp
        val dataStore = app.dataStoreManager

        // Reuse the course list
        childFragmentManager.beginTransaction()
            .replace(binding.dashboardContainer.id, CourseListFragment())
            .commit()

        // Load auto-sync status & last sync
        viewLifecycleOwner.lifecycleScope.launch {
            val isEnabled = dataStore.autoSyncEnabled.first()
            val lastSync = dataStore.lastSyncTime.first()
            binding.switchAutoSync.isChecked = isEnabled
            binding.tvSyncStatus.text = "Auto-sync: ${if (isEnabled) "Enabled" else "Disabled"}"
            binding.tvLastSync.text = "Last sync: $lastSync"
        }

        // Toggle auto-sync
        binding.switchAutoSync.setOnCheckedChangeListener { _, isChecked ->
            viewLifecycleOwner.lifecycleScope.launch {
                dataStore.setAutoSync(isChecked)
                binding.tvSyncStatus.text = "Auto-sync: ${if (isChecked) "Enabled" else "Disabled"}"
            }
        }

        // Manual SYNC button
        binding.btnSyncCalendar.setOnClickListener {
            val hasRead = ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED
            val hasWrite = ContextCompat.checkSelfPermission(ctx, Manifest.permission.WRITE_CALENDAR) == PackageManager.PERMISSION_GRANTED
            if (!hasRead || !hasWrite) {
                requestCalendarPerms.launch(
                    arrayOf(
                        Manifest.permission.READ_CALENDAR,
                        Manifest.permission.WRITE_CALENDAR
                    )
                )
                return@setOnClickListener
            }
            doManualSync()
        }
    }

    @SuppressLint("SetTextI18n")
    private fun doManualSync() {
        val ctx = requireContext()
        val app = requireActivity().application as ScheduleApp
        val dao = app.db.courseDao()
        val dataStore = app.dataStoreManager

        viewLifecycleOwner.lifecycleScope.launch {
            val courses = dao.getAll()
            val helper = CalendarSyncHelper(ctx)
            val insertedCount = helper.syncCoursesToCalendar(courses, lookaheadDays = 3) // 3-day window
            val now = ZonedDateTime.now().toString()
            dataStore.setLastSyncTime(now)
            binding.tvLastSync.text = "Last sync: $now"
            Toast.makeText(ctx, "Synced $insertedCount events to calendar", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}