
package zhang.myapplication.ui.screens

import android.annotation.SuppressLint
import android.graphics.Color
import android.os.Bundle
import android.view.*
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.gridlayout.widget.GridLayout
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import zhang.myapplication.R
import zhang.myapplication.ScheduleApp
import zhang.myapplication.data.Course
import zhang.myapplication.data.WeekFilter
import java.time.*

class EditCourseScreen : Fragment() {
    private lateinit var grid: GridLayout
    private lateinit var weekFilterSpinner: Spinner
    private lateinit var colorSpinner: Spinner
    private lateinit var durationSpinner: Spinner
    private lateinit var previewBlock: TextView
    private lateinit var saveButton: Button
    private lateinit var undoButton: Button
    private lateinit var resetButton: Button
    private lateinit var courseListText: TextView
    private val blockViews = mutableListOf<TextView>()
    private val courseData = mutableMapOf<TextView, Course>()

    @SuppressLint("SetTextI18n")
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val view = inflater.inflate(R.layout.fragment_edit_course_screen, container, false)

        grid = view.findViewById(R.id.timetableGrid)
        weekFilterSpinner = view.findViewById(R.id.spWeekFilter)
        colorSpinner = view.findViewById(R.id.spColor)
        durationSpinner = view.findViewById(R.id.spDuration)
        previewBlock = view.findViewById(R.id.previewBlock)
        saveButton = Button(requireContext()).apply {
            text = "Save All"
            setOnClickListener { saveAllCourses() }
        }
        undoButton = view.findViewById(R.id.btnUndo)
        resetButton = view.findViewById(R.id.btnReset)
        courseListText = view.findViewById(R.id.tvCourseList)
        undoButton.setOnClickListener { undoLastBlock() }
        resetButton.setOnClickListener { resetAllBlocks() }

        (grid.parent as LinearLayout).addView(saveButton)

        setupSpinners()
        setupPreviewBlock()
        setupGrid()
        loadExistingCourses()

