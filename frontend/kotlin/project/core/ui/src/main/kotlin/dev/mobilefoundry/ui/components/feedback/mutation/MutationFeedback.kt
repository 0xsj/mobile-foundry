package dev.mobilefoundry.ui.components.feedback.mutation

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import dev.mobilefoundry.kernel.publicInfo
import dev.mobilefoundry.query.MutationState
import dev.mobilefoundry.ui.theme.FoundryTheme

/** Presentation only. Retry safety belongs to the feature that owns the write. */
@Composable
fun <Value : Any> MutationFeedback(
    state: MutationState<Value>,
    submitting: String,
    modifier: Modifier = Modifier,
    success: @Composable (Value) -> Unit,
) {
    Column(modifier.semantics { liveRegion = LiveRegionMode.Polite }) {
        when (state) {
            MutationState.Idle -> Unit
            MutationState.Submitting -> Text(submitting, color = FoundryTheme.tokens.colors.inkSecondary.color)
            is MutationState.Succeeded -> success(state.value)
            is MutationState.Failed -> Text("Error: " + state.failure.publicInfo().meta.message,
                color = FoundryTheme.tokens.colors.crit.color)
        }
    }
}
