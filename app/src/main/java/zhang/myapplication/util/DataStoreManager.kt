package zhang.myapplication.util

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "settings")

class DataStoreManager(private val context: Context) {

    // Keys
    private object Keys {
        val DARK = booleanPreferencesKey("dark_theme")
        val SOUND = booleanPreferencesKey("notif_sound")
        val VIBRATE = booleanPreferencesKey("notif_vibrate")
        val FULLSCREEN = booleanPreferencesKey("notif_fullscreen")
        val AUTO_SYNC = booleanPreferencesKey("auto_sync")
        val LAST_SYNC = stringPreferencesKey("last_sync_time")
    }

    // Exposed flows (with sane defaults)
    val darkThemeEnabled: Flow<Boolean> =
        context.dataStore.data.map { it[Keys.DARK] ?: false }

    val notificationSoundEnabled: Flow<Boolean> =
        context.dataStore.data.map { it[Keys.SOUND] ?: true }

    val notificationVibrateEnabled: Flow<Boolean> =
        context.dataStore.data.map { it[Keys.VIBRATE] ?: true }

    val fullscreenAlertEnabled: Flow<Boolean> =
        context.dataStore.data.map { it[Keys.FULLSCREEN] ?: false }

    val autoSyncEnabled: Flow<Boolean> =
        context.dataStore.data.map { it[Keys.AUTO_SYNC] ?: false }

    val lastSyncTime: Flow<String> =
        context.dataStore.data.map { it[Keys.LAST_SYNC] ?: "—" }

    // Setters
    suspend fun setDarkThemeEnabled(value: Boolean) {
        context.dataStore.edit { it[Keys.DARK] = value }
    }

    suspend fun setNotificationSoundEnabled(value: Boolean) {
        context.dataStore.edit { it[Keys.SOUND] = value }
    }

    suspend fun setNotificationVibrateEnabled(value: Boolean) {
        context.dataStore.edit { it[Keys.VIBRATE] = value }
    }

    suspend fun setFullscreenAlertEnabled(value: Boolean) {
        context.dataStore.edit { it[Keys.FULLSCREEN] = value }
    }

    suspend fun setAutoSync(value: Boolean) {
        context.dataStore.edit { it[Keys.AUTO_SYNC] = value }
    }

    suspend fun setLastSyncTime(value: String) {
        context.dataStore.edit { it[Keys.LAST_SYNC] = value }
    }
}