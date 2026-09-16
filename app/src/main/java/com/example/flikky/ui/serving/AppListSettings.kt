package com.example.flikky.ui.serving

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import com.example.flikky.R

/** OEM app-list controls have no portable runtime permission request. */
internal fun openAppListSettings(context: Context) {
    val intents = listOf(
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")),
        Intent(Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS),
    )
    for (intent in intents) {
        try {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return
        } catch (_: ActivityNotFoundException) {
            // An OEM may omit the per-app activity; try the standard apps list.
        } catch (_: SecurityException) {
            // Some ROMs reject direct entry into their per-app settings.
        }
    }
    Toast.makeText(context, R.string.apps_access_settings_unavailable, Toast.LENGTH_LONG).show()
}
