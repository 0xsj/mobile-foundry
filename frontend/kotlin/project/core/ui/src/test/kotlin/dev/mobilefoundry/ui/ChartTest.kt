package dev.mobilefoundry.ui

import dev.mobilefoundry.ui.components.charts.barchart.ChartBar
import dev.mobilefoundry.ui.components.charts.sparkline.normalizedSparkline
import org.junit.Assert.*
import org.junit.Test

class ChartTest {
    @Test fun extremeSignedAndNegativeSamplesStayFiniteInsideBounds() {
        val points = normalizedSparkline(listOf(-Double.MAX_VALUE, 0.0, Double.MAX_VALUE))
        assertEquals(listOf(0.0 to 1.0, .5 to .5, 1.0 to 0.0), points)
        assertTrue(points.all { it.first.isFinite() && it.second.isFinite() && it.second in 0.0..1.0 })
        assertEquals(listOf(1.0, .5, 0.0), normalizedSparkline(listOf(-30.0, -20.0, -10.0)).map { it.second })
    }
    @Test fun emptySingleConstantAndInvalidInputsHaveExplicitBehavior() {
        assertTrue(normalizedSparkline(emptyList()).isEmpty())
        assertEquals(listOf(.5 to .5), normalizedSparkline(listOf(42.0)))
        assertEquals(listOf(.5, .5, .5), normalizedSparkline(listOf(0.0, 0.0, 0.0)).map { it.second })
        assertThrows(IllegalArgumentException::class.java) { normalizedSparkline(listOf(Double.NaN)) }
        assertThrows(IllegalArgumentException::class.java) { ChartBar("bad", "Invalid", -1.0, "-1") }
    }
}
