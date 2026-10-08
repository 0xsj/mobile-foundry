package dev.mobilefoundry.graphics

import java.nio.ByteBuffer
import kotlin.math.pow
import kotlin.math.roundToInt

/** Owned top-left straight sRGB RGBA8. Transparent RGB cannot leak into filtering. */
class AlphaImage private constructor(val width: Int, val height: Int, private val rgba: ByteArray) {
    internal fun linearBuffer() = linearBuffer(rgba)
    companion object {
        fun create(width: Int, height: Int, rgba: ByteArray): AlphaImage? =
            if (width in 1..4096 && height in 1..4096 && rgba.size == width * height * 4)
                AlphaImage(width, height, rgba.copyOf()) else null
    }
}

internal fun linearPremultiplied(source: ByteArray): ByteArray {
    val transfer = DoubleArray(256) { value ->
        val c = value / 255.0
        if (c <= .04045) c / 12.92 else ((c + .055) / 1.055).pow(2.4)
    }
    val bytes = source.copyOf()
    for (i in bytes.indices step 4) {
        val alpha = bytes[i + 3].toInt() and 255
        for (channel in 0..2) bytes[i + channel] = (transfer[bytes[i + channel].toInt() and 255] * alpha).roundToInt().toByte()
    }
    return bytes
}
internal fun linearBuffer(source: ByteArray): ByteBuffer = linearPremultiplied(source).let {
    ByteBuffer.allocateDirect(it.size).put(it).apply { position(0) }
}
enum class CompositeBlend(val shaderIndex: Float) { NORMAL(0f), MULTIPLY(1f), SCREEN(2f) }

/** Scale/radius/feather use canvas height; blur uses target pixels. Comparison 1 is original. */
@ConsistentCopyVisibility
data class CompositeSettings private constructor(
    val opacity: Float, val scale: Float, val layerCenter: EffectPoint, val maskCenter: EffectPoint,
    val radius: Float, val feather: Float, val blur: Float, val glow: Float, val comparison: Float,
    val maskEnabled: Boolean, val blend: CompositeBlend
) {
    companion object {
        fun make(opacity: Float = .85f, scale: Float = .65f, layerCenter: EffectPoint = EffectPoint.make(),
                 maskCenter: EffectPoint = EffectPoint.make(), radius: Float = .32f, feather: Float = .12f,
                 blur: Float = 0f, glow: Float = .35f, comparison: Float = .5f,
                 maskEnabled: Boolean = true, blend: CompositeBlend = CompositeBlend.NORMAL): CompositeSettings {
            fun bound(v: Float, lo: Float, hi: Float, fallback: Float) = if (v.isFinite()) v.coerceIn(lo, hi) else fallback
            return CompositeSettings(bound(opacity, 0f, 1f, .85f), bound(scale, .15f, 1f, .65f), layerCenter, maskCenter,
                bound(radius, .05f, .75f, .32f), bound(feather, 0f, .3f, .12f), bound(blur, 0f, 24f, 0f),
                bound(glow, 0f, 1f, .35f), bound(comparison, 0f, 1f, .5f), maskEnabled, blend)
        }
    }
}

/** Byte counts describe texture payload, excluding driver/native depth/display buffers. */
data class GraphicsProfile(val frame: Int, val width: Int, val height: Int, val passes: Int = 4,
    val uploads: Int, val targetAllocations: Int, val inputTextureBytes: Long, val offscreenTextureBytes: Long,
    val cpuEncodeMilliseconds: Double, val gpuMilliseconds: Double?, val gpuTiming: String)
