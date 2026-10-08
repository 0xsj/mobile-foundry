package dev.mobilefoundry.ui.components.forms.submitbutton

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import dev.mobilefoundry.ui.theme.FoundryTheme
import dev.mobilefoundry.ui.components.forms.button.ActionButton

/** One explicit action; state changes never dispatch the callback. */
@Composable
fun SubmitButton(
    title: String,
    submittingTitle: String,
    isSubmitting: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    ActionButton(onClick = onClick, modifier = modifier, isBusy = isSubmitting, enabled = enabled) {
        Text(if (isSubmitting) submittingTitle else title)
    }
}
