package com.pashe.app.ui.parent

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.pashe.app.R
import com.pashe.app.alarm.AlarmScheduler
import com.pashe.app.alarm.Notifications
import com.pashe.app.ui.components.SectionCard
import com.pashe.app.ui.theme.PasheColors

/** Every permission the alarm needs, with how to check it and how to ask for it. */
object AlarmPermissions {
    fun notifications(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        else NotificationManagerCompat.from(context).areNotificationsEnabled()

    fun exactAlarms(context: Context) = AlarmScheduler.canScheduleExact(context)

    fun fullScreen(context: Context) = Notifications.canUseFullScreen(context)

    fun battery(context: Context): Boolean =
        context.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(context.packageName)

    fun allGranted(context: Context) =
        notifications(context) && exactAlarms(context) && fullScreen(context) && battery(context)

    private fun packageUri(context: Context) = Uri.parse("package:${context.packageName}")

    fun open(context: Context, action: String) {
        val intent = Intent(action, packageUri(context)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }.onFailure {
            // Some manufacturers lack the direct screen; fall back to the app's settings page.
            runCatching { context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageUri(context)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        }
    }
}

@Composable
fun PermissionsScreen(onDone: () -> Unit) {
    val context = LocalContext.current
    var refresh by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { refresh++ }
    val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { refresh++ }

    // Read refresh so these are re-evaluated after returning from system settings.
    val notificationsOk = refresh >= 0 && AlarmPermissions.notifications(context)
    val exactOk = refresh >= 0 && AlarmPermissions.exactAlarms(context)
    val fullScreenOk = refresh >= 0 && AlarmPermissions.fullScreen(context)
    val batteryOk = refresh >= 0 && AlarmPermissions.battery(context)

    Column(
        Modifier.fillMaxSize().background(PasheColors.Background).systemBarsPadding()
            .verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(stringResource(R.string.perm_title), fontSize = 30.sp, fontWeight = FontWeight.Bold, color = PasheColors.TealDark)
        Text(stringResource(R.string.perm_desc), fontSize = 24.sp, lineHeight = 34.sp)

        PermissionCard(R.string.perm_notifications_title, R.string.perm_notifications_desc, notificationsOk) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            else AlarmPermissions.open(context, Settings.ACTION_APP_NOTIFICATION_SETTINGS)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PermissionCard(R.string.perm_exact_title, R.string.perm_exact_desc, exactOk) {
                AlarmPermissions.open(context, Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            PermissionCard(R.string.perm_fullscreen_title, R.string.perm_fullscreen_desc, fullScreenOk) {
                AlarmPermissions.open(context, Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT)
            }
        }
        PermissionCard(R.string.perm_battery_title, R.string.perm_battery_desc, batteryOk) {
            @Suppress("BatteryLife") // Missed medicine alarms are exactly what this exemption exists for.
            AlarmPermissions.open(context, Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
        }

        Button(
            onClick = onDone,
            shape = RoundedCornerShape(24.dp),
            colors = ButtonDefaults.buttonColors(containerColor = PasheColors.Taken),
            modifier = Modifier.fillMaxWidth().heightIn(min = 80.dp),
        ) { Text(stringResource(R.string.perm_continue), fontSize = 30.sp, fontWeight = FontWeight.Bold) }
    }
}

@Composable
private fun PermissionCard(title: Int, description: Int, granted: Boolean, onRequest: () -> Unit) {
    SectionCard {
        Text(stringResource(title), fontSize = 26.sp, fontWeight = FontWeight.Bold, color = PasheColors.Ink)
        Text(stringResource(description), fontSize = 24.sp, lineHeight = 32.sp, color = PasheColors.InkMuted)
        if (granted) {
            Text(stringResource(R.string.perm_done), fontSize = 24.sp, fontWeight = FontWeight.Bold, color = PasheColors.Taken,
                modifier = Modifier.padding(top = 8.dp))
        } else {
            Button(
                onClick = onRequest,
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(top = 8.dp),
            ) { Text(stringResource(R.string.perm_allow), fontSize = 24.sp) }
        }
    }
}
