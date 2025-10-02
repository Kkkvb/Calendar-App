@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package zhang.myapplication.ui.settings

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

/* --------------------------------------------------------------------------
 * ViewModel + State (in‑memory; swap to DataStore later if you want persistence)
 * -------------------------------------------------------------------------- */

data class SettingsUiState(
    val fullScreenAlerts: Boolean = false,
    val followSystemTheme: Boolean = true,
    val darkTheme: Boolean = false,
    val notificationSound: Boolean = true,
    val notificationVibration: Boolean = true,
    val autoSyncToCalendar: Boolean = false
)

class SettingsViewModel : ViewModel() {
    private val _ui = MutableStateFlow(SettingsUiState())
    val ui: StateFlow<SettingsUiState> = _ui

    fun setFullScreenAlerts(v: Boolean)      = _ui.update { it.copy(fullScreenAlerts = v) }
    fun setFollowSystem(v: Boolean)          = _ui.update { it.copy(followSystemTheme = v) }
    fun setDarkTheme(v: Boolean)             = _ui.update { it.copy(darkTheme = v) }
    fun setNotificationSound(v: Boolean)     = _ui.update { it.copy(notificationSound = v) }
    fun setNotificationVibration(v: Boolean) = _ui.update { it.copy(notificationVibration = v) }
    fun setAutoSyncToCalendar(v: Boolean)    = _ui.update { it.copy(autoSyncToCalendar = v) }
}

/* --------------------------------------------------------------------------
 * Composable Screen
 * -------------------------------------------------------------------------- */

@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    vm: SettingsViewModel = viewModel() // requires lifecycle-viewmodel-compose
) {
    val ui by vm.ui.collectAsStateWithLifecycle() // requires lifecycle-runtime-compose
    val context = LocalContext.current

    Scaffold(
        topBar = { TopAppBar(title = { Text("Settings") }) }
    ) { inner ->
        Column(modifier = modifier.padding(inner)) {
            SectionTitle("Appearance")

            PreferenceSwitch(
                title = "Follow system theme",
                subtitle = "Use device light/dark mode",
                checked = ui.followSystemTheme,
                onCheckedChange = vm::setFollowSystem
            )

            PreferenceSwitch(
                title = "Dark theme",
                subtitle = if (ui.followSystemTheme)
                    "Turn off 'Follow system' to customize"
                else
                    "Use app dark theme",
                checked = ui.darkTheme,
                onCheckedChange = vm::setDarkTheme,
                enabled = !ui.followSystemTheme // gated as requested
            )

            HorizontalDivider(thickness = 8.dp, color = MaterialTheme.colorScheme.surfaceVariant)

            SectionTitle("Notifications")

            PreferenceSwitch(
                title = "Full-screen alerts",
                subtitle = if (Build.VERSION.SDK_INT >= 34)
                    "Manage permission in Special app access"
                else
                    "Uses full-screen intent where supported",
                checked = ui.fullScreenAlerts,
                onCheckedChange = { enabled ->
                    vm.setFullScreenAlerts(enabled)
                    if (enabled) context.openFullScreenIntentSettings()
                }
            )

            PreferenceSwitch(
                title = "Notification sound",
                subtitle = "Play sound for reminders",
                checked = ui.notificationSound,
                onCheckedChange = vm::setNotificationSound
            )

            PreferenceSwitch(
                title = "Vibrate",
                subtitle = "Vibrate on reminder",
                checked = ui.notificationVibration,
                onCheckedChange = vm::setNotificationVibration
            )

            HorizontalDivider(thickness = 8.dp, color = MaterialTheme.colorScheme.surfaceVariant)

            SectionTitle("Calendar")

            PreferenceSwitch(
                title = "Auto-sync to calendar",
                subtitle = "Keep timetable synced to system calendar",
                checked = ui.autoSyncToCalendar,
                onCheckedChange = vm::setAutoSyncToCalendar
            )
        }
    }
}

/* --------------------------------------------------------------------------
 * Row Switch building blocks
 * - Entire row is toggleable (a11y-friendly)
 * - Switch mirrors row state (onCheckedChange = null inside)
 * - Visible On/Off chip for clarity
 * -------------------------------------------------------------------------- */

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
    )
}

@Composable
fun PreferenceSwitch(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
    subtitle: String? = null
) {
    val interaction = remember { MutableInteractionSource() }

    ListItem(
        headlineContent = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = { subtitle?.let { Text(it) } },
        trailingContent = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OnOffLabel(checked)
                Switch(
                    checked = checked,
                    onCheckedChange = null, // handled by row.toggleable
                    enabled = enabled,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                        checkedTrackColor = MaterialTheme.colorScheme.primary,
                        uncheckedTrackColor = MaterialTheme.colorScheme.outlineVariant
                    )
                )
            }
        },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .toggleable(
                value = checked,
                enabled = enabled,
                role = Role.Switch,
                interactionSource = interaction,
                onValueChange = onCheckedChange
            )
            .padding(horizontal = 8.dp)
    )
}

@Composable
private fun OnOffLabel(isOn: Boolean) {
    val txt = if (isOn) "On" else "Off"
    val bg  = if (isOn) MaterialTheme.colorScheme.primaryContainer
    else      MaterialTheme.colorScheme.surfaceVariant
    val fg  = if (isOn) MaterialTheme.colorScheme.onPrimaryContainer
    else      MaterialTheme.colorScheme.onSurfaceVariant
    Text(
        txt,
        color = fg,
        style = MaterialTheme.typography.labelSmall,
        modifier = Modifier
            .background(bg, RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 2.dp)
    )
}

/* --------------------------------------------------------------------------
 * Intents & helpers
 * -------------------------------------------------------------------------- */

/**
 * Opens the system page to manage Full‑screen Intent permission WITHOUT crashing.
 * Android 14+ requires ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT **with a `package:` URI**.
 * Falls back to the app’s Notification Settings on older/OEM ROMs.
 */
fun Context.openFullScreenIntentSettings() {
    try {
        if (Build.VERSION.SDK_INT >= 34) {
            val intent = Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT).apply {
                data = Uri.parse("package:$packageName") // <— mandatory on many devices
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(intent)
            return
        }
    } catch (_: ActivityNotFoundException) {
        // fall through
    }

    // Fallback
    val fallback = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
        putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    startActivity(fallback)
}