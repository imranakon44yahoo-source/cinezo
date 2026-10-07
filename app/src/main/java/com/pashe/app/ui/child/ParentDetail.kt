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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
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
import com.pashe.app.domain.MealTiming
import com.pashe.app.domain.Medicine
import com.pashe.app.domain.ParentProfile
import com.pashe.app.domain.Schedule
import com.pashe.app.ui.components.LoadingBox
import com.pashe.app.ui.components.PasheTopBar
import com.pashe.app.ui.components.SectionCard
import com.pashe.app.ui.components.isBengali
import com.pashe.app.ui.theme.PasheColors
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId

class ParentDetailViewModel(val parentId: String) : ViewModel() {
    val parent = Graph.childRepo.parent(parentId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val medicines = Graph.childRepo.medicines(parentId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    var busy by mutableStateOf(false)
    var error by mutableStateOf(false)

    fun generateCode(parent: ParentProfile) = launchAction { Graph.childRepo.generatePairingCode(parent) }
    fun deleteMedicine(medId: String) = launchAction { Graph.childRepo.deleteMedicine(parentId, medId) }
    fun deleteParent(parent: ParentProfile, onDone: () -> Unit) = launchAction { Graph.childRepo.deleteParent(parent); onDone() }

    private fun launchAction(block: suspend () -> Unit) {
        busy = true
        error = false
        viewModelScope.launch {
            try { block() } catch (e: Exception) { error = true } finally { busy = false }
        }
    }
}

@Composable
fun ParentDetailScreen(
    parentId: String,
    onBack: () -> Unit,
    onEditParent: () -> Unit,
    onAddMedicine: () -> Unit,
    onEditMedicine: (String) -> Unit,
    onHistory: () -> Unit,
    onDeleted: () -> Unit,
) {
    val vm: ParentDetailViewModel = viewModel(key = "parent-$parentId") { ParentDetailViewModel(parentId) }
    val parent by vm.parent.collectAsStateWithLifecycle()
    val medicines by vm.medicines.collectAsStateWithLifecycle()
    val bengali = isBengali()
    var deletingMedicine by rememberSaveable { mutableStateOf<String?>(null) }
    var deletingParent by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        topBar = {
            PasheTopBar(parent?.displayName(bengali) ?: "", onBack) {
                IconButton(onClick = onEditParent) { Icon(Icons.Default.Edit, stringResource(R.string.edit)) }
            }
        },
        containerColor = PasheColors.Background,
    ) { padding ->
        val p = parent
        val meds = medicines
        if (p == null || meds == null) { LoadingBox(); return@Scaffold }
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { PairingCard(p, vm.busy) { vm.generateCode(p) } }
            item {
                OutlinedButton(onClick = onHistory, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.history_7days)) }
            }
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.medicines), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    Button(onClick = onAddMedicine) {
                        Icon(Icons.Default.Add, null)
                        Text(stringResource(R.string.add_medicine))
                    }
                }
                Text(stringResource(R.string.times_in_parent_zone, p.timezone), color = PasheColors.InkMuted, style = MaterialTheme.typography.bodySmall)
            }
            if (meds.isEmpty()) item { Text(stringResource(R.string.no_medicines), color = PasheColors.InkMuted) }
            items(meds, key = { it.id }) { med ->
                MedicineCard(med, onEdit = { onEditMedicine(med.id) }, onDelete = { deletingMedicine = med.id })
            }
            item {
                Spacer(Modifier.height(24.dp))
                TextButton(onClick = { deletingParent = true }) { Text(stringResource(R.string.delete_parent), color = PasheColors.Missed) }
            }
            if (vm.error) item { Text(stringResource(R.string.error_generic), color = PasheColors.Missed) }
        }

        deletingMedicine?.let { medId ->
            val name = meds.firstOrNull { it.id == medId }?.name.orEmpty()
            ConfirmDialog(stringResource(R.string.delete_medicine_confirm, name), onConfirm = {
                vm.deleteMedicine(medId); deletingMedicine = null
            }, onDismiss = { deletingMedicine = null })
        }
        if (deletingParent) {
            ConfirmDialog(stringResource(R.string.delete_parent_confirm), onConfirm = {
                deletingParent = false
                vm.deleteParent(p, onDeleted)
            }, onDismiss = { deletingParent = false })
        }
    }
}

@Composable
private fun PairingCard(parent: ParentProfile, busy: Boolean, onGenerate: () -> Unit) {
    val bengali = isBengali()
    SectionCard {
        Text(stringResource(R.string.pairing_title), style = MaterialTheme.typography.titleMedium)
        Text(
            stringResource(if (parent.deviceUid != null) R.string.device_linked else R.string.device_not_linked),
            color = if (parent.deviceUid != null) PasheColors.Taken else PasheColors.Pending,
        )
        val expiresAt = parent.pairingCodeExpiresAt
        val code = parent.pairingCode
        if (code != null && expiresAt != null && expiresAt > System.currentTimeMillis()) {
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.pairing_desc), color = PasheColors.InkMuted)
            Text(
                code.chunked(3).joinToString(" "),
                fontSize = 40.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace,
                color = PasheColors.TealDark, letterSpacing = 4.sp,
                modifier = Modifier.padding(vertical = 8.dp),
            )
            val local = Instant.ofEpochMilli(expiresAt).atZone(ZoneId.systemDefault())
            Text(
                stringResource(R.string.code_valid_until,
                    "${BnFormat.time(local.toLocalTime(), bengali)}, ${BnFormat.shortDate(local.toLocalDate(), bengali)}"),
                color = PasheColors.InkMuted, style = MaterialTheme.typography.bodySmall,
            )
        }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = onGenerate, enabled = !busy) { Text(stringResource(R.string.generate_code)) }
    }
}

@Composable
private fun MedicineCard(medicine: Medicine, onEdit: () -> Unit, onDelete: () -> Unit) {
    val bengali = isBengali()
    SectionCard {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(medicine.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    "${medicine.dose} · " + stringResource(if (medicine.mealTiming == MealTiming.BEFORE) R.string.meal_before else R.string.meal_after),
                    color = PasheColors.InkMuted,
                )
                Text(
                    medicine.times.mapNotNull(Schedule::parseTime).joinToString(", ") { BnFormat.time(it, bengali) },
                    color = PasheColors.Teal,
                )
                medicine.note?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = PasheColors.InkMuted) }
                if (!medicine.active) Text(stringResource(R.string.inactive), color = PasheColors.Missed, style = MaterialTheme.typography.bodySmall)
            }
            IconButton(onClick = onEdit) { Icon(Icons.Default.Edit, stringResource(R.string.edit), tint = PasheColors.Teal) }
            IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, stringResource(R.string.delete), tint = PasheColors.Missed) }
        }
    }
}

@Composable
fun ConfirmDialog(text: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        text = { Text(text) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(R.string.delete), color = PasheColors.Missed) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
