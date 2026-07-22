package com.mikeliang.questtracker.reminders

import android.Manifest
import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The two revocable capabilities reminders depend on. Every check degrades safely:
 * when a capability is missing the feature is simply off — never a crash, never a
 * nag. Below the API levels where these permissions exist, they are always effectively
 * granted.
 */
@Singleton
class ReminderPermissions @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    private val alarmManager: AlarmManager? = context.getSystemService()

    /**
     * Whether we may set exact alarms. True below API 31 (granted at install), and on
     * 31+ reflects the user's toggle. When false the scheduler falls back to inexact
     * alarms rather than throwing.
     */
    fun canScheduleExactAlarms(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            alarmManager?.canScheduleExactAlarms() == true
        } else {
            true
        }

    /**
     * Whether we may post notifications. True below API 33 (implicitly granted); on
     * 33+ reflects the runtime grant. When false [ReminderNotifier] silently skips.
     */
    fun canPostNotifications(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
        } else {
            true
        }

    /**
     * The system settings screen where the user can grant exact-alarm access, or null
     * if that concept doesn't exist on this OS version. The UI may offer this; nothing
     * forces it.
     */
    fun exactAlarmSettingsIntent(): Intent? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                .setData(Uri.fromParts("package", context.packageName, null))
        } else {
            null
        }
}
