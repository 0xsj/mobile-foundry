package dev.mobilefoundry.ui.styles.tokens

import androidx.compose.material3.Typography
import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight

/** Native system styles retain Compose font scaling. */
@Immutable
object FoundryTypography {
    private val native = Typography()
    val material = native.copy(
        headlineLarge = native.headlineLarge.copy(fontWeight = FontWeight.SemiBold),
        titleLarge = native.titleLarge.copy(fontWeight = FontWeight.SemiBold),
    )
    val title = material.headlineLarge
    val heading = material.titleLarge
    val body = material.bodyLarge
    val label = material.labelLarge
    val caption = material.bodySmall
    val code = material.bodyMedium.copy(fontFamily = FontFamily.Monospace)
}
