package dev.mobilefoundry.catalog.ui.camera

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import androidx.exifinterface.media.ExifInterface
import dev.mobilefoundry.graphics.RasterImage
import dev.mobilefoundry.kernel.*
import kotlin.math.roundToInt
import java.io.ByteArrayInputStream
import java.io.IOException

/** CameraX supplies clockwise rotation separately from its JPEG pixel buffer. */
object PhotoDecoder {
    /** Library photos own their EXIF orientation, including reflected orientations. */
    fun decodeLibrary(data: ByteArray): AppResult<RasterImage> {
        val exif = try { ExifInterface(ByteArrayInputStream(data)) } catch (_: IOException) { return Outcome.Err(invalid) }
        return decode(data, exif.rotationDegrees, exif.isFlipped)
    }

    fun decode(data: ByteArray, rotation: Int): AppResult<RasterImage> = decode(data, rotation, false)

    private fun decode(data: ByteArray, rotation: Int, flipped: Boolean): AppResult<RasterImage> {
        if (rotation !in listOf(0, 90, 180, 270)) return Outcome.Err(invalid)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(data, 0, data.size, bounds)
        if (bounds.outWidth !in 1..65535 || bounds.outHeight !in 1..65535) return Outcome.Err(invalid)
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 2048) sample *= 2
        val source = BitmapFactory.decodeByteArray(data, 0, data.size,
            BitmapFactory.Options().apply { inSampleSize = sample; inPreferredConfig = Bitmap.Config.ARGB_8888 })
            ?: return Outcome.Err(invalid)
        try {
            val rotated = Bitmap.createBitmap(source, 0, 0, source.width, source.height,
                Matrix().apply { if (flipped) postScale(-1f, 1f); postRotate(rotation.toFloat()) }, true)
            try {
                val scale = minOf(1f, 2048f / maxOf(rotated.width, rotated.height))
                val width = (rotated.width * scale).roundToInt().coerceAtLeast(1)
                val height = (rotated.height * scale).roundToInt().coerceAtLeast(1)
                val opaque = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                try {
                    Canvas(opaque).apply {
                        drawColor(Color.WHITE)
                        drawBitmap(rotated, null, android.graphics.Rect(0, 0, width, height), Paint(Paint.FILTER_BITMAP_FLAG))
                    }
                    val colors = IntArray(width * height)
                    opaque.getPixels(colors, 0, width, 0, 0, width, height)
                    val rgba = ByteArray(colors.size * 4)
                    colors.forEachIndexed { i, c ->
                        rgba[i * 4] = (c shr 16).toByte(); rgba[i * 4 + 1] = (c shr 8).toByte()
                        rgba[i * 4 + 2] = c.toByte(); rgba[i * 4 + 3] = (-1).toByte()
                    }
                    return RasterImage.create(width, height, rgba)?.let { Outcome.Ok(it) } ?: Outcome.Err(invalid)
                } finally { opaque.recycle() }
            } finally { if (rotated !== source) rotated.recycle() }
        } finally { source.recycle() }
    }
    val invalid = Failure.Invalid(FailureMeta("This photo could not be opened.", "camera.photo-invalid"), emptyMap())
}
