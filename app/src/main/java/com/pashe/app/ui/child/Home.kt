package com.pashe.app.ui.child

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pashe.app.Graph
import com.pashe.app.R
import com.pashe.app.domain.BnFormat
import com.pashe.app.domain.DoseStatus
import com.pashe.app.domain.MealTiming
import com.pashe.app.domain.ResolvedDose
import com.pashe.app.domain.Schedule
import com.pashe.app.ui.components.EmptyMessage
import com.pashe.app.ui.components.LoadingBox
import com.pashe.app.ui.components.PasheTopBar
import com.pashe.app.ui.components.SectionCard
import com.pashe.app.ui.components.StatusChip
import com.pashe.app.ui.components.isBengali
import com.pashe.app.ui.theme.PasheColors
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import java.time.Instant
import java.time.ZoneId

@OptIn(ExperimentalCoroutinesApi::class)
class DashboardViewModel : ViewModel() {
    val days: StateFlow<List<ParentDay>?> = Graph.childRepo.parents()
        .flatMapLatest { parents ->
            if (parents.isEmpty()) flowOf(emptyList())
            else combine(parents.map(::parentToday)) { it.toList() }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}

@Composable
fun ChildHomeScreen(
    onOpenParent: (String) -> Unit,
    onAddParent: () -> Unit,
    onSettings: () -> Unit,
    vm: DashboardViewModel = viewModel(),
) {
    val days by vm.days.collectAsStateWithLifecycle()
    // Missed-dose alerts need the notification permission on Android 13+.
    val context = LocalContext.current
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
    Scaffold(
        topBar = {
            PasheTopBar(stringResource(R.string.dashboard_title)) {
                IconButton(onClick = onSettings) { Icon(Icons.Default.Settings, stringResource(R.string.settings)) }
            }
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAddParent,
                icon = { Icon(Icons.Default.Add, null) },
                text = { Text(stringResource(R.string.add_parent)) },
                containerColor = PasheColors.Teal, contentColor = PasheColors.Surface,
            )
        },
        containerColor = PasheColors.Background,
    ) { padding ->
        val list = days
        when {
            list == null -> LoadingBox()
            list.isEmpty() -> EmptyMessage(stringResource(R.string.no_parents), Modifier.padding(padding))
            else -> LazyColumn(
                Modifier.fillMaxSize().padding(padding),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp, 16.dp, 16.dp, 96.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                items(list, key = { it.parent.id }) { day -> ParentTodayCard(day, onClick = { onOpenParent(day.parent.id) }) }
            }
        }
    }
}

@Composable
private fun ParentTodayCard(day: ParentDay, onClick: () -> Unit) {
    val bengali = isBengali()
    SectionCard(Modifier.clickable(onClick = onClick)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(day.parent.displayName(bengali), style = MaterialTheme.typography.titleLarge, color = PasheColors.TealDark)
                if (day.parent.relation != com.pashe.app.domain.Relation.OTHER) {
                    Text(day.parent.name, color = PasheColors.InkMuted)
                }
            }
            val taken = day.doses.count { it.status == DoseStatus.TAKEN }
            if (day.doses.isNotEmpty()) {
                Text(
                    stringResource(R.string.today_summary, BnFormat.number(taken, bengali), BnFormat.number(day.doses.size, bengali)),
                    color = PasheColors.InkMuted, style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        if (day.doses.isEmpty()) {
            Text(stringResource(R.string.no_doses_today), color = PasheColors.InkMuted)
        } else {
            day.doses.forEachIndexed { index, dose ->
                if (index > 0) HorizontalDivider(Modifier.padding(vertical = 8.dp), color = PasheColors.Mint)
                DoseRow(dose, Schedule.zoneOf(day.parent.timezone))
            }
        }
    }
}

/** One dose with the time in the parent's timezone and, when different, in the child's own. */
@Composable
fun DoseRow(dose: ResolvedDose, parentZone: ZoneId, showStatus: Boolean = true) {
    val bengali = isBengali()
    val medicine = dose.planned.medicine
    val instant = Instant.ofEpochMilli(dose.planned.scheduledAt)
    val myZone = ZoneId.systemDefault()
    val sameOffset = parentZone.rules.getOffset(instant) == myZone.rules.getOffset(instant)
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.weight(1f)) {
            Text(medicine.name, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyLarge)
            Text(
                "${medicine.dose} · " + stringResource(if (medicine.mealTiming == MealTiming.BEFORE) R.string.meal_before else R.string.meal_after),
                color = PasheColors.InkMuted, style = MaterialTheme.typography.bodyMedium,
            )
            val theirs = BnFormat.time(dose.planned.localTime, bengali)
            Text(
                if (sameOffset) theirs
                else "$theirs (${stringResource(R.string.their_time)}) · " +
                    "${BnFormat.time(instant.atZone(myZone).toLocalTime(), bengali)} (${stringResource(R.string.your_time)})",
                color = PasheColors.Teal, style = MaterialTheme.typography.bodyMedium,
            )
        }
        if (showStatus) {
            Spacer(Modifier.width(8.dp))
            StatusChip(dose.status)
        }
    }
}
