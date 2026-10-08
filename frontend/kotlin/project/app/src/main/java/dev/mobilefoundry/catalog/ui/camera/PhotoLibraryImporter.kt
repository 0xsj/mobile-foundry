package dev.mobilefoundry.catalog.ui.camera

import android.content.Context
import android.net.Uri
import android.util.Log
import dev.mobilefoundry.graphics.RasterImage
import dev.mobilefoundry.kernel.*
import kotlinx.coroutines.*
import java.io.ByteArrayOutputStream

object PhotoLibraryImporter {
    suspend fun load(context: Context, uri: Uri): AppResult<RasterImage> = withContext(Dispatchers.IO) {
        try {
            val bytes = context.contentResolver.openInputStream(uri)?.use { input ->
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                while (true) {
                    ensureActive()
                    val count = input.read(buffer)
                    if (count < 0) break
                    if (output.size() + count > 32 * 1024 * 1024) {
                        return@withContext Outcome.Err(Failure.Invalid(FailureMeta(
                            "Choose a photo smaller than 32 MB.", "photo.too-large"), emptyMap()))
                    }
                    output.write(buffer, 0, count)
                }
                output.toByteArray()
            } ?: return@withContext Outcome.Err(PhotoDecoder.invalid)
            val result = withContext(Dispatchers.Default) { PhotoDecoder.decodeLibrary(bytes) }
            ensureActive()
            result
        } catch (error: CancellationException) { throw error }
        catch (error: Exception) {
            Log.e("FoundryPhotos", "Photo import failed", error)
            Outcome.Err(Failure.Unavailable(FailureMeta(
                "This photo could not be opened. Please choose another.", "photo.import")))
        }
    }
}
