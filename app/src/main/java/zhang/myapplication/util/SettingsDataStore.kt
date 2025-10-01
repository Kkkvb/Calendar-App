package zhang.myapplication.util

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore

val Context.dataStore by preferencesDataStore(name = "settings")

val AUTO_SYNC_KEY = booleanPreferencesKey("auto_sync_enabled")
val FULLSCREEN_ALERT_KEY = booleanPreferencesKey("fullscreen_alert_enabled")
val NOTIF_SOUND_KEY = booleanPreferencesKey("notification_sound_enabled")
val NOTIF_VIBRATE_KEY = booleanPreferencesKey("notification_vibrate_enabled")
val LAST_SYNC_KEY = stringPreferencesKey("last_sync_time")
