package zhang.myapplication.data

import androidx.room.TypeConverter
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

class Converters {
    @TypeConverter
    fun dowToInt(d: DayOfWeek) = d.value
    @TypeConverter fun intToDow(v: Int) = DayOfWeek.of(v)
    @TypeConverter fun ltToStr(t: LocalTime) = t.toString()
    @TypeConverter fun strToLt(s: String) = LocalTime.parse(s)
    @TypeConverter fun ldToStr(d: LocalDate) = d.toString()
    @TypeConverter fun strToLd(s: String) = LocalDate.parse(s)
    @TypeConverter fun setToStr(set: Set<Int>) = set.joinToString(",")
    @TypeConverter fun strToSet(s: String) =
        if (s.isBlank()) emptySet() else s.split(",").map { it.trim().toInt() }.toSet()
    @TypeConverter fun wfToStr(w: WeekFilter) = w.name
    @TypeConverter fun strToWf(s: String) = WeekFilter.valueOf(s)
}
