package dev.mobilefoundry.graphics

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI

private fun bounded(value: Float, lower: Float, upper: Float, fallback: Float) = if (value.isFinite()) value.coerceIn(lower, upper) else fallback

/** Owned opaque top-left sRGB RGBA8 pixels. Reference identity is the texture cache key. */
class RasterImage private constructor(val width: Int, val height: Int, private val rgba: ByteArray) {
    internal fun buffer(): ByteBuffer = ByteBuffer.allocateDirect(rgba.size).put(rgba).apply { position(0) }
    companion object {
        fun create(width: Int, height: Int, rgba: ByteArray): RasterImage? =
            if (width in 1..4096 && height in 1..4096 && rgba.size == width * height * 4 &&
                (3 until rgba.size step 4).all { rgba[it] == (-1).toByte() }) RasterImage(width, height, rgba.copyOf()) else null
    }
}

class PreviewMesh private constructor(private val vertices: FloatArray) {
    val vertexCount get() = vertices.size / 7
    internal fun buffer() = ByteBuffer.allocateDirect(vertices.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer().apply { put(vertices); position(0) }
    companion object {
        fun create(vertices: FloatArray): PreviewMesh? {
            if (vertices.isEmpty() || vertices.size % 21 != 0 || vertices.size / 7 > 100000 || vertices.any { !it.isFinite() }) return null
            for (i in vertices.indices step 7) {
                if ((0..2).any { kotlin.math.abs(vertices[i + it]) > 10f } ||
                    (3..5).any { kotlin.math.abs(vertices[i + it]) > 10f } ||
                    (3..5).sumOf { (vertices[i + it] * vertices[i + it]).toDouble() } <= .000001 ||
                    vertices[i + 6] !in listOf(0f, 1f, 2f)) return null
            }
            return PreviewMesh(vertices.copyOf())
        }
    }
}

@ConsistentCopyVisibility
data class ImageAdjustments private constructor(val exposure: Float, val saturation: Float, val vignette: Float) {
    init { require(exposure.isFinite() && exposure in -2f..2f && saturation.isFinite() && saturation in 0f..2f && vignette.isFinite() && vignette in 0f..1f) }
    companion object { fun make(exposure: Float = 0f, saturation: Float = 1f, vignette: Float = 0f) =
        ImageAdjustments(bounded(exposure, -2f, 2f, 0f), bounded(saturation, 0f, 2f, 1f), bounded(vignette, 0f, 1f, 0f)) }
}
@ConsistentCopyVisibility
data class ImageViewport private constructor(val zoom: Float, val x: Float, val y: Float) {
    init { require(zoom.isFinite() && zoom in 1f..4f && x.isFinite() && x in -0.75f..0.75f && y.isFinite() && y in -0.75f..0.75f) }
    fun moved(dx: Float, dy: Float) = make(zoom, x + dx, y + dy)
    fun scaled(factor: Float) = make(zoom * if (factor.isFinite() && factor > 0) factor else 1f, x, y)
    companion object { fun make(zoom: Float = 1f, x: Float = 0f, y: Float = 0f) = ImageViewport(bounded(zoom, 1f, 4f, 1f), bounded(x, -.75f, .75f, 0f), bounded(y, -.75f, .75f, 0f)) }
}
@ConsistentCopyVisibility
data class OrbitCamera private constructor(val yaw: Float, val pitch: Float, val distance: Float) {
    init { require(yaw.isFinite() && pitch.isFinite() && pitch in -0.8f..0.8f && distance.isFinite() && distance in 2.5f..7f) }
    fun rotated(dx: Float, dy: Float) = make(yaw + dx * PI.toFloat() * 2, pitch + dy * PI.toFloat(), distance)
    fun scaled(factor: Float) = make(yaw, pitch, distance / if (factor.isFinite() && factor > 0) factor else 1f)
    companion object {
        fun make(yaw: Float = .35f, pitch: Float = .15f, distance: Float = 4.5f): OrbitCamera {
            val period = PI.toFloat() * 2
            val angle = if (yaw.isFinite()) yaw else .35f
            val wrapped = (angle + PI.toFloat()) % period
            return OrbitCamera((if (wrapped < 0) wrapped + period else wrapped) - PI.toFloat(), bounded(pitch, -.8f, .8f, .15f), bounded(distance, 2.5f, 7f, 4.5f))
        }
    }
}
enum class ProductFinish { PORCELAIN, COBALT, BRONZE }
sealed interface CanvasGesture {
    data class Drag(val x: Float, val y: Float, val dx: Float, val dy: Float) : CanvasGesture
    data class Zoom(val factor: Float) : CanvasGesture
}
sealed interface PreviewContent {
    data class Image(val image: RasterImage, val adjustments: ImageAdjustments = ImageAdjustments.make(), val viewport: ImageViewport = ImageViewport.make(), val comparison: Float = .5f) : PreviewContent
    data class Product(val mesh: PreviewMesh, val camera: OrbitCamera = OrbitCamera.make(), val finish: ProductFinish = ProductFinish.PORCELAIN) : PreviewContent
}
