package dev.mobilefoundry.physics

import java.io.File
import kotlinx.serialization.json.*
import kotlin.math.*
import org.junit.Assert.*
import org.junit.Test

class ThrowSimulationTest {
    @Test fun sharedShotsScoreOnceAndSettle() {
        val rows = Json.parseToJsonElement(File(System.getProperty("physics.fixtures"), "shots.json").readText()).jsonObject.getValue("shots").jsonArray
        for (entry in rows) {
            val row = entry.jsonObject
            val target = if (row.getValue("target").jsonPrimitive.content == "hoop") ThrowTarget.HOOP else ThrowTarget.LANDING_ZONE
            var world = ThrowSimulation.make(target).launch(PhysicsVector(row.getValue("vx").jsonPrimitive.double, row.getValue("vy").jsonPrimitive.double))
            repeat(1500) { world = world.advance(ThrowSimulation.timeStep) }
            val score = row.getValue("score").jsonPrimitive.int
            assertEquals("$row", score, world.scores); assertEquals(1, world.shots); assertEquals(ThrowPhase.SETTLED, world.phase)
            assertTrue(world.position.isFinite); assertEquals(PhysicsVector(), world.velocity)
            world = world.resetBall()
            assertEquals(ThrowPhase.READY, world.phase); assertEquals(1, world.shots); assertEquals(score, world.scores); assertFalse(world.scored)
        }
    }
    @Test fun gravityUsesWorldUnitsAndFixedSteps() {
        val world = ThrowSimulation.make().launch(PhysicsVector(2.0, 6.0)).advance(.1).advance(.1).advance(.05)
        val dt = ThrowSimulation.timeStep; val n = 30.0
        assertEquals(1.65, world.position.x, 1e-10)
        assertEquals(.65 + 6*.25 - 9.81*dt*dt*n*(n+1)/2, world.position.y, 1e-10)
        assertEquals(6 - 9.81*.25, world.velocity.y, 1e-10)
        fun at(fps: Int): ThrowSimulation {
            var w = ThrowSimulation.make().launch(PhysicsVector(5.0, 8.6))
            repeat(fps*2) { w = w.advance(1.0/fps) }; return w
        }
        assertEquals(0.0, (at(30).position-at(120).position).length, 1e-10)
        assertEquals(0.0, (at(60).velocity-at(120).velocity).length, 1e-10)
        assertEquals(1, at(30).scores); assertEquals(1, at(120).scores)
    }
    @Test fun contactsReflectAndRestitutionDissipatesEnergy() {
        val floor = ThrowSimulation.make().launch(PhysicsVector(y = -3.0)).advance(.1).advance(.05)
        assertTrue(floor.velocity.y > 0 && floor.position.y >= ThrowSimulation.ballRadius)
        var dead = ThrowSimulation.make(settings = PhysicsSettings.make(restitution = 0.0)).launch(PhysicsVector(y = -3.0))
        repeat(60) { dead = dead.advance(1.0/60) }
        assertEquals(ThrowPhase.SETTLED, dead.phase); assertEquals(PhysicsVector(), dead.velocity)
        var wall = ThrowSimulation.make(ThrowTarget.LANDING_ZONE).launch(PhysicsVector(x = 14.0))
        repeat(40) { wall = wall.advance(1.0/60) }
        assertTrue(wall.velocity.x < 0 && wall.position.x <= 10-ThrowSimulation.ballRadius)
        var board = ThrowSimulation.make().launch(PhysicsVector(7.0, 8.1))
        repeat(72) { board = board.advance(1.0/60) }
        assertTrue(board.velocity.x < 0); assertTrue(board.scores <= 1)
    }
    @Test fun inputBudgetsResetAndPreviewAreSafe() {
        val original = ThrowSimulation.make()
        assertEquals(original, original.launch(PhysicsVector(x = Double.NaN)))
        assertEquals(original, original.launch(PhysicsVector(x = Double.POSITIVE_INFINITY)))
        assertEquals(original, original.launch(PhysicsVector()))
        assertTrue(ThrowSimulation.aimVelocity(PhysicsVector(1e308, 1e308)).isFinite)
        assertEquals(14.0, ThrowSimulation.aimVelocity(PhysicsVector(100.0, 100.0)).length, 1e-10)
        assertTrue(original.trajectory(PhysicsVector(5.5, 8.1)).size > 10)
        var world = original.launch(PhysicsVector(100.0, 100.0))
        assertEquals(14.0, world.velocity.length, 1e-10)
        assertEquals(world, world.launch(PhysicsVector(1.0, 1.0)))
        assertEquals(world, world.advance(Double.NaN)); assertEquals(world, world.advance(-1.0))
        assertEquals(world.advance(.1), world.advance(3600.0))
        world = world.configure(ThrowTarget.LANDING_ZONE, PhysicsSettings.make(Double.NaN, 5.0))
        assertEquals(0, world.shots); assertEquals(0, world.scores); assertEquals(ThrowPhase.READY, world.phase)
        assertEquals(9.81, world.settings.gravity, 0.0); assertEquals(.9, world.settings.restitution, 0.0)
    }
    @Test fun boundedShotsStayInsideTheWorld() {
        for (target in ThrowTarget.entries) for (angle in -160..160 step 20) {
            var world = ThrowSimulation.make(target).launch(PhysicsVector(cos(angle*PI/180)*14, sin(angle*PI/180)*14))
            repeat(1500) {
                world = world.advance(ThrowSimulation.timeStep)
                assertTrue(world.position.isFinite && world.velocity.isFinite)
                assertTrue(world.position.x >= ThrowSimulation.ballRadius-1e-9 && world.position.x <= 10-ThrowSimulation.ballRadius+1e-9)
                assertTrue(world.position.y >= ThrowSimulation.ballRadius-1e-9 && world.position.y <= 7-ThrowSimulation.ballRadius+1e-9)
            }
            assertTrue(world.scores <= 1); assertEquals(ThrowPhase.SETTLED, world.phase)
        }
    }
}
