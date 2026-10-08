package dev.mobilefoundry.catalog.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.clearAndSetSemantics
import dev.mobilefoundry.ui.components.display.badge.Badge
import dev.mobilefoundry.ui.components.display.card.Card
import dev.mobilefoundry.ui.components.display.keyvalue.KeyValueRow
import dev.mobilefoundry.ui.components.feedback.alert.MessageTone
import dev.mobilefoundry.ui.components.feedback.validationchecklist.ValidationChecklist
import dev.mobilefoundry.ui.components.feedback.validationchecklist.ValidationItem
import dev.mobilefoundry.ui.components.forms.button.ActionButton
import dev.mobilefoundry.ui.components.forms.button.ButtonVariant
import dev.mobilefoundry.ui.components.forms.multiline.MultilineField
import dev.mobilefoundry.ui.components.forms.password.PasswordField
import dev.mobilefoundry.ui.components.forms.toggle.ToggleField
import dev.mobilefoundry.ui.components.layout.aspectratio.MediaFrame
import dev.mobilefoundry.ui.components.navigation.navlink.NavLink
import dev.mobilefoundry.ui.components.navigation.stepindicator.StepIndicator
import dev.mobilefoundry.ui.components.navigation.stepindicator.StepItem
import dev.mobilefoundry.ui.components.navigation.stepindicator.StepStatus
import dev.mobilefoundry.ui.components.patterns.actionbar.ActionBar
import dev.mobilefoundry.ui.components.patterns.onboardingpage.OnboardingPage
import dev.mobilefoundry.ui.components.patterns.pageheader.PageHeader
import dev.mobilefoundry.ui.components.shells.authshell.AuthShell
import dev.mobilefoundry.ui.theme.FoundryTheme

internal data class JourneyValues(val password: TextFieldState, val bio: TextFieldState, val step: Int = 0,
    val enabled: Boolean = true, val updates: Boolean = true, val finished: Boolean = false,
    val accountPreviews: Int = 0, val finishedPreviews: Int = 0) {
    val hasNote get() = bio.text.isNotBlank()
    val checklist get() = listOf(
        ValidationItem("password", "Password entered", password.text.isNotEmpty(), if (password.text.isEmpty()) "Needed" else "Satisfied"),
        ValidationItem("note", "Profile note added", hasNote, if (hasNote) "Satisfied" else "Needed"))
    val steps get() = listOf("Profile", "Preferences", "Review").mapIndexed { index, title ->
        val status = if (finished || index < step) StepStatus.COMPLETED else if (index == step) StepStatus.CURRENT else StepStatus.UPCOMING
        StepItem(title, title, status, when (status) {
            StepStatus.COMPLETED -> "Completed"; StepStatus.CURRENT -> "Current"; StepStatus.UPCOMING -> "Upcoming"
        })
    }
}

@Composable
internal fun JourneyExamples(values: JourneyValues, onChange: (JourneyValues) -> Unit, onRoute: (Int) -> Unit) {
    val focus = LocalFocusManager.current
    Card {
        Text("Richer input", style = FoundryTheme.tokens.typography.heading)
        JourneyPassword(values)
        MultilineField("About you", values.bio, Modifier.fillMaxWidth(), help = "A short introduction for your preview.", enabled = values.enabled)
        ValidationChecklist(values.checklist)
        ToggleField("Enable preview inputs", values.enabled, { onChange(values.copy(enabled = it)) })
        ActionButton({ focus.clearFocus() }, variant = ButtonVariant.QUIET) { Text("Done editing") }
    }
    Card {
        NavLink("Open account preview", { focus.clearFocus(); onRoute(1) }, subtitle = "A readable account-form layout")
        NavLink("Open onboarding preview", { focus.clearFocus(); onRoute(2) }, subtitle = "Three steps with a shared draft")
        Text("Account previews: ${values.accountPreviews}")
        Text("Finished previews: ${values.finishedPreviews}")
    }
}

