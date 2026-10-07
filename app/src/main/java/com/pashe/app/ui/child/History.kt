package com.pashe.app.ui.child

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pashe.app.Graph
import com.pashe.app.R
import com.pashe.app.domain.BnFormat
import com.pashe.app.domain.DoseStatus
import com.pashe.app.domain.ParentProfile
import com.pashe.app.domain.ResolvedDose
import com.pashe.app.domain.Schedule
import com.pashe.app.ui.components.EmptyMessage
import com.pashe.app.ui.components.LoadingBox
import com.pashe.app.ui.components.PasheTopBar
import com.pashe.app.ui.components.SectionCard
import com.pashe.app.ui.components.isBengali
import com.pashe.app.ui.theme.PasheColors
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate

data class HistoryDay(val date: LocalDate, val doses: List<ResolvedDose>)
data class HistoryState(val parent: ParentProfile, val days: List<HistoryDay>, val adherence: Int?)

@OptIn(ExperimentalCoroutinesApi::class)
class HistoryViewModel(parentId: String) : ViewModel() {
    val state = Graph.childRepo.parent(parentId).filterNotNull().flatMapLatest { parent ->
        val zone = Schedule.zoneOf(parent.timezone)
        val now = System.currentTimeMillis()
        val today = Schedule.today(zone, now)
        val from = Schedule.startOfDay(zone, today.minusDays(6))
        val to = Schedule.startOfDay(zone, today.plusDays(1))
        combine(Graph.childRepo.medicines(parentId), Graph.childRepo.doses(parentId, from, to), minuteTicker()) { meds, records, tick ->
            val days = (0L..6L).map { back ->
                val date = today.minusDays(back)
                // Only doses whose time has come count towards history.
                HistoryDay(date, resolveDay(meds, records, zone, date, tick).filter { it.planned.scheduledAt <= tick })
            }
            HistoryState(parent, days, Schedule.adherencePercent(days.flatMap { it.doses }))
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}

@Composable
fun HistoryScreen(parentId: String, onBack: () -> Unit) {
    val vm: HistoryViewModel = viewModel(key = "history-$parentId") { HistoryViewModel(parentId) }
    val state by vm.state.collectAsStateWithLifecycle()
    val bengali = isBengali()
    Scaffold(
        topBar = { PasheTopBar(stringResource(R.string.history_title), onBack) },
        containerColor = PasheColors.Background,
    ) { padding ->
        val s = state ?: run { LoadingBox(); return@Scaffold }
        val zone = Schedule.zoneOf(s.parent.timezone)
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                SectionCard {
                    Text(s.parent.displayName(bengali), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.adherence), color = PasheColors.InkMuted)
                    Text(
                        s.adherence?.let { stringResource(R.string.adherence_value, BnFormat.number(it, bengali)) }
                            ?: stringResource(R.string.adherence_none),
                        fontSize = 48.sp, fontWeight = FontWeight.Bold, color = adherenceColor(s.adherence),
                    )
                    s.adherence?.let {
                        LinearProgressIndicator(
                            progress = { it / 100f }, color = adherenceColor(it), trackColor = PasheColors.Mint,
                            modifier = Modifier.fillMaxWidth().height(10.dp),
                        )
                    }
                }
            }
            if (s.days.all { it.doses.isEmpty() }) item { EmptyMessage(stringResource(R.string.no_medicines)) }
            items(s.days.filter { it.doses.isNotEmpty() }, key = { it.date.toString() }) { day ->
                SectionCard {
                    val taken = day.doses.count { it.status == DoseStatus.TAKEN }
                    val missed = day.doses.count { it.status == DoseStatus.MISSED }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(BnFormat.date(day.date, bengali), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        Schedule.adherencePercent(day.doses)?.let {
                            Text(stringResource(R.string.adherence_value, BnFormat.number(it, bengali)),
                                color = adherenceColor(it), fontWeight = FontWeight.Bold)
                        }
                    }
                    Text(
                        stringResource(R.string.taken_count, BnFormat.number(taken, bengali)) + " · " +
                            stringResource(R.string.missed_count, BnFormat.number(missed, bengali)),
                        color = PasheColors.InkMuted, style = MaterialTheme.typography.bodySmall,
                    )
                    Spacer(Modifier.height(8.dp))
                    Column {
                        day.doses.forEachIndexed { i, dose ->
                            if (i > 0) HorizontalDivider(Modifier.padding(vertical = 6.dp), color = PasheColors.Mint)
                            DoseRow(dose, zone)
                        }
                    }
                }
            }
        }
    }
}

private fun adherenceColor(percent: Int?) = when {
    percent == null -> PasheColors.InkMuted
    percent >= 80 -> PasheColors.Taken
    percent >= 50 -> PasheColors.Pending
    else -> PasheColors.Missed
}
