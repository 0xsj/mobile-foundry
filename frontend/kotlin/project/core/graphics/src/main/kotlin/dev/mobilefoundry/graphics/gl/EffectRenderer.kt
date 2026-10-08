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

/** All methods and GPU handles belong to GLSurfaceView's render thread. */
internal class EffectRenderer(private val assets: AssetManager, private val event: (EffectEvent) -> Unit,
                              private val diagnostic: (Exception) -> Unit) : GLSurfaceView.Renderer {
    var settings = EffectSettings.make()
    var running = false
    val clock = EffectClock()
    private var program = 0
    private var position = 0
    private var viewport = 0
    private var interaction = 0
    private var width = 1
    private var height = 1
    private var frames = 0
    private var reportFrames = 0
    private var reportTime = 0.0
    private val vertices = ByteBuffer.allocateDirect(6 * 4).order(ByteOrder.nativeOrder()).asFloatBuffer().apply {
        put(floatArrayOf(-1f, -1f, 3f, -1f, -1f, 3f)); position(0)
    }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        // The preceding EGL context was released; its integer handles are no longer valid.
        program = 0; clock.suspend(); reportTime = seconds(); reportFrames = frames
        try {
            val vertex = compile(GL_VERTEX_SHADER, "effects.vert")
            val fragment = try { compile(GL_FRAGMENT_SHADER, "effects.frag") } catch (error: Exception) { glDeleteShader(vertex); throw error }
            val candidate = glCreateProgram()
            try {
                glAttachShader(candidate, vertex); glAttachShader(candidate, fragment); glLinkProgram(candidate)
                val status = IntArray(1); glGetProgramiv(candidate, GL_LINK_STATUS, status, 0)
                check(status[0] != 0) { "GPU link failed: ${glGetProgramInfoLog(candidate)}" }
                program = candidate
            } catch (error: Exception) { glDeleteProgram(candidate); throw error }
            finally { glDeleteShader(vertex); glDeleteShader(fragment) }
            position = glGetAttribLocation(program, "position")
            viewport = glGetUniformLocation(program, "viewport")
            interaction = glGetUniformLocation(program, "interaction")
            event(EffectEvent.Ready("OpenGL ES · ${glGetString(GL_RENDERER)}"))
        } catch (error: Exception) {
            diagnostic(error)
            event(EffectEvent.Failed(Failure.Internal(FailureMeta("GPU initialization failed.", "graphics.pipeline"))))
        }
    }

    private fun compile(kind: Int, file: String): Int {
        val source = assets.open("foundry_graphics/$file").bufferedReader().use { it.readText() }
        val shader = glCreateShader(kind)
        glShaderSource(shader, source); glCompileShader(shader)
        val status = IntArray(1); glGetShaderiv(shader, GL_COMPILE_STATUS, status, 0)
        if (status[0] == 0) {
            val detail = glGetShaderInfoLog(shader); glDeleteShader(shader)
            error("GPU shader $file failed: $detail")
        }
        return shader
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        this.width = width; this.height = height; glViewport(0, 0, width, height)
    }

    override fun onDrawFrame(gl: GL10?) {
        if (program == 0 || width <= 0 || height <= 0) return
        val now = seconds()
        val time = clock.frame(now, running)
        glUseProgram(program)
        glUniform4f(viewport, width.toFloat(), height.toFloat(), time.toFloat(), settings.strength)
        glUniform4f(interaction, settings.point.x, settings.point.y, if (settings.effect == EffectKind.RIPPLE) 0f else 1f, settings.quality.steps.toFloat())
        glEnableVertexAttribArray(position)
        glVertexAttribPointer(position, 2, GL_FLOAT, false, 0, vertices)
        glDrawArrays(GL_TRIANGLES, 0, 3); glDisableVertexAttribArray(position)
        frames++
        if (!running || now - reportTime >= 1.0) {
            val rate = if (running) (frames - reportFrames) / maxOf(.001, now - reportTime) else 0.0
            event(EffectEvent.Statistics(EffectStatistics(frames, rate, width, height)))
            reportFrames = frames; reportTime = now
        }
    }
    private fun seconds() = SystemClock.elapsedRealtimeNanos() / 1_000_000_000.0
}
