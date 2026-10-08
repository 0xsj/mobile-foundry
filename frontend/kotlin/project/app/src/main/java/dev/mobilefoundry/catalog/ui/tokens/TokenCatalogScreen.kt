package dev.mobilefoundry.catalog.ui.tokens

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.mobilefoundry.ui.styles.tokens.FoundryAppearance
import dev.mobilefoundry.ui.styles.tokens.FoundryThemeStyle
import dev.mobilefoundry.ui.components.layout.surface.FoundrySurface
import dev.mobilefoundry.ui.theme.FoundryTheme

private enum class PreviewAppearance(val label: String, val appearance: FoundryAppearance?) {
    SYSTEM("System", null), LIGHT("Light", FoundryAppearance.LIGHT), DARK("Dark", FoundryAppearance.DARK),
}

@Composable
fun TokenCatalogScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    var appearance by rememberSaveable { mutableStateOf(PreviewAppearance.SYSTEM) }
    var reduceMotion by rememberSaveable { mutableStateOf(false) }
    var style by rememberSaveable { mutableStateOf<FoundryThemeStyle?>(null) }
    var reduceTransparency by rememberSaveable { mutableStateOf(false) }
    val tokens = FoundryTheme.tokens
    Column(modifier.fillMaxSize()) {
        TextButton(onClick = onBack) { Text("Back") }
        Text("Tokens", style = tokens.typography.title)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(tokens.space.inline)) {
            PreviewAppearance.entries.forEach { value ->
                FilterChip(selected = appearance == value, onClick = { appearance = value }, label = { Text(value.label) })
            }
        }
        Text("Surface theme", style = tokens.typography.label)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(tokens.space.inline)) {
            listOf(null to "App theme", FoundryThemeStyle.SOLID to "Solid", FoundryThemeStyle.GLASS to "Glass").forEach { (value, label) ->
                FilterChip(selected = style == value, onClick = { style = value }, label = { Text(label) })
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(tokens.space.inline)) {
            Text("Reduce transparency preview", modifier = Modifier.weight(1f))
            Switch(checked = reduceTransparency, onCheckedChange = { reduceTransparency = it },
                modifier = Modifier.semantics { contentDescription = "Reduce transparency preview" })
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(tokens.space.inline)) {
            Text("Reduce motion preview", modifier = Modifier.weight(1f))
            Switch(checked = reduceMotion, onCheckedChange = { reduceMotion = it },
                modifier = Modifier.semantics { contentDescription = "Reduce motion preview" })
        }
        Text("Preview settings apply only to the examples below.", style = tokens.typography.caption)
        FoundryTheme(appearance = appearance.appearance, reduceMotion = reduceMotion, style = style,
            reduceTransparency = reduceTransparency) {
            TokenExamples(Modifier.weight(1f))
        }
    }
}

