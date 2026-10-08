package dev.mobilefoundry.graphics

import java.io.File
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class EffectPolicyTest {
    @Test fun suppliedContentIsBoundedAndOwned() {
        val fixture = fixture()
        fixture.getValue("kinds").jsonObject.forEach { (name, index) ->
            assertEquals(index.jsonPrimitive.float, EffectKind.valueOf(name.uppercase()).shaderIndex, 0f)
        }
        fixture.getValue("progress").jsonArray.forEach { entry ->
            val row = entry.jsonObject
            assertEquals(row.getValue("expected").jsonPrimitive.float, EffectSettings.make(progress = row.getValue("input").jsonPrimitive.float).progress, 0f)
        }
        assertEquals(.5f, EffectSettings.make(progress = Float.NaN).progress, 0f)
        fixture.getValue("samples").jsonArray.forEach { entry ->
            val row = entry.jsonObject
            val sample = EffectFieldSample.make(EffectPoint.make(), row.getValue("weight").jsonPrimitive.float, row.getValue("radius").jsonPrimitive.float)
            assertEquals(row.getValue("expectedWeight").jsonPrimitive.float, sample.weight, 0f)
            assertEquals(row.getValue("expectedRadius").jsonPrimitive.float, sample.radius, 0f)
        }
        val invalid = EffectFieldSample.make(EffectPoint.make(), Float.POSITIVE_INFINITY, Float.NaN)
        assertEquals(0f, invalid.weight, 0f); assertEquals(.18f, invalid.radius, 0f)
        val source = MutableList(20) { EffectFieldSample.make(EffectPoint.make()) }
        val settings = EffectSettings.make(samples = source)
        source.clear()
        assertEquals(12, settings.samples.size)
        assertThrows(UnsupportedOperationException::class.java) { (settings.samples as MutableList).clear() }
        assertTrue(EffectSettings.make().samples.isEmpty())
    }
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
