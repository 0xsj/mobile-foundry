package dev.mobilefoundry.catalog.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp
import dev.mobilefoundry.ui.components.display.card.Card
import dev.mobilefoundry.ui.components.feedback.alert.InlineAlert
import dev.mobilefoundry.ui.components.feedback.alert.MessageTone
import dev.mobilefoundry.ui.components.feedback.progress.ProgressIndicator
import dev.mobilefoundry.ui.components.forms.button.ActionButton
import dev.mobilefoundry.ui.components.forms.button.ButtonVariant
import dev.mobilefoundry.ui.components.forms.onetimecode.CodeFormat
import dev.mobilefoundry.ui.components.forms.onetimecode.OneTimeCodeField
import dev.mobilefoundry.ui.components.forms.select.SelectField
import dev.mobilefoundry.ui.components.forms.toggle.ToggleField
import dev.mobilefoundry.ui.components.layout.surface.SurfaceRole
import dev.mobilefoundry.ui.components.navigation.navlink.NavLink
import dev.mobilefoundry.ui.components.patterns.sectionheader.SectionHeader
import dev.mobilefoundry.ui.components.patterns.verificationcard.VerificationCard
import dev.mobilefoundry.ui.theme.FoundryTheme

enum class VerificationChannel(val label: String, val destination: String) { EMAIL("Email", "j••••@example.test"), SMS("SMS", "+1 ••• ••• 0142") }
enum class VerificationResponse(val label: String) { MATCH_CODE("Match demo code"), UNAVAILABLE("Service unavailable") }
enum class VerificationReply { ACCEPTED, INCORRECT, UNAVAILABLE }
enum class VerificationIssue(val label: String) { INCORRECT("That code did not match. Try again."), UNAVAILABLE("The preview service is unavailable. Try again."), EXPIRED("This code has expired. Request a new code.") }
data class VerificationAttempt(val id: Int, val generation: Int, val channel: VerificationChannel, val code: String)
/** Local challenge values and identity-scoped attempts. No delivery, authentication or automatic clock. */
data class VerificationValues(val channel: VerificationChannel = VerificationChannel.EMAIL, val draft: String = "",
    val response: VerificationResponse = VerificationResponse.MATCH_CODE, val generation: Int = 1, val cooldown: Int = 30,
    val expiresIn: Int = 120, val enabled: Boolean = true, val verified: Boolean = false, val request: VerificationAttempt? = null,
    val issue: VerificationIssue? = null, val attempts: Int = 0, val resends: Int = 0) {
    val canEdit get() = enabled && !verified && request == null && expiresIn > 0
    val canSubmit get() = canEdit && format.isComplete(draft)
    val canResend get() = enabled && !verified && request == null && cooldown == 0
    val canChange get() = enabled && request == null
    val canAdvance get() = enabled && !verified && expiresIn > 0
    val error get() = (issue ?: if (expiresIn == 0 && !verified) VerificationIssue.EXPIRED else null)?.label
    fun edit(input: String): VerificationValues {
        if (!canEdit) return this
        val next = format.admit(input) ?: return this
        return if (next == draft) this else copy(draft = next, issue = null)
    }
    fun begin() = if (canSubmit) copy(attempts = attempts + 1, issue = null,
        request = VerificationAttempt(attempts + 1, generation, channel, draft)) else this
    fun finish(attempt: VerificationAttempt, reply: VerificationReply): VerificationValues {
        if (!enabled || verified || expiresIn == 0 || request != attempt || attempt.generation != generation || attempt.channel != channel) return this
        return when (reply) {
            VerificationReply.ACCEPTED -> copy(request = null, verified = true, draft = "", issue = null)
            VerificationReply.INCORRECT -> copy(request = null, issue = VerificationIssue.INCORRECT)
            VerificationReply.UNAVAILABLE -> copy(request = null, issue = VerificationIssue.UNAVAILABLE)
        }
    }
    fun finishPreview(): VerificationValues {
        val attempt = request ?: return this
        return finish(attempt, if (response == VerificationResponse.UNAVAILABLE) VerificationReply.UNAVAILABLE else if (attempt.code == "123456") VerificationReply.ACCEPTED else VerificationReply.INCORRECT)
    }
    fun cancel() = if (enabled && request != null) copy(request = null) else this
    fun advance(seconds: Int = 30): VerificationValues {
        if (!canAdvance || seconds <= 0) return this
        val remaining = expiresIn - minOf(expiresIn, seconds)
        return copy(cooldown = cooldown - minOf(cooldown, seconds), expiresIn = remaining,
            request = if (remaining == 0) null else request, draft = if (remaining == 0) "" else draft,
            issue = if (remaining == 0) VerificationIssue.EXPIRED else issue)
    }
    fun resend() = if (canResend) newChallenge().copy(resends = resends + 1) else this
    fun chooseChannel(value: VerificationChannel) = if (canChange && value != channel) copy(channel = value).newChallenge() else this
    fun chooseResponse(value: VerificationResponse) = if (canChange) copy(response = value) else this
    fun setEnabled(value: Boolean) = copy(enabled = value, request = if (value) request else null)
    fun reset() = if (enabled) copy(channel = VerificationChannel.EMAIL, response = VerificationResponse.MATCH_CODE).newChallenge() else this
    private fun newChallenge() = copy(generation = generation + 1, cooldown = 30, expiresIn = 120, draft = "", issue = null, request = null, verified = false)
    companion object { val format = CodeFormat() }
}
@Composable
fun VerificationExamples(values: VerificationValues, onChange: (VerificationValues) -> Unit, onPreview: () -> Unit) {
    Card {
        SectionHeader("Verification and recovery", "Native code entry with caller-owned challenge state.")
        NavLink("Open verification preview", onPreview, subtitle = "Try code entry, resend and expiry")
    }
    VerificationContent(values, onChange)
}
@Composable
fun VerificationPreviewScreen(values: VerificationValues, onChange: (VerificationValues) -> Unit, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    Column(Modifier.fillMaxSize()) {
        TextButton(onClick = onBack) { Text("Back to components") }
        Text("Verification preview", style = FoundryTheme.tokens.typography.title)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(20.dp)) { VerificationContent(values, onChange) }
    }
}
@Composable
fun VerificationContent(values: VerificationValues, onChange: (VerificationValues) -> Unit) {
    val t = FoundryTheme.tokens
    val focus = LocalFocusManager.current; val keyboard = LocalSoftwareKeyboardController.current
    fun blur() { focus.clearFocus(); keyboard?.hide() }
    fun begin() { if (values.canSubmit) { blur(); onChange(values.begin()) } }
    LaunchedEffect(values.canEdit) { if (!values.canEdit) blur() }
    Column(verticalArrangement = Arrangement.spacedBy(t.space.section)) {
        Card(role = SurfaceRole.FLOATING) {
            ToggleField("Enable verification actions", values.enabled, { onChange(values.setEnabled(it)) })
            SelectField("Delivery channel", VerificationChannel.entries, values.channel, { onChange(values.chooseChannel(it)) }, enabled = values.canChange, label = { it.label })
            SelectField("Preview response", VerificationResponse.entries, values.response, { onChange(values.chooseResponse(it)) }, enabled = values.canChange, label = { it.label })
            Text("Demo code: 123456. Time advances manually. No message is sent and no account is verified.", style = t.typography.caption)
            ActionButton({ blur(); onChange(values.advance()) }, variant = ButtonVariant.SECONDARY, enabled = values.canAdvance) { Text("Advance 30 seconds") }
            ActionButton({ blur(); onChange(values.reset()) }, variant = ButtonVariant.QUIET, enabled = values.enabled) { Text("Reset verification") }
            Text("Checks started: ${values.attempts} · Resend requests: ${values.resends}", style = t.typography.caption)
        }
        VerificationCard("Verify your ${if (values.channel == VerificationChannel.EMAIL) "email" else "phone"}", values.channel.destination,
            "Verification by ${values.channel.label}, ${values.channel.destination}", artwork = {
                // Passive mark; identity is supplied by the parent group.
                Text(if (values.channel == VerificationChannel.EMAIL) "@" else "#", style = t.typography.heading, color = t.colors.accent.color, modifier = Modifier.width(40.dp))
            }, content = {
                if (!values.verified) OneTimeCodeField("Verification code", values.draft, { onChange(values.edit(it)) },
                    help = "Enter or paste six digits.", error = values.error, enabled = values.canEdit, canSubmit = values.canSubmit, onSubmit = ::begin)
            }, status = {
                if (values.verified) InlineAlert("Preview verified", "The local check completed.", tone = MessageTone.INFO)
                else {
                    Text("Code expires in ${values.expiresIn} seconds", style = t.typography.caption)
                    if (values.request != null) ProgressIndicator("Checking preview code")
                }
            }, actions = {
                if (!values.verified) {
                    ActionButton(::begin, isBusy = values.request != null, enabled = values.canSubmit) { Text("Verify code") }
                    if (values.request != null) {
                        ActionButton({ onChange(values.finishPreview()) }, variant = ButtonVariant.SECONDARY, enabled = values.enabled) { Text("Finish local check") }
                        ActionButton({ onChange(values.cancel()) }, variant = ButtonVariant.QUIET, enabled = values.enabled) { Text("Cancel check") }
                    }
                    ActionButton({ blur(); onChange(values.resend()) }, variant = ButtonVariant.QUIET, enabled = values.canResend) {
                        Text(if (values.cooldown == 0) "Resend code" else "Resend in ${values.cooldown} seconds")
                    }
                }
            })
    }
}
