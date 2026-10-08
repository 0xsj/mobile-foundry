package dev.mobilefoundry.ui.styles.tokens

import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.dp

enum class FoundryThemeStyle { SOLID, GLASS }
enum class FoundrySurfaceMaterial { SOLID, GLASS }

/** Material choice is independent of the light/dark color mapping. */
@Immutable
data class FoundryMaterials(
    val style: FoundryThemeStyle = FoundryThemeStyle.SOLID,
    val reduceTransparency: Boolean = false,
) {
    val backdropBlur get() = 16.dp
    val edgeHighlightAlpha get() = .48f
    fun floatingTintAlpha(appearance: FoundryAppearance) = if (appearance == FoundryAppearance.DARK) .86f else .88f
    val content: FoundrySurfaceMaterial get() = FoundrySurfaceMaterial.SOLID
    val floating: FoundrySurfaceMaterial get() =
        if (style == FoundryThemeStyle.GLASS && !reduceTransparency) FoundrySurfaceMaterial.GLASS
        else FoundrySurfaceMaterial.SOLID

    // Compose backdrop effects require Android 12+ and a source supplied by the host.
    fun floatingWithBackdrop(blurSupported: Boolean, hasBackdrop: Boolean): FoundrySurfaceMaterial =
        if (blurSupported && hasBackdrop) floating else FoundrySurfaceMaterial.SOLID
}
