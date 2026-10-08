package dev.mobilefoundry.graphics.gl

import android.content.res.AssetManager
import android.opengl.GLES20.*
import dev.mobilefoundry.graphics.*
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Context-owned multipass resources. All calls belong to the existing GL thread. */
internal class CompositorRenderer(assets: AssetManager) {
    private var program = 0
    private var base: RasterImage? = null
    private var overlay: AlphaImage? = null
    private val sources = IntArray(2)
    private val targets = IntArray(3)
    private val framebuffer = IntArray(1)
    private val uniforms = IntArray(5)
    private var position = 0
    private var width = 0; private var height = 0
    var uploads = 0; private set
    var targetAllocations = 0; private set
    val inputBytes get() = ((base?.let { it.width.toLong() * it.height } ?: 0) + (overlay?.let { it.width.toLong() * it.height } ?: 0)) * 4
    val targetBytes get() = width.toLong() * height * 12
    private val triangle = ByteBuffer.allocateDirect(24).order(ByteOrder.nativeOrder()).asFloatBuffer().apply {
        put(floatArrayOf(-1f,-1f,3f,-1f,-1f,3f)); position(0)
    }
    init {
        fun compile(type: Int, name: String): Int {
            val shader = glCreateShader(type)
            glShaderSource(shader, assets.open("foundry_graphics/$name").bufferedReader().use { it.readText() }); glCompileShader(shader)
            val ok = IntArray(1); glGetShaderiv(shader, GL_COMPILE_STATUS, ok, 0)
            if (ok[0] == 0) { val message = glGetShaderInfoLog(shader); glDeleteShader(shader); error(message) }
            return shader
        }
        val vertex = compile(GL_VERTEX_SHADER, "compositor.vert")
        val fragment = try { compile(GL_FRAGMENT_SHADER, "compositor.frag") } catch (e: Exception) { glDeleteShader(vertex); throw e }
        val candidate = glCreateProgram()
        try {
            glAttachShader(candidate, vertex); glAttachShader(candidate, fragment); glLinkProgram(candidate)
            val ok = IntArray(1); glGetProgramiv(candidate, GL_LINK_STATUS, ok, 0)
            check(ok[0] != 0) { glGetProgramInfoLog(candidate) }; program = candidate
        } catch (e: Exception) { glDeleteProgram(candidate); throw e }
        finally { glDeleteShader(vertex); glDeleteShader(fragment) }
        position = glGetAttribLocation(program, "position")
        listOf("viewport","layer","mask","style","imageInfo").forEachIndexed { i, name -> uniforms[i] = glGetUniformLocation(program, name) }
        glUseProgram(program)
        for (i in 0..2) glUniform1i(glGetUniformLocation(program,"source$i"),i)
        glGenFramebuffers(1,framebuffer,0)
    }
    fun dispose() {
        glDeleteTextures(2,sources,0); glDeleteTextures(3,targets,0)
        glDeleteFramebuffers(1,framebuffer,0); glDeleteProgram(program)
    }
    private fun texture(handle: Int) {
        glBindTexture(GL_TEXTURE_2D,handle)
        glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MIN_FILTER,GL_LINEAR);glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MAG_FILTER,GL_LINEAR)
        glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_WRAP_S,GL_CLAMP_TO_EDGE);glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_WRAP_T,GL_CLAMP_TO_EDGE)
    }
    fun draw(value: PreviewContent.Composite, width: Int, height: Int) {
        val maximum=IntArray(1);glGetIntegerv(GL_MAX_TEXTURE_SIZE,maximum,0)
        if (maxOf(value.base.width,value.base.height,value.overlay.width,value.overlay.height,width,height)>maximum[0]) throw CompositorCapability()
        glActiveTexture(GL_TEXTURE0)
        if (base !== value.base) {
            glDeleteTextures(1,sources,0);glGenTextures(1,sources,0);texture(sources[0])
            glTexImage2D(GL_TEXTURE_2D,0,GL_RGBA,value.base.width,value.base.height,0,GL_RGBA,GL_UNSIGNED_BYTE,value.base.linearBuffer())
            checkResource();base=value.base;uploads++
        }
        if (overlay !== value.overlay) {
            glDeleteTextures(1,sources,1);glGenTextures(1,sources,1);texture(sources[1])
            glTexImage2D(GL_TEXTURE_2D,0,GL_RGBA,value.overlay.width,value.overlay.height,0,GL_RGBA,GL_UNSIGNED_BYTE,value.overlay.linearBuffer())
            checkResource();overlay=value.overlay;uploads++
        }
        if (this.width!=width || this.height!=height) {
            glDeleteTextures(3,targets,0);glGenTextures(3,targets,0)
            for(handle in targets) { texture(handle);glTexImage2D(GL_TEXTURE_2D,0,GL_RGBA,width,height,0,GL_RGBA,GL_UNSIGNED_BYTE,null as java.nio.Buffer?);checkResource() }
            this.width=width;this.height=height;targetAllocations+=3
        }
        val original=IntArray(1);glGetIntegerv(GL_FRAMEBUFFER_BINDING,original,0)
        glUseProgram(program);glDisable(GL_DEPTH_TEST);glDisable(GL_BLEND);glBindBuffer(GL_ARRAY_BUFFER,0)
        glEnableVertexAttribArray(position);glVertexAttribPointer(position,2,GL_FLOAT,false,0,triangle)
        val s=value.settings
        glUniform4f(uniforms[1],s.layerCenter.x,s.layerCenter.y,s.scale,s.opacity)
        glUniform4f(uniforms[2],s.maskCenter.x,s.maskCenter.y,s.radius,s.feather)
        glUniform4f(uniforms[3],if(s.maskEnabled)1f else 0f,s.blend.shaderIndex,s.glow,s.comparison)
        glUniform4f(uniforms[4],value.base.width.toFloat(),value.base.height.toFloat(),value.overlay.width.toFloat(),value.overlay.height.toFloat())
        try {
            for(pass in 0..3) {
                if(pass<3) {
                    glBindFramebuffer(GL_FRAMEBUFFER,framebuffer[0]);glFramebufferTexture2D(GL_FRAMEBUFFER,GL_COLOR_ATTACHMENT0,GL_TEXTURE_2D,targets[pass],0)
                    val status=glCheckFramebufferStatus(GL_FRAMEBUFFER)
                    if(status==GL_FRAMEBUFFER_UNSUPPORTED) throw CompositorCapability()
                    check(status==GL_FRAMEBUFFER_COMPLETE) { "Incomplete compositor framebuffer: $status" }
                } else glBindFramebuffer(GL_FRAMEBUFFER,original[0])
                val first=if(pass==0)sources[1] else if(pass==3)sources[0] else targets[pass-1]
                for(i in 0..2) {
                    glActiveTexture(GL_TEXTURE0+i)
                    glBindTexture(GL_TEXTURE_2D,if(pass==3 && i==1)targets[0] else if(pass==3 && i==2)targets[2] else first)
                }
                val spread=if(pass==3)s.blur else maxOf(s.blur,if(s.glow>0f)8f else 0f)
                glUniform4f(uniforms[0],width.toFloat(),height.toFloat(),pass.toFloat(),spread)
                glViewport(0,0,width,height);glDrawArrays(GL_TRIANGLES,0,3)
                check(glGetError()==GL_NO_ERROR) { "Compositor draw failed" }
            }
        } finally { glBindFramebuffer(GL_FRAMEBUFFER,original[0]);glDisableVertexAttribArray(position);glActiveTexture(GL_TEXTURE0) }
    }
    private fun checkResource() {
        val error=glGetError()
        if(error==GL_OUT_OF_MEMORY) throw CompositorCapability()
        check(error==GL_NO_ERROR) { "Compositor resource error: $error" }
    }
}
internal class CompositorCapability : Exception()
