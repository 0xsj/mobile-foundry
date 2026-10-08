package dev.mobilefoundry.ui.styles.tokens

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import java.util.Locale

/** sRGB data; raw palette values belong in presets, not components. */
@Immutable
data class TokenColor(val rgb: Int, val alpha: Float = 1f) {
    init { require(rgb in 0..0xFFFFFF && alpha in 0f..1f) }
    val hex: String get() = String.format(Locale.ROOT, "#%06X", rgb)
    val color: Color get() = Color(0xFF000000L or rgb.toLong()).copy(alpha = alpha)
}

internal object Palette {
    const val white = 0xFFFFFF
    const val porcelain50 = 0xFAFBFD
    const val porcelain100 = 0xF3F4F6
    const val porcelain200 = 0xE8EBF0
    const val graphite50 = 0xEEF2F8
    const val graphite300 = 0xB3BFD1
    const val graphite400 = 0x8E9DB3
    const val graphite500 = 0x606E82
    const val graphite600 = 0x4D5A6C
    const val graphite800 = 0x222C3C
    const val graphite850 = 0x182132
    const val graphite900 = 0x181F2C
    const val graphite950 = 0x10151F
    const val graphite1000 = 0x0B0F17
    const val cobalt300 = 0xA1B5FF
    const val cobalt700 = 0x3155C6
    const val cobalt950 = 0x10182C
    const val sky300 = 0x8FC8E7
    const val sky700 = 0x286A8A
    const val ochre300 = 0xE9BC74
    const val ochre700 = 0x8A570F
    const val vermilion300 = 0xF3A197
    const val vermilion700 = 0xB54238
}
