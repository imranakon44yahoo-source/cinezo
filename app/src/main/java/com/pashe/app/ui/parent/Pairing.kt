package com.pashe.app.ui.parent

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pashe.app.Graph
import com.pashe.app.R
import com.pashe.app.data.ParentDeviceRepository.PairResult
import com.pashe.app.ui.theme.HindSiliguri
import com.pashe.app.ui.theme.PasheColors
import kotlinx.coroutines.launch

class PairingViewModel : ViewModel() {
    var code by mutableStateOf("")
    var loading by mutableStateOf(false)
    var error by mutableStateOf<Int?>(null)
    var paired by mutableStateOf(false)

    fun submit() {
        loading = true
        error = null
        viewModelScope.launch {
            when (val result = Graph.parentRepo.redeemPairingCode(code)) {
                is PairResult.Success -> paired = true
                PairResult.InvalidCode -> error = R.string.pair_error_invalid
                is PairResult.Failed -> error = if (result.error is java.io.IOException) R.string.error_offline else R.string.error_generic
            }
            loading = false
        }
    }
}

/** Converts Bengali digits typed on a Bengali keyboard to ASCII. */
private fun normalizeDigits(input: String) = input.map { c ->
    if (c in '০'..'৯') '0' + (c - '০') else c
}.filter { it.isDigit() }.joinToString("").take(6)

@Composable
fun PairingScreen(onPaired: () -> Unit, onWrongMode: () -> Unit, vm: PairingViewModel = viewModel()) {
    LaunchedEffect(vm.paired) { if (vm.paired) onPaired() }
    Column(
        Modifier.fillMaxSize().background(PasheColors.Background).systemBarsPadding().imePadding()
            .verticalScroll(rememberScrollState()).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Spacer(Modifier.height(16.dp))
        Text(stringResource(R.string.pair_title), fontSize = 32.sp, fontWeight = FontWeight.Bold, color = PasheColors.TealDark, textAlign = TextAlign.Center, lineHeight = 42.sp)
        Text(stringResource(R.string.pair_desc), fontSize = 24.sp, color = PasheColors.Ink, textAlign = TextAlign.Center, lineHeight = 34.sp)
        OutlinedTextField(
            value = vm.code,
            onValueChange = { vm.code = normalizeDigits(it); vm.error = null },
            textStyle = TextStyle(fontFamily = HindSiliguri, fontSize = 44.sp, fontWeight = FontWeight.Bold, letterSpacing = 8.sp, textAlign = TextAlign.Center),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            singleLine = true,
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.fillMaxWidth(),
        )
        vm.error?.let { Text(stringResource(it), fontSize = 24.sp, color = PasheColors.Missed, textAlign = TextAlign.Center) }
        Button(
            onClick = vm::submit,
            enabled = vm.code.length == 6 && !vm.loading,
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier.fillMaxWidth().heightIn(min = 80.dp),
        ) {
            if (vm.loading) CircularProgressIndicator(color = PasheColors.Surface)
            else Text(stringResource(R.string.pair_button), fontSize = 30.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(24.dp))
        TextButton(onClick = onWrongMode) { Text(stringResource(R.string.pair_change_mode), fontSize = 20.sp) }
    }
}
