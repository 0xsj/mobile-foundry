package dev.mobilefoundry.graphics

/** Active monotonic-frame deltas only. A pause clears the baseline, not the phase. */
class EffectClock {
    var elapsed: Double = 0.0
        private set
    private var previous: Double? = null
    fun suspend() { previous = null }
    fun frame(now: Double, running: Boolean): Double {
        if (!running || !now.isFinite()) { suspend(); return elapsed }
        previous?.let { elapsed += (now - it).coerceIn(0.0, .1) }
        previous = now
        return elapsed
    }
}
