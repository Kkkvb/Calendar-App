package zhang.myapplication.domain

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.flow.first
import zhang.myapplication.ScheduleApp

class CalendarSyncWorker(
    private val ctx: Context,
    params: WorkerParameters
) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result {
        // Check *both* permissions to avoid SecurityException on OEMs
        val hasRead = ContextCompat.checkSelfPermission(
            ctx, Manifest.permission.READ_CALENDAR
        ) == PackageManager.PERMISSION_GRANTED
        val hasWrite = ContextCompat.checkSelfPermission(
            ctx, Manifest.permission.WRITE_CALENDAR
        ) == PackageManager.PERMISSION_GRANTED
        if (!hasRead || !hasWrite) return Result.retry() // nicer after user grants

        val app = ctx.applicationContext as ScheduleApp
        val dataStore = app.dataStoreManager

        // Respect user's auto-sync preference
        val isEnabled = dataStore.autoSyncEnabled.first()
        if (!isEnabled) return Result.success()

        val dao = app.db.courseDao()
        val courses = dao.getAll()
        CalendarSyncHelper(ctx).syncCoursesToCalendar(courses, lookaheadDays = 3)
        return Result.success()
    }
}
