package dev.mobilefoundry.ui.styles.tokens

import androidx.compose.runtime.Immutable

/** Role names survive appearance and future preset changes. */
@Immutable
data class FoundryColors(
    val surfaceGround: TokenColor, val surfaceSunk: TokenColor,
    val surfacePanel: TokenColor, val surfaceRaised: TokenColor,
    val ink: TokenColor, val inkSecondary: TokenColor, val inkMuted: TokenColor,
    val line: TokenColor, val lineStrong: TokenColor,
    val accent: TokenColor, val accentTint: TokenColor,
    val fill: TokenColor, val fillInk: TokenColor,
    val info: TokenColor, val warn: TokenColor, val crit: TokenColor,
)

enum class FoundryAppearance { LIGHT, DARK }

@Immutable
data class FoundryTokens(
    val appearance: FoundryAppearance,
    val colors: FoundryColors,
    val space: FoundrySpace = FoundrySpace,
    val shape: FoundryShape = FoundryShape,
    val typography: FoundryTypography = FoundryTypography,
    val motion: FoundryMotion,
    val materials: FoundryMaterials = FoundryMaterials(),
)
