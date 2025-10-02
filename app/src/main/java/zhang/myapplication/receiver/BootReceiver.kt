package zhang.myapplication.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.*
import zhang.myapplication.ScheduleApp
import zhang.myapplication.domain.ReminderScheduler

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (Intent.ACTION_BOOT_COMPLETED != intent.action) return

        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val app = context.applicationContext as ScheduleApp
                val dao = app.db.courseDao()
                val scheduler = ReminderScheduler(context, app.alarmManager)
                val courses = dao.getAll()
                for (course in courses) {
                    scheduler.scheduleNext(course)
                }
            } finally {
                pending.finish()
            }
        }
    }
}
