package com.pashe.app.alarm

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pashe.app.Graph
import com.pashe.app.R
import com.pashe.app.domain.BnFormat
import com.pashe.app.domain.PlannedDose
import com.pashe.app.ui.theme.PasheColors
import com.pashe.app.ui.theme.PasheTheme

/** Full-screen alarm shown over the lock screen at dose time. Always Bengali. */
class AlarmActivity : AppCompatActivity() {
    private val slot = mutableLongStateOf(0L)
    private val attempt = mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Manifest showWhenLocked/turnScreenOn cover API 27+; Android 8.0 needs the window flags.
        @Suppress("DEPRECATION")
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O_MR1) {
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        readIntent(intent)
        setContent {
            PasheTheme {
                val version by Graph.store.version.collectAsState()
                val doses = remember(version, slot.longValue) { DoseAlarms.pendingAt(Graph.store, slot.longValue) }
                LaunchedEffect(doses.isEmpty()) { if (doses.isEmpty()) finish() }
                if (doses.isNotEmpty()) {
                    AlarmScreen(
                        doses = doses,
                        onTaken = { DoseActions.confirmSlot(this, slot.longValue); finish() },
                        onSnooze = { DoseActions.snooze(this, slot.longValue, attempt.intValue); finish() },
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        readIntent(intent)
    }

    private fun readIntent(intent: Intent) {
        slot.longValue = intent.getLongExtra(AlarmScheduler.EXTRA_SLOT, 0L)
        attempt.intValue = intent.getIntExtra(AlarmScheduler.EXTRA_ATTEMPT, 0)
    }

    companion object {
        fun intent(context: Context, slot: Long, attempt: Int) = Intent(context, AlarmActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION)
            .putExtra(AlarmScheduler.EXTRA_SLOT, slot)
            .putExtra(AlarmScheduler.EXTRA_ATTEMPT, attempt)
    }
}

@Composable
private fun AlarmScreen(doses: List<PlannedDose>, onTaken: () -> Unit, onSnooze: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PasheColors.AlarmBackground)
            .systemBarsPadding()
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("💊", fontSize = 64.sp)
        Text(
            stringResource(R.string.alarm_title),
            color = PasheColors.OnAlarm, fontSize = 34.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
        )
        Text(
            BnFormat.time(doses.first().localTime, bengali = true),
            color = PasheColors.OnAlarm, fontSize = 30.sp, fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(16.dp))
        Column(
            modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            doses.forEach { dose ->
                Card(
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = PasheColors.Surface),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(20.dp)) {
                        Text(dose.medicine.name, fontSize = 30.sp, fontWeight = FontWeight.Bold, color = PasheColors.Ink, lineHeight = 38.sp)
                        Text(dose.medicine.dose, fontSize = 28.sp, color = PasheColors.Ink)
                        Text(
                            Notifications.mealText(context, dose.medicine.mealTiming),
                            fontSize = 28.sp, fontWeight = FontWeight.SemiBold, color = PasheColors.TealDark,
                        )
                        dose.medicine.note?.let { Text(it, fontSize = 24.sp, color = PasheColors.InkMuted) }
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = onTaken,
            colors = ButtonDefaults.buttonColors(containerColor = PasheColors.Taken, contentColor = PasheColors.OnTaken),
            shape = RoundedCornerShape(28.dp),
            modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp),
        ) {
            Text(stringResource(R.string.taken_btn), fontSize = 40.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(16.dp))
        OutlinedButton(
            onClick = onSnooze,
            shape = RoundedCornerShape(20.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = PasheColors.OnAlarm),
            modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp),
        ) {
            Text(stringResource(R.string.snooze_btn), fontSize = 24.sp, textAlign = TextAlign.Center)
        }
    }
}
