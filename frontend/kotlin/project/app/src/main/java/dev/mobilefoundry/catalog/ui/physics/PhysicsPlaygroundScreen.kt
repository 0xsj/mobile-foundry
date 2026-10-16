package dev.mobilefoundry.catalog.ui.physics

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.mobilefoundry.physics.*
import dev.mobilefoundry.ui.components.display.card.Card
import dev.mobilefoundry.ui.theme.FoundryTheme
import kotlin.math.*
import java.util.Locale

@Composable
fun PhysicsPlaygroundScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    var simulation by remember { mutableStateOf(ThrowSimulation.make()) }
    var angle by remember { mutableDoubleStateOf(60.0) }
    var speed by remember { mutableDoubleStateOf(9.9) }
    var paused by remember { mutableStateOf(false) }
    var dragVelocity by remember { mutableStateOf<PhysicsVector?>(null) }
    var trail by remember { mutableStateOf(emptyList<PhysicsVector>()) }
    val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateFlow.collectAsStateWithLifecycle()
    val running = lifecycle.isAtLeast(Lifecycle.State.RESUMED) && !paused && simulation.phase == ThrowPhase.FLYING
    val tokens = FoundryTheme.tokens
    val controlVelocity = PhysicsVector(cos(angle * PI / 180) * speed, sin(angle * PI / 180) * speed)
    val status = when {
        simulation.scored -> if (simulation.target == ThrowTarget.HOOP) "Basket!" else "On target!"
        paused && simulation.phase == ThrowPhase.FLYING -> "Paused"
        simulation.phase == ThrowPhase.READY -> "Ready to throw"
        simulation.phase == ThrowPhase.FLYING -> "In flight"
        else -> "Try another throw"
    }
    fun reset() { simulation = simulation.resetBall(); paused = false; dragVelocity = null; trail = emptyList() }
    fun configure(target: ThrowTarget = simulation.target, settings: PhysicsSettings = simulation.settings) {
        simulation = simulation.configure(target, settings); paused = false; dragVelocity = null; trail = emptyList()
    }
    fun launch(v: PhysicsVector) {
        val next = simulation.launch(v)
        if (next != simulation) { simulation = next; paused = false; trail = listOf(next.position) }
    }
    LaunchedEffect(running) {
        if (!running) return@LaunchedEffect
        var previous = withFrameNanos { it }
        while (simulation.phase == ThrowPhase.FLYING) {
            val now = withFrameNanos { it }
            simulation = simulation.advance((now-previous)/1e9); previous = now
            trail = (trail + simulation.position).takeLast(70)
        }
    }
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(tokens.space.stack)) {
        TextButton(onClick = onBack) { Text("Back") }
        Text("Physics playground", style = tokens.typography.title)
        Text("Make the shot", style = tokens.typography.heading)
        Text("Drag from the ball towards the target, then release. A longer drag gives more power.", style = tokens.typography.caption)
        Row(horizontalArrangement = Arrangement.spacedBy(tokens.space.inline)) {
            FilterChip(selected = simulation.target == ThrowTarget.HOOP, onClick = { configure(ThrowTarget.HOOP) }, label = { Text("Hoop") })
            FilterChip(selected = simulation.target == ThrowTarget.LANDING_ZONE, onClick = { configure(ThrowTarget.LANDING_ZONE) }, label = { Text("Landing zone") }, modifier = Modifier.testTag("physics-zone"))
        }
        PhysicsCourt(simulation, simulation.trajectory(dragVelocity ?: controlVelocity), trail,
            Modifier.fillMaxWidth().aspectRatio(10f/7f).clip(RoundedCornerShape(24.dp)).testTag("physics-court")
                .semantics { contentDescription = "Physics court. $status. Use angle, power and Launch ball controls as an alternative to dragging." }
                .pointerInput(simulation.phase) {
                    try {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            val ball = Offset((simulation.position.x/10*size.width).toFloat(), ((7-simulation.position.y)/7*size.height).toFloat())
                            if (simulation.phase == ThrowPhase.READY && (down.position-ball).getDistance() <= 36.dp.toPx()) {
                                down.consume()
                                var released = false
                                while (true) {
                                    val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                                    if (change.isConsumed) break
                                    val displacement = change.position-down.position
                                    dragVelocity = ThrowSimulation.aimVelocity(PhysicsVector(displacement.x/size.width*10.0, -displacement.y/size.height*7.0))
                                    change.consume()
                                    if (!change.pressed) { released = true; break }
                                }
                                if (released) dragVelocity?.let { launch(it) }
                                dragVelocity = null
                            }
                        }
                    } finally { dragVelocity = null }
                })
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(status, style = tokens.typography.heading, modifier = Modifier.testTag("physics-status"))
            Text("${simulation.scores} / ${simulation.shots}", style = tokens.typography.code,
                modifier = Modifier.testTag("physics-score").semantics { contentDescription = "${simulation.scores} scored out of ${simulation.shots} throws" })
        }
        Row(horizontalArrangement = Arrangement.spacedBy(tokens.space.inline)) {
            Button(onClick = { reset() }, modifier = Modifier.testTag("physics-reset")) { Text("New throw") }
            OutlinedButton(onClick = { paused = !paused }, enabled = simulation.phase == ThrowPhase.FLYING, modifier = Modifier.testTag("physics-pause")) { Text(if (paused) "Resume" else "Pause") }
        }
        Card {
            Text("Aim without dragging", style = tokens.typography.label)
            Text(String.format(Locale.ROOT, "Angle %.0f° · Power %.1f m/s", angle, speed), style = tokens.typography.caption)
            Slider(value = angle.toFloat(), onValueChange = { angle = it.toDouble() }, valueRange = 20f..80f, modifier = Modifier.semantics { contentDescription = "Launch angle" })
            Slider(value = speed.toFloat(), onValueChange = { speed = it.toDouble() }, valueRange = 4f..14f, modifier = Modifier.semantics { contentDescription = "Launch power" })
            Button(onClick = { launch(controlVelocity) }, enabled = simulation.phase == ThrowPhase.READY, modifier = Modifier.testTag("physics-launch")) { Text("Launch ball") }
        }
        Card {
            Text("World settings", style = tokens.typography.label)
            Text(String.format(Locale.ROOT, "Gravity %.1f m/s²", simulation.settings.gravity), style = tokens.typography.caption)
            Slider(value = simulation.settings.gravity.toFloat(), onValueChange = { configure(settings = PhysicsSettings.make(it.toDouble(), simulation.settings.restitution)) }, valueRange = 4f..20f,
                modifier = Modifier.semantics { contentDescription = "Gravity" })
            Text(String.format(Locale.ROOT, "Bounce %.2f", simulation.settings.restitution), style = tokens.typography.caption)
            Slider(value = simulation.settings.restitution.toFloat(), onValueChange = { configure(settings = PhysicsSettings.make(simulation.settings.gravity, it.toDouble())) }, valueRange = 0f..0.9f,
                modifier = Modifier.semantics { contentDescription = "Bounce" })
            Text("Changing the target or physics resets the ball. The landing zone scores the first ground contact.", style = tokens.typography.caption)
        }
        Text("2D rigid-ball physics · Gravity, rim/backboard collisions and ground friction · Local session scores", style = tokens.typography.caption)
    }
}

