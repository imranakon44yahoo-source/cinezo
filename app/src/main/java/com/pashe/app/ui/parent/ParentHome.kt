package com.pashe.app.ui.parent

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pashe.app.Graph
import com.pashe.app.R
import com.pashe.app.alarm.DoseActions
import com.pashe.app.alarm.DoseAlarms
import com.pashe.app.alarm.Notifications
import com.pashe.app.data.ParentDeviceRepository
import com.pashe.app.domain.BnFormat
import com.pashe.app.domain.DoseStatus
import com.pashe.app.domain.ResolvedDose
import com.pashe.app.domain.Schedule
import com.pashe.app.ui.components.StatusChip
import com.pashe.app.ui.theme.PasheColors
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class ParentHomeViewModel : ViewModel() {
    private var listener: Job? = null

    init {
        viewModelScope.launch { Graph.parentRepo.sync() }
    }

    fun startListening() {
        if (listener?.isActive != true) listener = Graph.parentRepo.listen(viewModelScope)
    }

    fun stopListening() {
        listener?.cancel()
        listener = null
    }
}

/** Doses can be confirmed from an hour before their time. */
private const val EARLY_CONFIRM_MILLIS = 60L * 60 * 1000

@Composable
fun ParentHomeScreen(onFixPermissions: () -> Unit, vm: ParentHomeViewModel = viewModel()) {
    val context = LocalContext.current
    val store = Graph.store
    val version by store.version.collectAsState()
    val now by produceState(System.currentTimeMillis()) {
        while (true) { delay(30_000); value = System.currentTimeMillis() }
    }
    var permissionsCheck by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { permissionsCheck++; vm.startListening() }
    LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) { vm.stopListening() }
    val permissionsOk = remember(permissionsCheck) { AlarmPermissions.allGranted(context) }

    val doses = remember(version, now) { DoseAlarms.today(store, now) }
    val zone = ParentDeviceRepository.zoneFor(store)
    val greetingName = store.parentProfile?.displayName() ?: ""

    LazyColumn(
        Modifier.fillMaxSize().background(PasheColors.Background).systemBarsPadding(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Text(stringResource(R.string.parent_home_title), fontSize = 34.sp, fontWeight = FontWeight.Bold, color = PasheColors.TealDark)
            Text(
                (if (greetingName.isNotBlank()) "$greetingName · " else "") + BnFormat.date(Schedule.today(zone, now), bengali = true),
                fontSize = 24.sp, color = PasheColors.InkMuted,
            )
        }
        if (!permissionsOk) item {
            Text(
                stringResource(R.string.parent_perm_banner),
                fontSize = 24.sp, color = PasheColors.Missed, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.fillMaxWidth().background(PasheColors.MissedBg, RoundedCornerShape(20.dp))
                    .clickable(onClick = onFixPermissions).padding(16.dp),
            )
        }
        if (doses.isEmpty()) item {
            Text(
                stringResource(R.string.parent_none_today), fontSize = 28.sp, textAlign = TextAlign.Center, lineHeight = 40.sp,
                color = PasheColors.Ink, modifier = Modifier.fillMaxWidth().padding(top = 48.dp),
            )
        }
        items(doses, key = { it.planned.doseId }) { dose ->
            ParentDoseCard(
                dose = dose,
                canConfirm = dose.status != DoseStatus.TAKEN && now >= dose.planned.scheduledAt - EARLY_CONFIRM_MILLIS,
                onTaken = { DoseActions.confirm(context, listOf(dose.planned)) },
            )
        }
    }
    LaunchedEffect(Unit) { Notifications.createChannels(context) }
}

@Composable
private fun ParentDoseCard(dose: ResolvedDose, canConfirm: Boolean, onTaken: () -> Unit) {
    val context = LocalContext.current
    val medicine = dose.planned.medicine
    val border: Color = when (dose.status) {
        DoseStatus.TAKEN -> PasheColors.TakenBg
        DoseStatus.PENDING -> PasheColors.Surface
        DoseStatus.MISSED -> PasheColors.MissedBg
    }
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = border),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    BnFormat.time(dose.planned.localTime, bengali = true),
                    fontSize = 30.sp, fontWeight = FontWeight.Bold, color = PasheColors.TealDark, modifier = Modifier.weight(1f),
                )
                StatusChip(dose.status, parentMode = true, fontSize = 24.sp)
            }
            Text(medicine.name, fontSize = 28.sp, fontWeight = FontWeight.Bold, color = PasheColors.Ink, lineHeight = 36.sp)
            Text("${medicine.dose} · ${Notifications.mealText(context, medicine.mealTiming)}", fontSize = 24.sp, color = PasheColors.Ink)
            medicine.note?.let { Text(it, fontSize = 24.sp, color = PasheColors.InkMuted) }
            if (canConfirm) {
                Button(
                    onClick = onTaken,
                    shape = RoundedCornerShape(20.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = PasheColors.Taken, contentColor = PasheColors.OnTaken),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 80.dp).padding(top = 8.dp),
                ) { Text(stringResource(R.string.taken_btn), fontSize = 32.sp, fontWeight = FontWeight.Bold) }
            }
        }
    }
}
