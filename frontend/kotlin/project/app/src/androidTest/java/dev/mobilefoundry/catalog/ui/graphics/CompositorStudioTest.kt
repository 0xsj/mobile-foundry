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
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
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

class CompositorStudioTest {
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
    private fun view():PreviewGLView {
        fun find(v:View):PreviewGLView? {
            if(v is PreviewGLView) return v
            if(v is ViewGroup) for(i in 0 until v.childCount) find(v.getChildAt(i))?.let { return it }
            return null
        }
        var result:PreviewGLView?=null
        compose.runOnIdle { result=find(compose.activity.window.decorView) }
        return checkNotNull(result)
    }
    private fun pixels(v:PreviewGLView):IntArray {
        val bitmap=Bitmap.createBitmap(128,96,Bitmap.Config.ARGB_8888)
        var status=PixelCopy.ERROR_SOURCE_NO_DATA
        for(attempt in 0..<50) {
            val done=CountDownLatch(1)
            PixelCopy.request(v.holder.surface,bitmap,{status=it;done.countDown()},Handler(Looper.getMainLooper()))
            assertTrue(done.await(5,TimeUnit.SECONDS))
            if(status!=PixelCopy.ERROR_SOURCE_NO_DATA) break
            Thread.sleep(20) // Statistics may publish before the first EGL swap is available.
        }
        assertEquals(PixelCopy.SUCCESS,status)
        return IntArray(128*96).also { bitmap.getPixels(it,0,128,0,0,128,96);bitmap.recycle() }
    }
    private fun difference(a:IntArray,b:IntArray)=a.indices.sumOf { i->
        abs((a[i] shr 16 and 255)-(b[i] shr 16 and 255))+abs((a[i] shr 8 and 255)-(b[i] shr 8 and 255))+abs((a[i] and 255)-(b[i] and 255))
    }.toDouble()/(a.size*3)
    private fun waitFor(text:String) { compose.waitUntil(10_000) { compose.onAllNodesWithText(text,substring=true).fetchSemanticsNodes().isNotEmpty() } }
    private fun changed(from:IntArray) { compose.onNodeWithTag("preview-canvas").performScrollTo();compose.waitUntil(5_000) { difference(from,pixels(view()))>1 } }


    @Test fun studioControlsAndGesturesChangeRealPixelsAndReset() {
        compose.setContent { FoundryTheme { CompositorStudioScreen({},Modifier.safeDrawingPadding().padding(16.dp)) } }
        waitFor("Uploads:")
        val canvas=compose.onNodeWithTag("preview-canvas");canvas.performScrollTo()
        val initial=pixels(view());assertTrue(initial.distinct().size>100)
        compose.onNodeWithText("Composed",substring=false).performScrollTo().performClick();changed(initial)
        val normal=pixels(view())
        compose.onNodeWithText("Multiply",substring=false).performScrollTo().performClick();changed(normal)
        val multiply=pixels(view())
        compose.onNodeWithText("Screen",substring=false).performScrollTo().performClick();changed(multiply)
        val screen=pixels(view())
        compose.onNodeWithContentDescription("Blur · target pixels").performScrollTo().performSemanticsAction(SemanticsActions.SetProgress) { it(24f) };changed(screen)
        val blurred=pixels(view())
        compose.onNodeWithText("Layer",substring=false).performScrollTo().performClick();canvas.performScrollTo()
        canvas.performTouchInput { swipe(Offset(width*.4f,height*.4f),Offset(width*.7f,height*.6f),300) };changed(blurred)
        val moved=pixels(view())
        canvas.performTouchInput { pinch(start0=Offset(width*.4f,height*.5f),end0=Offset(width*.2f,height*.5f),
            start1=Offset(width*.6f,height*.5f),end1=Offset(width*.8f,height*.5f),durationMillis=400) };changed(moved)
        val scaled=pixels(view())
        compose.onNodeWithText("Mask",substring=false).performScrollTo().performClick();canvas.performScrollTo()
        canvas.performTouchInput { swipe(Offset(width*.6f,height*.5f),Offset(width*.2f,height*.3f),300) };changed(scaled)
        compose.onNodeWithText("Reset composition").performScrollTo().performClick();canvas.performScrollTo()
        compose.waitUntil(5_000) { difference(initial,pixels(view()))<.2 }
        compose.onNodeWithContentDescription("Profile redraws").performScrollTo().performClick();waitFor("Redraw workload requested")
        compose.onNodeWithContentDescription("Reduce motion preview").performScrollTo().performClick();waitFor("Draw on change")
    }

