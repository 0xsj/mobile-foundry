package dev.mobilefoundry.graphics

import java.io.File
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class CompositorValuesTest {
    private fun fixtures() = Json.parseToJsonElement(File(System.getProperty("foundry.graphics.fixtures"), "compositor.json").readText()).jsonObject
    @Test fun sharedBoundsAndPremultipliedLinearPixels() {
        for (row in fixtures().getValue("settings").jsonArray) {
            val r = row.jsonObject; val i = r.getValue("input").jsonArray.map { it.jsonPrimitive.float }
            val s = CompositeSettings.make(opacity=i[0], scale=i[1], radius=i[2], feather=i[3], blur=i[4], glow=i[5], comparison=i[6])
            assertEquals(r.getValue("expected").jsonArray.map { it.jsonPrimitive.float }, listOf(s.opacity,s.scale,s.radius,s.feather,s.blur,s.glow,s.comparison))
        }
        for (row in fixtures().getValue("pixels").jsonArray) {
            val r = row.jsonObject
            val input = r.getValue("input").jsonArray.map { it.jsonPrimitive.int.toByte() }.toByteArray()
            assertEquals(r.getValue("expected").jsonArray.map { it.jsonPrimitive.int }, linearPremultiplied(input).map { it.toInt() and 255 })
        }
        assertEquals(CompositeSettings.make(), CompositeSettings.make(opacity=Float.NaN,scale=Float.POSITIVE_INFINITY,radius=Float.NaN,feather=Float.NaN,blur=Float.POSITIVE_INFINITY,glow=Float.NaN,comparison=Float.NaN))
        assertEquals(listOf(0f,1f,2f), CompositeBlend.entries.map { it.shaderIndex })
    }
    @Test fun transparentAssetsOwnBytesAndRetainIdentity() {
        val bytes = byteArrayOf(-1, 0, -56, -128)
        val a = AlphaImage.create(1,1,bytes)!!; bytes[0]=0
        assertEquals(128, a.linearBuffer().get(0).toInt() and 255)
        assertNull(AlphaImage.create(0,1,bytes)); assertNull(AlphaImage.create(4097,1,bytes)); assertNull(AlphaImage.create(1,1,byteArrayOf()))
        val base = RasterImage.create(1,1,byteArrayOf(-128,-128,-128,-1))!!
        assertEquals(PreviewContent.Composite(base,a),PreviewContent.Composite(base,a))
        assertNotEquals(PreviewContent.Composite(base,a),PreviewContent.Composite(base,AlphaImage.create(1,1,bytes)!!))
    }
}
