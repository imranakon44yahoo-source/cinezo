package com.pashe.app.ui.child

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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.messaging.FirebaseMessaging
import com.pashe.app.Graph
import com.pashe.app.R
import com.pashe.app.auth.AuthManager.PhoneEvent
import com.pashe.app.ui.components.findActivity
import com.pashe.app.ui.theme.PasheColors
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class SignInViewModel : ViewModel() {
    var phone by mutableStateOf("+880")
    var code by mutableStateOf("")
    var verificationId by mutableStateOf<String?>(null)
    var loading by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    var signedIn by mutableStateOf(false)

    fun sendCode(activity: android.app.Activity) {
        loading = true
        error = null
        Graph.auth.startPhoneVerification(activity, phone.filterNot { it.isWhitespace() }) { event ->
            when (event) {
                is PhoneEvent.CodeSent -> { verificationId = event.verificationId; loading = false }
                is PhoneEvent.AutoVerified -> signIn { Graph.auth.signInWithPhoneCredential(event.credential) }
                is PhoneEvent.Failed -> { error = event.message; loading = false }
            }
        }
    }

    fun verify() {
        val id = verificationId ?: return
        signIn { Graph.auth.signInWithPhoneCode(id, code.trim()) }
    }

    fun google(activity: android.app.Activity) = signIn { Graph.auth.signInWithGoogle(activity) }

    private fun signIn(block: suspend () -> FirebaseUser) {
        loading = true
        error = null
        viewModelScope.launch {
            try {
                val user = block()
                val language = if (java.util.Locale.getDefault().language == "en") "en" else "bn"
                Graph.childRepo.ensureUserProfile(user.displayName, user.phoneNumber, language)
                runCatching { Graph.childRepo.registerFcmToken(FirebaseMessaging.getInstance().token.await()) }
                signedIn = true
            } catch (e: Exception) {
                if (e !is androidx.credentials.exceptions.GetCredentialCancellationException) {
                    error = e.localizedMessage ?: e.javaClass.simpleName
                }
            } finally {
                loading = false
            }
        }
    }
}

@Composable
fun ChildSignInScreen(onSignedIn: () -> Unit, onBack: () -> Unit, vm: SignInViewModel = viewModel()) {
    val activity = LocalContext.current.findActivity()
    if (vm.signedIn) {
        androidx.compose.runtime.LaunchedEffect(Unit) { onSignedIn() }
    }
    Column(
        Modifier.fillMaxSize().background(PasheColors.Background).systemBarsPadding().imePadding()
            .verticalScroll(rememberScrollState()).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Spacer(Modifier.height(24.dp))
        Text(stringResource(R.string.app_name), fontSize = 40.sp, fontWeight = FontWeight.Bold, color = PasheColors.TealDark)
        Text(stringResource(R.string.signin_title), style = MaterialTheme.typography.headlineSmall)
        Text(stringResource(R.string.signin_subtitle), color = PasheColors.InkMuted)
        Spacer(Modifier.height(8.dp))

        if (vm.verificationId == null) {
            OutlinedTextField(
                value = vm.phone, onValueChange = { vm.phone = it },
                label = { Text(stringResource(R.string.phone_label)) },
                placeholder = { Text(stringResource(R.string.phone_hint)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                singleLine = true, modifier = Modifier.fillMaxWidth(),
            )
            Button(
                onClick = { vm.sendCode(activity) },
                enabled = !vm.loading && vm.phone.count { it.isDigit() } >= 8,
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
            ) { Text(stringResource(R.string.send_code)) }
        } else {
            OutlinedTextField(
                value = vm.code, onValueChange = { vm.code = it.filter(Char::isDigit).take(6) },
                label = { Text(stringResource(R.string.otp_label)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                singleLine = true, modifier = Modifier.fillMaxWidth(),
            )
            Button(
                onClick = vm::verify, enabled = !vm.loading && vm.code.length == 6,
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
            ) { Text(stringResource(R.string.verify)) }
            TextButton(onClick = { vm.verificationId = null; vm.code = "" }) { Text(stringResource(R.string.change_number)) }
        }

        Text(stringResource(R.string.or), color = PasheColors.InkMuted)
        OutlinedButton(
            onClick = { vm.google(activity) }, enabled = !vm.loading,
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
        ) { Text(stringResource(R.string.google_signin)) }

        if (vm.loading) CircularProgressIndicator()
        vm.error?.let { Text(stringResource(R.string.signin_error, it), color = PasheColors.Missed) }
        TextButton(onClick = onBack) { Text(stringResource(R.string.back)) }
    }
}
