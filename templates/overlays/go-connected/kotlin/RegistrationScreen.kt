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
import dev.mobilefoundry.kernel.Failure
import dev.mobilefoundry.product.connection.RegistrationModel
import dev.mobilefoundry.session.SessionController

@Composable fun RegistrationScreen(model: RegistrationModel, session: SessionController, onSignIn: (String) -> Unit) {
    val state by model.state.collectAsStateWithLifecycle()
    val current by session.state.collectAsStateWithLifecycle()
    var login by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    val focus = LocalFocusManager.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(current.epoch) { password = ""; focus.clearFocus(); model.cancel() }
    DisposableEffect(model, lifecycle) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_STOP) { password = ""; focus.clearFocus(); model.cancel() } }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer); password = ""; model.cancel() }
    }
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Create account", style = MaterialTheme.typography.headlineSmall)
        if (state.busy) CircularProgressIndicator()
        state.message?.let { Text(it, Modifier.testTag("registration.message")) }
        state.failure?.let { failure ->
            Text(failure.meta.message, Modifier.testTag("registration.failure"), color = MaterialTheme.colorScheme.error)
            (failure as? Failure.Invalid)?.fields?.toSortedMap()?.forEach { (field, message) ->
                Text(message, Modifier.testTag("registration.error.$field"), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
        }
        if (state.registered == null) {
            val enabled = model.canSubmit
            OutlinedTextField(login, { login = it }, label = { Text("Login") }, singleLine = true, enabled = enabled,
                modifier = Modifier.fillMaxWidth().testTag("registration.login"))
            OutlinedTextField(name, { name = it }, label = { Text("Display name") }, singleLine = true, enabled = enabled,
                modifier = Modifier.fillMaxWidth().testTag("registration.name"))
            OutlinedTextField(password, { password = it }, label = { Text("New password") }, singleLine = true, enabled = enabled,
                visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth().testTag("registration.password"))
            Text("Use at least 12 password characters. Your login and recovery email are separate; add a verified recovery address after signing in.", style = MaterialTheme.typography.bodySmall)
            Button(onClick = { focus.clearFocus(); val secret = password; password = ""; model.register(login, name, secret) }, enabled = model.canSubmit,
                modifier = Modifier.testTag("registration.submit")) { Text("Create account") }
        }
        TextButton(onClick = { password = ""; focus.clearFocus(); onSignIn(state.loginHint) }, modifier = Modifier.testTag("registration.continue")) {
            Text(if (state.registered == null) "Back to sign in" else "Continue to sign in")
        }
    }
}