@Composable
private fun PhysicsCourt(simulation: ThrowSimulation, trajectory: List<PhysicsVector>, trail: List<PhysicsVector>, modifier: Modifier) {
    Canvas(modifier) {
        val unit = size.width/10
        fun point(p: PhysicsVector) = Offset((p.x*unit).toFloat(), size.height-(p.y*unit).toFloat())
        fun line(a: PhysicsVector, b: PhysicsVector, color: Color, width: Float = 1f) { drawLine(color, point(a), point(b), width) }
        fun circle(p: PhysicsVector, radius: Double, color: Color) { drawCircle(color, (radius*unit).toFloat(), point(p)) }
        drawRect(Brush.linearGradient(listOf(Color(0xFF0F1933), Color(0xFF1F304D))))
        for (x in 1..9) line(PhysicsVector(x.toDouble()), PhysicsVector(x.toDouble(), 7.0), Color.White.copy(alpha = .045f))
        for (y in 1..6) line(PhysicsVector(y = y.toDouble()), PhysicsVector(10.0, y.toDouble()), Color.White.copy(alpha = .045f))
        line(PhysicsVector(y = .03), PhysicsVector(10.0, .03), Color.White.copy(alpha = .4f), 2f)
        val orange = Color(0xFFFFAA42); val mint = Color(0xFF82EBCB)
        if (simulation.target == ThrowTarget.HOOP) {
            val c = ThrowSimulation.hoopCenter; val half = ThrowSimulation.hoopHalfWidth; val net = Color.White.copy(alpha = .55f)
            for (i in 0..4) {
                val x = c.x-half + i/4.0*half*2
                line(PhysicsVector(x, c.y), PhysicsVector(c.x+(x-c.x)*.6, c.y-.85), net)
            }
            line(PhysicsVector(c.x-half*.6, c.y-.85), PhysicsVector(c.x+half*.6, c.y-.85), net)
            line(PhysicsVector(c.x-half*.85, c.y-.35), PhysicsVector(c.x+half*.85, c.y-.35), net)
            line(PhysicsVector(c.x+half+.35, c.y-.15), PhysicsVector(c.x+half+.35, c.y+1.3), Color.White, .14f*unit)
            for (x in listOf(c.x-half, c.x+half)) circle(PhysicsVector(x, c.y), ThrowSimulation.rimRadius, orange)
        } else {
            drawRect(mint, point(PhysicsVector(ThrowSimulation.zoneMin, .2)), androidx.compose.ui.geometry.Size(((ThrowSimulation.zoneMax-ThrowSimulation.zoneMin)*unit).toFloat(), .2f*unit))
            line(PhysicsVector(7.5, .45), PhysicsVector(7.5, 1.0), mint.copy(alpha = .7f), 2f)
        }
        trail.forEachIndexed { i, p -> circle(p, .045, orange.copy(alpha = (i+1f)/max(1,trail.size)*.4f)) }
        if (simulation.phase == ThrowPhase.READY) {
            trajectory.forEach { circle(it, .035, Color.White.copy(alpha = .45f)) }
            circle(simulation.position, .45, orange.copy(alpha = .15f))
        }
        circle(simulation.position, ThrowSimulation.ballRadius, orange)
        val p = simulation.position; val r = ThrowSimulation.ballRadius*.9
        line(PhysicsVector(p.x-r, p.y), PhysicsVector(p.x+r, p.y), Color.Black.copy(alpha = .55f))
        line(PhysicsVector(p.x, p.y-r), PhysicsVector(p.x, p.y+r), Color.Black.copy(alpha = .55f))
    }
}
