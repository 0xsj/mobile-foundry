package dev.mobilefoundry.product.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.mobilefoundry.product.connection.IdentitySettingsModel
import dev.mobilefoundry.session.SessionController

@Composable
fun IdentitySettingsScreen(model: IdentitySettingsModel, controller: SessionController) {
    val state by model.state.collectAsStateWithLifecycle()
    val session by controller.state.collectAsStateWithLifecycle()
    var email by remember { mutableStateOf("") }
    var token by remember { mutableStateOf("") }
    var verificationPassword by remember { mutableStateOf("") }
    var currentPassword by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    val focus = LocalFocusManager.current
    fun clear() { verificationPassword = ""; token = ""; currentPassword = ""; newPassword = "" }
    fun submit(action: () -> Unit) { focus.clearFocus(); action(); clear() }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle, model) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_STOP) clear() }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer); clear(); model.cancel() }
    }
    LaunchedEffect(session.epoch) { clear() }
    val enabled = !state.busy && !session.phase.isBusy
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Account security", style = MaterialTheme.typography.headlineSmall)
        if (state.busy) CircularProgressIndicator()
        state.message?.let { Text(it, Modifier.testTag("identity.message")) }
        state.failure?.let { Text(it.meta.message, Modifier.testTag("identity.failure"), color = MaterialTheme.colorScheme.error) }
        if (controller.lease != null) {
            Text("Recovery email", style = MaterialTheme.typography.titleMedium)
            Text(state.contact?.email ?: "No verified address loaded.")
            TextButton(onClick = { model.loadContact() }, enabled = enabled) { Text("Refresh verified address") }
        }
        OutlinedTextField(email, { email = it }, label = { Text("Email address") }, singleLine = true, enabled = enabled,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email), modifier = Modifier.fillMaxWidth().testTag("identity.email"))
        if (controller.lease != null) {
            SecretField("Current password", verificationPassword, { verificationPassword = it }, enabled, "identity.current-password")
            Button(onClick = { submit { model.requestVerification(email, verificationPassword) } }, enabled = enabled, modifier = Modifier.testTag("identity.request-verification")) { Text("Send verification code") }
            SecretField("Verification code", token, { token = it }, enabled, "identity.code")
            Button(onClick = { submit { model.verify(token) } }, enabled = enabled, modifier = Modifier.testTag("identity.verify")) { Text("Verify email") }
            Text("The first verified address is your recovery contact. Contact changes are a separate workflow.")
            HorizontalDivider(); Text("Change password", style = MaterialTheme.typography.titleMedium)
            SecretField("Current password", currentPassword, { currentPassword = it }, enabled, "identity.change-current-password")
            SecretField("New password", newPassword, { newPassword = it }, enabled, "identity.new-password")
            Button(onClick = { submit { model.change(currentPassword, newPassword) } }, enabled = enabled, modifier = Modifier.testTag("identity.change")) { Text("Change password and sign out") }
            Text("Use at least 12 characters. All sessions are revoked. If interrupted, sign in again or use recovery.")
        } else {
            Text("Recover access", style = MaterialTheme.typography.titleMedium)
            Button(onClick = { submit { model.requestReset(email) } }, enabled = enabled, modifier = Modifier.testTag("identity.request-reset")) { Text("Request reset code") }
            SecretField("Reset code", token, { token = it }, enabled, "identity.code")
            SecretField("New password", newPassword, { newPassword = it }, enabled, "identity.new-password")
            Button(onClick = { submit { model.reset(token, newPassword) } }, enabled = enabled, modifier = Modifier.testTag("identity.reset")) { Text("Reset password") }
            Text("Use the email code and at least 12 password characters. Requesting a code does not confirm an account exists.")
        }
    }
}

@Composable
private fun SecretField(label: String, value: String, onChange: (String) -> Unit, enabled: Boolean, tag: String) {
    OutlinedTextField(value, onChange, label = { Text(label) }, singleLine = true, enabled = enabled,
        visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        modifier = Modifier.fillMaxWidth().testTag(tag))
}
