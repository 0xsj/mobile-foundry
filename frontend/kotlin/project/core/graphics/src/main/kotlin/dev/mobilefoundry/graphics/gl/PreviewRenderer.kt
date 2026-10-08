package dev.mobilefoundry.graphics.gl

import android.content.res.AssetManager
import android.opengl.GLES20.*
import android.opengl.GLSurfaceView
import android.os.SystemClock
import dev.mobilefoundry.graphics.*
import dev.mobilefoundry.kernel.*
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

internal class PreviewRenderer(private val assets: AssetManager, private val event: (EffectEvent) -> Unit,
                               private val diagnostic: (Exception) -> Unit,
                               private val profile: (GraphicsProfile) -> Unit = {}) : GLSurfaceView.Renderer {
    var content: PreviewContent? = null
    var running = false
    val clock = EffectClock()
    private var program = 0
    private var image: RasterImage? = null
    private var geometry: PreviewMesh? = null
    private var texture = 0
    private var compositor: CompositorRenderer? = null
    private var buffer = 0
    private var width = 1
    private var height = 1
    private var frames = 0
    private var reported = 0
    private var reportTime = 0.0
    private val uniforms = IntArray(5)
    private var position = 0; private var normal = 0; private var slot = 0
    private val triangle = ByteBuffer.allocateDirect(9 * 4).order(ByteOrder.nativeOrder()).asFloatBuffer().apply {
        put(floatArrayOf(-1f,-1f,0f,3f,-1f,0f,-1f,3f,0f)); position(0)
    }
    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        program = 0; texture = 0; buffer = 0; image = null; geometry = null; compositor = null; clock.suspend()
        reportTime = seconds(); reported = frames
        try {
            val vertex = compile(GL_VERTEX_SHADER, "preview.vert")
            val fragment = try { compile(GL_FRAGMENT_SHADER, "preview.frag") } catch (e: Exception) { glDeleteShader(vertex); throw e }
            val next = glCreateProgram()
            try {
                glAttachShader(next, vertex); glAttachShader(next, fragment); glLinkProgram(next)
                val ok = IntArray(1); glGetProgramiv(next, GL_LINK_STATUS, ok, 0)
                check(ok[0] != 0) { glGetProgramInfoLog(next) }
                program = next
            } catch (e: Exception) { glDeleteProgram(next); throw e }
            finally { glDeleteShader(vertex); glDeleteShader(fragment) }
            position = glGetAttribLocation(program, "position"); normal = glGetAttribLocation(program, "normal"); slot = glGetAttribLocation(program, "slot")
            listOf("viewport", "edit", "imageInfo", "panCamera", "style").forEachIndexed { i, name -> uniforms[i] = glGetUniformLocation(program, name) }
            glUseProgram(program); glUniform1i(glGetUniformLocation(program, "photograph"), 0)
            event(EffectEvent.Ready("OpenGL ES · ${glGetString(GL_RENDERER)}"))
        } catch (e: Exception) { fail(e) }
    }
    private fun compile(type: Int, name: String): Int {
        val source = assets.open("foundry_graphics/$name").bufferedReader().use { it.readText() }
        val shader = glCreateShader(type); glShaderSource(shader, source); glCompileShader(shader)
        val ok = IntArray(1); glGetShaderiv(shader, GL_COMPILE_STATUS, ok, 0)
        if (ok[0] == 0) { val message = glGetShaderInfoLog(shader); glDeleteShader(shader); error(message) }
        return shader
    }
    private fun prepare(value: PreviewContent) {
        when (value) {
            is PreviewContent.Composite -> Unit // Owned by the multipass renderer below.
            is PreviewContent.Image -> if (image !== value.image) {
                val max = IntArray(1); glGetIntegerv(GL_MAX_TEXTURE_SIZE, max, 0)
                if (value.image.width > max[0] || value.image.height > max[0]) throw UnsupportedAsset()
                if (texture != 0) glDeleteTextures(1, intArrayOf(texture), 0)
                if (buffer != 0) { glDeleteBuffers(1, intArrayOf(buffer), 0); buffer = 0; geometry = null }
                val handle = IntArray(1); glGenTextures(1, handle, 0); texture = handle[0]
                glActiveTexture(GL_TEXTURE0); glBindTexture(GL_TEXTURE_2D, texture)
                glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR); glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR)
                glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE); glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE)
                glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, value.image.width, value.image.height, 0, GL_RGBA, GL_UNSIGNED_BYTE, value.image.buffer())
                check(glGetError() == GL_NO_ERROR) { "Texture upload failed" }; image = value.image
            }
            is PreviewContent.Product -> if (geometry !== value.mesh) {
                if (buffer != 0) glDeleteBuffers(1, intArrayOf(buffer), 0)
                if (texture != 0) { glDeleteTextures(1, intArrayOf(texture), 0); texture = 0; image = null }
                val handle = IntArray(1); glGenBuffers(1, handle, 0); buffer = handle[0]
                glBindBuffer(GL_ARRAY_BUFFER, buffer)
                glBufferData(GL_ARRAY_BUFFER, value.mesh.vertexCount * 7 * 4, value.mesh.buffer(), GL_STATIC_DRAW)
                check(glGetError() == GL_NO_ERROR) { "Mesh upload failed" }; geometry = value.mesh
            }
        }
    }
    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) { this.width = width; this.height = height; glViewport(0,0,width,height) }
    override fun onDrawFrame(gl: GL10?) {
        val value = content ?: return
        if (program == 0 || width <= 0 || height <= 0) return
        try {
            if (value is PreviewContent.Composite) {
                if (compositor == null) {
                    compositor = CompositorRenderer(assets)
                    if(texture!=0) { glDeleteTextures(1,intArrayOf(texture),0);texture=0;image=null }
                    if(buffer!=0) { glDeleteBuffers(1,intArrayOf(buffer),0);buffer=0;geometry=null }
                }
                val now=seconds()
                val compositor=checkNotNull(compositor)
                compositor.draw(value,width,height)
                val cpuMs=(seconds()-now)*1000
                frames++
                if(!running || now-reportTime>=1.0) {
                    event(EffectEvent.Statistics(EffectStatistics(frames,if(running)(frames-reported)/maxOf(.001,now-reportTime) else 0.0,width,height)))
                    profile(GraphicsProfile(frames,width,height,uploads=compositor.uploads,targetAllocations=compositor.targetAllocations,
                        inputTextureBytes=compositor.inputBytes,offscreenTextureBytes=compositor.targetBytes,cpuEncodeMilliseconds=cpuMs,
                        gpuMilliseconds=null,gpuTiming="GPU timing unavailable on GLES 2; use a device profiler"))
                    reported=frames;reportTime=now
                }
                return
            }
            compositor?.dispose();compositor=null
            glUseProgram(program); prepare(value)
            glClearDepthf(1f); glClear(GL_DEPTH_BUFFER_BIT); glDisable(GL_DEPTH_TEST)
            glBindBuffer(GL_ARRAY_BUFFER, 0)
            glEnableVertexAttribArray(position); glVertexAttribPointer(position,3,GL_FLOAT,false,0,triangle)
            glDisableVertexAttribArray(normal); glDisableVertexAttribArray(slot)
            val now = seconds(); val time = (clock.frame(now, running) % (kotlin.math.PI * 8)).toFloat()
            when (value) {
                is PreviewContent.Composite -> Unit // Returned above before the single-pass path.
                is PreviewContent.Image -> {
                    val e=value.adjustments; val v=value.viewport
                    val split=if(value.comparison.isFinite()) value.comparison.coerceIn(0f,1f) else .5f
                    glUniform4f(uniforms[0],width.toFloat(),height.toFloat(),0f,0f)
                    glUniform4f(uniforms[1],e.exposure,e.saturation,e.vignette,split)
                    glUniform4f(uniforms[2],value.image.width.toFloat(),value.image.height.toFloat(),v.zoom,0f)
                    glUniform4f(uniforms[3],v.x,v.y,0f,0f)
                    glActiveTexture(GL_TEXTURE0); glBindTexture(GL_TEXTURE_2D,texture); glDrawArrays(GL_TRIANGLES,0,3)
                }
                is PreviewContent.Product -> {
                    val c=value.camera
                    glUniform4f(uniforms[0],width.toFloat(),height.toFloat(),time,1f)
                    glUniform4f(uniforms[3],0f,0f,c.yaw,c.pitch)
                    glUniform4f(uniforms[4],c.distance,value.finish.ordinal.toFloat(),0f,0f)
                    glDrawArrays(GL_TRIANGLES,0,3)
                    glUniform4f(uniforms[0],width.toFloat(),height.toFloat(),time,2f)
                    glEnable(GL_DEPTH_TEST); glDepthFunc(GL_LESS); glBindBuffer(GL_ARRAY_BUFFER,buffer)
                    glVertexAttribPointer(position,3,GL_FLOAT,false,28,0)
                    glEnableVertexAttribArray(normal); glVertexAttribPointer(normal,3,GL_FLOAT,false,28,12)
                    glEnableVertexAttribArray(slot); glVertexAttribPointer(slot,1,GL_FLOAT,false,28,24)
                    glDrawArrays(GL_TRIANGLES,0,value.mesh.vertexCount)
                    glDisableVertexAttribArray(normal); glDisableVertexAttribArray(slot)
                }
            }
            glDisableVertexAttribArray(position); glBindBuffer(GL_ARRAY_BUFFER,0)
            frames++
            if (!running || now-reportTime>=1.0) {
                event(EffectEvent.Statistics(EffectStatistics(frames,if(running) (frames-reported)/maxOf(.001,now-reportTime) else 0.0,width,height)))
                reported=frames;reportTime=now
            }
        } catch (e: Exception) { program=0; fail(e) }
    }
    private fun fail(e: Exception) {
        if(e is UnsupportedAsset || e is CompositorCapability) event(EffectEvent.Failed(Failure.Unavailable(FailureMeta("GPU resources are unavailable for this composition or asset.","graphics.asset-capability"))))
        else { diagnostic(e);event(EffectEvent.Failed(Failure.Internal(FailureMeta("GPU initialization failed.","graphics.pipeline")))) }
    }
    private fun seconds() = SystemClock.elapsedRealtimeNanos()/1_000_000_000.0
    private class UnsupportedAsset : Exception()
}
