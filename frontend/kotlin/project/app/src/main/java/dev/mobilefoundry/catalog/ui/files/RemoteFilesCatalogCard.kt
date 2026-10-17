package dev.mobilefoundry.catalog.ui.files

import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.mobilefoundry.catalog.ui.graphics.ImageEditorContent
import dev.mobilefoundry.kernel.*
import dev.mobilefoundry.services.UploadSpec
import dev.mobilefoundry.services.FileStatus
import dev.mobilefoundry.ui.components.display.card.Card
import dev.mobilefoundry.ui.components.forms.button.ActionButton
import dev.mobilefoundry.ui.components.forms.button.ButtonVariant
import dev.mobilefoundry.ui.components.patterns.sectionheader.SectionHeader
import dev.mobilefoundry.ui.theme.FoundryTheme
import kotlinx.coroutines.*
import java.io.ByteArrayOutputStream

@Composable fun RemoteFilesCatalogCard(model: RemoteFilesCatalogModel) {
    val state = model.state.collectAsStateWithLifecycle().value
    val context = LocalContext.current.applicationContext; val scope = rememberCoroutineScope()
    var ticket by remember { mutableLongStateOf(0) }; var importing by remember { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) { val current = ticket; importing = true
            scope.launch {
                try {
                    val prepared = withContext(Dispatchers.IO) {
                        val bytes = context.contentResolver.openInputStream(uri)?.use { input ->
                            val output = ByteArrayOutputStream(); val buffer = ByteArray(8192)
                            while (true) { ensureActive(); val count = input.read(buffer); if (count < 0) break; if (output.size() + count > 10 * 1024 * 1024) return@withContext null; output.write(buffer, 0, count) }
                            output.toByteArray()
                        } ?: return@withContext null
                        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                        val type = bounds.outMimeType ?: return@withContext null
                        val extension = when (type) { "image/jpeg" -> "jpg"; "image/png" -> "png"; "image/webp" -> "webp"; "image/heic", "image/heif" -> "heic"; else -> return@withContext null }
                        when (val spec = UploadSpec.prepare("Photo.$extension", type, bytes)) { is Outcome.Ok -> spec.value to bytes; is Outcome.Err -> null }
                    }
                    ensureActive()
                    if (prepared != null) model.upload(prepared.first, prepared.second, current)
                    else model.importFailed(Failure.Invalid(FailureMeta("Choose a supported photo smaller than 10 MiB."), emptyMap()), current)
                } catch (error: CancellationException) { throw error } catch (_: Exception) { model.importFailed(Failure.Unavailable(FailureMeta("The selected photo could not be opened.")), current) }
                finally { importing = false }
            }
        }
    }
    Card {
        SectionHeader("Account files", "Upload a gallery photo, download it, then open it in the image editor. Maximum 10 MiB per file.")
        ActionButton({ ticket = model.selectionTicket; picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }, enabled = model.canUpload && !importing) { Text("Choose photo to upload") }
        ActionButton({ model.refresh() }, variant = ButtonVariant.SECONDARY, enabled = !state.busy && !importing) { Text("Refresh files") }
        if (state.busy || importing) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (state.pending) {
            Text("An unconfirmed operation is retained for this account while this screen's owner is alive.", style = FoundryTheme.tokens.typography.caption)
            ActionButton({ model.retry() }, variant = ButtonVariant.SECONDARY, enabled = !state.busy && !importing) { Text("Retry same file operation") }
        }
        state.failure?.let { Text(it.meta.message, style = FoundryTheme.tokens.typography.caption) }
        if (state.files.isEmpty() && !state.busy) Text("No files in this account.")
        state.files.forEach { file -> key(file.id) {
            Text(file.spec.name, style = FoundryTheme.tokens.typography.label)
            Text("${file.status.wire} · ${file.spec.byteSize} bytes · v${file.version}", style = FoundryTheme.tokens.typography.caption)
            if (file.status == FileStatus.AVAILABLE && file.spec.contentType.startsWith("image/")) ActionButton({ model.open(file) }, variant = ButtonVariant.QUIET, enabled = !state.busy && !importing) { Text("Download and edit preview") }
            ActionButton({ model.delete(file) }, variant = ButtonVariant.DESTRUCTIVE, enabled = model.canUpload && !importing) { Text("Delete file") }
        } }
        if (state.hasMore) Text("Showing the first 20 files.")
        Text("Editor adjustments are a local preview. Uploading an edited render is a separate export capability.", style = FoundryTheme.tokens.typography.caption)
    }
    state.preview?.let { image -> Dialog(onDismissRequest = model::closePreview, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize()) { Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            TextButton(onClick = model::closePreview) { Text("Close preview") }
            ImageEditorContent(image, startsEdited = true)
        } }
    } }
}
