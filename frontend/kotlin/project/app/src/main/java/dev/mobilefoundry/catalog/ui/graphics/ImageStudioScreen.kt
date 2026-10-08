package dev.mobilefoundry.catalog.ui.graphics

import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import dev.mobilefoundry.graphics.*
import dev.mobilefoundry.kernel.*
import dev.mobilefoundry.ui.theme.FoundryTheme
import kotlinx.coroutines.CancellationException

@Composable
fun ImageStudioScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current.applicationContext
    var image by remember { mutableStateOf<RasterImage?>(null) }
    var failure by remember { mutableStateOf<Failure?>(null) }
    var attempt by remember { mutableIntStateOf(0) }
    val tokens = FoundryTheme.tokens
    LaunchedEffect(attempt) {
        try {
            when (val result = PreviewAssets.image(context)) {
                is Outcome.Ok -> image = result.value
                is Outcome.Err -> failure = result.error
            }
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) {
            Log.e("FoundryGraphics", "Unexpected asset error", e)
            failure = Failure.Internal(FailureMeta("Asset loading failed."))
        }
    }
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(tokens.space.stack)) {
        TextButton(onClick = onBack) { Text("Back") }
        Text("Image studio", style = tokens.typography.title)
        val problem = failure
        val asset = image
        if (problem != null) {
            Text(problem.publicInfo().meta.message)
            Button(onClick = { failure = null; attempt++ }) { Text("Try again") }
        } else if (asset != null) {
            key(asset) { ImageEditorContent(asset) }
        } else CircularProgressIndicator()
    }
}
