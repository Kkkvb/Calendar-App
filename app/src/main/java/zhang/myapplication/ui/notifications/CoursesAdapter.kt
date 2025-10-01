package zhang.myapplication.ui.notifications

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import zhang.myapplication.R
import zhang.myapplication.data.Course
import java.time.format.DateTimeFormatter

class CoursesAdapter(
    private val onClick: (Course) -> Unit,
    private val onLongClick: (Course) -> Unit
) : ListAdapter<Course, CoursesAdapter.VH>(DIFF) {

    companion object {
        private val timeFmt = DateTimeFormatter.ofPattern("HH:mm")
        private val DIFF = object : DiffUtil.ItemCallback<Course>() {
            override fun areItemsTheSame(oldItem: Course, newItem: Course) = oldItem.id == newItem.id
            override fun areContentsTheSame(oldItem: Course, newItem: Course) = oldItem == newItem
        }
    }

    inner class VH(v: View) : RecyclerView.ViewHolder(v) {
        private val tvTitle = v.findViewById<TextView>(R.id.tvTitle)
        private val tvDay = v.findViewById<TextView>(R.id.tvDay)
        private val tvTime = v.findViewById<TextView>(R.id.tvTime)
        private val tvLocation = v.findViewById<TextView>(R.id.tvLocation)

        fun bind(c: Course) {
            tvTitle.text = c.title
            tvDay.text = c.dayOfWeek.name.take(3) // MON, TUE ...
            tvTime.text = "${c.startTime.format(timeFmt)}–${c.endTime.format(timeFmt)}"
            tvLocation.text = c.location ?: ""
            itemView.setOnClickListener { onClick(c) }
            itemView.setOnLongClickListener { onLongClick(c); true }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_course, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(getItem(position))
}
