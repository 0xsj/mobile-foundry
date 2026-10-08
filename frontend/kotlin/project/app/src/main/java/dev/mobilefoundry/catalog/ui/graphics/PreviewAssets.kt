package dev.mobilefoundry.catalog.ui.graphics

import android.content.Context
import android.graphics.BitmapFactory
import dev.mobilefoundry.graphics.*
import dev.mobilefoundry.kernel.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException

/** Catalog-only decoding. Reusable graphics accepts already admitted CPU assets. */
object PreviewAssets {
    /** Code-authored straight-alpha disc and orbit ring; no external asset provenance. */
    fun overlay(): AlphaImage {
        val size = 256
        val bytes = ByteArray(size * size * 4)
        for (y in 0 until size) for (x in 0 until size) {
            val u = (x + .5) / size; val v = (y + .5) / size
            val distance = kotlin.math.hypot(u - .5, v - .5)
            val ring = (1 - kotlin.math.abs(distance - .37) / .035).coerceAtLeast(0.0)
            val disc = ((.26 - kotlin.math.hypot(u - .43, v - .43)) / .018).coerceIn(0.0, 1.0) * .82
            val satellite = ((.09 - kotlin.math.hypot(u - .77, v - .72)) / .012).coerceIn(0.0, 1.0)
            val alpha = maxOf(ring, disc, satellite); val i = (y * size + x) * 4
            bytes[i] = kotlin.math.round(70 + 175 * v).toInt().toByte()
            bytes[i + 1] = kotlin.math.round(120 + 55 * v).toInt().toByte()
            bytes[i + 2] = kotlin.math.round(245 - 100 * v).toInt().toByte()
            bytes[i + 3] = kotlin.math.round(alpha * 255).toInt().toByte()
        }
        return checkNotNull(AlphaImage.create(size, size, bytes))
    }
    suspend fun image(context: Context): AppResult<RasterImage> = withContext(Dispatchers.IO) {
        try {
            val bitmap=context.assets.open("graphics/studio-still-life.png").use { BitmapFactory.decodeStream(it) } ?: return@withContext Outcome.Err(invalid)
            try {
                if(bitmap.width !in 1..4096 || bitmap.height !in 1..4096) return@withContext Outcome.Err(invalid)
                val colors=IntArray(bitmap.width*bitmap.height);bitmap.getPixels(colors,0,bitmap.width,0,0,bitmap.width,bitmap.height)
                val rgba=ByteArray(colors.size*4)
                colors.forEachIndexed { i,c -> rgba[i*4]=(c shr 16).toByte();rgba[i*4+1]=(c shr 8).toByte();rgba[i*4+2]=c.toByte();rgba[i*4+3]=(c ushr 24).toByte() }
                RasterImage.create(bitmap.width,bitmap.height,rgba)?.let { Outcome.Ok(it) } ?: Outcome.Err(invalid)
            } finally { bitmap.recycle() }
        } catch (_:IOException) { Outcome.Err(missing) }
    }
    suspend fun mesh(context: Context): AppResult<PreviewMesh> = withContext(Dispatchers.IO) {
        try {
            val data=context.assets.open("graphics/studio-lamp.json").bufferedReader().use { JSONObject(it.readText()) }
            if(data.getInt("version")!=1 || data.getString("layout")!="position3-normal3-slot1") return@withContext Outcome.Err(invalid)
            val raw=data.getJSONArray("vertices")
            if(raw.length()>700000) return@withContext Outcome.Err(invalid)
            PreviewMesh.create(FloatArray(raw.length()) { raw.getDouble(it).toFloat() })?.let { Outcome.Ok(it) } ?: Outcome.Err(invalid)
        } catch (_:IOException) { Outcome.Err(missing) }
        catch (_:org.json.JSONException) { Outcome.Err(invalid) }
    }
    private val missing=Failure.NotFound(FailureMeta("The bundled preview asset could not be loaded.","graphics.asset-missing"))
    private val invalid=Failure.Invalid(FailureMeta("The preview asset is invalid.","graphics.asset-invalid"),emptyMap())
}
