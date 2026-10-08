package dev.mobilefoundry.ui.components.forms.button

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import dev.mobilefoundry.ui.theme.FoundryTheme

enum class ButtonVariant { PRIMARY, SECONDARY, QUIET, DESTRUCTIVE }

/** Native button interaction; busy and disabled never dispatch the callback. */
@Composable
fun ActionButton(
    onClick: () -> Unit, modifier: Modifier = Modifier,
    variant: ButtonVariant = ButtonVariant.PRIMARY, isBusy: Boolean = false, enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    val t = FoundryTheme.tokens
    val bounds = modifier.sizeIn(minWidth = t.shape.minimumInteractive, minHeight = t.shape.minimumInteractive)
    val label: @Composable RowScope.() -> Unit = {
        if (isBusy) {
            CircularProgressIndicator(Modifier.size(16.dp).clearAndSetSemantics {},
                color = LocalContentColor.current, strokeWidth = 2.dp)
            Spacer(Modifier.width(t.space.inline))
        }
        content()
    }
    when (variant) {
        ButtonVariant.PRIMARY -> Button(onClick, bounds, enabled && !isBusy, content = label)
        ButtonVariant.SECONDARY -> OutlinedButton(onClick, bounds, enabled && !isBusy, content = label)
        ButtonVariant.QUIET -> TextButton(onClick, bounds, enabled && !isBusy, content = label)
        ButtonVariant.DESTRUCTIVE -> OutlinedButton(onClick, bounds, enabled && !isBusy,
            colors = ButtonDefaults.outlinedButtonColors(contentColor = t.colors.crit.color),
            border = BorderStroke(1.dp, t.colors.crit.color), content = label)
    }
}
