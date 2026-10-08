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
