package com.pashe.app.ui.child

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pashe.app.Graph
import com.pashe.app.R
import com.pashe.app.domain.ParentProfile
import com.pashe.app.domain.Relation
import com.pashe.app.ui.components.PasheTopBar
import com.pashe.app.ui.theme.PasheColors
import kotlinx.coroutines.launch
import java.time.ZoneId

class ParentEditViewModel(private val parentId: String?) : ViewModel() {
    var name by mutableStateOf("")
    var relation by mutableStateOf(Relation.MOTHER)
    var timezone by mutableStateOf(ParentProfile.DEFAULT_TIMEZONE)
    var saving by mutableStateOf(false)
    var error by mutableStateOf<Int?>(null)

    init {
        if (parentId != null) viewModelScope.launch {
            runCatching { Graph.childRepo.getParent(parentId) }.getOrNull()?.let {
                name = it.name; relation = it.relation; timezone = it.timezone
            }
        }
    }

    fun save(onSaved: (String) -> Unit) {
        if (name.isBlank()) { error = R.string.name_required; return }
        saving = true
        viewModelScope.launch {
            try {
                onSaved(Graph.childRepo.saveParent(parentId, name, relation, timezone))
            } catch (e: Exception) {
                error = R.string.error_generic
            } finally {
                saving = false
            }
        }
    }
}

@Composable
fun ParentEditScreen(parentId: String?, onBack: () -> Unit, onSaved: (String) -> Unit) {
    val vm: ParentEditViewModel = viewModel(key = "parent-edit-$parentId") { ParentEditViewModel(parentId) }
    var pickingZone by rememberSaveable { mutableStateOf(false) }
    Scaffold(
        topBar = { PasheTopBar(stringResource(if (parentId == null) R.string.add_parent_title else R.string.edit_parent_title), onBack) },
        containerColor = PasheColors.Background,
    ) { padding ->
        Column(
            Modifier.padding(padding).imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            OutlinedTextField(
                value = vm.name, onValueChange = { vm.name = it; vm.error = null },
                label = { Text(stringResource(R.string.parent_name)) }, singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(stringResource(R.string.relation), style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(Relation.MOTHER to R.string.relation_mother, Relation.FATHER to R.string.relation_father, Relation.OTHER to R.string.relation_other)
                    .forEach { (value, label) ->
                        FilterChip(selected = vm.relation == value, onClick = { vm.relation = value }, label = { Text(stringResource(label)) })
                    }
            }
            Text(stringResource(R.string.timezone), style = MaterialTheme.typography.titleMedium)
            TextButton(onClick = { pickingZone = true }) { Text(vm.timezone, style = MaterialTheme.typography.bodyLarge) }
            vm.error?.let { Text(stringResource(it), color = PasheColors.Missed) }
            Button(
                onClick = { vm.save(onSaved) }, enabled = !vm.saving,
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
            ) { Text(stringResource(R.string.save)) }
        }
    }
    if (pickingZone) {
        TimezonePickerDialog(onPick = { vm.timezone = it; pickingZone = false }, onDismiss = { pickingZone = false })
    }
}

private val COMMON_ZONES = listOf(
    "Asia/Dhaka", "Asia/Kolkata", "Asia/Dubai", "Asia/Riyadh", "Asia/Qatar", "Asia/Kuwait", "Asia/Singapore",
    "Asia/Kuala_Lumpur", "Asia/Tokyo", "Europe/London", "Europe/Rome", "Europe/Berlin", "America/New_York",
    "America/Toronto", "America/Los_Angeles", "Australia/Sydney",
)

@Composable
fun TimezonePickerDialog(onPick: (String) -> Unit, onDismiss: () -> Unit) {
    var query by remember { mutableStateOf("") }
    val all = remember { COMMON_ZONES + (ZoneId.getAvailableZoneIds().sorted() - COMMON_ZONES.toSet()) }
    val filtered = remember(query) { all.filter { it.contains(query.trim().replace(' ', '_'), ignoreCase = true) } }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
        title = { Text(stringResource(R.string.timezone)) },
        text = {
            Column {
                OutlinedTextField(
                    value = query, onValueChange = { query = it }, singleLine = true,
                    placeholder = { Text(stringResource(R.string.timezone_search)) }, modifier = Modifier.fillMaxWidth(),
                )
                LazyColumn(Modifier.heightIn(max = 360.dp)) {
                    items(filtered) { zone ->
                        Text(zone, Modifier.fillMaxWidth().clickable { onPick(zone) }.padding(vertical = 12.dp))
                    }
                }
            }
        },
    )
}
