package dev.mobilefoundry.catalog.ui.graphics

import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import dev.mobilefoundry.graphics.*
import dev.mobilefoundry.graphics.gl.GPUPreviewSurface
import dev.mobilefoundry.kernel.*
import dev.mobilefoundry.ui.components.layout.surface.FoundrySurface
import dev.mobilefoundry.ui.theme.FoundryTheme
import kotlinx.coroutines.CancellationException
import java.util.Locale

@Composable
fun CompositorStudioScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current.applicationContext
    val tokens = FoundryTheme.tokens
    var image by remember { mutableStateOf<RasterImage?>(null) }
    val overlay = remember { PreviewAssets.overlay() }
    var failure by remember { mutableStateOf<Failure?>(null) }
    var opacity by rememberSaveable { mutableFloatStateOf(.85f) }
    var scale by rememberSaveable { mutableFloatStateOf(.65f) }
    var radius by rememberSaveable { mutableFloatStateOf(.32f) }
    var feather by rememberSaveable { mutableFloatStateOf(.12f) }
    var blur by rememberSaveable { mutableFloatStateOf(0f) }
    var glow by rememberSaveable { mutableFloatStateOf(.35f) }
    var comparison by rememberSaveable { mutableFloatStateOf(.5f) }
    var layerX by rememberSaveable { mutableFloatStateOf(.5f) }
    var layerY by rememberSaveable { mutableFloatStateOf(.5f) }
    var maskX by rememberSaveable { mutableFloatStateOf(.5f) }
    var maskY by rememberSaveable { mutableFloatStateOf(.5f) }
    var maskEnabled by rememberSaveable { mutableStateOf(true) }
    var profiling by rememberSaveable { mutableStateOf(false) }
    var reduced by rememberSaveable { mutableStateOf(false) }
    var tool by rememberSaveable { mutableStateOf("Mask") }
    var blend by rememberSaveable { mutableStateOf(CompositeBlend.NORMAL) }
    var quality by rememberSaveable { mutableStateOf(EffectQuality.BALANCED) }
    var profile by remember { mutableStateOf<GraphicsProfile?>(null) }
    var attempt by remember { mutableIntStateOf(0) }
    val running = profiling && !reduced && !tokens.motion.reduced // Native attachment also gates foreground/removal.
    val settings = CompositeSettings.make(opacity, scale, EffectPoint.make(layerX, layerY), EffectPoint.make(maskX, maskY),
        radius, feather, blur, glow, comparison, maskEnabled, blend)
    LaunchedEffect(attempt) {
        try { when (val result = PreviewAssets.image(context)) {
            is Outcome.Ok -> image = result.value
            is Outcome.Err -> failure = result.error
        } } catch (e: CancellationException) { throw e }
        catch (e: Exception) {
            Log.e("FoundryGraphics", "Unexpected compositor asset error", e)
            failure = Failure.Internal(FailureMeta("Asset loading failed."))
        }
    }
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(tokens.space.stack)) {
        TextButton(onClick = onBack) { Text("Back") }
        Text("Compositor studio", style = tokens.typography.title)
        Text("Layer a transparent graphic over a photograph. Drag with the selected tool; pinch to scale the layer.", style = tokens.typography.caption)
        val problem = failure; val asset = image
        if (problem != null) {
            Text(problem.publicInfo().meta.message)
            Button(onClick = { failure = null; image = null; profile = null; attempt++ }) { Text("Try again") }
        } else if (asset != null) key(attempt) {
            GPUPreviewSurface(PreviewContent.Composite(asset, overlay, settings), quality, running,
                modifier = Modifier.fillMaxWidth().aspectRatio(1.2f), onGesture = { when (it) {
                    is CanvasGesture.Drag -> when (tool) {
                        "Layer" -> { layerX = (layerX + it.dx).coerceIn(0f, 1f); layerY = (layerY + it.dy).coerceIn(0f, 1f) }
                        "Mask" -> { maskX = it.x.coerceIn(0f, 1f); maskY = it.y.coerceIn(0f, 1f) }
                        else -> comparison = it.x.coerceIn(0f, 1f)
                    }
                    is CanvasGesture.Zoom -> scale = (scale * it.factor).coerceIn(.15f, 1f)
                } }, onEvent = { if (it is EffectEvent.Failed) failure = it.failure },
                onUnexpectedError = { Log.e("FoundryGraphics", "Unexpected compositor error", it) }, onProfile = { profile = it })
        } else CircularProgressIndicator()
        Text("Original on the left · Composed on the right", style = tokens.typography.caption)
        FoundrySurface {
            Column(Modifier.fillMaxWidth().padding(tokens.space.page), verticalArrangement = Arrangement.spacedBy(tokens.space.stack)) {
                Row(horizontalArrangement = Arrangement.spacedBy(tokens.space.inline)) {
                    listOf("Layer", "Mask", "Compare").forEach { value -> FilterChip(selected = tool == value, onClick = { tool = value }, label = { Text(value) }) }
                }
                Text("Drag to ${if (tool == "Compare") "compare the composition" else "move the ${tool.lowercase()}"}.", style = tokens.typography.caption)
                Row(horizontalArrangement = Arrangement.spacedBy(tokens.space.inline)) {
                    CompositeBlend.entries.forEach { value -> FilterChip(selected = blend == value, onClick = { blend = value }, label = { Text(value.name.lowercase().replaceFirstChar { it.uppercase() }) }) }
                }
                CompositeControl("Opacity", opacity, { opacity = it })
                CompositeControl("Layer scale", scale, { scale = it }, .15f..1f)
                CompositeControl("Layer X", layerX, { layerX = it })
                CompositeControl("Layer Y", layerY, { layerY = it })
                Row { Text("Feathered mask", Modifier.weight(1f)); Switch(maskEnabled, { maskEnabled = it }, Modifier.semantics { contentDescription = "Feathered mask" }) }
                CompositeControl("Mask radius", radius, { radius = it }, .05f..0.75f, maskEnabled)
                CompositeControl("Mask feather", feather, { feather = it }, 0f..0.3f, maskEnabled)
                CompositeControl("Mask X", maskX, { maskX = it }, enabled = maskEnabled)
                CompositeControl("Mask Y", maskY, { maskY = it }, enabled = maskEnabled)
                CompositeControl("Blur · target pixels", blur, { blur = it }, 0f..24f)
                CompositeControl("Glow", glow, { glow = it })
                CompositeControl("Comparison", comparison, { comparison = it })
                Row { TextButton(onClick = { comparison = 1f }) { Text("Original") }; TextButton(onClick = { comparison = 0f }) { Text("Composed") } }
                OutlinedButton(onClick = {
                    opacity = .85f; scale = .65f; radius = .32f; feather = .12f; blur = 0f; glow = .35f
                    comparison = .5f; layerX = .5f; layerY = .5f; maskX = .5f; maskY = .5f
                    maskEnabled = true; blend = CompositeBlend.NORMAL; tool = "Mask"
                }) { Text("Reset composition") }
            }
        }
        FoundrySurface {
            Column(Modifier.fillMaxWidth().padding(tokens.space.page), verticalArrangement = Arrangement.spacedBy(tokens.space.stack)) {
                Text("Render workload", style = tokens.typography.heading)
                Row(horizontalArrangement = Arrangement.spacedBy(tokens.space.inline)) {
                    EffectQuality.entries.forEach { q -> FilterChip(selected = quality == q, onClick = { quality = q }, label = { Text(q.name.lowercase().replaceFirstChar { it.uppercase() }) }) }
                }
                Row { Text("Profile redraws", Modifier.weight(1f)); Switch(profiling, { profiling = it }, Modifier.semantics { contentDescription = "Profile redraws" }) }
                Row { Text("Reduce motion preview", Modifier.weight(1f)); Switch(reduced, { reduced = it }, Modifier.semantics { contentDescription = "Reduce motion preview" }) }
                Text(if (running) "Redraw workload requested" else "Draw on change", style = tokens.typography.label)
                profile?.let { p ->
                    Text("${p.width} × ${p.height} · ${p.passes} passes · Frame ${p.frame}", style = tokens.typography.caption)
                    Text("Uploads: ${p.uploads} · Target allocations: ${p.targetAllocations}", style = tokens.typography.caption)
                    Text(String.format(Locale.ROOT, "Texture payload · Inputs: %.2f MiB · Targets: %.2f MiB", p.inputTextureBytes / 1_048_576.0, p.offscreenTextureBytes / 1_048_576.0), style = tokens.typography.caption)
                    Text(String.format(Locale.ROOT, "CPU encode: %.2f ms", p.cpuEncodeMilliseconds), style = tokens.typography.caption)
                    Text(p.gpuTiming, style = tokens.typography.caption)
                }
                Text("Payload estimates exclude driver, depth and display buffers. Profile sustained workloads on a physical device.", style = tokens.typography.caption)
            }
        }
    }
}

@Composable
private fun CompositeControl(title: String, value: Float, change: (Float) -> Unit, range: ClosedFloatingPointRange<Float> = 0f..1f, enabled: Boolean = true) {
    Text(String.format(Locale.ROOT, "%s · %.2f", title, value), style = FoundryTheme.tokens.typography.caption)
    Slider(value, change, valueRange = range, enabled = enabled, modifier = Modifier.semantics { contentDescription = title })
}