@Composable
private fun JourneyPassword(values: JourneyValues) {
    val focus = LocalFocusManager.current
    PasswordField("Preview password", values.password, Modifier.fillMaxWidth(), help = "Use any example text.", enabled = values.enabled,
        onKeyboardAction = { focus.clearFocus() })
    ActionButton({ focus.clearFocus() }, variant = ButtonVariant.QUIET) { Text("Done editing password") }
}

@Composable
internal fun JourneyDestination(values: JourneyValues, onChange: (JourneyValues) -> Unit, account: Boolean, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    Column(Modifier.fillMaxSize()) {
        TextButton(onClick = onBack) { Text("Back to components") }
        if (account) AccountPreviewContent(values, onChange, Modifier.weight(1f))
        else OnboardingPreviewContent(values, onChange, Modifier.weight(1f))
    }
}

@Composable
private fun AccountPreviewContent(values: JourneyValues, onChange: (JourneyValues) -> Unit, modifier: Modifier) {
    AuthShell(modifier, header = { PageHeader("Welcome back", "Explore an account form layout.") }, content = {
        Card {
            KeyValueRow("Account", "atlas@example.com")
            JourneyPassword(values)
            ValidationChecklist(values.checklist)
        }
    }, footer = {
        Column {
            ActionButton({ onChange(values.copy(accountPreviews = values.accountPreviews + 1)) }, Modifier.fillMaxWidth(),
                enabled = values.enabled && values.password.text.isNotEmpty()) { Text("Continue account preview") }
            Text("Account previews: ${values.accountPreviews}")
        }
    })
}

@Composable
private fun OnboardingPreviewContent(values: JourneyValues, onChange: (JourneyValues) -> Unit, modifier: Modifier) {
    val t = FoundryTheme.tokens
    val focus = LocalFocusManager.current
    key(values.step) {
        OnboardingPage(listOf("Make it yours", "Choose your updates", "Ready to explore")[values.step],
            "Build a local profile preview.", modifier, artwork = {
                MediaFrame(Modifier.clearAndSetSemantics {}, ratio = 3f) {
                    Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(t.colors.accentTint.color, t.colors.accent.color)),
                        RoundedCornerShape(t.shape.panel)))
                }
            }, content = {
                StepIndicator(if (values.finished) "Preview complete" else "Step ${values.step + 1} of 3", values.steps)
                Card {
                    when (values.step) {
                        0 -> {
                            MultilineField("Profile introduction", values.bio, Modifier.fillMaxWidth(), help = "Add a note to continue.")
                            ActionButton({ focus.clearFocus() }, variant = ButtonVariant.QUIET) { Text("Done editing introduction") }
                        }
                        1 -> ToggleField("Include activity updates", values.updates, { onChange(values.copy(updates = it)) })
                        else -> {
                            KeyValueRow("Introduction", values.bio.text.toString())
                            KeyValueRow("Activity updates", if (values.updates) "Included" else "Off")
                            if (values.finished) Badge("Preview complete", tone = MessageTone.INFO)
                        }
                    }
                }
            }, actions = {
                ActionBar {
                    ActionButton({
                        focus.clearFocus()
                        if (values.step < 2) onChange(values.copy(step = values.step + 1))
                        else onChange(values.copy(finished = true, finishedPreviews = values.finishedPreviews + 1))
                    }, Modifier.fillMaxWidth(), enabled = !values.finished && (values.step != 0 || values.hasNote)) {
                        Text(if (values.step == 2) "Finish preview" else "Next step")
                    }
                    ActionButton({ focus.clearFocus(); onChange(values.copy(step = values.step - 1, finished = false)) },
                        variant = ButtonVariant.SECONDARY, enabled = values.step > 0) { Text("Previous step") }
                    ActionButton({ focus.clearFocus(); onChange(values.copy(step = 0, finished = false)) },
                        variant = ButtonVariant.QUIET) { Text("Restart steps") }
                }
            })
    }
}
