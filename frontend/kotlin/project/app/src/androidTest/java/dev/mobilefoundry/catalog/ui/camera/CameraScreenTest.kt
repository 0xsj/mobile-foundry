package dev.mobilefoundry.catalog.ui.camera

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import android.view.View
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraState
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.runtime.CompositionLocalProvider
import androidx.core.app.ActivityOptionsCompat
import androidx.exifinterface.media.ExifInterface
import androidx.test.platform.app.InstrumentationRegistry
import dev.mobilefoundry.catalog.FoundryCatalogRoot
import dev.mobilefoundry.graphics.gl.PreviewGLView
import dev.mobilefoundry.kernel.Outcome
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import kotlinx.coroutines.runBlocking
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.math.abs

class CameraScreenTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private fun tab(label: String) = compose.onNode(hasText(label) and hasClickAction())
    private fun waitFor(text: String) = compose.waitUntil(15_000) {
        compose.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty()
    }
    private fun surface(): PreviewGLView? {
        fun find(view: View): PreviewGLView? {
            if (view is PreviewGLView) return view
            if (view is ViewGroup) for (i in 0 until view.childCount) find(view.getChildAt(i))?.let { return it }
            return null
        }
        var result: PreviewGLView? = null
        compose.runOnIdle { result = find(compose.activity.window.decorView) }
        return result
    }
    private fun pixels(): IntArray {
        val view = checkNotNull(surface())
        val bitmap = Bitmap.createBitmap(96, 80, Bitmap.Config.ARGB_8888)
        var status = PixelCopy.ERROR_SOURCE_NO_DATA
        for (attempt in 0 until 50) {
            val done = CountDownLatch(1)
            PixelCopy.request(view.holder.surface, bitmap, { status = it; done.countDown() }, Handler(Looper.getMainLooper()))
            assertTrue(done.await(5, TimeUnit.SECONDS))
            if (status != PixelCopy.ERROR_SOURCE_NO_DATA) break
            Thread.sleep(20)
        }
        assertEquals(PixelCopy.SUCCESS, status)
        return IntArray(96 * 80).also { bitmap.getPixels(it, 0, 96, 0, 0, 96, 80); bitmap.recycle() }
    }
    private fun chroma(pixels: IntArray): Double {
        // Sample the photograph, excluding the intentionally tinted letterbox background.
        var total = 0L
        for (y in 20 until 60) for (x in 24 until 72) {
            val pixel = pixels[y * 96 + x]
            total += abs((pixel shr 16 and 255) - (pixel shr 8 and 255)) +
                abs((pixel shr 8 and 255) - (pixel and 255))
        }
        return total.toDouble() / (40 * 48)
    }

    /** Stub only the OS result; real URI I/O, decode, editor and GPU output still execute. */
    private fun galleryContent(vararg results: Uri?) {
        val pending = results.iterator()
        val registry = object : ActivityResultRegistry() {
            override fun <I, O> onLaunch(requestCode: Int, contract: ActivityResultContract<I, O>, input: I,
                options: ActivityOptionsCompat?) {
                assertTrue(contract is ActivityResultContracts.PickVisualMedia)
                val uri = pending.next()
                Handler(Looper.getMainLooper()).post {
                    dispatchResult(requestCode, if (uri == null) Activity.RESULT_CANCELED else Activity.RESULT_OK,
                        uri?.let { Intent().setData(it) })
                }
            }
        }
        val owner = object : ActivityResultRegistryOwner { override val activityResultRegistry = registry }
        compose.setContent {
            CompositionLocalProvider(LocalActivityResultRegistryOwner provides owner) { FoundryCatalogRoot() }
        }
    }

    @Test fun galleryReusesRealFiltersEditsAndReleasesRendererAcrossTabs() {
        val file = File.createTempFile("gallery-", ".png", compose.activity.cacheDir)
        compose.activity.assets.open("graphics/studio-still-life.png").use { input -> file.outputStream().use { input.copyTo(it) } }
        galleryContent(Uri.fromFile(file))
        tab("Camera").performClick()
        compose.onNodeWithContentDescription("Take photo").assertIsNotEnabled()
        compose.onNodeWithText("Choose photo").performClick()
        waitFor("Submitted:")
        compose.onNodeWithTag("preview-canvas").performScrollTo()
        val original = pixels()
        assertTrue(chroma(original) > 3)
        compose.onNodeWithText("Mono", substring = false).performScrollTo().performClick()
        compose.onNodeWithTag("preview-canvas").performScrollTo()
        compose.waitUntil(5_000) { chroma(pixels()) < 2 }
        compose.onNodeWithText("Vivid", substring = false).performScrollTo().performClick()
        compose.onNodeWithTag("preview-canvas").performScrollTo()
        compose.waitUntil(5_000) { chroma(pixels()) > 3 }
        compose.onNodeWithContentDescription("Exposure").performScrollTo()
            .performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.SetProgress) { it(1f) }
        tab("Home").performClick()
        assertNull(surface())
        tab("Camera").performClick()
        waitFor("Submitted:")
        compose.onNodeWithText("Retake").performScrollTo().performClick()
        compose.onNodeWithText("Enable camera").assertIsDisplayed()
        compose.onNodeWithContentDescription("Take photo").assertIsNotEnabled()
        file.delete()
    }

    @Test fun galleryCancellationAndBadPhotoLeaveTheViewfinderUsable() {
        val invalid = File.createTempFile("gallery-bad-", ".png", compose.activity.cacheDir).apply { writeBytes(byteArrayOf(1, 2, 3)) }
        galleryContent(null, Uri.fromFile(invalid))
        tab("Camera").performClick()
        compose.onNodeWithText("Choose photo").performClick()
        compose.onNodeWithText("Choose photo").assertIsEnabled()
        compose.onNodeWithText("Edit photo").assertDoesNotExist()
        compose.onNodeWithText("Choose photo").performClick()
        waitFor("This photo could not be opened.")
        compose.onNodeWithText("Choose photo").assertIsEnabled()
        compose.onNodeWithContentDescription("Take photo").assertIsNotEnabled()
        invalid.delete()
    }

    @Test fun galleryReadsExifOrientationAndReportsLostUriAccess() = runBlocking {
        val file = File.createTempFile("gallery-exif-", ".jpg", compose.activity.cacheDir)
        val bitmap = Bitmap.createBitmap(300, 150, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(0xff885522.toInt())
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 95, it) }; bitmap.recycle()
        try {
            for (orientation in listOf(ExifInterface.ORIENTATION_ROTATE_90, ExifInterface.ORIENTATION_TRANSVERSE)) {
                ExifInterface(file).apply { setAttribute(ExifInterface.TAG_ORIENTATION, orientation.toString()); saveAttributes() }
                val result = PhotoLibraryImporter.load(compose.activity, Uri.fromFile(file)) as Outcome.Ok
                assertEquals(150, result.value.width); assertEquals(300, result.value.height)
            }
        } finally { file.delete() }
        assertTrue(PhotoLibraryImporter.load(compose.activity, Uri.fromFile(file)) is Outcome.Err)
    }

    @Test fun decoderBoundsRotatesFlattensAndRefusesMalformedInput() {
        val bitmap = Bitmap.createBitmap(3000, 1500, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(0x66884422)
        val bytes = ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
        bitmap.recycle()
        val result = PhotoDecoder.decode(bytes, 90) as Outcome.Ok
        assertTrue(result.value.width <= 2048 && result.value.height <= 2048)
        assertEquals(result.value.width * 2, result.value.height)
        assertTrue(PhotoDecoder.decode(byteArrayOf(1, 2, 3), 0) is Outcome.Err)
        assertTrue(PhotoDecoder.decode(bytes, 45) is Outcome.Err)
    }

    @Test fun virtualCameraCapturesAndUnbindsWhenLeavingTheTab() {
        assumeTrue("Only exercise synthetic emulator cameras", Build.VERSION.SDK_INT >= 28 &&
            (Build.FINGERPRINT.contains("generic") || Build.MODEL.contains("sdk")))
        InstrumentationRegistry.getInstrumentation().uiAutomation.grantRuntimePermission(compose.activity.packageName, Manifest.permission.CAMERA)
        compose.setContent { FoundryCatalogRoot() }
        tab("Camera").performClick()
        compose.onNodeWithText("Enable camera").performClick()
        compose.waitUntil(20_000) {
            compose.onAllNodes(hasContentDescription("Take photo") and isEnabled()).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithContentDescription("Take photo").performClick()
        waitFor("Edit photo")
        waitFor("Submitted:")
        compose.onNodeWithText("Retake").performScrollTo().performClick()
        compose.waitUntil(20_000) {
            compose.onAllNodes(hasContentDescription("Take photo") and isEnabled()).fetchSemanticsNodes().isNotEmpty()
        }
        tab("Library").performClick()
        val provider = ProcessCameraProvider.getInstance(compose.activity).get(10, TimeUnit.SECONDS)
        compose.waitUntil(10_000) { provider.availableCameraInfos.all { it.cameraState.value?.type == CameraState.Type.CLOSED } }
        tab("Library").assertIsSelected()
    }
}
