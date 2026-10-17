package dev.mobilefoundry.product.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.mobilefoundry.product.connection.ConnectedSessionModel
import dev.mobilefoundry.ui.components.forms.toggle.ToggleField

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun AccountScreen(glass: Boolean, onGlassChange: (Boolean) -> Unit, model: ConnectedSessionModel) {
    val session by model.controller.state.collectAsStateWithLifecycle()
    val directory by model.directory.collectAsStateWithLifecycle()
    var login by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var registering by remember { mutableStateOf(false) }
    var security by remember { mutableStateOf(false) }
    var managing by remember { mutableStateOf(false) }
    val workspace by model.workspaces.state.collectAsStateWithLifecycle()
    val focus = LocalFocusManager.current
    LaunchedEffect(session.epoch) { password = ""; if (model.controller.lease == null) managing = false }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Your preferences", style = MaterialTheme.typography.headlineSmall)
        ToggleField("Glass", glass, onGlassChange, help = "Use translucent surfaces.")
        if (model.identity != null) TextButton(onClick = { password = ""; security = true }, Modifier.testTag("identity.open")) { Text("Account security / recover access") }
        if (model.controller.isBusy) CircularProgressIndicator()
        session.failure?.let { Text(it.meta.message, color = MaterialTheme.colorScheme.error) }
        if (session.account != null) {
            Text("Selected: ${workspace.displayAccount?.name ?: directory.accounts.firstOrNull { it.id == session.account!!.id }?.name ?: session.account!!.name}", Modifier.testTag("connection.account"))
            Text(session.principal?.displayName ?: "")
        } else {
            Text("Sign in to your workspace")
            OutlinedTextField(login, { login = it }, label = { Text("Login") }, singleLine = true, modifier = Modifier.fillMaxWidth().testTag("connection.login"))
            OutlinedTextField(password, { password = it }, label = { Text("Password") }, singleLine = true,
                visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth().testTag("connection.password"))
            Button(onClick = { focus.clearFocus(); val secret = password; password = ""; model.signIn(login, secret) }, enabled = !session.phase.isBusy,
                modifier = Modifier.testTag("connection.sign-in")) { Text("Sign in") }
            model.registration?.let { registration ->
                val status by registration.state.collectAsStateWithLifecycle()
                TextButton(onClick = { password = ""; registration.open(); registering = true }, enabled = !status.busy && session.phase == dev.mobilefoundry.session.SessionPhase.SIGNED_OUT,
                    modifier = Modifier.testTag("registration.open")) { Text("Create account") }
                if (status.uncertain) Text(dev.mobilefoundry.product.connection.RegistrationModel.interrupted, Modifier.testTag("registration.uncertain"))
            }
            TextButton(onClick = { password = ""; model.restore() }, enabled = !session.phase.isBusy) { Text("Restore saved session") }
        }
        if (model.controller.canRenew) TextButton(onClick = { model.renew() }, Modifier.testTag("connection.renew")) { Text("Renew access") }
        TextButton(onClick = { password = ""; login = ""; model.signOut() }, enabled = !session.phase.isBusy,
            modifier = Modifier.testTag("connection.sign-out")) { Text("Sign out") }
        if (model.controller.lease != null) {
            HorizontalDivider(); Text("Workspaces", style = MaterialTheme.typography.titleMedium)
            TextButton(onClick = { managing = true }, Modifier.testTag("workspace.open")) { Text("Manage workspaces") }
            if (directory.loading) CircularProgressIndicator()
            directory.failure?.let { Text(it.meta.message, color = MaterialTheme.colorScheme.error) }
            directory.accounts.forEach { account ->
                TextButton(onClick = { model.select(account.id) }, enabled = model.controller.canSelectAccount && session.account?.id != account.id) { Text("Use ${account.name}") }
            }
            TextButton(onClick = { model.loadAccounts() }, enabled = !directory.loading) { Text("Refresh workspaces") }
            directory.nextCursor?.let { cursor -> TextButton(onClick = { model.loadAccounts(cursor) }, enabled = !directory.loading) { Text("More workspaces") } }
        }
    }
    if (managing) {
        ModalBottomSheet(onDismissRequest = { managing = false; model.loadAccounts() }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
            TextButton(onClick = { managing = false; model.loadAccounts() }, modifier = Modifier.testTag("workspace.close")) { Text("Close workspaces") }
            WorkspaceScreen(model.workspaces, model.controller)
        }
    }
    if (registering) model.registration?.let { registration ->
        ModalBottomSheet(onDismissRequest = { registering = false }) {
            TextButton(onClick = { registering = false }, modifier = Modifier.testTag("registration.close")) { Text("Close registration") }
            RegistrationScreen(registration, model.controller) { hint ->
                if (hint.isNotEmpty()) login = hint
                password = ""; registering = false
            }
        }
    }
    if (security) model.identity?.let { identity ->
        ModalBottomSheet(onDismissRequest = { security = false }) {
            TextButton(onClick = { security = false }, modifier = Modifier.testTag("identity.close")) { Text("Close account security") }
            IdentitySettingsScreen(identity, model.controller)
        }
    }
}
