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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

@Composable
fun ProductStudioScreen(onBack:()->Unit,modifier:Modifier=Modifier) {
    val context=LocalContext.current.applicationContext
    var mesh by remember { mutableStateOf<PreviewMesh?>(null) }
    var failure by remember { mutableStateOf<Failure?>(null) }
    var yaw by rememberSaveable { mutableFloatStateOf(.35f) };var pitch by rememberSaveable { mutableFloatStateOf(.15f) };var distance by rememberSaveable { mutableFloatStateOf(4.5f) }
    var finish by rememberSaveable { mutableStateOf(ProductFinish.PORCELAIN) }
    var quality by rememberSaveable { mutableStateOf(EffectQuality.BALANCED) }
    var turntable by rememberSaveable { mutableStateOf(false) };var reduced by rememberSaveable { mutableStateOf(false) }
    var attempt by remember { mutableIntStateOf(0) }
    var status by remember { mutableStateOf("Loading model…") }
    val camera=OrbitCamera.make(yaw,pitch,distance)
    fun use(value:OrbitCamera) { yaw=value.yaw;pitch=value.pitch;distance=value.distance }
    val tokens=FoundryTheme.tokens;val running=turntable && !reduced && !tokens.motion.reduced
    LaunchedEffect(attempt) {
        withContext(Dispatchers.Main.immediate) {
        try { when(val result=PreviewAssets.mesh(context)) { is Outcome.Ok->mesh=result.value;is Outcome.Err->failure=result.error } }
        catch(e:CancellationException) { throw e }
        catch(e:Exception) { Log.e("FoundryGraphics","Unexpected asset error",e);failure=Failure.Internal(FailureMeta("Asset loading failed.")) }
        }
    }
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(tokens.space.stack)) {
        TextButton(onClick=onBack) { Text("Back") }
        Text("Product studio",style=tokens.typography.title)
        Text("Studio lamp",style=tokens.typography.heading)
        Text("Drag to orbit. Pinch to zoom. Explore three finishes on the same model.",style=tokens.typography.caption)
        val problem=failure;val asset=mesh
        if(problem!=null) {
            Text(problem.publicInfo().meta.message)
            Button(onClick={failure=null;mesh=null;attempt++}) { Text("Try again") }
        } else if(asset!=null) key(attempt) {
            GPUPreviewSurface(PreviewContent.Product(asset,camera,finish),quality,running,Modifier.fillMaxWidth().aspectRatio(1.2f),onGesture={ when(it) {
                is CanvasGesture.Drag->use(OrbitCamera.make(yaw,pitch,distance).rotated(it.dx,it.dy))
                is CanvasGesture.Zoom->use(OrbitCamera.make(yaw,pitch,distance).scaled(it.factor))
            } },onEvent={ when(it) {
                is EffectEvent.Ready->status=it.backend
                is EffectEvent.Statistics->status="Target: ${it.value.width} × ${it.value.height} · Submitted: ${it.value.submittedFrames}"
                is EffectEvent.Failed->failure=it.failure
            } },onUnexpectedError={ Log.e("FoundryGraphics","Unexpected preview error",it) })
        } else CircularProgressIndicator()
        FoundrySurface {
            Column(Modifier.fillMaxWidth().padding(tokens.space.page),verticalArrangement=Arrangement.spacedBy(tokens.space.stack)) {
                Row(horizontalArrangement=Arrangement.spacedBy(tokens.space.inline)) {
                    ProductFinish.entries.forEach { f->FilterChip(selected=finish==f,onClick={finish=f},label={Text(f.name.lowercase().replaceFirstChar { it.uppercase() })}) }
                }
                Text("${finish.name.lowercase().replaceFirstChar { it.uppercase() }} finish",style=tokens.typography.label)
                Row { TextButton(onClick={use(camera.rotated(-.08f,0f))}) { Text("Rotate left") };TextButton(onClick={use(camera.rotated(.08f,0f))}) { Text("Rotate right") } }
                Row { TextButton(onClick={use(camera.scaled(1/1.2f))}) { Text("Zoom out") };TextButton(onClick={use(camera.scaled(1.2f))}) { Text("Zoom in") } }
                OutlinedButton(onClick={use(OrbitCamera.make())}) { Text("Reset camera") }
                Text(String.format(Locale.ROOT,"Camera: %.2f, %.2f · Distance: %.2f",camera.yaw,camera.pitch,camera.distance),style=tokens.typography.caption)
                Row { Text("Turntable",Modifier.weight(1f));Switch(checked=turntable,onCheckedChange={turntable=it},modifier=Modifier.semantics { contentDescription="Turntable" }) }
                Row { Text("Reduce motion preview",Modifier.weight(1f));Switch(checked=reduced,onCheckedChange={reduced=it},modifier=Modifier.semantics { contentDescription="Reduce motion preview" }) }
                Text(if(running) "Turntable running" else "Turntable paused",style=tokens.typography.label)
                Row(horizontalArrangement=Arrangement.spacedBy(tokens.space.inline)) {
                    EffectQuality.entries.forEach { q->FilterChip(selected=quality==q,onClick={quality=q},label={Text(q.name.lowercase().replaceFirstChar { it.uppercase() })}) }
                }
            }
        }
        Text(status,style=tokens.typography.caption)
        Text("Studio lighting preview. Appearance is illustrative.",style=tokens.typography.caption)
    }
}
