package dev.mobilefoundry.graphics.gl

import android.app.ActivityManager
import android.content.Context
import android.opengl.GLSurfaceView
import android.view.Choreographer
import android.view.MotionEvent
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import dev.mobilefoundry.graphics.*
import dev.mobilefoundry.kernel.*

/** View lifecycle owns scheduling/context. App state supplies only bounded snapshots. */
@Composable
fun GPUEffectSurface(settings: EffectSettings, running: Boolean, modifier: Modifier = Modifier,
                     onPoint: (EffectPoint) -> Unit, onEvent: (EffectEvent) -> Unit, onUnexpectedError: (Exception) -> Unit) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val currentPoint by rememberUpdatedState(onPoint)
    val currentEvent by rememberUpdatedState(onEvent)
    val currentDiagnostic by rememberUpdatedState(onUnexpectedError)
    val capable = remember(context) { context.getSystemService(ActivityManager::class.java).deviceConfigurationInfo.reqGlEsVersion >= 0x20000 }
    if (!capable) {
        LaunchedEffect(Unit) { currentEvent(EffectEvent.Failed(Failure.Unavailable(FailureMeta("GPU rendering is unavailable on this device.", "graphics.unavailable")))) }
        return
    }
    var native by remember { mutableStateOf<EffectGLView?>(null) }
    AndroidView(modifier = modifier.testTag("gpu-canvas"), factory = {
        EffectGLView(it, { currentPoint(it) }, { currentEvent(it) }, { currentDiagnostic(it) }).also { view -> native = view }
    }, update = { it.update(settings, running) }, onRelease = { it.dispose(); native = null })
    DisposableEffect(native, owner) {
        val view = native
        val observer = LifecycleEventObserver { _, _ -> view?.foreground(owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }
        owner.lifecycle.addObserver(observer)
        view?.foreground(owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
        onDispose { owner.lifecycle.removeObserver(observer); view?.foreground(false) }
    }
}

/** Choreographer runs on main; queued updates and GL handles run on the GL thread. */
class EffectGLView internal constructor(context: Context, private val point: (EffectPoint) -> Unit,
                                       event: (EffectEvent) -> Unit, diagnostic: (Exception) -> Unit) : GLSurfaceView(context), Choreographer.FrameCallback {
    private var disposed = false
    private var foreground = false
    private var paused = false
    private var requested = false
    private var scheduled = false
    private var previousFrame = 0L
    private var settings = EffectSettings.make()
    private val scheduler = Choreographer.getInstance()
    private val renderer = EffectRenderer(context.assets,
        { value -> post { if (!disposed) event(value) } },
        { error -> post { if (!disposed) diagnostic(error) } })

    init {
        contentDescription = "Interactive GPU canvas"
        setEGLContextClientVersion(2); setEGLConfigChooser(8, 8, 8, 8, 0, 0)
        preserveEGLContextOnPause = false
        setRenderer(renderer); renderMode = RENDERMODE_WHEN_DIRTY
    }

    fun update(settings: EffectSettings, running: Boolean) {
        if (disposed || (settings == this.settings && requested == running)) return
        this.settings = settings; requested = running
        resize()
        val active = foreground && requested
        queueEvent {
            if (renderer.running != active) renderer.clock.suspend()
            renderer.settings = settings; renderer.running = active
        }
        pace()
        if (foreground) requestRender()
    }

    internal fun foreground(value: Boolean) {
        if (disposed || foreground == value) return
        foreground = value
        if (value) {
            if (paused) { onResume(); paused = false }
            val animate = requested
            queueEvent { renderer.clock.suspend(); renderer.running = animate }
            requestRender()
        } else {
            queueEvent { renderer.clock.suspend(); renderer.running = false }
            onPause(); paused = true
        }
        pace()
    }

    internal fun dispose() {
        if (disposed) return
        disposed = true; foreground = false
        scheduler.removeFrameCallback(this); scheduled = false
        // Releasing the non-preserved EGL context frees its program and GPU resources.
        if (!paused) { onPause(); paused = true }
    }

    private fun pace() {
        scheduler.removeFrameCallback(this); scheduled = false; previousFrame = 0
        if (!disposed && foreground && requested) { scheduler.postFrameCallback(this); scheduled = true }
    }
    override fun doFrame(frameTimeNanos: Long) {
        scheduled = false
        if (disposed || !foreground || !requested) return
        if (previousFrame == 0L || frameTimeNanos - previousFrame >= 1_000_000_000L / settings.quality.framesPerSecond - 500_000L) {
            requestRender(); previousFrame = frameTimeNanos
        }
        scheduler.postFrameCallback(this); scheduled = true
    }
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) { super.onSizeChanged(w, h, oldw, oldh); resize() }
    private var target: Pair<Int, Int>? = null
    private fun resize() {
        val next = settings.quality.resolution(width.toDouble(), height.toDouble()) ?: return
        if (target != next) { target = next; holder.setFixedSize(next.first, next.second) }
    }
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> parent?.requestDisallowInterceptTouchEvent(true)
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> parent?.requestDisallowInterceptTouchEvent(false)
        }
        if (event.actionMasked != MotionEvent.ACTION_CANCEL && width > 0 && height > 0) point(EffectPoint.make(event.x / width, event.y / height))
        if (event.actionMasked == MotionEvent.ACTION_UP) performClick()
        return true
    }
    override fun performClick(): Boolean { super.performClick(); return true }
}
