package dev.mobilefoundry.catalog.ui.graphics

import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import android.view.View
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.lifecycle.*
import androidx.lifecycle.compose.LocalLifecycleOwner
import dev.mobilefoundry.catalog.MainNavigation
import dev.mobilefoundry.graphics.*
import dev.mobilefoundry.graphics.gl.*
import dev.mobilefoundry.ui.theme.FoundryTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.math.abs

class GPUEffectsScreenTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private fun glView(): EffectGLView {
        fun find(view: View): EffectGLView? {
            if (view is EffectGLView) return view
            if (view is ViewGroup) for (i in 0 until view.childCount) find(view.getChildAt(i))?.let { return it }
            return null
        }
        var result: EffectGLView? = null
        compose.runOnIdle { result = find(compose.activity.window.decorView) }
        return checkNotNull(result)
    }
    private fun pixels(view: EffectGLView): IntArray {
        val bitmap = Bitmap.createBitmap(128, 96, Bitmap.Config.ARGB_8888)
        var status = -1
        for (attempt in 0 until 50) {
            val done = CountDownLatch(1)
            PixelCopy.request(view.holder.surface, bitmap, { status = it; done.countDown() }, Handler(Looper.getMainLooper()))
            assertTrue("PixelCopy did not complete", done.await(5, TimeUnit.SECONDS))
            if (status != PixelCopy.ERROR_SOURCE_NO_DATA) break
            Thread.sleep(20) // Surface can exist before its first swap.
        }
        assertEquals(PixelCopy.SUCCESS, status)
        return IntArray(128 * 96).also { bitmap.getPixels(it, 0, 128, 0, 0, 128, 96); bitmap.recycle() }
    }
    private fun difference(a: IntArray, b: IntArray): Double = a.indices.sumOf { index ->
        val one = a[index]; val two = b[index]
        abs((one shr 16 and 255) - (two shr 16 and 255)) + abs((one shr 8 and 255) - (two shr 8 and 255)) + abs((one and 255) - (two and 255))
    }.toDouble() / (a.size * 3)
    private fun waitFor(text: String) {
        compose.waitUntil(10_000) { compose.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty() }
    }

    @Test fun suppliedProgressParticlesAndFieldDriveActualPixelsOnARetainedSurface() {
        val settings = mutableStateOf(EffectSettings.make(effect = EffectKind.FLOW))
        var frames = 0
        compose.setContent {
            GPUEffectSurface(settings.value, false, Modifier.size(300.dp, 250.dp), onPoint = {}, onEvent = {
                if (it is EffectEvent.Statistics) frames = it.value.submittedFrames
                if (it is EffectEvent.Failed) fail(it.failure.toString())
            }, onUnexpectedError = { throw it })
        }
        compose.waitUntil(10_000) { frames > 0 }
        val view = glView()
        fun render(effect: EffectKind, progress: Float = .5f, point: EffectPoint = EffectPoint.make(), samples: List<EffectFieldSample> = emptyList()): IntArray {
            val before = frames
            compose.runOnIdle { settings.value = EffectSettings.make(effect = effect, progress = progress, point = point, samples = samples) }
            compose.waitUntil(5_000) { frames > before }
            assertSame("Control changes must retain the native surface", view, glView())
            return pixels(view)
        }
        val flow = pixels(view)
        assertTrue(flow.distinct().size > 50)
        assertTrue(difference(flow, render(EffectKind.FLOW, point = EffectPoint.make(.1f, .8f))) > 1)
        val material = render(EffectKind.MATERIAL)
        assertTrue(difference(flow, material) > 5)
        assertTrue(difference(material, render(EffectKind.MATERIAL, point = EffectPoint.make(.1f, .8f))) > 2)
        val empty = render(EffectKind.LIQUID, progress = 0f)
        val half = render(EffectKind.LIQUID)
        val full = render(EffectKind.LIQUID, progress = 1f)
        assertTrue(difference(empty, half) > 5); assertTrue(difference(half, full) > 5)
        val burst = render(EffectKind.PARTICLES, progress = .32f)
        val start = render(EffectKind.PARTICLES, progress = 0f)
        val end = render(EffectKind.PARTICLES, progress = 1f)
        assertArrayEquals(start, end)
        assertTrue(difference(burst, end) > .2)
        val samples = listOf(EffectFieldSample.make(EffectPoint.make(.3f, .4f)), EffectFieldSample.make(EffectPoint.make(.7f, .6f), .7f))
        val field = render(EffectKind.FIELD, samples = samples)
        val neutral = render(EffectKind.FIELD)
        assertTrue(difference(field, neutral) > 5)
        assertArrayEquals(neutral, render(EffectKind.FIELD, samples = listOf(EffectFieldSample.make(EffectPoint.make(), 0f))))
        compose.runOnIdle { assertTrue(view.renderMode == android.opengl.GLSurfaceView.RENDERMODE_WHEN_DIRTY) }
    }

    @Test fun catalogDataControlsAndFiniteReplayRespectPauseReductionAndBackground() {
        val owner = object : LifecycleOwner {
            val registry = LifecycleRegistry(this)
            override val lifecycle: Lifecycle get() = registry
        }
        compose.runOnIdle { owner.registry.currentState = Lifecycle.State.RESUMED }
        compose.setContent {
            CompositionLocalProvider(LocalLifecycleOwner provides owner) {
                FoundryTheme { GPUEffectsScreen(onBack = {}, modifier = Modifier.safeDrawingPadding().padding(16.dp)) }
            }
        }
        waitFor("OpenGL ES")
        compose.onNodeWithText("Liquid").performScrollTo().performClick()
        compose.onNodeWithText("Full").performScrollTo().performClick()
        compose.onNodeWithText("Progress: 100%", substring = true).assertExists()
        compose.onNodeWithText("Empty").performClick()
        compose.onNodeWithText("Progress: 0%", substring = true).assertExists()
        compose.onNodeWithText("Field", substring = false).performScrollTo().performClick()
        compose.onNodeWithText("Clear data").performScrollTo().performClick()
        compose.onNodeWithText("Samples: 0/12", substring = true).assertExists()
        compose.onNodeWithText("Add at focus").performClick()
        compose.onNodeWithText("Samples: 1/12", substring = true).assertExists()
        compose.onNodeWithText("Trail").performClick()
        compose.onNodeWithText("Samples: 6/12", substring = true).assertExists()
        compose.onNodeWithText("Particles", substring = false).performScrollTo().performClick()
        compose.onNodeWithContentDescription("Reduce motion preview").performScrollTo().performClick()
        compose.onNodeWithText("Replay celebration").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Celebration playhead").performSemanticsAction(SemanticsActions.SetProgress) { it(.4f) }
        compose.onNodeWithText("Playhead: 40% · Static preview").assertExists()
        compose.onNodeWithContentDescription("Reduce motion preview").performClick()
        compose.onNodeWithText("Replay celebration").performScrollTo()
        compose.mainClock.autoAdvance = false
        fun click(text: String) { compose.onNodeWithText(text).performSemanticsAction(SemanticsActions.OnClick) { it() }; compose.mainClock.advanceTimeByFrame() }
        fun toggle(description: String) { compose.onNodeWithContentDescription(description).performSemanticsAction(SemanticsActions.OnClick) { it() }; compose.mainClock.advanceTimeByFrame() }
        fun caption() = compose.onNodeWithText("Playhead:", substring = true).fetchSemanticsNode().config[SemanticsProperties.Text].first().text
        click("Replay celebration")
        compose.mainClock.advanceTimeBy(600)
        assertTrue(caption().contains("Playing")); assertFalse(caption().startsWith("Playhead: 0%"))
        toggle("Animate")
        val paused = caption()
        compose.mainClock.advanceTimeBy(1000)
        assertEquals(paused, caption()); assertTrue(paused.contains("Paused"))
        toggle("Animate")
        compose.runOnIdle { owner.registry.currentState = Lifecycle.State.STARTED }
        compose.mainClock.advanceTimeByFrame()
        val background = caption()
        compose.mainClock.advanceTimeBy(1000)
        assertEquals(background, caption())
        compose.runOnIdle { owner.registry.currentState = Lifecycle.State.RESUMED }
        compose.mainClock.advanceTimeBy(3000)
        compose.onNodeWithText("Playhead: 100% · Completed").assertExists()
        val completed = caption()
        compose.mainClock.advanceTimeBy(3000)
        assertEquals(completed, caption())
        click("Replay celebration")
        compose.mainClock.advanceTimeBy(200)
        click("Replay celebration") // Restart while already playing must establish a new baseline.
        compose.onNodeWithContentDescription("Celebration playhead").performSemanticsAction(SemanticsActions.SetProgress) { it(.6f) }
        compose.mainClock.advanceTimeBy(1000)
        compose.onNodeWithText("Playhead: 60% · Static preview").assertExists()
        compose.mainClock.autoAdvance = true
    }

    @Test fun actualFramebufferChangesWithEffectTouchAndQualityWhilePaused() {
        compose.setContent { FoundryTheme(reduceMotion = true) { GPUEffectsScreen(onBack = {}, modifier = Modifier.safeDrawingPadding().padding(16.dp)) } }
        waitFor("OpenGL ES")
        waitFor("Submitted:")
        compose.onNodeWithText("Animation paused").assertExists()
        val canvas = compose.onNodeWithTag("gpu-canvas")
        canvas.performScrollTo()
        val view = glView()
        val ripple = pixels(view)
        assertTrue("Expected a shaded framebuffer", ripple.distinct().size > 50)
        canvas.performTouchInput { click(Offset(width * .2f, height * .7f)) }
        waitFor("Focus: 0.20, 0.70")
        compose.waitUntil(5_000) { difference(ripple, pixels(view)) > 2.0 }
        compose.onNodeWithText("Orbit").performScrollTo().performClick()
        canvas.performScrollTo()
        compose.waitUntil(5_000) { difference(ripple, pixels(view)) > 5.0 }
        compose.onNodeWithText("Economy").performScrollTo().performClick()
        canvas.performScrollTo()
        compose.waitUntil(5_000) { view.holder.surfaceFrame.width() == view.width / 2 }
        val orbit = pixels(view)
        assertTrue("Orbit should shade many colors", orbit.distinct().size > 50)
        compose.onNodeWithText("Reset focus").performScrollTo().performClick()
        waitFor("Focus: 0.50, 0.50")
        compose.onNodeWithText("Animation paused").assertExists()
    }

    @Test fun foregroundRecreatesContextAndDisposalStopsPublication() {
        val owner = object : LifecycleOwner {
            val registry = LifecycleRegistry(this)
            override val lifecycle: Lifecycle get() = registry
        }
        val events = mutableListOf<EffectEvent>()
        val shown = mutableStateOf(true)
        compose.setContent {
            CompositionLocalProvider(LocalLifecycleOwner provides owner) {
                if (shown.value) GPUEffectSurface(EffectSettings.make(quality = EffectQuality.ECONOMY), true,
                    Modifier.size(240.dp, 200.dp), onPoint = {}, onEvent = { events.add(it) }, onUnexpectedError = { throw it })
            }
        }
        compose.runOnIdle { owner.registry.currentState = Lifecycle.State.RESUMED }
        compose.waitUntil(10_000) { events.any { it is EffectEvent.Statistics } }
        compose.runOnIdle { owner.registry.currentState = Lifecycle.State.STARTED }
        compose.waitForIdle()
        val pausedFrames = events.filterIsInstance<EffectEvent.Statistics>().last().value.submittedFrames
        Thread.sleep(1_200) // Native Choreographer/GL time is independent of Compose's test clock.
        compose.runOnIdle { assertEquals(pausedFrames, events.filterIsInstance<EffectEvent.Statistics>().last().value.submittedFrames) }
        val ready = events.filterIsInstance<EffectEvent.Ready>().size
        compose.runOnIdle { owner.registry.currentState = Lifecycle.State.RESUMED }
        compose.waitUntil(10_000) { events.filterIsInstance<EffectEvent.Ready>().size > ready }
        compose.waitUntil(5_000) { events.filterIsInstance<EffectEvent.Statistics>().last().value.submittedFrames > pausedFrames }
        compose.runOnIdle { shown.value = false }
        compose.waitForIdle()
        val count = events.size
        Thread.sleep(1_200)
        compose.runOnIdle { assertEquals(count, events.size) }
    }

    @Test fun navigationRemovalAndReopeningBuildsAUsableRenderer() {
        compose.setContent { FoundryTheme { MainNavigation() } }
        compose.onNodeWithText("GPU effects").performClick()
        waitFor("OpenGL ES")
        compose.onNodeWithText("Back").performScrollTo().performClick()
        compose.onNodeWithText("GPU effects").performClick()
        waitFor("OpenGL ES")
        compose.onNodeWithContentDescription("Animate").performScrollTo().performClick().assertIsOff()
        waitFor("Animation paused")
        compose.onNodeWithContentDescription("Reduce motion preview").performScrollTo().performClick().assertIsOn()
        compose.onNodeWithContentDescription("Animate").performClick().assertIsOn()
        compose.onNodeWithText("Animation paused").assertExists()
    }
}
