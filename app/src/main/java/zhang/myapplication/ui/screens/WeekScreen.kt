package zhang.myapplication.ui.screens

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.fragment.app.Fragment
import zhang.myapplication.R
import zhang.myapplication.data.WeekFilter

class WeekScreen : Fragment() {
    private lateinit var grid: GridLayout
    private val selectedWeeks = mutableSetOf<Int>()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val view = inflater.inflate(R.layout.fragment_week_screen, container, false)
        grid = view.findViewById(R.id.gridWeeks)

        for (i in 1..16) {
            val cb = CheckBox(requireContext()).apply {
                text = "Week $i"
                setOnCheckedChangeListener { _, isChecked ->
                    if (isChecked) selectedWeeks.add(i) else selectedWeeks.remove(i)
                }
            }
            grid.addView(cb)
        }

        val rg = view.findViewById<RadioGroup>(R.id.rgWeekFilter)
        rg.setOnCheckedChangeListener { _, checkedId ->
            val filter = when (checkedId) {
                R.id.rbAll -> WeekFilter.ALL
                R.id.rbOdd -> WeekFilter.ODD
                R.id.rbEven -> WeekFilter.EVEN
                else -> WeekFilter.ALL
            }
            // Save or pass filter + selectedWeeks to parent
        }

        return view
    }
}
