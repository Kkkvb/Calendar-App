package zhang.myapplication.domain

import zhang.myapplication.data.Course
import zhang.myapplication.data.WeekFilter
import java.time.*

/** Finds the next occurrence strictly after `from`. */
fun nextOccurrence(course: Course, from: ZonedDateTime = ZonedDateTime.now()): ZonedDateTime? {
    val zone = from.zone
    val startOfWeekMonday = from
        .with(LocalTime.MIN)
        .with(java.time.temporal.TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        .toLocalDate()

    // Look ahead up to 52 weeks (safety)
    for (offset in 0 until 52) {
        val weekStart = startOfWeekMonday.plusWeeks(offset.toLong())
        val dateForCourse = weekStart.with(course.dayOfWeek)

        val weekIndex = ((java.time.temporal.ChronoUnit.DAYS
            .between(course.semesterStart, dateForCourse)) / 7).toInt() + 1

        if (weekIndex <= 0 || weekIndex > course.totalWeeks) continue

        val passesSet = course.includedWeeks.isEmpty() || weekIndex in course.includedWeeks
        val passesOE = when (course.weekFilter) {
            WeekFilter.ALL -> true
            WeekFilter.ODD -> weekIndex % 2 == 1
            WeekFilter.EVEN -> weekIndex % 2 == 0
        }
        if (!passesSet || !passesOE) continue

        val start = ZonedDateTime.of(dateForCourse, course.startTime, zone)
        if (start.isAfter(from)) return start
    }
    return null
}
