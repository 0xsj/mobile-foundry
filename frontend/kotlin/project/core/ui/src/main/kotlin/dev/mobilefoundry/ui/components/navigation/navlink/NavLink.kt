package dev.mobilefoundry.ui.components.navigation.navlink

import androidx.compose.foundation.clickable
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.LayoutDirection
import dev.mobilefoundry.ui.components.display.listrow.ListRow

/** Full-row navigation affordance. The caller owns the route, back stack and destination. */
@Composable
fun NavLink(title: String, onNavigate: () -> Unit, modifier: Modifier = Modifier,
    subtitle: String? = null, enabled: Boolean = true, leading: @Composable () -> Unit = {}) {
    ListRow(title, subtitle, modifier.clickable(enabled = enabled, role = Role.Button, onClick = onNavigate),
        leading = leading, trailing = {
            Text(if (LocalLayoutDirection.current == LayoutDirection.Rtl) "‹" else "›",
                modifier = Modifier.clearAndSetSemantics {})
        })
}
