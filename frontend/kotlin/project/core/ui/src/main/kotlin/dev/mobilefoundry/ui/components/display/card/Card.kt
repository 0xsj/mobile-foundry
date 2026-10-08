package dev.mobilefoundry.ui.components.display.card

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.mobilefoundry.ui.components.layout.surface.Surface
import dev.mobilefoundry.ui.components.layout.surface.SurfaceRole
import dev.mobilefoundry.ui.theme.FoundryTheme

/** No implicit interaction. Native modifiers and slots customize composition. */
@Composable
fun Card(modifier: Modifier = Modifier, role: SurfaceRole = SurfaceRole.CONTENT,
         contentPadding: PaddingValues? = null, content: @Composable ColumnScope.() -> Unit) {
    val t = FoundryTheme.tokens
    Surface(modifier, role) {
        Column(Modifier.fillMaxWidth().padding(contentPadding ?: PaddingValues(t.space.page)),
            verticalArrangement = Arrangement.spacedBy(t.space.stack), content = content)
    }
}
