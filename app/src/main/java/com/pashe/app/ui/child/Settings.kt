package com.pashe.app.ui.child

import android.content.Intent
import android.net.Uri
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.firebase.messaging.FirebaseMessaging
import com.pashe.app.BuildConfig
import com.pashe.app.Graph
import com.pashe.app.R
import com.pashe.app.ui.components.SectionCard
import com.pashe.app.ui.components.isBengali
import com.pashe.app.ui.components.PasheTopBar
import com.pashe.app.ui.theme.PasheColors
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class SettingsViewModel : ViewModel() {
    var name by mutableStateOf("")
    var message by mutableStateOf<Int?>(null)
    var busy by mutableStateOf(false)

    init {
        viewModelScope.launch {
            Graph.childRepo.userProfile().collect { if (name.isEmpty()) name = it.name }
        }
    }

    fun saveName() = viewModelScope.launch { runCatching { Graph.childRepo.updateUser(mapOf("name" to name.trim())) } }

    fun setLanguage(tag: String) {
        // Stored server-side too, so missed-dose alerts arrive in the same language.
        viewModelScope.launch { runCatching { Graph.childRepo.updateUser(mapOf("language" to tag)) } }
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tag))
    }

    fun seed() {
        busy = true
        viewModelScope.launch {
            message = if (runCatching { Graph.childRepo.seedSampleData() }.isSuccess) R.string.seed_done else R.string.error_generic
            busy = false
        }
    }

    fun signOut(onDone: () -> Unit) = viewModelScope.launch {
        runCatching { Graph.childRepo.unregisterFcmToken(FirebaseMessaging.getInstance().token.await()) }
        Graph.auth.signOut()
        onDone()
    }
}

@Composable
fun ChildSettingsScreen(onBack: () -> Unit, onSignedOut: () -> Unit, vm: SettingsViewModel = viewModel()) {
    val context = LocalContext.current
    val bengali = isBengali()
    var showDisclaimer by rememberSaveable { mutableStateOf(false) }

    Scaffold(topBar = { PasheTopBar(stringResource(R.string.settings), onBack) }, containerColor = PasheColors.Background) { padding ->
        Column(
            Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SectionCard {
                Text(stringResource(R.string.language), style = MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(bengali, { vm.setLanguage("bn") }, { Text(stringResource(R.string.language_bn)) })
                    FilterChip(!bengali, { vm.setLanguage("en") }, { Text(stringResource(R.string.language_en)) })
                }
            }
            SectionCard {
                OutlinedTextField(
                    value = vm.name, onValueChange = { vm.name = it },
                    label = { Text(stringResource(R.string.your_name)) }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                TextButton(onClick = { vm.saveName() }) { Text(stringResource(R.string.save)) }
            }
            SectionCard {
                TextButton(onClick = {
                    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(Graph.PRIVACY_POLICY_URL))) }
                }) { Text(stringResource(R.string.privacy_policy)) }
                TextButton(onClick = { showDisclaimer = true }) { Text(stringResource(R.string.about_disclaimer)) }
            }
            SectionCard {
                OutlinedButton(onClick = vm::seed, enabled = !vm.busy) { Text(stringResource(R.string.seed_data)) }
                vm.message?.let { Text(stringResource(it), color = PasheColors.Teal) }
            }
            OutlinedButton(onClick = { vm.signOut(onSignedOut) }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.sign_out), color = PasheColors.Missed)
            }
            Text(stringResource(R.string.version, BuildConfig.VERSION_NAME), color = PasheColors.InkMuted, style = MaterialTheme.typography.bodySmall)
        }
    }
    if (showDisclaimer) {
        AlertDialog(
            onDismissRequest = { showDisclaimer = false },
            title = { Text(stringResource(R.string.disclaimer_title)) },
            text = { Text(stringResource(R.string.disclaimer_body)) },
            confirmButton = { TextButton(onClick = { showDisclaimer = false }) { Text(stringResource(R.string.ok)) } },
        )
    }
}
