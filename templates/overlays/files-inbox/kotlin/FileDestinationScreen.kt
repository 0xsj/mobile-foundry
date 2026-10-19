package dev.mobilefoundry.product.screens

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.mobilefoundry.kernel.FailureKind
import dev.mobilefoundry.product.connection.FileDestinationStore
import dev.mobilefoundry.services.FileStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable fun FileDestinationScreen(store: FileDestinationStore) {
    val state by store.state.collectAsStateWithLifecycle()
    if (!state.presented) return
    Dialog(onDismissRequest = store::close) {
        Surface(shape = MaterialTheme.shapes.large) {
            Column(Modifier.fillMaxWidth().heightIn(max = 600.dp).verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("File details", style = MaterialTheme.typography.headlineSmall)
                if (state.busy) CircularProgressIndicator()
                state.file?.let { file ->
                    Text(file.spec.name, Modifier.testTag("destination.name"))
                    Text("${file.spec.byteSize} bytes · ${file.spec.contentType}")
                    Text(if (file.status == FileStatus.AVAILABLE) "Available" else "Awaiting upload")
                    if (file.status == FileStatus.AVAILABLE) TextButton(store::download, enabled = !state.busy, modifier = Modifier.testTag("destination.download")) { Text("Download preview") }
                }
                state.failure?.let { Text(if (it.kind == FailureKind.NOT_FOUND) "This file is no longer available." else it.meta.message, Modifier.testTag("destination.failure")) }
                state.bytes?.let { bytes ->
                    var thumbnail by remember(bytes) { mutableStateOf<ImageBitmap?>(null) }
                    LaunchedEffect(bytes) {
                        thumbnail = withContext(Dispatchers.Default) {
                            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                            val options = BitmapFactory.Options().apply { inSampleSize = 1; while (maxOf(bounds.outWidth, bounds.outHeight) / inSampleSize > 1600) inSampleSize *= 2 }
                            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)?.asImageBitmap()
                        }
                    }
                    Text("Download verified", Modifier.testTag("destination.verified"))
                    thumbnail?.let { Image(it, null, Modifier.fillMaxWidth().heightIn(max = 260.dp)) }
                }
                TextButton(store::refresh, enabled = !state.busy, modifier = Modifier.testTag("destination.refresh")) { Text("Refresh file") }
                Text("Opening a notification leaves it unread. File access is checked again; a notice does not guarantee availability.", style = MaterialTheme.typography.bodySmall)
                TextButton(store::close) { Text("Close file") }
            }
        }
    }
}
