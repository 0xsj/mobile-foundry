package dev.mobilefoundry.product.screens

import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.mobilefoundry.kernel.*
import dev.mobilefoundry.services.*
import dev.mobilefoundry.session.SessionPhase
import dev.mobilefoundry.product.connection.*
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.*

@Composable fun TodayScreen(model: ConnectedSessionModel) {
    val session by model.controller.state.collectAsState()
    val lease = model.controller.lease
    if (session.phase != SessionPhase.SIGNED_IN || lease == null) {
        Text("Sign in from Account to view your files.", Modifier.padding(24.dp)); return
    }
    key(lease.scope) { FilesContent(model.files) }
}
private data class PhotoDraft(val spec: UploadSpec, val bytes: ByteArray)
@Composable private fun FilesContent(store: FilesStore) {
    val state by store.state.collectAsState()
    val resolver = LocalContext.current.contentResolver
    val owner = rememberCoroutineScope()
    var draft by remember { mutableStateOf<PhotoDraft?>(null) }
    var importing by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<RemoteFile?>(null) }
    var reviewing by remember { mutableStateOf<FileRecoveryReview?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) owner.launch {
            importing = true; draft = null
            try {
                val prepared = withContext(Dispatchers.IO) {
                    val bytes = resolver.openInputStream(uri)?.use { input ->
                        val output = ByteArrayOutputStream(); val buffer = ByteArray(8192)
                        while (true) {
                            ensureActive(); val n = input.read(buffer); if (n < 0) break
                            require(output.size()+n <= 10*1024*1024); output.write(buffer,0,n)
                        }; output.toByteArray()
                    } ?: error("Missing photo")
                    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeByteArray(bytes,0,bytes.size,bounds)
                    val mime = bounds.outMimeType ?: error("Unsupported photo")
                    val extension = mapOf("image/jpeg" to "jpg", "image/png" to "png", "image/webp" to "webp", "image/heic" to "heic", "image/heif" to "heif")[mime] ?: error("Unsupported photo")
                    val result = UploadSpec.prepare("Photo.$extension",mime,bytes)
                    if (result is Outcome.Ok) PhotoDraft(result.value,bytes) else error("Invalid photo")
                }
                draft = prepared
            } catch (error: CancellationException) { throw error }
            catch (_: Exception) { currentCoroutineContext().ensureActive(); store.importFailed(store.selectionTicket) }
            finally { importing = false }
        }
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Text("Account files",style=MaterialTheme.typography.headlineMedium)
        Text("Showing ${state.files.size} files.",Modifier.testTag("files.loaded"))
        Button({ picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },enabled=store.canEdit && !importing,modifier=Modifier.testTag("files.choose")) { Text("Choose photo") }
        if (importing) Text("Preparing photo…")
        draft?.let { photo ->
            Text("${photo.spec.name} · ${photo.spec.byteSize} bytes",Modifier.testTag("files.draft"))
            Button({ store.upload(photo.spec,photo.bytes,store.selectionTicket); draft=null },enabled=store.canEdit && !importing,modifier=Modifier.testTag("files.upload")) { Text("Upload photo") }
            TextButton({ draft=null }) { Text("Discard photo") }
        }
        OutlinedButton({ store.refresh() },enabled=!state.busy,modifier=Modifier.testTag("files.refresh")) { Text("Refresh files") }
        if (state.busy) Text("Updating files…")
        if (state.pending) {
            Text("An operation needs confirmation. Retry keeps the same photo and command.",Modifier.testTag("files.pending"))
            Button({ store.retry() },enabled=!state.busy,modifier=Modifier.testTag("files.retry")) { Text("Retry pending operation") }
            TextButton({ reviewing=store.reviewPending() },enabled=!state.busy,modifier=Modifier.testTag("files.review")) { Text("Review pending operation") }
        }
        if (state.blockedByOtherScope) Text("Return to the original account to settle its pending file operation before another change.")
        state.failure?.let { Text(it.meta.message,color=MaterialTheme.colorScheme.error,modifier=Modifier.testTag("files.failure")) }
        if (state.nextCursor != null) OutlinedButton({ store.loadMore() },enabled=store.canLoadMore) { Text("Load more files") }
        if (state.files.size >= 200) Text("Showing up to 200 files. Refresh for the latest page.")
        if (state.ready && state.files.isEmpty()) Text("Your account has no files.")
        state.files.forEach { file ->
            HorizontalDivider(); Text(file.spec.name)
            Text("${file.spec.byteSize} bytes · ${if (file.status == FileStatus.AVAILABLE) "Available" else "Awaiting upload"}")
            if (file.status == FileStatus.AVAILABLE) TextButton({ store.open(file) },enabled=!state.busy,modifier=Modifier.testTag("files.open.${file.id}")) { Text("Download preview") }
            TextButton({ deleting=file },enabled=store.canEdit,modifier=Modifier.testTag("files.delete.${file.id}")) { Text("Delete file") }
        }
        Text("Photos up to 10 MiB. Accepted operations are saved in private, backup-excluded recovery storage before transfer. Relaunch restores the original operation; retry or review it explicitly. Downloads are checked against the server's size and digest.",style=MaterialTheme.typography.bodySmall)
    }
    deleting?.let { file -> AlertDialog(onDismissRequest={ deleting=null }, title={ Text("Delete this file?") },text={ Text("This removes access for the whole account.") },
        confirmButton={ TextButton({ store.delete(file); deleting=null }) { Text("Confirm delete") } },dismissButton={ TextButton({ deleting=null }) { Text("Cancel") } }) }
    reviewing?.let { review -> AlertDialog(onDismissRequest={ reviewing=null },title={ Text("Forget local recovery?") },text={
        Text("${if (review.deleting) "Delete" else "Upload"}: ${review.name}\n\n${review.consequence} Retry keeps the original command; forgetting requires a fresh file list before another change.")
    },confirmButton={ TextButton({ store.forgetLocalRecovery(review);reviewing=null }) { Text("Forget local recovery") } },
        dismissButton={ TextButton({ reviewing=null }) { Text("Keep pending operation") } }) }
    state.preview?.let { preview ->
        var thumbnail by remember(preview) { mutableStateOf<ImageBitmap?>(null) }
        LaunchedEffect(preview) {
            thumbnail = withContext(Dispatchers.Default) {
                val bytes = preview.bytes()
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds=true }
                BitmapFactory.decodeByteArray(bytes,0,bytes.size,bounds)
                val options = BitmapFactory.Options().apply { inSampleSize=1; while (maxOf(bounds.outWidth,bounds.outHeight)/inSampleSize > 1600) inSampleSize *= 2 }
                BitmapFactory.decodeByteArray(bytes,0,bytes.size,options)?.asImageBitmap()
            }
        }
        AlertDialog(onDismissRequest={ store.closePreview() },title={ Text("Download verified",Modifier.testTag("files.verified")) },text={
            Column { Text(preview.name); thumbnail?.let { Image(it,null,Modifier.fillMaxWidth().heightIn(max=320.dp)) } }
        },confirmButton={ TextButton({ store.closePreview() }) { Text("Close preview") } })
    }
}