    @Test fun compositePixelsProfilesCacheResizeContextAndDisposal() {
        val owner=object:LifecycleOwner { val registry=LifecycleRegistry(this);override val lifecycle:Lifecycle get()=registry }
        val profiles=mutableListOf<GraphicsProfile>();val events=mutableListOf<EffectEvent>()
        val shown=mutableStateOf(true);val quality=mutableStateOf(EffectQuality.BALANCED)
        val running=mutableStateOf(false)
        val settings=mutableStateOf(CompositeSettings.make(opacity=1f,scale=.8f,glow=0f,comparison=0f,maskEnabled=false))
        val base=RasterImage.create(1,1,byteArrayOf(-128,-128,-128,-1))!!
        val overlay=AlphaImage.create(2,2,byteArrayOf(-1,0,0,-128,-1,0,0,-128,0,0,-1,-128,0,0,-1,-128))!!
        compose.setContent { CompositionLocalProvider(LocalLifecycleOwner provides owner) {
            if(shown.value) GPUPreviewSurface(PreviewContent.Composite(base,overlay,settings.value),quality.value,running.value,Modifier.size(240.dp,180.dp),
                onGesture={},onEvent={events.add(it)},onUnexpectedError={throw it},onProfile={profiles.add(it)})
        } }
        compose.runOnIdle { owner.registry.currentState=Lifecycle.State.RESUMED }
        compose.waitUntil(10_000) { profiles.isNotEmpty() }
        val initial=pixels(view());val originalView=view()
        val top=initial[25*128+64];val bottom=initial[71*128+64]
        assertTrue((top shr 16 and 255)>(top and 255));assertTrue((bottom and 255)>(bottom shr 16 and 255))
        assertEquals(205.0,(top shr 16 and 255).toDouble(),4.0);assertEquals(92.0,(top shr 8 and 255).toDouble(),4.0)
        compose.runOnIdle {
            val p=profiles.last();assertEquals(4,p.passes);assertEquals(2,p.uploads);assertEquals(3,p.targetAllocations)
            assertEquals(20L,p.inputTextureBytes);assertEquals(p.width.toLong()*p.height*12,p.offscreenTextureBytes)
            assertNull(p.gpuMilliseconds);assertTrue(p.gpuTiming.contains("unavailable"));assertTrue(p.cpuEncodeMilliseconds>=0)
        }
        val count=profiles.size;Thread.sleep(150);compose.runOnIdle { assertEquals(count,profiles.size) }
        compose.runOnIdle { settings.value=CompositeSettings.make(opacity=1f,scale=.8f,glow=0f,comparison=0f,maskEnabled=false,blend=CompositeBlend.MULTIPLY) }
        compose.waitUntil(5_000) { profiles.size>count && difference(initial,pixels(view()))>3 }
        assertSame(originalView,view())
        compose.runOnIdle { assertEquals(2,profiles.last().uploads);assertEquals(3,profiles.last().targetAllocations);quality.value=EffectQuality.ECONOMY }
        compose.waitUntil(5_000) { profiles.last().targetAllocations==6 }
        compose.runOnIdle { assertEquals(2,profiles.last().uploads);running.value=true }
        val frame=profiles.last().frame
        compose.waitUntil(5_000) { profiles.last().frame>frame+5 }
        val before=pixels(view())
        compose.runOnIdle { owner.registry.currentState=Lifecycle.State.STARTED }
        compose.waitForIdle();val paused=profiles.size;Thread.sleep(1_200)
        compose.runOnIdle { assertEquals(paused,profiles.size) }
        val ready=events.filterIsInstance<EffectEvent.Ready>().size
        compose.runOnIdle { owner.registry.currentState=Lifecycle.State.RESUMED }
        compose.waitUntil(10_000) { events.filterIsInstance<EffectEvent.Ready>().size>ready && profiles.size>paused }
        compose.waitUntil(5_000) { difference(before,pixels(view()))<.2 }
        compose.runOnIdle { assertEquals(2,profiles.last().uploads);assertEquals(3,profiles.last().targetAllocations);shown.value=false }
        compose.waitForIdle();val removed=profiles.size;val eventCount=events.size;Thread.sleep(1_200)
        compose.runOnIdle { assertEquals(removed,profiles.size);assertEquals(eventCount,events.size) }
    }

    @Test fun compositorNavigationReopens() {
        compose.setContent { FoundryTheme { MainNavigation() } }
        repeat(2) {
            compose.onNodeWithText("Compositor studio",substring=false).performClick();waitFor("Uploads:")
            compose.onNodeWithText("Back",substring=false).performScrollTo().performClick()
        }
    }
}
