package dev.mobilefoundry.ui.theme

import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.compositeOver
import dev.mobilefoundry.ui.styles.presets.FoundryPreset
import dev.mobilefoundry.ui.styles.tokens.*

private val LocalFoundry = staticCompositionLocalOf<FoundryTokens?> { null }

object FoundryTheme {
    val tokens: FoundryTokens @Composable get() = LocalFoundry.current ?: FoundryPreset.v1(
        if (isSystemInDarkTheme()) FoundryAppearance.DARK else FoundryAppearance.LIGHT,
    )
}

/** Scoped deterministic theme. Explicit reduction cannot override an ancestor/system reduction. */
@Composable
fun FoundryTheme(
    appearance: FoundryAppearance? = null,
    reduceMotion: Boolean = false,
    style: FoundryThemeStyle? = null,
    reduceTransparency: Boolean = false,
    content: @Composable () -> Unit,
) {
    val inherited = LocalFoundry.current
    val systemDark = isSystemInDarkTheme()
    val systemReduced = systemReducedMotion()
    val tokens = FoundryPreset.v1(
        appearance ?: inherited?.appearance ?: if (systemDark) FoundryAppearance.DARK else FoundryAppearance.LIGHT,
        reduceMotion || inherited?.motion?.reduced == true || systemReduced,
        style ?: inherited?.materials?.style ?: FoundryThemeStyle.SOLID,
        reduceTransparency || inherited?.materials?.reduceTransparency == true,
    )
    val c = tokens.colors
    val inverse = FoundryPreset.v1(if (tokens.appearance == FoundryAppearance.DARK) FoundryAppearance.LIGHT else FoundryAppearance.DARK).colors
    val tint = c.accentTint.color.compositeOver(c.surfacePanel.color)
    val base = if (tokens.appearance == FoundryAppearance.DARK) darkColorScheme() else lightColorScheme()
    val material = base.copy(
        primary = c.fill.color, onPrimary = c.fillInk.color,
        primaryContainer = tint, onPrimaryContainer = c.ink.color,
        secondary = c.accent.color, onSecondary = c.surfaceGround.color,
        secondaryContainer = tint, onSecondaryContainer = c.ink.color,
        tertiary = c.info.color, onTertiary = c.surfaceGround.color,
        tertiaryContainer = c.surfaceSunk.color, onTertiaryContainer = c.ink.color,
        background = c.surfaceGround.color, onBackground = c.ink.color,
        surface = c.surfacePanel.color, onSurface = c.ink.color,
        surfaceVariant = c.surfaceSunk.color, onSurfaceVariant = c.inkSecondary.color,
        surfaceTint = c.accent.color,
        error = c.crit.color, onError = c.surfacePanel.color,
        errorContainer = c.crit.color.copy(alpha = .12f).compositeOver(c.surfacePanel.color), onErrorContainer = c.crit.color,
        outline = c.lineStrong.color.compositeOver(c.surfacePanel.color),
        outlineVariant = c.line.color.compositeOver(c.surfacePanel.color),
        inverseSurface = c.ink.color, inverseOnSurface = c.surfacePanel.color, inversePrimary = inverse.accent.color,
        surfaceContainerLowest = c.surfaceGround.color, surfaceContainerLow = c.surfaceSunk.color,
        surfaceContainer = c.surfacePanel.color, surfaceContainerHigh = c.surfaceRaised.color,
        surfaceContainerHighest = c.surfaceRaised.color, surfaceBright = c.surfaceRaised.color, surfaceDim = c.surfaceSunk.color,
    )
    val radii = tokens.shape.radii
    CompositionLocalProvider(LocalFoundry provides tokens) {
        MaterialTheme(colorScheme = material, typography = tokens.typography.material,
            shapes = Shapes(RoundedCornerShape(radii[0]), RoundedCornerShape(radii[1]),
                RoundedCornerShape(radii[2]), RoundedCornerShape(radii[3]), RoundedCornerShape(radii[3])), content = content)
    }
}

/** Observe the platform switch without changing settings; release the observer on disposal. */
@Composable
private fun systemReducedMotion(): Boolean {
    val resolver = LocalContext.current.contentResolver
    fun read() = Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    var reduced by remember(resolver) { mutableStateOf(read()) }
    DisposableEffect(resolver) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) { reduced = read() }
        }
        resolver.registerContentObserver(Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE), false, observer)
        reduced = read()
        onDispose { resolver.unregisterContentObserver(observer) }
    }
    return reduced
}
