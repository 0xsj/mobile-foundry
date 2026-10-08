package dev.mobilefoundry.catalog.ui.camera

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.PickVisualMediaRequest
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import dev.mobilefoundry.catalog.R
import dev.mobilefoundry.catalog.ui.graphics.ImageEditorContent
import dev.mobilefoundry.graphics.RasterImage
import dev.mobilefoundry.kernel.*
import dev.mobilefoundry.ui.theme.FoundryTheme
import kotlinx.coroutines.launch

@Composable
fun CameraScreen(photo: RasterImage?, onPhotoChange: (RasterImage?) -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val lifecycle by owner.lifecycle.currentStateAsState()
    val tokens = FoundryTheme.tokens
    val scope = rememberCoroutineScope()
    var front by remember { mutableStateOf(false) }
    var enabled by remember { mutableStateOf(false) }
    var permission by remember { mutableStateOf(false) }
    var denied by remember { mutableStateOf(false) }
    var grid by remember { mutableStateOf(true) }
    var loading by remember { mutableStateOf(false) }
    var pickerPresented by remember { mutableStateOf(false) }
    var importFailure by remember { mutableStateOf<Failure?>(null) }
    val camera = remember(context, owner, front) { CameraCaptureController(context, owner, front) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        permission = it; denied = !it; enabled = it
    }
    val photoLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        pickerPresented = false
        if (uri != null) {
            loading = true; importFailure = null
            scope.launch {
                try {
                    when (val result = PhotoLibraryImporter.load(context.applicationContext, uri)) {
                        is Outcome.Ok -> onPhotoChange(result.value)
                        is Outcome.Err -> importFailure = result.error
                    }
                } finally { loading = false }
            }
        }
    }
    LaunchedEffect(lifecycle) {
        if (lifecycle == Lifecycle.State.RESUMED) {
            permission = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
            if (!permission && enabled) denied = true
        }
    }
    val shouldRun = enabled && permission && lifecycle == Lifecycle.State.RESUMED && photo == null && !pickerPresented && !loading
    DisposableEffect(camera, shouldRun) {
        if (shouldRun) camera.start()
        onDispose { camera.stop() }
    }
    if (photo != null) {
        Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(tokens.space.stack)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically) {
                Text("Edit photo", style = tokens.typography.title)
                TextButton(onClick = { onPhotoChange(null) }) { Text("Retake") }
            }
            ImageEditorContent(photo, startsEdited = true)
        }
    } else {
        Column(modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(tokens.space.stack)) {
            Text("Camera", style = tokens.typography.title)
            Box(Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(tokens.shape.panel))
                .background(Color.Black), contentAlignment = Alignment.Center) {
                if (shouldRun) AndroidView(factory = { camera.previewView }, modifier = Modifier.fillMaxSize())
                if (camera.ready && grid) Canvas(Modifier.fillMaxSize()) {
                    for (fraction in listOf(1f / 3, 2f / 3)) {
                        drawLine(Color.White.copy(alpha = .3f), Offset(size.width * fraction, 0f), Offset(size.width * fraction, size.height))
                        drawLine(Color.White.copy(alpha = .3f), Offset(0f, size.height * fraction), Offset(size.width, size.height * fraction))
                    }
                }
                if (!camera.ready) Column(Modifier.padding(tokens.space.page),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(tokens.space.stack)) {
                    Icon(painterResource(R.drawable.ic_tab_camera), null, Modifier.size(48.dp), tint = Color.White)
                    Text(if (denied) "Camera access is off. Enable it in Settings or choose a photo."
                        else if (enabled) camera.message else "Enable the camera to take a photo.", color = Color.White)
                    Button(onClick = {
                        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                            permission = true; enabled = true
                            if (shouldRun) { camera.stop(); camera.start() }
                        } else launcher.launch(Manifest.permission.CAMERA)
                    }, enabled = !loading) { Text("Enable camera") }
                    if (denied) TextButton(onClick = {
                        context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")))
                    }) { Text("Open Settings") }
                }
            }
            Text(if (camera.ready) camera.message else "PHOTO", style = tokens.typography.caption)
            if (loading) LinearProgressIndicator(Modifier.fillMaxWidth().semantics { contentDescription = "Opening photo" })
            importFailure?.let { Text(it.publicInfo().meta.message) }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = {
                    pickerPresented = true; importFailure = null
                    try {
                        photoLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    } catch (error: Exception) {
                        Log.e("FoundryPhotos", "Photo picker could not open", error)
                        pickerPresented = false
                        importFailure = Failure.Unavailable(FailureMeta("The photo picker could not be opened. Please try again."))
                    }
                }, enabled = !loading && !camera.busy && !pickerPresented) { Text("Choose photo") }
                FilledIconButton(onClick = { camera.takePhoto(scope) { onPhotoChange(it) } },
                    enabled = camera.ready && !camera.busy && !loading && !pickerPresented,
                    modifier = Modifier.size(70.dp).semantics { contentDescription = "Take photo" },
                    shape = CircleShape) { Icon(painterResource(R.drawable.ic_tab_camera), null) }
                TextButton(onClick = { front = !front }, enabled = camera.ready && camera.canFlip && !camera.busy) { Text("Flip camera") }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Viewfinder grid", modifier = Modifier.weight(1f))
                Switch(grid, onCheckedChange = { grid = it }, modifier = Modifier.semantics { contentDescription = "Viewfinder grid" })
            }
        }
    }
}
