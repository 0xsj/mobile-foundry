package dev.mobilefoundry.ui.components.forms.submitbutton

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import dev.mobilefoundry.ui.theme.FoundryTheme

/** One explicit action; state changes never dispatch the callback. */
@Composable
fun FoundrySubmitButton(
    title: String,
    submittingTitle: String,
    isSubmitting: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val tokens = FoundryTheme.tokens
    Button(onClick = onClick, modifier = modifier.heightIn(min = tokens.shape.minimumInteractive),
        enabled = enabled && !isSubmitting) {
        Row(horizontalArrangement = Arrangement.spacedBy(tokens.space.inline)) {
            if (isSubmitting) CircularProgressIndicator(Modifier.size(tokens.space.steps[4]).clearAndSetSemantics {},
                color = MaterialTheme.colorScheme.onPrimary, strokeWidth = tokens.space.steps[0])
            Text(if (isSubmitting) submittingTitle else title)
        }
    }
}
