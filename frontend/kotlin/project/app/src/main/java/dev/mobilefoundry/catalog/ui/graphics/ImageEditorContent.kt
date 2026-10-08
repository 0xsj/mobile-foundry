package dev.mobilefoundry.catalog.ui.graphics

import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import dev.mobilefoundry.graphics.*
import dev.mobilefoundry.graphics.gl.GPUPreviewSurface
import dev.mobilefoundry.kernel.*
import dev.mobilefoundry.ui.components.layout.surface.Surface
import dev.mobilefoundry.ui.theme.FoundryTheme
import java.util.Locale

/** Shared by the bundled Image studio and the camera's transient photo draft. Host owns scrolling. */
@Composable
fun ImageEditorContent(image: RasterImage, modifier: Modifier = Modifier, startsEdited: Boolean = false) {
    var failure by remember { mutableStateOf<Failure?>(null) }
    var exposure by rememberSaveable { mutableFloatStateOf(if (startsEdited) 0f else .35f) }
    var saturation by rememberSaveable { mutableFloatStateOf(if (startsEdited) 1f else 1.15f) }
    var vignette by rememberSaveable { mutableFloatStateOf(if (startsEdited) 0f else .25f) }
    var comparison by rememberSaveable { mutableFloatStateOf(if (startsEdited) 0f else .5f) }
    var zoom by rememberSaveable { mutableFloatStateOf(1f) }
    var x by rememberSaveable { mutableFloatStateOf(0f) };var y by rememberSaveable { mutableFloatStateOf(0f) }
    var move by rememberSaveable { mutableStateOf(false) }
    var quality by rememberSaveable { mutableStateOf(EffectQuality.BALANCED) }
    var status by remember { mutableStateOf("Loading photograph…") }
    var attempt by remember { mutableIntStateOf(0) }
    val viewport=ImageViewport.make(zoom,x,y)
    fun use(value:ImageViewport) { zoom=value.zoom;x=value.x;y=value.y }
    val tokens=FoundryTheme.tokens
    Column(modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(tokens.space.stack)) {
        Text("Compare live adjustments. Pinch to zoom; choose Move image to pan.",style=tokens.typography.caption)
        val problem=failure;val asset=image
        if(problem!=null) {
            Text(problem.publicInfo().meta.message)
            Button(onClick={failure=null;attempt++}) { Text("Try again") }
        } else key(attempt) {
            GPUPreviewSurface(PreviewContent.Image(asset,ImageAdjustments.make(exposure,saturation,vignette),viewport,comparison),quality,
                modifier=Modifier.fillMaxWidth().aspectRatio(1.2f),onGesture={ when(it) {
                    is CanvasGesture.Drag -> if(move) use(ImageViewport.make(zoom,x,y).moved(it.dx,it.dy)) else comparison=it.x.coerceIn(0f,1f)
                    is CanvasGesture.Zoom -> use(ImageViewport.make(zoom,x,y).scaled(it.factor))
                } },onEvent={ when(it) {
                    is EffectEvent.Ready->status=it.backend
                    is EffectEvent.Statistics->status="Static preview · ${it.value.width} × ${it.value.height} · Submitted: ${it.value.submittedFrames}"
                    is EffectEvent.Failed->failure=it.failure
                } },onUnexpectedError={ Log.e("FoundryGraphics","Unexpected preview error",it) })
        }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(tokens.space.inline)) {
            PhotoFilter.entries.forEach { filter ->
                FilterChip(selected = filter.adjustments == ImageAdjustments.make(exposure, saturation, vignette),
                    onClick = { val value = filter.adjustments; exposure = value.exposure; saturation = value.saturation; vignette = value.vignette; comparison = 0f },
                    label = { Text(filter.label) })
            }
        }
        Surface {
            Column(Modifier.fillMaxWidth().padding(tokens.space.page),verticalArrangement=Arrangement.spacedBy(tokens.space.stack)) {
                Row(horizontalArrangement=Arrangement.spacedBy(tokens.space.inline)) {
                    FilterChip(selected=!move,onClick={move=false},label={Text("Compare")})
                    FilterChip(selected=move,onClick={move=true},label={Text("Move image")})
                }
                Text(if(move) "Drag to pan the image." else "Drag to move the before/after divider.",style=tokens.typography.caption)
                Text("Exposure");Slider(value=exposure,onValueChange={exposure=it},valueRange=-2f..2f,modifier=Modifier.semantics { contentDescription="Exposure" })
                Text("Saturation");Slider(value=saturation,onValueChange={saturation=it},valueRange=0f..2f,modifier=Modifier.semantics { contentDescription="Saturation" })
                Text("Vignette");Slider(value=vignette,onValueChange={vignette=it},modifier=Modifier.semantics { contentDescription="Vignette" })
                Text("Comparison");Slider(value=comparison,onValueChange={comparison=it},modifier=Modifier.semantics { contentDescription="Comparison" })
                Row { TextButton(onClick={comparison=1f}) { Text("Original") };TextButton(onClick={comparison=0f}) { Text("Edited") } }
                Row { TextButton(onClick={use(viewport.scaled(1/1.25f))}) { Text("Zoom out") };TextButton(onClick={use(viewport.scaled(1.25f))}) { Text("Zoom in") } }
                Text(String.format(Locale.ROOT,"Zoom: %.2f×",zoom),style=tokens.typography.caption)
                Row(horizontalArrangement=Arrangement.spacedBy(tokens.space.inline)) {
                    EffectQuality.entries.forEach { q->FilterChip(selected=quality==q,onClick={quality=q},label={Text(q.name.lowercase().replaceFirstChar { it.uppercase() })}) }
                }
                OutlinedButton(onClick={exposure=0f;saturation=1f;vignette=0f;comparison=.5f;use(ImageViewport.make())}) { Text("Reset edits") }
            }
        }
        Text("Original on the left · Edited on the right",style=tokens.typography.caption)
        Text(status,style=tokens.typography.caption)
    }
}
