package dev.mobilefoundry.ui

import dev.mobilefoundry.ui.styles.presets.FoundryPreset
import dev.mobilefoundry.ui.styles.tokens.*
import java.io.File
import kotlin.math.pow
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class TokensTest {
    private fun fixture() = Json.parseToJsonElement(File(System.getProperty("foundry.ui.fixtures"), "tokens.json").readText()).jsonObject
    private fun roles(c: FoundryColors) = mapOf(
        "surfaceGround" to c.surfaceGround, "surfaceSunk" to c.surfaceSunk, "surfacePanel" to c.surfacePanel,
        "surfaceRaised" to c.surfaceRaised, "ink" to c.ink, "inkSecondary" to c.inkSecondary, "inkMuted" to c.inkMuted,
        "line" to c.line, "lineStrong" to c.lineStrong, "accent" to c.accent, "accentTint" to c.accentTint,
        "fill" to c.fill, "fillInk" to c.fillInk, "info" to c.info, "warn" to c.warn, "crit" to c.crit,
    )

    @Test fun v1MatchesSharedPalettesAndNativeScales() {
        val f = fixture()
        for (appearance in FoundryAppearance.entries) {
            val tokens = FoundryPreset.v1(appearance)
            val key = if (appearance == FoundryAppearance.LIGHT) "light" else "dark"
            val expected = f.getValue("presets").jsonObject.getValue("v1").jsonObject.getValue(key).jsonObject
            val actual = roles(tokens.colors)
            assertEquals(expected.keys, actual.keys)
            for ((name, color) in actual) {
                val value = expected.getValue(name).jsonObject
                assertEquals("#" + value.getValue("rgb").jsonPrimitive.content, color.hex)
                assertEquals(value.getValue("alpha").jsonPrimitive.float, color.alpha, .00001f)
            }
            assertEquals(f.getValue("space").jsonArray.map { it.jsonPrimitive.int }, tokens.space.steps.map { it.value.toInt() })
            assertEquals(f.getValue("radii").jsonArray.map { it.jsonPrimitive.int }, tokens.shape.radii.map { it.value.toInt() })
            assertEquals(f.getValue("minimumInteractive").jsonObject.getValue("kotlin").jsonPrimitive.int, tokens.shape.minimumInteractive.value.toInt())
            assertEquals(listOf(8, 12, 24, 20), listOf(tokens.space.inline, tokens.space.stack, tokens.space.section, tokens.space.page).map { it.value.toInt() })
        }
    }

    private fun luminance(color: TokenColor): Double {
        fun linear(value: Int): Double {
            val c = value / 255.0
            return if (c <= .04045) c / 12.92 else ((c + .055) / 1.055).pow(2.4)
        }
        return .2126 * linear((color.rgb shr 16) and 255) + .7152 * linear((color.rgb shr 8) and 255) + .0722 * linear(color.rgb and 255)
    }

    @Test fun prescribedTextPairsHaveMinimumContrast() {
        for (appearance in FoundryAppearance.entries) {
            val c = FoundryPreset.v1(appearance).colors
            val inverse = FoundryPreset.v1(if (appearance == FoundryAppearance.DARK) FoundryAppearance.LIGHT else FoundryAppearance.DARK).colors
            val pairs = listOf(c.ink to c.surfaceGround, c.ink to c.surfacePanel,
                c.inkSecondary to c.surfaceGround, c.inkSecondary to c.surfacePanel,
                c.inkMuted to c.surfaceGround, c.inkMuted to c.surfacePanel,
                c.accent to c.surfacePanel, c.fillInk to c.fill,
                c.info to c.surfacePanel, c.warn to c.surfacePanel, c.crit to c.surfacePanel,
                inverse.accent to c.ink)
            for ((foreground, background) in pairs) {
                assertEquals(1f, foreground.alpha, 0f); assertEquals(1f, background.alpha, 0f)
                val a = luminance(foreground); val b = luminance(background)
                assertTrue((maxOf(a, b) + .05) / (minOf(a, b) + .05) >= 4.5)
            }
        }
    }

    @Test fun reducedMotionZeroesDurationsWithoutChangingPalette() {
        val expected = fixture().getValue("motionMilliseconds").jsonArray.map { it.jsonPrimitive.int }
        for (appearance in FoundryAppearance.entries) {
            val normal = FoundryPreset.v1(appearance)
            val reduced = FoundryPreset.v1(appearance, reduceMotion = true)
            assertEquals(expected, normal.motion.milliseconds)
            assertEquals(listOf(0, 0, 0), reduced.motion.milliseconds)
            assertEquals(normal.colors, reduced.colors)
        }
    }

    @Test fun materialsMatchSharedRolesAndUnsupportedBackdropsBecomeOpaque() {
        val cases = Json.parseToJsonElement(File(System.getProperty("foundry.ui.fixtures"), "materials.json").readText()).jsonArray
        for (appearance in FoundryAppearance.entries) {
            val baseline = FoundryPreset.v1(appearance)
            for (entry in cases) {
                val row = entry.jsonObject
                val style = FoundryThemeStyle.valueOf(row.getValue("style").jsonPrimitive.content.uppercase())
                val tokens = FoundryPreset.v1(appearance, style = style,
                    reduceTransparency = row.getValue("reduceTransparency").jsonPrimitive.boolean)
                val materials = tokens.materials
                assertEquals(row.getValue("content").jsonPrimitive.content, materials.content.name.lowercase())
                assertEquals(row.getValue("floating").jsonPrimitive.content, materials.floating.name.lowercase())
                assertEquals(materials.floating, materials.floatingWithBackdrop(true, true))
                assertEquals(FoundrySurfaceMaterial.SOLID, materials.floatingWithBackdrop(false, true))
                assertEquals(FoundrySurfaceMaterial.SOLID, materials.floatingWithBackdrop(true, false))
                assertEquals(style, materials.style)
                assertEquals(baseline.colors, tokens.colors)
                assertEquals(baseline.motion.milliseconds, tokens.motion.milliseconds)
            }
        }
    }
}
