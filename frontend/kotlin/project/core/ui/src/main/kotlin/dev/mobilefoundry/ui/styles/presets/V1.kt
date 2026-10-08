package dev.mobilefoundry.ui.styles.presets

import dev.mobilefoundry.ui.styles.tokens.*

/** Foundry Studio: porcelain surfaces, blue graphite, and cobalt actions. */
object FoundryPreset {
    fun v1(appearance: FoundryAppearance, reduceMotion: Boolean = false,
           style: FoundryThemeStyle = FoundryThemeStyle.SOLID, reduceTransparency: Boolean = false): FoundryTokens {
        val dark = appearance == FoundryAppearance.DARK
        return FoundryTokens(appearance, FoundryColors(
            surfaceGround = TokenColor(if (dark) Palette.graphite950 else Palette.porcelain100),
            surfaceSunk = TokenColor(if (dark) Palette.graphite1000 else Palette.porcelain200),
            surfacePanel = TokenColor(if (dark) Palette.graphite900 else Palette.porcelain50),
            surfaceRaised = TokenColor(if (dark) Palette.graphite800 else Palette.white),
            ink = TokenColor(if (dark) Palette.graphite50 else Palette.graphite850),
            inkSecondary = TokenColor(if (dark) Palette.graphite300 else Palette.graphite600),
            inkMuted = TokenColor(if (dark) Palette.graphite400 else Palette.graphite500),
            line = TokenColor(if (dark) Palette.graphite50 else Palette.graphite850, .10f),
            lineStrong = TokenColor(if (dark) Palette.graphite50 else Palette.graphite850, .20f),
            accent = TokenColor(if (dark) Palette.cobalt300 else Palette.cobalt700),
            accentTint = TokenColor(if (dark) Palette.cobalt300 else Palette.cobalt700, if (dark) .12f else .08f),
            fill = TokenColor(if (dark) Palette.cobalt300 else Palette.cobalt700),
            fillInk = TokenColor(if (dark) Palette.cobalt950 else Palette.white),
            info = TokenColor(if (dark) Palette.sky300 else Palette.sky700),
            warn = TokenColor(if (dark) Palette.ochre300 else Palette.ochre700),
            crit = TokenColor(if (dark) Palette.vermilion300 else Palette.vermilion700),
        ), motion = FoundryMotion(reduceMotion), materials = FoundryMaterials(style, reduceTransparency))
    }
}