        return view
    }

    private fun setupSpinners() {
        weekFilterSpinner.adapter = ArrayAdapter(requireContext(),
            android.R.layout.simple_spinner_dropdown_item,
            listOf("All", "Odd", "Even"))

        colorSpinner.adapter = ArrayAdapter(requireContext(),
            android.R.layout.simple_spinner_dropdown_item,
            listOf("Cyan", "Green", "Yellow", "Red"))

        durationSpinner.adapter = ArrayAdapter(requireContext(),
            android.R.layout.simple_spinner_dropdown_item,
            listOf("50", "60", "90", "120"))
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupPreviewBlock() {
        updatePreviewBlock()
        previewBlock.setOnTouchListener { v, event ->
            if (event.action == MotionEvent.ACTION_DOWN) {
                val shadow = View.DragShadowBuilder(v)
                v.startDragAndDrop(null, shadow, v, 0)
            }
            true
        }
    }

    private fun updatePreviewBlock() {
        val color = when (colorSpinner.selectedItem.toString()) {
            "Green" -> Color.GREEN
            "Yellow" -> Color.YELLOW
            "Red" -> Color.RED
            else -> Color.CYAN
        }
        previewBlock.setBackgroundColor(color)
    }

    private fun setupGrid() {
        val days = listOf("Time", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
        for (row in 0..24) {
            for (col in 0..7) {
                val cell = TextView(requireContext()).apply {
                    setPadding(8, 8, 8, 8)
                    gravity = Gravity.CENTER
                    setBackgroundColor(if (row == 0 || col == 0) Color.DKGRAY else Color.LTGRAY)
                    setTextColor(Color.WHITE)
                    textSize = 12f
                    text = when {
                        row == 0 -> days.getOrNull(col) ?: ""
                        col == 0 -> "%02d:00".format(row - 1)
                        else -> ""
                    }
                    if (row > 0 && col > 0) {
                        setOnDragListener(dragListener)
                    }
                }

                val params = GridLayout.LayoutParams().apply {
                    rowSpec = GridLayout.spec(row, 1f)
                    columnSpec = GridLayout.spec(col, 1f)
                    width = 0
                    height = 120
                    setMargins(2, 2, 2, 2)
                }
                grid.addView(cell, params)
            }
        }
    }

    private val dragListener = View.OnDragListener { target, event ->
        when (event.action) {
            DragEvent.ACTION_DROP -> {
                val dragged = event.localState as TextView
                (dragged.parent as? ViewGroup)?.removeView(dragged)
                (target as ViewGroup).addView(dragged)
                val course = createCourseFromDrop(target, dragged.text.toString())
                courseData[dragged] = course
                blockViews.add(dragged)
                true
            }
            else -> true
        }
    }

    private fun createCourseFromDrop(target: View, title: String): Course {
        val index = grid.indexOfChild(target)
        val row = index / 8
        val col = index % 8
        val day = DayOfWeek.of(col)
        val start = LocalTime.of(row - 1, 0)

        val duration = durationSpinner.selectedItem.toString().toInt()
        val end = start.plusMinutes(duration.toLong())

        val weekFilter = when (weekFilterSpinner.selectedItem.toString()) {
            "Odd" -> WeekFilter.ODD
            "Even" -> WeekFilter.EVEN
            else -> WeekFilter.ALL
        }

        val color = when (colorSpinner.selectedItem.toString()) {
            "Green" -> Color.GREEN
            "Yellow" -> Color.YELLOW
            "Red" -> Color.RED
            else -> Color.CYAN
        }

        return Course(
            title = title,
            dayOfWeek = day,
            startTime = start,
            endTime = end,
            semesterStart = LocalDate.now().with(java.time.temporal.TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)),
            totalWeeks = 16,
            weekFilter = weekFilter,
            includedWeeks = selectIncludedWeeks(),
            color = color,
            reminderMinutesBefore = 10
        )
    }

    private fun selectIncludedWeeks(): Set<Int> {
        val selected = mutableSetOf<Int>()
        val dialog = AlertDialog.Builder(requireContext())
        val weeks = (1..16).map { "Week $it" }.toTypedArray()
        val checked = BooleanArray(16)

        dialog.setTitle("Select Included Weeks")
        dialog.setMultiChoiceItems(weeks, checked) { _, which, isChecked ->
            if (isChecked) selected.add(which + 1) else selected.remove(which + 1)
        }
        dialog.setPositiveButton("OK", null)
        dialog.show()

        return selected
    }

    private fun saveAllCourses() {
        val app = requireActivity().application as ScheduleApp
        val dao = app.db.courseDao()

        lifecycleScope.launch {
            for ((_, course) in courseData) {
                dao.upsert(course)
            }
            Toast.makeText(requireContext(), "Courses saved", Toast.LENGTH_SHORT).show()
        }
    }

    private fun loadExistingCourses() {
        val app = requireActivity().application as ScheduleApp
        val dao = app.db.courseDao()

        lifecycleScope.launch {
            val courses = dao.getAll()
            for (course in courses) {
                val col = course.dayOfWeek.value
                val row = course.startTime.hour + 1
                val block = createCourseBlock(course.title, course.color)
                val index = row * 8 + col
                grid.addView(block, index)
                courseData[block] = course
                blockViews.add(block)
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun createCourseBlock(title: String, color: Int): TextView {
        return TextView(requireContext()).apply {
            text = title
            setBackgroundColor(color)
            setPadding(16, 16, 16, 16)
            setOnTouchListener { v, event ->
                if (event.action == MotionEvent.ACTION_DOWN) {
                    val shadow = View.DragShadowBuilder(v)
                    v.startDragAndDrop(null, shadow, v, 0)
                }
                true
            }
            setOnClickListener { showEditDialog(this) }
            setOnLongClickListener {
                AlertDialog.Builder(requireContext())
                    .setTitle("Delete Course?")
                    .setMessage("Remove this course block?")
                    .setPositiveButton("Delete") { _, _ ->
                        (parent as? ViewGroup)?.removeView(this)
                        courseData.remove(this)
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
                true
            }
        }
    }

    private fun showEditDialog(block: TextView) {
        val input = EditText(requireContext()).apply {
            setText(block.text)
        }

        AlertDialog.Builder(requireContext())
            .setTitle("Edit Course Title")
            .setView(input)
            .setPositiveButton("Save") { _, _ ->
                block.text = input.text.toString()
                courseData[block]?.let {
                    courseData[block] = it.copy(title = input.text.toString())
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun undoLastBlock() {
        if (blockViews.isNotEmpty()) {
            val last = blockViews.removeLast()
            (last.parent as? ViewGroup)?.removeView(last)
            courseData.remove(last)
            updateCourseListPreview()
        }
    }

    private fun resetAllBlocks() {
        for (block in blockViews) {
            (block.parent as? ViewGroup)?.removeView(block)
        }
        blockViews.clear()
        courseData.clear()
        updateCourseListPreview()
    }

    @SuppressLint("SetTextI18n")
    private fun updateCourseListPreview() {
        val list = courseData.values.joinToString("\n") {
            "${it.title} · ${it.dayOfWeek} ${it.startTime}-${it.endTime}"
        }
        courseListText.text = "Courses:\n$list"
    }
}

