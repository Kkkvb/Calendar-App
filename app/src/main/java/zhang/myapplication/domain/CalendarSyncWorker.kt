package zhang.myapplication.domain

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import zhang.myapplication.ScheduleApp

// Extension for DataStore
val Context.dataStore by preferencesDataStore(name = "settings")
val AUTO_SYNC_KEY = booleanPreferencesKey("auto_sync_enabled")

class CalendarSyncWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        // Check calendar permission
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_CALENDAR)
            != PackageManager.PERMISSION_GRANTED) {
            return Result.failure()
        }

        // Check if auto-sync is enabled
        val isEnabled = context.dataStore.data
            .map { prefs -> prefs[AUTO_SYNC_KEY] ?: true }
            .first()

        if (!isEnabled) return Result.success()

        // Proceed with syncing
        val app = context.applicationContext as ScheduleApp
        val dao = app.db.courseDao()
        val courses = dao.getAll()

        CalendarSyncHelper(context).syncCoursesToCalendar(courses)

        return Result.success()
    }
}