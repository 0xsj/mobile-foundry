package dev.mobilefoundry.catalog.ui.session

import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import dev.mobilefoundry.kernel.Failure
import dev.mobilefoundry.session.*
import dev.mobilefoundry.data.AccountNotesRepositories
import dev.mobilefoundry.data.AccountNotesStorage
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
import kotlinx.coroutines.launch

@Composable
fun SessionCatalogScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    var provider by rememberSaveable { mutableStateOf(SessionProvider.DEVICE) }
    val context = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    val clock = remember(provider) { ManualSessionClock(System.currentTimeMillis()) }
    val authority = remember(provider) { DemoSessionAuthority(clock::now) }
    val controller = remember(provider) { SessionController(scope, authority, SessionComposition.vault(context, provider), clock::now) {
        Log.e("Session", "Unexpected session error", it)
    } }
    val repositories = remember(provider) { AccountNotesRepositories(context, if (provider == SessionProvider.DEVICE) AccountNotesStorage.DEVICE else AccountNotesStorage.MEMORY) }
    val state by controller.state.collectAsStateWithLifecycle()
    LaunchedEffect(controller) { controller.restore()?.join() }
    DisposableEffect(controller) { onDispose { controller.shutdown() } }
    Column(modifier.fillMaxSize()) {
        TextButton(onClick = onBack) { Text("Back") }
        Text("Identity and session", style = FoundryTheme.tokens.typography.title)
        key(controller) {
            val login = remember { TextFieldState("alex@example.test") }
            // A password is an ephemeral draft, never rememberSaveable or persisted.
            val password = remember { TextFieldState() }
            val focus = LocalFocusManager.current; val keyboard = LocalSoftwareKeyboardController.current
            val t = FoundryTheme.tokens
            val fields = (state.failure as? Failure.Invalid)?.fields.orEmpty()
            fun blur() { focus.clearFocus(); keyboard?.hide() }
            fun signIn() { if (controller.signIn(login.text.toString(), password.text.toString()) != null) { password.edit { replace(0, length, "") }; blur() } }
            DisposableEffect(password) { onDispose { password.edit { replace(0, length, "") } } }
            VerticalStack(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).imePadding().padding(20.dp), spacing = t.space.section) {
                Card(role = SurfaceRole.FLOATING) {
                    SectionHeader("Demo authority", "Local lifecycle example. These accounts do not authorize real data.")
                    SelectField("Credentials", SessionProvider.entries, provider, { if (!controller.isBusy) { blur(); provider = it } }, enabled = !state.phase.isBusy, label = { it.label })
                    Text(if (provider == SessionProvider.DEVICE) "A refresh credential is encrypted using Android Keystore and saved outside backups. Reopen this gallery to restore it."
                        else "Credentials live in memory until this source or screen closes.", style = t.typography.caption)
                }
                Card {
                    SectionHeader("Session", "State: ${state.phase.name.lowercase()}")
                    Text(state.principal?.displayName ?: "No active identity")
                    state.account?.let { Text(it.name) }
                    Text(if (controller.lease == null) "Account access is closed." else "Account access is active.", style = t.typography.caption)
                    state.failure?.let { Text(it.meta.message, style = t.typography.caption) }
                }
                Card {
                    SectionHeader("Sign in", "Use alex@example.test or sam@example.test with password catalog-demo.")
                    LabeledTextField("Login", login, Modifier.fillMaxWidth(), error = fields["login"], enabled = !state.phase.isBusy,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next))
                    PasswordField("Password", password, Modifier.fillMaxWidth(), error = fields["password"], enabled = !state.phase.isBusy,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done), onKeyboardAction = { signIn() })
                    ActionButton(::signIn, isBusy = state.phase == SessionPhase.SIGNING_IN, enabled = !state.phase.isBusy) { Text("Sign in") }
                }
                Card {
                    SectionHeader("Lifecycle controls", "The demo clock moves only when you advance it. Access lasts 60 seconds.")
                    ActionButton({ controller.restore() }, variant = ButtonVariant.SECONDARY, enabled = !state.phase.isBusy) { Text("Restore saved session") }
                    ActionButton({ controller.renew() }, variant = ButtonVariant.SECONDARY, enabled = controller.canRenew) { Text("Renew access") }
                    ActionButton({ clock.advance(61_000); controller.expireIfNeeded() }, variant = ButtonVariant.QUIET, enabled = !state.phase.isBusy) { Text("Advance time 61 seconds") }
                    ActionButton({ scope.launch { authority.rejectRenewals(); controller.renew()?.join() } }, variant = ButtonVariant.QUIET,
                        enabled = !state.phase.isBusy && state.phase == SessionPhase.SIGNED_IN) { Text("Reject next renewal") }
                    ActionButton({ password.edit { replace(0, length, "") }; blur(); controller.signOut() }, variant = ButtonVariant.DESTRUCTIVE,
                        enabled = state.phase != SessionPhase.SIGNING_OUT) { Text("Sign out") }
                    Text("Sign-out closes account access and clears the credential. Saved account notes remain on this device; guest Local notes stay separate.", style = t.typography.caption)
                }
                controller.lease?.let { lease -> key(lease) { AccountAccessPanel(controller, lease, repositories) } }
            }
        }
    }
}
