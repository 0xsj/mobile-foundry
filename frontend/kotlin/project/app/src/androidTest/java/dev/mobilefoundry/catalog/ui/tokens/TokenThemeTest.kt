package dev.mobilefoundry.catalog.ui.tokens

import android.os.Build
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import dev.mobilefoundry.catalog.MainNavigation
import dev.mobilefoundry.catalog.FoundryCatalogRoot
import dev.mobilefoundry.catalog.theme.FoundryCatalogTheme
import dev.mobilefoundry.ui.styles.presets.FoundryPreset
import dev.mobilefoundry.ui.styles.tokens.FoundryAppearance
import dev.mobilefoundry.ui.styles.tokens.FoundryThemeStyle
import dev.mobilefoundry.ui.theme.FoundryTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class TokenThemeTest {
    @get:Rule val compose = createComposeRule()

    @Test fun nestedAppearanceAndReductionStayScopedAndReachMaterial() {
        val materialMatches = mutableMapOf<String, Boolean>()
        @Composable fun Probe(name: String) {
            val tokens = FoundryTheme.tokens
            val material = MaterialTheme.colorScheme
            val inverse = FoundryPreset.v1(if (tokens.appearance == FoundryAppearance.DARK) FoundryAppearance.LIGHT else FoundryAppearance.DARK).colors
            SideEffect {
                materialMatches[name] = material.background == tokens.colors.surfaceGround.color &&
                    material.primary == tokens.colors.fill.color && material.onPrimary == tokens.colors.fillInk.color &&
                    material.inversePrimary == inverse.accent.color
            }
            Text(name + " " + tokens.colors.surfaceGround.hex + " " + tokens.motion.reduced)
        }
        compose.setContent {
            FoundryTheme(FoundryAppearance.LIGHT) {
                Column {
                    Probe("Outer")
                    FoundryTheme(FoundryAppearance.DARK, reduceMotion = true) {
                        Column {
                            Probe("Inner")
                            FoundryTheme { Probe("Inherited") }
                        }
                    }
                    Probe("Sibling")
                }
            }
        }
        // The system may itself reduce motion; nested explicit reduction is always retained.
        compose.onNodeWithText("Outer #F3F4F6", substring = true).assertExists()
        compose.onNodeWithText("Inner #10151F true").assertExists()
        compose.onNodeWithText("Inherited #10151F true").assertExists()
        compose.onNodeWithText("Sibling #F3F4F6", substring = true).assertExists()
        compose.runOnIdle { assertEquals(mapOf("Outer" to true, "Inner" to true, "Inherited" to true, "Sibling" to true), materialMatches) }
    }

    @Test fun nativeTypographyScalesAndButtonsKeepTheirMinimum() {
        val scale = mutableFloatStateOf(1f)
        val heights = mutableMapOf<Float, Int>()
        compose.setContent {
            val density = LocalDensity.current.density
            CompositionLocalProvider(LocalDensity provides Density(density, scale.floatValue)) {
                FoundryTheme(FoundryAppearance.LIGHT) {
                    val tokens = FoundryTheme.tokens
                    Column {
                        val current = scale.floatValue
                        Text("Scaling sample", style = tokens.typography.body, onTextLayout = { heights[current] = it.size.height })
                        Button(onClick = {}) { Text("Touch target") }
                    }
                }
            }
        }
        val touchHeight = compose.onNodeWithText("Touch target").fetchSemanticsNode().touchBoundsInRoot.height
        assertTrue(touchHeight >= with(compose.density) { 48.dp.toPx() } - 1f)
        compose.runOnIdle { scale.floatValue = 2f }
        compose.waitUntil { heights[2f] != null }
        compose.runOnIdle { assertTrue(heights.getValue(2f) > heights.getValue(1f)) }
        val scaledTouchHeight = compose.onNodeWithText("Touch target").fetchSemanticsNode().touchBoundsInRoot.height
        assertTrue(scaledTouchHeight >= with(compose.density) { 48.dp.toPx() } - 1f)
    }

    @Test fun catalogAppearanceAndReducedMotionControlsWork() {
        compose.setContent { FoundryCatalogTheme { MainNavigation() } }
        compose.onNodeWithText("Tokens").performClick()
        compose.onNodeWithText("Light").performClick()
        compose.onNodeWithText("V1 · Light").assertExists()
        compose.onNodeWithText("Dark").performClick()
        compose.onNodeWithText("V1 · Dark").assertExists()
        compose.onNodeWithContentDescription("Reduce motion preview").performClick().assertIsOn()
        compose.onNodeWithText("Durations: 0 / 0 / 0 ms").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Toggle position").performScrollTo().performClick()
        compose.onNodeWithText("Position: end").performScrollTo().assertIsDisplayed()
    }

    @Test fun previewThemeChangesPreserveExampleState() {
        compose.setContent { FoundryCatalogTheme { TokenCatalogScreen(onBack = {}) } }
        compose.onNodeWithText("Primary action").performScrollTo().performClick()
        compose.onNodeWithText("Actions: 1").assertExists()
        compose.onNodeWithText("Dark").performClick()
        compose.onNodeWithText("Actions: 1").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Secondary action").performScrollTo().performClick()
        compose.onNodeWithText("Actions: 2").assertExists()
    }

    @Test fun appThemePreviewOverrideAndOpaqueFallbackPreserveSceneState() {
        compose.setContent { FoundryCatalogRoot() }
        compose.onNodeWithContentDescription("Glass surfaces").performClick().assertIsOn()
        compose.onNodeWithText("Tokens").performClick()
        val floating = if (Build.VERSION.SDK_INT >= 31) "Glass" else "Solid"
        compose.onNodeWithText("Theme: Glass · Floating: $floating").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Select object").performScrollTo().performClick()
        compose.onNodeWithText("Move scene").performScrollTo().performClick()
        compose.onNodeWithText("Selections: 1 · Scene: B").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Solid").performClick()
        compose.onNodeWithText("Theme: Solid · Floating: Solid").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Selections: 1 · Scene: B").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Glass").performClick()
        compose.onNodeWithContentDescription("Reduce transparency preview").performClick().assertIsOn()
        compose.onNodeWithText("Theme: Glass · Floating: Solid").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Selections: 1 · Scene: B").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Back").performClick()
        compose.onNodeWithContentDescription("Glass surfaces").assertIsOn()
    }

    @Test fun nestedMaterialChoiceInheritsAndCannotUndoAncestorTransparencyReduction() {
        @Composable fun Probe(name: String) {
            val m = FoundryTheme.tokens.materials
            Text("$name ${m.style} ${m.floating}")
        }
        compose.setContent {
            FoundryTheme(style = FoundryThemeStyle.GLASS) {
                Column {
                    Probe("Outer")
                    FoundryTheme { Probe("Inherited") }
                    FoundryTheme(style = FoundryThemeStyle.SOLID) { Probe("Local") }
                    FoundryTheme(reduceTransparency = true) {
                        FoundryTheme(style = FoundryThemeStyle.GLASS, reduceTransparency = false) { Probe("Reduced") }
                    }
                    Probe("Sibling")
                }
            }
        }
        compose.onNodeWithText("Outer GLASS GLASS").assertExists()
        compose.onNodeWithText("Inherited GLASS GLASS").assertExists()
        compose.onNodeWithText("Local SOLID SOLID").assertExists()
        compose.onNodeWithText("Reduced GLASS SOLID").assertExists()
        compose.onNodeWithText("Sibling GLASS GLASS").assertExists()
    }
}
