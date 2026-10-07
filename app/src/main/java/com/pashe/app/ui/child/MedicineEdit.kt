package com.pashe.app.ui.child

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pashe.app.Graph
import com.pashe.app.R
import com.pashe.app.domain.BnFormat
import com.pashe.app.domain.MealTiming
import com.pashe.app.domain.Medicine
import com.pashe.app.domain.Schedule
import com.pashe.app.ui.components.PasheTopBar
import com.pashe.app.ui.components.isBengali
import com.pashe.app.ui.theme.PasheColors
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset

class MedicineEditViewModel(private val parentId: String, private val medId: String?) : ViewModel() {
    var name by mutableStateOf("")
    var dose by mutableStateOf("১টা ট্যাবলেট")
    var mealTiming by mutableStateOf(MealTiming.AFTER)
    val times = mutableStateListOf<LocalTime>()
    var startDate by mutableStateOf(LocalDate.now())
    var endDate by mutableStateOf<LocalDate?>(null)
    var note by mutableStateOf("")
    var active by mutableStateOf(true)
    var timezone by mutableStateOf("Asia/Dhaka")
    var saving by mutableStateOf(false)
    var error by mutableStateOf<Int?>(null)
    var saved by mutableStateOf(false)

    init {
        viewModelScope.launch {
            runCatching { Graph.childRepo.getParent(parentId) }.getOrNull()?.let {
                timezone = it.timezone
                if (medId == null) startDate = LocalDate.now(Schedule.zoneOf(it.timezone))
            }
            if (medId != null) runCatching { Graph.childRepo.getMedicine(parentId, medId) }.getOrNull()?.let { m ->
                name = m.name; dose = m.dose; mealTiming = m.mealTiming
                times.clear(); times.addAll(m.times.mapNotNull(Schedule::parseTime))
                runCatching { LocalDate.parse(m.startDate) }.getOrNull()?.let { startDate = it }
                endDate = m.endDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
                note = m.note.orEmpty(); active = m.active
            }
        }
    }

    fun addTime(time: LocalTime) {
        if (time !in times) { times.add(time); times.sort() }
        error = null
    }

    fun save() {
        error = when {
            name.isBlank() -> R.string.need_name_error
            times.isEmpty() -> R.string.need_time_error
            endDate?.isBefore(startDate) == true -> R.string.end_before_start_error
            else -> null
        }
        if (error != null) return
        saving = true
        viewModelScope.launch {
            try {
                Graph.childRepo.saveMedicine(
                    parentId,
                    Medicine(
                        id = medId.orEmpty(), name = name, dose = dose, mealTiming = mealTiming,
                        times = times.map(Schedule::formatTime), startDate = startDate.toString(),
                        endDate = endDate?.toString(), note = note, active = active,
                    ),
                )
                saved = true
            } catch (e: Exception) {
                error = R.string.error_generic
            } finally {
                saving = false
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MedicineEditScreen(parentId: String, medId: String?, onDone: () -> Unit) {
    val vm: MedicineEditViewModel = viewModel(key = "med-$parentId-$medId") { MedicineEditViewModel(parentId, medId) }
    val bengali = isBengali()
    var pickingTime by rememberSaveable { mutableStateOf(false) }
    var pickingStart by rememberSaveable { mutableStateOf(false) }
    var pickingEnd by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(vm.saved) { if (vm.saved) onDone() }

    Scaffold(
        topBar = { PasheTopBar(stringResource(if (medId == null) R.string.add_medicine_title else R.string.edit_medicine_title), onDone) },
        containerColor = PasheColors.Background,
    ) { padding ->
        Column(
            Modifier.padding(padding).imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            OutlinedTextField(
                value = vm.name, onValueChange = { vm.name = it; vm.error = null },
                label = { Text(stringResource(R.string.medicine_name)) },
                placeholder = { Text(stringResource(R.string.medicine_name_hint)) },
                singleLine = true, modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = vm.dose, onValueChange = { vm.dose = it },
                label = { Text(stringResource(R.string.dose_label)) },
                placeholder = { Text(stringResource(R.string.dose_hint)) },
                singleLine = true, modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(vm.mealTiming == MealTiming.BEFORE, { vm.mealTiming = MealTiming.BEFORE }, { Text(stringResource(R.string.meal_before)) })
                FilterChip(vm.mealTiming == MealTiming.AFTER, { vm.mealTiming = MealTiming.AFTER }, { Text(stringResource(R.string.meal_after)) })
            }

            Text(stringResource(R.string.times_label), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.times_in_parent_zone, vm.timezone), style = MaterialTheme.typography.bodySmall, color = PasheColors.InkMuted)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                vm.times.forEach { time ->
                    InputChip(
                        selected = false, onClick = { vm.times.remove(time) },
                        label = { Text(BnFormat.time(time, bengali)) },
                        trailingIcon = { Icon(Icons.Default.Close, stringResource(R.string.delete)) },
                    )
                }
                AssistChip(
                    onClick = { pickingTime = true },
                    label = { Text(stringResource(R.string.add_time)) },
                    leadingIcon = { Icon(Icons.Default.Add, null) },
                )
            }

            Text(stringResource(R.string.start_date), style = MaterialTheme.typography.titleMedium)
            OutlinedButton(onClick = { pickingStart = true }) { Text(BnFormat.date(vm.startDate, bengali)) }
            Text(stringResource(R.string.end_date), style = MaterialTheme.typography.titleMedium)
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = { pickingEnd = true }) {
                    Text(vm.endDate?.let { BnFormat.date(it, bengali) } ?: stringResource(R.string.end_date_none))
                }
                if (vm.endDate != null) TextButton(onClick = { vm.endDate = null }) { Text(stringResource(R.string.end_date_none)) }
            }
            OutlinedTextField(
                value = vm.note, onValueChange = { vm.note = it },
                label = { Text(stringResource(R.string.note)) }, modifier = Modifier.fillMaxWidth(),
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.active), modifier = Modifier.weight(1f))
                Switch(checked = vm.active, onCheckedChange = { vm.active = it })
            }
            vm.error?.let { Text(stringResource(it), color = PasheColors.Missed) }
            Button(onClick = vm::save, enabled = !vm.saving, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                Text(stringResource(R.string.save))
            }
        }
    }

    if (pickingTime) TimePickDialog(onPick = { vm.addTime(it); pickingTime = false }, onDismiss = { pickingTime = false })
    if (pickingStart) DatePickDialog(vm.startDate, onPick = { vm.startDate = it; pickingStart = false }, onDismiss = { pickingStart = false })
    if (pickingEnd) DatePickDialog(vm.endDate ?: vm.startDate, onPick = { vm.endDate = it; pickingEnd = false }, onDismiss = { pickingEnd = false })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimePickDialog(onPick: (LocalTime) -> Unit, onDismiss: () -> Unit) {
    val state = rememberTimePickerState(initialHour = 8, initialMinute = 0, is24Hour = false)
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = { onPick(LocalTime.of(state.hour, state.minute)) }) { Text(stringResource(R.string.ok)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
        text = { TimePicker(state = state) },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DatePickDialog(initial: LocalDate, onPick: (LocalDate) -> Unit, onDismiss: () -> Unit) {
    // The picker works in UTC midnights.
    val state = rememberDatePickerState(initialSelectedDateMillis = initial.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                state.selectedDateMillis?.let { onPick(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) } ?: onDismiss()
            }) { Text(stringResource(R.string.ok)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    ) { DatePicker(state = state) }
}
