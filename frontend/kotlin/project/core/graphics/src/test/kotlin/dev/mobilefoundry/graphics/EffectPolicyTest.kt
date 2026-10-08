package dev.mobilefoundry.graphics

import java.io.File
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class EffectPolicyTest {
    private fun fixture() = Json.parseToJsonElement(File(System.getProperty("foundry.graphics.fixtures"), "effects.json").readText()).jsonObject
    @Test fun sharedInputAndResolutionBudgetsAreBounded() {
        for (entry in fixture().getValue("points").jsonArray) {
            val row = entry.jsonObject
            val point = EffectPoint.make(row.getValue("x").jsonPrimitive.float, row.getValue("y").jsonPrimitive.float)
            assertEquals(row.getValue("expectedX").jsonPrimitive.float, point.x, 0f)
            assertEquals(row.getValue("expectedY").jsonPrimitive.float, point.y, 0f)
        }
        assertEquals(EffectPoint.make(), EffectPoint.make(Float.NaN, Float.POSITIVE_INFINITY))
        assertEquals(.65f, EffectSettings.make(strength = Float.NaN).strength, 0f)
        assertEquals(0f, EffectSettings.make(strength = -1f).strength, 0f)
        assertEquals(1f, EffectSettings.make(strength = 5f).strength, 0f)
        for (entry in fixture().getValue("sizes").jsonArray) {
            val row = entry.jsonObject
            val quality = EffectQuality.valueOf(row.getValue("quality").jsonPrimitive.content.uppercase())
            val size = quality.resolution(row.getValue("width").jsonPrimitive.double, row.getValue("height").jsonPrimitive.double)
            assertEquals(row.getValue("expectedWidth").jsonPrimitive.intOrNull, size?.first)
            assertEquals(row.getValue("expectedHeight").jsonPrimitive.intOrNull, size?.second)
        }
        assertNull(EffectQuality.BALANCED.resolution(Double.POSITIVE_INFINITY, 100.0))
        assertEquals(30, EffectQuality.ECONOMY.framesPerSecond); assertEquals(32, EffectQuality.ECONOMY.steps)
        assertEquals(60, EffectQuality.BALANCED.framesPerSecond); assertEquals(64, EffectQuality.BALANCED.steps)
    }
    @Test fun sharedClockExcludesPausesAndBoundsStalls() {
        val clock = EffectClock()
        for (entry in fixture().getValue("clock").jsonArray) {
            val row = entry.jsonObject
            assertEquals(row.getValue("elapsed").jsonPrimitive.double,
                clock.frame(row.getValue("now").jsonPrimitive.double, row.getValue("running").jsonPrimitive.boolean), .000001)
        }
        val phase = clock.elapsed
        clock.suspend()
        assertEquals(phase, clock.frame(1000.0, true), 0.0)
        assertEquals(phase, clock.frame(Double.NaN, true), 0.0)
        assertEquals(phase, clock.frame(1001.0, true), 0.0)
    }
}
