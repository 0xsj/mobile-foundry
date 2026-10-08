package dev.mobilefoundry.graphics

import java.io.File
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class PreviewValuesTest {
    private fun fixture() = Json.parseToJsonElement(File(System.getProperty("foundry.graphics.fixtures"),"previews.json").readText()).jsonObject
    private fun JsonElement.values() = jsonArray.map { it.jsonPrimitive.float }.toFloatArray()
    @Test fun previewInputsAdmitBoundedValuesAcrossPlatforms() {
        for (entry in fixture().getValue("adjustments").jsonArray) {
            val r=entry.jsonObject;val i=r.getValue("input").values();val v=ImageAdjustments.make(i[0],i[1],i[2])
            assertArrayEquals(r.getValue("expected").values(),floatArrayOf(v.exposure,v.saturation,v.vignette),0f)
        }
        for (entry in fixture().getValue("viewports").jsonArray) {
            val r=entry.jsonObject;val i=r.getValue("input").values();val v=ImageViewport.make(i[0],i[1],i[2])
            assertArrayEquals(r.getValue("expected").values(),floatArrayOf(v.zoom,v.x,v.y),0f)
        }
        for (entry in fixture().getValue("cameras").jsonArray) {
            val r=entry.jsonObject;val i=r.getValue("input").values();val v=OrbitCamera.make(i[0],i[1],i[2])
            assertArrayEquals(r.getValue("expected").values(),floatArrayOf(v.yaw,v.pitch,v.distance),.00001f)
        }
        assertEquals(ImageAdjustments.make(),ImageAdjustments.make(Float.NaN,Float.POSITIVE_INFINITY,Float.NaN))
        assertEquals(ImageViewport.make(),ImageViewport.make(Float.NaN,Float.POSITIVE_INFINITY,Float.NaN))
        assertEquals(OrbitCamera.make(),OrbitCamera.make(Float.NaN,Float.POSITIVE_INFINITY,Float.NaN))
        assertEquals(ImageViewport.make(),ImageViewport.make().scaled(-1f))
        assertEquals(OrbitCamera.make(),OrbitCamera.make().scaled(0f))
        assertEquals(ImageViewport.make(x=.75f,y=-.75f),ImageViewport.make().moved(9f,-9f))
    }
    @Test fun previewAssetsRejectMalformedDataAndOwnTheirBytes() {
        val rows=fixture().getValue("meshes").jsonArray
        for (entry in rows) { val r=entry.jsonObject;assertEquals(r.getValue("name").jsonPrimitive.content,r.getValue("valid").jsonPrimitive.boolean,PreviewMesh.create(r.getValue("vertices").values())!=null) }
        val bytes=byteArrayOf(80,120,-96,-1);val image=RasterImage.create(1,1,bytes)!!;bytes[0]=0
        assertEquals(80,image.buffer().get(0).toInt())
        assertNull(RasterImage.create(1,1,byteArrayOf(0,0,0,0)))
        assertNull(RasterImage.create(4097,1,byteArrayOf()))
        assertNull(RasterImage.create(0,1,byteArrayOf()))
        assertNull(RasterImage.create(1,1,byteArrayOf()))
        val vertices=rows[0].jsonObject.getValue("vertices").values();val mesh=PreviewMesh.create(vertices)!!;vertices[0]=99f
        assertEquals(-1f,mesh.buffer().get(0),0f);vertices[0]=Float.NaN;assertNull(PreviewMesh.create(vertices))
    }
}
