package dev.mobilefoundry.physics

import kotlin.math.*

data class PhysicsVector(val x: Double = 0.0, val y: Double = 0.0) {
    val length get() = hypot(x, y)
    val isFinite get() = x.isFinite() && y.isFinite()
    operator fun plus(b: PhysicsVector) = PhysicsVector(x + b.x, y + b.y)
    operator fun minus(b: PhysicsVector) = PhysicsVector(x - b.x, y - b.y)
    operator fun times(b: Double) = PhysicsVector(x * b, y * b)
    fun dot(b: PhysicsVector) = x * b.x + y * b.y
}
enum class ThrowTarget { HOOP, LANDING_ZONE }
enum class ThrowPhase { READY, FLYING, SETTLED }
@ConsistentCopyVisibility
data class PhysicsSettings private constructor(val gravity: Double, val restitution: Double) {
    companion object {
        fun make(gravity: Double = 9.81, restitution: Double = .65) = PhysicsSettings(
            if (gravity.isFinite()) gravity.coerceIn(4.0, 20.0) else 9.81,
            if (restitution.isFinite()) restitution.coerceIn(0.0, .9) else .65)
    }
}

/** Metres, +Y up. Immutable snapshots; scheduling, pause and drawing belong to the caller. */
@ConsistentCopyVisibility
data class ThrowSimulation private constructor(
    val target: ThrowTarget, val settings: PhysicsSettings,
    val position: PhysicsVector = launchPosition, val velocity: PhysicsVector = PhysicsVector(),
    val phase: ThrowPhase = ThrowPhase.READY, val shots: Int = 0, val scores: Int = 0, val scored: Boolean = false,
    private val accumulator: Double = 0.0, private val flightTime: Double = 0.0,
    private val restTime: Double = 0.0, private val firstLanding: Boolean = false,
) {
    companion object {
        const val width = 10.0; const val height = 7.0
        const val ballRadius = .22; const val rimRadius = .09
        val hoopCenter = PhysicsVector(7.75, 3.3)
        const val hoopHalfWidth = .65
        const val zoneMin = 6.5; const val zoneMax = 8.5
        val launchPosition = PhysicsVector(1.15, .65)
        const val timeStep = 1.0 / 120.0
        fun make(target: ThrowTarget = ThrowTarget.HOOP, settings: PhysicsSettings = PhysicsSettings.make()) = ThrowSimulation(target, settings)
        fun aimVelocity(displacement: PhysicsVector): PhysicsVector {
            if (!displacement.isFinite || !displacement.length.isFinite()) return PhysicsVector()
            return displacement * min(2.8, 14 / max(1e-12, displacement.length))
        }
    }
    fun resetBall() = copy(position = launchPosition, velocity = PhysicsVector(), phase = ThrowPhase.READY,
        scored = false, accumulator = 0.0, flightTime = 0.0, restTime = 0.0, firstLanding = false)
    fun configure(target: ThrowTarget, settings: PhysicsSettings) = resetBall().copy(target = target, settings = settings,
        shots = if (this.target == target) shots else 0, scores = if (this.target == target) scores else 0)
    fun launch(velocity: PhysicsVector): ThrowSimulation {
        if (phase != ThrowPhase.READY || !velocity.isFinite || !velocity.length.isFinite() || velocity.length < .2) return this
        return copy(velocity = if (velocity.length > 14) velocity * (14 / velocity.length) else velocity,
            phase = ThrowPhase.FLYING, shots = shots + 1)
    }
    /** 120 Hz semi-implicit Euler, circle contacts and normal impulses. Stalls beyond 100 ms are discarded. */
    fun advance(elapsed: Double): ThrowSimulation {
        if (phase != ThrowPhase.FLYING || !elapsed.isFinite() || elapsed <= 0) return this
        var result = copy(accumulator = accumulator + min(.1, elapsed))
        var steps = 0
        while (result.accumulator + 1e-12 >= timeStep && steps < 12 && result.phase == ThrowPhase.FLYING) {
            val remainder = max(0.0, result.accumulator - timeStep)
            result = result.step().copy(accumulator = remainder); steps++
        }
        return result
    }
    fun trajectory(velocity: PhysicsVector): List<PhysicsVector> {
        var preview = launch(velocity)
        if (preview.phase != ThrowPhase.FLYING || phase != ThrowPhase.READY) return emptyList()
        val points = mutableListOf(preview.position)
        repeat(48) {
            preview = preview.advance(timeStep * 6); points.add(preview.position)
            if (preview.position.y <= ballRadius + .01 || preview.scored) return points.toList()
        }
        return points.toList()
    }
    private fun step(): ThrowSimulation {
        val previous = position
        var v = velocity + PhysicsVector(y = -settings.gravity * timeStep)
        var p = position + v * timeStep
        var made = scored; var points = scores; var landed = firstLanding
        fun bounce(normal: PhysicsVector) {
            val incoming = v.dot(normal)
            if (incoming < 0) v -= normal * ((1 + settings.restitution) * incoming)
        }
        fun collide(center: PhysicsVector, radius: Double): Boolean {
            val offset = p - center; val distance = offset.length; val separation = ballRadius + radius
            if (distance >= separation) return false
            val normal = if (distance > 1e-9) offset * (1 / distance) else PhysicsVector(y = 1.0)
            p = center + normal * separation; bounce(normal); return true
        }
        fun score() { made = true; points++ }
        if (p.x < ballRadius) { p = p.copy(x = ballRadius); bounce(PhysicsVector(x = 1.0)) }
        if (p.x > width - ballRadius) { p = p.copy(x = width - ballRadius); bounce(PhysicsVector(x = -1.0)) }
        if (p.y > height - ballRadius) { p = p.copy(y = height - ballRadius); bounce(PhysicsVector(y = -1.0)) }
        if (target == ThrowTarget.HOOP) {
            var hitRim = false
            for (x in listOf(hoopCenter.x - hoopHalfWidth, hoopCenter.x + hoopHalfWidth)) {
                hitRim = collide(PhysicsVector(x, hoopCenter.y), rimRadius) || hitRim
            }
            collide(PhysicsVector(hoopCenter.x + hoopHalfWidth + .35, p.y.coerceIn(hoopCenter.y - .15, hoopCenter.y + 1.3)), .07)
            if (!made && !hitRim && previous.y > hoopCenter.y && p.y <= hoopCenter.y && v.y < 0) {
                val fraction = (previous.y - hoopCenter.y) / (previous.y - p.y)
                val crossingX = previous.x + (p.x - previous.x) * fraction
                if (abs(crossingX - hoopCenter.x) < hoopHalfWidth - rimRadius - ballRadius) score()
            }
        }
        if (p.y < ballRadius) {
            if (target == ThrowTarget.LANDING_ZONE && !landed && !made && p.x - ballRadius >= zoneMin && p.x + ballRadius <= zoneMax) score()
            landed = true; p = p.copy(y = ballRadius); bounce(PhysicsVector(y = 1.0))
            v = PhysicsVector(v.x * .985, if (abs(v.y) < .35) 0.0 else v.y)
        }
        val rest = if (p.y <= ballRadius + .001 && v.length < .25) restTime + timeStep else 0.0
        val flight = flightTime + timeStep; val settled = rest >= .3 || flight >= 12
        return copy(position = p, velocity = if (settled) PhysicsVector() else v,
            phase = if (settled) ThrowPhase.SETTLED else ThrowPhase.FLYING,
            scored = made, scores = points, firstLanding = landed, restTime = rest, flightTime = flight)
    }
}
