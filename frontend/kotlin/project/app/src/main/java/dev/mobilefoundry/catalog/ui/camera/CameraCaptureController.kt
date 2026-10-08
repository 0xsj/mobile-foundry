package dev.mobilefoundry.catalog.ui.camera

import android.content.Context
import android.util.Log
import android.view.Surface
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.runtime.*
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import dev.mobilefoundry.graphics.RasterImage
import dev.mobilefoundry.kernel.*
import kotlinx.coroutines.*

/** Main-thread session state; JPEG conversion runs off main. Owned use cases only. */
class CameraCaptureController(context: Context, private val owner: LifecycleOwner, private val front: Boolean) {
    val previewView = PreviewView(context).apply {
        implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        scaleType = PreviewView.ScaleType.FILL_CENTER
    }
    var ready by mutableStateOf(false); private set
    var busy by mutableStateOf(false); private set
    var canFlip by mutableStateOf(false); private set
    var message by mutableStateOf("Starting camera…"); private set
    private val main = ContextCompat.getMainExecutor(context)
    private val future = ProcessCameraProvider.getInstance(context.applicationContext)
    private var provider: ProcessCameraProvider? = null
    private var preview: Preview? = null
    private var capture: ImageCapture? = null
    private var generation = 0
    private var cameraState: LiveData<CameraState>? = null
    private val stateObserver = Observer<CameraState> { state ->
        ready = state.type == CameraState.Type.OPEN && capture != null
        val error = state.error
        if (error != null) {
            Log.e("FoundryCamera", "Camera state error ${error.code}", error.cause)
            message = "Camera interrupted. Enable it again to retry."
        } else if (ready) message = "Capture, then apply filters and edits."
    }

    fun start() {
        val request = ++generation
        ready = false
        future.addListener({
            if (request != generation) return@addListener
            try {
                val cameraProvider = future.get()
                val selector = if (front) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA
                if (!cameraProvider.hasCamera(selector)) {
                    message = "This camera is unavailable. Choose a photo from your library."
                    return@addListener
                }
                val live = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
                val still = ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                    .setTargetRotation(previewView.display?.rotation ?: Surface.ROTATION_0).build()
                provider = cameraProvider; preview = live; capture = still
                val camera = cameraProvider.bindToLifecycle(owner, selector, live, still)
                canFlip = cameraProvider.hasCamera(if (front) CameraSelector.DEFAULT_BACK_CAMERA else CameraSelector.DEFAULT_FRONT_CAMERA)
                cameraState = camera.cameraInfo.cameraState
                cameraState?.observe(owner, stateObserver)
            } catch (error: Exception) {
                Log.e("FoundryCamera", "Camera configuration failed", error)
                message = "Camera could not be started. Enable it again to retry."
                ready = false
            }
        }, main)
    }
    fun stop() {
        generation++
        ready = false; busy = false
        cameraState?.removeObserver(stateObserver); cameraState = null
        val owned = listOfNotNull(preview, capture).toTypedArray()
        if (owned.isNotEmpty()) provider?.unbind(*owned)
        preview = null; capture = null
    }
    fun takePhoto(scope: CoroutineScope, onPhoto: (RasterImage) -> Unit) {
        val still = capture ?: return
        if (!ready || busy) return
        busy = true
        val request = generation
        still.targetRotation = previewView.display?.rotation ?: Surface.ROTATION_0
        still.takePicture(main, object : ImageCapture.OnImageCapturedCallback() {
            override fun onCaptureSuccess(image: ImageProxy) {
                val rotation = image.imageInfo.rotationDegrees
                val data = try {
                    val buffer = image.planes[0].buffer
                    if (buffer.remaining() > 32 * 1024 * 1024) null else ByteArray(buffer.remaining()).also { buffer.get(it) }
                } finally { image.close() }
                if (request != generation) return
                scope.launch {
                    try {
                        val result = withContext(Dispatchers.Default) {
                            if (data == null) Outcome.Err(PhotoDecoder.invalid) else PhotoDecoder.decode(data, rotation)
                        }
                        if (request != generation) return@launch
                        busy = false
                        when (result) {
                            is Outcome.Ok -> onPhoto(result.value)
                            is Outcome.Err -> message = result.error.publicInfo().meta.message
                        }
                    } catch (error: CancellationException) { throw error }
                    catch (error: Exception) {
                        Log.e("FoundryCamera", "Photo conversion failed", error)
                        if (request == generation) { busy = false; message = "This photo could not be opened." }
                    }
                }
            }
            override fun onError(exception: ImageCaptureException) {
                Log.e("FoundryCamera", "Photo capture failed", exception)
                if (request == generation) { busy = false; message = "Photo capture failed. Please try again." }
            }
        })
    }
}
