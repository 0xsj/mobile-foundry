package dev.mobilefoundry.graphics

import dev.mobilefoundry.kernel.Failure
import kotlin.math.floor
import java.util.Collections

enum class EffectKind(val shaderIndex: Float) {
    RIPPLE(0f), ORBIT(1f), FLOW(2f), MATERIAL(3f), LIQUID(4f), PARTICLES(5f), FIELD(6f)
}
enum class EffectQuality(val framesPerSecond: Int, val steps: Int, private val scale: Double, private val cap: Double) {
    ECONOMY(30, 32, .5, 900.0), BALANCED(60, 64, .75, 1600.0);
    fun resolution(width: Double, height: Double): Pair<Int, Int>? {
        if (!width.isFinite() || !height.isFinite() || width <= 0 || height <= 0) return null
        val factor = minOf(scale, cap / maxOf(width, height))
        return maxOf(1, floor(width * factor).toInt()) to maxOf(1, floor(height * factor).toInt())
    }
}

@ConsistentCopyVisibility
data class EffectPoint private constructor(val x: Float, val y: Float) {
    init { require(x.isFinite() && y.isFinite() && x in 0f..1f && y in 0f..1f) }
    companion object {
        fun make(x: Float = .5f, y: Float = .5f) = EffectPoint(
            if (x.isFinite()) x.coerceIn(0f, 1f) else .5f,
            if (y.isFinite()) y.coerceIn(0f, 1f) else .5f)
    }
}

/** Supplied scalar density contribution; no sensor or physical simulation semantics. */
@ConsistentCopyVisibility
data class EffectFieldSample private constructor(val point: EffectPoint, val weight: Float, val radius: Float) {
    companion object {
        fun make(point: EffectPoint, weight: Float = 1f, radius: Float = .18f) = EffectFieldSample(point,
            if (weight.isFinite()) weight.coerceIn(0f, 1f) else 0f,
            if (radius.isFinite()) radius.coerceIn(.03f, .5f) else .18f)
    }
}

@ConsistentCopyVisibility
data class EffectSettings private constructor(val effect: EffectKind, val quality: EffectQuality, val strength: Float,
                                             val point: EffectPoint, val progress: Float, val samples: List<EffectFieldSample>) {
    companion object {
        const val MAXIMUM_FIELD_SAMPLES = 12
        fun make(effect: EffectKind = EffectKind.RIPPLE, quality: EffectQuality = EffectQuality.BALANCED,
                 strength: Float = .65f, point: EffectPoint = EffectPoint.make(),
                 progress: Float = .5f, samples: List<EffectFieldSample> = emptyList()) =
            EffectSettings(effect, quality, if (strength.isFinite()) strength.coerceIn(0f, 1f) else .65f, point,
                if (progress.isFinite()) progress.coerceIn(0f, 1f) else .5f,
                Collections.unmodifiableList(ArrayList(samples.take(MAXIMUM_FIELD_SAMPLES))))
    }
}

data class EffectStatistics(val submittedFrames: Int, val submissionsPerSecond: Double, val width: Int, val height: Int)
sealed interface EffectEvent {
    data class Ready(val backend: String) : EffectEvent
    data class Statistics(val value: EffectStatistics) : EffectEvent
    data class Failed(val failure: Failure) : EffectEvent
}
