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

class PreviewStudioTest {
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

    @Test fun imageToolsFilterZoomPanAndResizeActualPixels() {
        compose.setContent { FoundryTheme { ImageStudioScreen({},Modifier.safeDrawingPadding().padding(16.dp)) } }
        waitFor("Submitted:")
        val canvas=compose.onNodeWithTag("preview-canvas")
        canvas.performScrollTo()
        val initial=pixels(view());assertTrue(initial.distinct().size>100)
        compose.onNodeWithText("Original",substring=false).performScrollTo().performClick();changed(initial)
        val original=pixels(view())
        compose.onNodeWithText("Edited",substring=false).performScrollTo().performClick();changed(original)
        val edited=pixels(view())
        compose.onNodeWithText("Zoom in",substring=false).performScrollTo().performClick();waitFor("Zoom: 1.25×");changed(edited)
        compose.onNodeWithText("Move image",substring=false).performScrollTo().performClick()
        canvas.performScrollTo();val beforePan=pixels(view())
        canvas.performTouchInput { swipe(Offset(width*.4f,height*.5f),Offset(width*.7f,height*.65f),300) };changed(beforePan)
        compose.onNodeWithText("Compare",substring=false).performScrollTo().performClick()
        canvas.performScrollTo();val beforeDivider=pixels(view())
        canvas.performTouchInput { swipe(Offset(width*.8f,height*.5f),Offset(width*.2f,height*.5f),300) };changed(beforeDivider)
        compose.onNodeWithText("Economy",substring=false).performScrollTo().performClick();canvas.performScrollTo()
        compose.waitUntil(5_000) { view().holder.surfaceFrame.width()==view().width/2 }
        compose.onNodeWithText("Reset edits").performScrollTo().performClick();waitFor("Zoom: 1.00×")
    }

    @Test fun productFinishCameraPinchAndMotionControlsUseRealMesh() {
        compose.setContent { FoundryTheme { ProductStudioScreen({},Modifier.safeDrawingPadding().padding(16.dp)) } }
        waitFor("Submitted:")
        val canvas=compose.onNodeWithTag("preview-canvas");canvas.performScrollTo()
        val porcelain=pixels(view());assertTrue(porcelain.distinct().size>50)
        compose.onNodeWithText("Cobalt",substring=false).performScrollTo().performClick();changed(porcelain)
        val cobalt=pixels(view())
        compose.onNodeWithText("Bronze",substring=false).performScrollTo().performClick();changed(cobalt)
        compose.onNodeWithText("Rotate right",substring=false).performScrollTo().performClick();changed(cobalt)
        val bronze=pixels(view())
        canvas.performTouchInput { swipe(Offset(width*.3f,height*.4f),Offset(width*.7f,height*.6f),300) }
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Camera: 0.85, 0.15",substring=true).fetchSemanticsNodes().isEmpty() }
        changed(bronze)
        val orbited=pixels(view())
        canvas.performTouchInput { pinch(start0=Offset(width*.4f,height*.5f),end0=Offset(width*.2f,height*.5f),
            start1=Offset(width*.6f,height*.5f),end1=Offset(width*.8f,height*.5f),durationMillis=400) }
        changed(orbited)
        compose.onNodeWithContentDescription("Turntable").performScrollTo().performClick();waitFor("Turntable running")
        compose.onNodeWithContentDescription("Reduce motion preview").performScrollTo().performClick();waitFor("Turntable paused")
    }

    @Test fun contextRebuildsResourcesAndDisposalStopsEvents() {
        val owner=object:LifecycleOwner { val registry=LifecycleRegistry(this);override val lifecycle:Lifecycle get()=registry }
        val events=mutableListOf<EffectEvent>();val shown=mutableStateOf(true)
        val image=RasterImage.create(1,1,byteArrayOf(80,120,-96,-1))!!
        compose.setContent { CompositionLocalProvider(LocalLifecycleOwner provides owner) {
            if(shown.value) GPUPreviewSurface(PreviewContent.Image(image),running=true,modifier=Modifier.size(240.dp,200.dp),
                onGesture={},onEvent={events.add(it)},onUnexpectedError={throw it})
        } }
        compose.runOnIdle { owner.registry.currentState=Lifecycle.State.RESUMED }
        compose.waitUntil(10_000) { events.any { it is EffectEvent.Statistics } }
        val before=pixels(view());assertTrue(before.distinct().size>1)
        compose.runOnIdle { owner.registry.currentState=Lifecycle.State.STARTED }
        compose.waitForIdle()
        val count=events.size;Thread.sleep(1_200)
        compose.runOnIdle { assertEquals(count,events.size) }
        val ready=events.filterIsInstance<EffectEvent.Ready>().size
        compose.runOnIdle { owner.registry.currentState=Lifecycle.State.RESUMED }
        compose.waitUntil(10_000) { events.filterIsInstance<EffectEvent.Ready>().size>ready }
        compose.waitUntil(5_000) { difference(before,pixels(view()))<.2 }
        compose.runOnIdle { shown.value=false };compose.waitForIdle()
        val disposedCount=events.size;Thread.sleep(1_200)
        compose.runOnIdle { assertEquals(disposedCount,events.size) }
    }

    @Test fun catalogNavigationReopensBothPreviews() {
        compose.setContent { FoundryTheme { MainNavigation() } }
        for(label in listOf("Image studio","Product studio","Image studio")) {
            compose.onNodeWithText(label,substring=false).performClick();waitFor("Submitted:")
            compose.onNodeWithText("Back",substring=false).performScrollTo().performClick()
        }
    }
}
