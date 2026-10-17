package dev.mobilefoundry.catalog.ui.session

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.mobilefoundry.catalog.composition.*
import dev.mobilefoundry.kernel.*
import dev.mobilefoundry.session.*
import dev.mobilefoundry.catalog.ui.notes.RemoteNotesCatalogCard
import dev.mobilefoundry.catalog.ui.files.RemoteFilesCatalogCard
import dev.mobilefoundry.catalog.ui.notifications.NotificationInboxCatalogCard
import dev.mobilefoundry.catalog.installations.InstallationCatalogCard
import dev.mobilefoundry.ui.components.display.card.Card
import dev.mobilefoundry.ui.components.forms.button.ActionButton
import dev.mobilefoundry.ui.components.forms.button.ButtonVariant
import dev.mobilefoundry.ui.components.forms.password.PasswordField
import dev.mobilefoundry.ui.components.forms.select.SelectField
import dev.mobilefoundry.ui.components.forms.textfield.LabeledTextField
import dev.mobilefoundry.ui.components.layout.stack.VerticalStack
import dev.mobilefoundry.ui.components.layout.surface.SurfaceRole
import dev.mobilefoundry.ui.components.patterns.sectionheader.SectionHeader
import dev.mobilefoundry.ui.theme.FoundryTheme

@Composable fun GoSessionCatalogScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    val apiBase = rememberSaveable(saver = TextFieldState.Saver) { TextFieldState("http://127.0.0.1:8080/v1") }
    var provider by rememberSaveable { mutableStateOf(SessionProvider.DEVICE) }
    var localHTTP by rememberSaveable { mutableStateOf(true) }
    var model by remember { mutableStateOf<GoSessionCatalogModel?>(null) }
    var configurationFailure by remember { mutableStateOf<Failure?>(null) }
    val current = model
    val busy = current?.controller?.state?.collectAsStateWithLifecycle()?.value?.phase?.isBusy == true
    DisposableEffect(current) { onDispose { current?.close() } }
    Column(modifier.fillMaxSize()) {
        TextButton(onClick = onBack) { Text("Back") }
        Text("Go identity and accounts", style = FoundryTheme.tokens.typography.title)
        VerticalStack(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).imePadding().padding(20.dp), spacing = FoundryTheme.tokens.space.section) {
            Card(role = SurfaceRole.FLOATING) {
                SectionHeader("Go backend", "Use an account registered with the Go Identity API. This example sends real HTTP requests.")
                LabeledTextField("API base", apiBase, Modifier.fillMaxWidth(), enabled = !busy,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done))
                SelectField("Credentials", SessionProvider.entries, provider, { provider = it }, enabled = !busy, label = { it.label })
                Row { Text("Allow loopback HTTP"); Switch(localHTTP, { localHTTP = it }, enabled = !busy) }
                ActionButton({
                    when (val answer = GoSessionAuthority.create(apiBase.text.toString(), SessionComposition.goTransport, allowLocalHTTP = localHTTP)) {
                        is Outcome.Err -> configurationFailure = answer.error.publicInfo()
                        is Outcome.Ok -> {
                            current?.close(); configurationFailure = null
                            model = GoSessionCatalogModel(scope, answer.value, SessionComposition.goVault(context, provider, answer.value.id), SessionComposition.goTransport, provider, localHTTP, SessionComposition.installationVault(context,provider,answer.value.id))
                            model!!.controller.restore()
                        }
                    }
                }, enabled = !busy) { Text(if (current == null) "Connect" else "Reconnect") }
                configurationFailure?.let { Text(it.meta.message, style = FoundryTheme.tokens.typography.caption) }
            }
            current?.let { key(it) { GoSessionContent(it) } }
        }
    }
}

@Composable private fun GoSessionContent(model: GoSessionCatalogModel) {
    val controller = model.controller
    // Observe here as well as in child cards: leases drive this owner's effects/layout.
    val state = controller.state.collectAsStateWithLifecycle().value
    val directory = model.directory.collectAsStateWithLifecycle().value
    val login = remember { TextFieldState() }
    val password = remember { TextFieldState() }
    val focus = LocalFocusManager.current; val keyboard = LocalSoftwareKeyboardController.current
    val lease = controller.lease
    LaunchedEffect(lease) { model.refreshScope() }
    DisposableEffect(password) { onDispose { password.edit { replace(0, length, "") } } }
    fun signIn() {
        if (controller.signIn(login.text.toString(), password.text.toString()) != null) {
            password.edit { replace(0, length, "") }; focus.clearFocus(); keyboard?.hide()
        }
    }
    Card {
        SectionHeader("Session", "State: ${state.phase.name.lowercase()}")
        Text(state.principal?.displayName ?: "No active identity")
        state.account?.let { Text("Selected: ${it.name}"); Text(it.id, style = FoundryTheme.tokens.typography.caption) }
        Text("Credentials: ${model.provider.label}", style = FoundryTheme.tokens.typography.caption)
        state.failure?.let { Text(it.meta.message, style = FoundryTheme.tokens.typography.caption) }
    }
    Card {
        SectionHeader("Sign in", "The first sign-in bootstraps a personal account. Passwords stay in this draft only.")
        LabeledTextField("Login", login, Modifier.fillMaxWidth(), enabled = !state.phase.isBusy,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next))
        PasswordField("Password", password, Modifier.fillMaxWidth(), enabled = !state.phase.isBusy,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done), onKeyboardAction = { signIn() })
        ActionButton(::signIn, isBusy = state.phase == SessionPhase.SIGNING_IN, enabled = !state.phase.isBusy) { Text("Sign in") }
    }
    Card {
        SectionHeader("Session controls", "Renewal and reopening preserve your selected account.")
        ActionButton({ controller.restore() }, variant = ButtonVariant.SECONDARY, enabled = !state.phase.isBusy) { Text("Restore saved session") }
        ActionButton({ controller.renew() }, variant = ButtonVariant.SECONDARY, enabled = controller.canRenew) { Text("Renew access") }
        ActionButton({ password.edit { replace(0, length, "") }; controller.signOut() }, variant = ButtonVariant.DESTRUCTIVE,
            enabled = state.phase != SessionPhase.SIGNING_OUT) { Text("Sign out") }
    }
    if (lease != null) {
        Card {
            SectionHeader("Owned accounts", "Selection stays on this device. Other devices can use another account.")
            ActionButton({ model.load() }, variant = ButtonVariant.SECONDARY, isBusy = directory.isLoading, enabled = !directory.isLoading) { Text("Refresh accounts") }
            directory.accounts.forEach { account ->
                key(account.id) { ActionButton({ controller.selectAccount(account.id) }, variant = ButtonVariant.QUIET,
                    enabled = controller.canSelectAccount && account.id != state.account?.id) { Text("Use ${account.name}") } }
            }
            directory.nextCursor?.let { cursor -> ActionButton({ model.load(cursor) }, variant = ButtonVariant.QUIET, enabled = !directory.isLoading) { Text("Next accounts") } }
            directory.failure?.let { Text(it.meta.message, style = FoundryTheme.tokens.typography.caption) }
            Text("Remote notes below use the selected account. Offline synchronization is a separate capability.", style = FoundryTheme.tokens.typography.caption)
        }
        key(lease.scope) { RemoteNotesCatalogCard(model.notes); RemoteFilesCatalogCard(model.files) }
        key(lease) { NotificationInboxCatalogCard(model.inbox); InstallationCatalogCard(model.installations) }
    }
}