@Composable
private fun TokenExamples(modifier: Modifier = Modifier) {
    val tokens = FoundryTheme.tokens
    val c = tokens.colors
    var shifted by rememberSaveable { mutableStateOf(false) }
    var actions by rememberSaveable { mutableIntStateOf(0) }
    val offset by animateDpAsState(if (shifted) tokens.space.steps[9] else 0.dp,
        tween(tokens.motion.milliseconds[1], easing = tokens.motion.standardEasing), label = "token-position")
    val swatches = listOf(
        "surfaceGround" to c.surfaceGround, "surfaceSunk" to c.surfaceSunk,
        "surfacePanel" to c.surfacePanel, "surfaceRaised" to c.surfaceRaised,
        "ink" to c.ink, "inkSecondary" to c.inkSecondary, "inkMuted" to c.inkMuted,
        "line" to c.line, "lineStrong" to c.lineStrong, "accent" to c.accent,
        "accentTint" to c.accentTint, "fill" to c.fill, "fillInk" to c.fillInk,
        "info" to c.info, "warn" to c.warn, "crit" to c.crit,
    )
    Column(modifier.fillMaxWidth().background(c.surfaceGround.color).verticalScroll(rememberScrollState())
        .padding(tokens.space.page), verticalArrangement = Arrangement.spacedBy(tokens.space.section)) {
        Text("V1 · " + if (tokens.appearance == FoundryAppearance.DARK) "Dark" else "Light", style = tokens.typography.heading, color = c.ink.color)
        Text("Foundry Studio · Porcelain, graphite, and cobalt.", color = c.inkSecondary.color)
        MaterialExample()
        TokenSection("Colors") {
            swatches.forEach { (name, color) ->
                Row(horizontalArrangement = Arrangement.spacedBy(tokens.space.inline)) {
                    Box(Modifier.size(tokens.space.steps[9]).background(color.color, RoundedCornerShape(tokens.shape.radii[1]))
                        .border(1.dp, c.lineStrong.color, RoundedCornerShape(tokens.shape.radii[1])))
                    Column {
                        Text(name, style = tokens.typography.label, color = c.ink.color)
                        Text(color.hex + " · " + color.alpha.toString(), style = tokens.typography.caption, color = c.inkSecondary.color)
                    }
                }
            }
        }
        TokenSection("Typography") {
            Text("Title · Native ideas", style = tokens.typography.title)
            Text("Heading · Clear hierarchy", style = tokens.typography.heading)
            Text("Body · System text scales with your preferences.", style = tokens.typography.body)
            Text("Label · Continue", style = tokens.typography.label)
            Text("Caption · Supporting context", style = tokens.typography.caption)
            Text("Code · val idea = 1", style = tokens.typography.code)
        }
        TokenSection("Spacing") {
            Text("dp · inline ${tokens.space.inline.value.toInt()} · stack ${tokens.space.stack.value.toInt()} · section ${tokens.space.section.value.toInt()} · page ${tokens.space.page.value.toInt()}", style = tokens.typography.caption)
            tokens.space.steps.forEachIndexed { index, size ->
                Row(horizontalArrangement = Arrangement.spacedBy(tokens.space.inline)) {
                    Text("${index + 1} · ${size.value.toInt()}", style = tokens.typography.code)
                    Box(Modifier.width(size).height(tokens.space.inline).background(c.accent.color))
                }
            }
        }
        TokenSection("Shape and actions") {
            tokens.shape.radii.forEach { radius ->
                Text("Radius ${radius.value.toInt()}", modifier = Modifier.fillMaxWidth()
                    .background(c.surfaceSunk.color, RoundedCornerShape(radius))
                    .border(1.dp, c.lineStrong.color, RoundedCornerShape(radius)).padding(tokens.space.inline))
            }
            Text("Minimum touch target: 48 dp. Content can grow.", style = tokens.typography.caption)
            Button(onClick = { actions++ }) { Text("Primary action") }
            OutlinedButton(onClick = { actions++ }) { Text("Secondary action") }
            Text("Actions: $actions", style = tokens.typography.caption)
        }
        TokenSection("Motion") {
            Text("Durations: ${tokens.motion.milliseconds.joinToString(" / ")} ms")
            Text(if (tokens.motion.reduced) "Reduced motion is active." else "Standard motion is active.", style = tokens.typography.caption)
            Box(Modifier.fillMaxWidth().height(48.dp)) {
                Box(Modifier.offset(x = offset).size(24.dp).background(c.accent.color, CircleShape))
            }
            OutlinedButton(onClick = { shifted = !shifted }) { Text("Toggle position") }
            Text(if (shifted) "Position: end" else "Position: start", style = tokens.typography.caption)
        }
    }
}

@Composable
private fun TokenSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    val tokens = FoundryTheme.tokens
    FoundrySurface {
        Column(Modifier.fillMaxWidth().padding(tokens.space.page), verticalArrangement = Arrangement.spacedBy(tokens.space.stack)) {
            Text(title, style = tokens.typography.heading, modifier = Modifier.semantics { heading() })
            content()
        }
    }
}
