package dev.mobilefoundry.catalog.ui.forms

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import dev.mobilefoundry.kernel.FailureKind
import dev.mobilefoundry.query.*
import dev.mobilefoundry.ui.components.feedback.mutation.MutationFeedback
import dev.mobilefoundry.ui.components.forms.submitbutton.FoundrySubmitButton
import dev.mobilefoundry.ui.components.forms.textfield.FoundryTextField
import dev.mobilefoundry.ui.components.layout.surface.FoundrySurface
import dev.mobilefoundry.ui.components.layout.surface.FoundrySurfaceRole
import dev.mobilefoundry.ui.theme.FoundryTheme

@Composable
fun CreateNoteScreen(state: CreateNoteFormState, editTitle: (String) -> Unit, blurTitle: () -> Unit,
                     submit: () -> Boolean, reset: () -> Unit) {
    val tokens = FoundryTheme.tokens
    val focus = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    var wasFocused by remember { mutableStateOf(false) }
    val submitAction = {
        if (state.canEdit) {
            if (submit()) focusManager.clearFocus() else focus.requestFocus()
        }
    }
    FoundrySurface(Modifier.testTag("create-note-panel"), role = FoundrySurfaceRole.FLOATING) {
        Column(Modifier.fillMaxWidth().padding(tokens.space.page), verticalArrangement = Arrangement.spacedBy(tokens.space.stack)) {
            Text("Create note", style = tokens.typography.heading)
            FoundryTextField("Title", value = state.title, onValueChange = editTitle,
                modifier = Modifier.fillMaxWidth().focusRequester(focus).onFocusChanged {
                    if (wasFocused && !it.isFocused) blurTitle()
                    wasFocused = it.isFocused
                }, help = "Required · Up to 200 characters.", error = state.titleError, enabled = state.canEdit,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { submitAction() }))
            FoundrySubmitButton("Create note", "Creating note…", state.mutation.isSubmitting,
                onClick = submitAction, enabled = state.canEdit)
            MutationFeedback(state.mutation, submitting = "Waiting for confirmation…") { note ->
                Column {
                    Text("Note created", style = tokens.typography.label)
                    Text(note.title)
                }
            }
            if (state.mutation.failure?.kind in setOf(FailureKind.TIMEOUT, FailureKind.CANCELED, FailureKind.UNAVAILABLE, FailureKind.INTERNAL)) {
                Text("We couldn’t confirm whether the note was saved. Check your notes before submitting again.",
                    style = tokens.typography.caption, color = tokens.colors.inkSecondary.color)
            }
            OutlinedButton(onClick = { reset(); focusManager.clearFocus() }, enabled = !state.mutation.isSubmitting) {
                Text(if (state.mutation.value == null) "Clear form" else "New note")
            }
        }
    }
}
