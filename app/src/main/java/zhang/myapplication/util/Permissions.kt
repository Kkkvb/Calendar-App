package zhang.myapplication.util

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings

fun showPermissionDisclaimer(context: Context, permissionName: String, message: String) {
    AlertDialog.Builder(context)
        .setTitle("$permissionName Permission Required")
        .setMessage(message)
        .setPositiveButton("Go to Settings") { _, _ ->
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:${context.packageName}")
            }
            context.startActivity(intent)
        }
        .setNegativeButton("Cancel", null)
        .show()
}