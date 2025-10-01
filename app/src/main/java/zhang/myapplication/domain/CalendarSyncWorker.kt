package zhang.myapplication.domain

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import zhang.myapplication.ScheduleApp

class CalendarSyncWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        // Check permission
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_CALENDAR)
            != PackageManager.PERMISSION_GRANTED) {
            return Result.failure()
        }

        val app = context.applicationContext as ScheduleApp
        val dao = app.db.courseDao()
        val courses = dao.getAll()

        CalendarSyncHelper(context).syncCoursesToCalendar(courses)
        return Result.success()
    }
}
