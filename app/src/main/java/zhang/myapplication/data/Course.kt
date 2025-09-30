package zhang.myapplication.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

@Entity
data class Course(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val title: String,
    val location: String? = null,
    val color: Int = 0xFF80DEEA.toInt(),
    val dayOfWeek: DayOfWeek,
    val startTime: LocalTime,
    val endTime: LocalTime,
    val semesterStart: LocalDate,
    val totalWeeks: Int = 14,
    val weekFilter: WeekFilter = WeekFilter.ALL,
    val includedWeeks: Set<Int> = emptySet(),
    val reminderMinutesBefore: Int = 10
)
enum class WeekFilter { ALL, ODD, EVEN }
