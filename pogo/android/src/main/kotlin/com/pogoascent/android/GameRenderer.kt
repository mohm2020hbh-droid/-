package com.pogoascent.android

import android.opengl.GLES30
import android.opengl.GLSurfaceView
import android.os.SystemClock
import com.pogoascent.app.AppCore
import com.pogoascent.render.InstanceBatch
import com.pogoascent.render.Meshes
import com.pogoascent.render.StaticMesh
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.ShortBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

/** Latest controls from the UI thread, read by the render thread every frame. */
class InputBridge {
  @Volatile var lean = 0.0
  @Volatile var jump = false
}

/**
 * OpenGL ES 3.0 renderer. Per frame: advance the game ([com.pogoascent.app.GameController]), then draw sky → merged static
 * level mesh → instanced scenery → instanced dynamic objects (movers, rider, flags, particles). Flat shading with vertex colours:
 * no textures, one draw call per mesh kind, GPU-instanced.
 */
class GameRenderer(private val core: AppCore, private val input: InputBridge) : GLSurfaceView.Renderer {
  private class Vao(val id: Int, val vbo: Int, val ibo: Int, val indexCount: Int, val instanceVbo: Int)

  private var surfaceW = 1
  private var surfaceH = 1
  private var lastNs = 0L
  private var staticProg = 0
  private var instProg = 0
  private var skyProg = 0
  private var staticVao = 0
  private var staticVbo = 0
  private var staticIbo = 0
  private var staticIndexCount = 0
  private var uploadedVersion = -1
  private val meshVaos = arrayOfNulls<Vao>(Meshes.KINDS)
  private val sceneryVaos = arrayOfNulls<Vao>(Meshes.KINDS)
  private var instBuf: FloatBuffer = ByteBuffer.allocateDirect(1024 * InstanceBatch.STRIDE * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
  private var skyVao = 0
  @Volatile var fpsCap = 60
  private var frameStartMs = 0L

  override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
    GLES30.glEnable(GLES30.GL_DEPTH_TEST)
    GLES30.glEnable(GLES30.GL_CULL_FACE)
    GLES30.glCullFace(GLES30.GL_BACK)
    GLES30.glFrontFace(GLES30.GL_CCW)
    GLES30.glEnable(GLES30.GL_BLEND)
    GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE_MINUS_SRC_ALPHA)
    staticProg = Gl.program(STATIC_VS, STATIC_FS)
    instProg = Gl.program(INST_VS, INST_FS)
    skyProg = Gl.program(SKY_VS, SKY_FS)
    // fresh GL context: everything uploaded before is gone
    uploadedVersion = -1
    for (k in 0 until Meshes.KINDS) meshVaos[k] = buildMeshVao(k, createInstanceVbo())
    val v = IntArray(1)
    GLES30.glGenVertexArrays(1, v, 0); skyVao = v[0]
    lastNs = SystemClock.elapsedRealtimeNanos()
  }

  override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
    surfaceW = width.coerceAtLeast(1); surfaceH = height.coerceAtLeast(1)
    GLES30.glViewport(0, 0, surfaceW, surfaceH)
  }

  override fun onDrawFrame(gl: GL10?) {
    frameStartMs = SystemClock.uptimeMillis()
    val now = SystemClock.elapsedRealtimeNanos()
    val dt = ((now - lastNs) / 1e9).coerceIn(0.0, 0.1)
    lastNs = now

    val controller = core.controller
    controller.input.set(input.lean, input.jump)
    controller.update(dt, surfaceW.toFloat() / surfaceH)
    val f = controller.frame

    GLES30.glClearColor(f.skyBottom[0], f.skyBottom[1], f.skyBottom[2], 1f)
    GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT or GLES30.GL_DEPTH_BUFFER_BIT)

    if (f.hasScene) {
      drawSky(f)
      if (uploadedVersion != f.sceneVersion) uploadScene(f)
      drawStatic(f)
      f.scenery?.let { drawScenery(it, f) }
      drawInstances(f.dynamic.cube, meshVaos[Meshes.CUBE], f)
      drawInstances(f.dynamic.cylinder, meshVaos[Meshes.CYLINDER], f)
      drawInstances(f.dynamic.sphere, meshVaos[Meshes.SPHERE], f)
    }

    // optional 30 FPS cap (battery saver)
    if (fpsCap <= 30) {
      val elapsed = SystemClock.uptimeMillis() - frameStartMs
      val wait = 33 - elapsed
      if (wait > 1) try { Thread.sleep(wait) } catch (_: InterruptedException) {}
    }
  }

  // ---------------------------------------------------------------------------------------------

  private fun drawSky(f: com.pogoascent.app.RenderFrame) {
    GLES30.glDisable(GLES30.GL_DEPTH_TEST)
    GLES30.glDisable(GLES30.GL_CULL_FACE)
    GLES30.glUseProgram(skyProg)
    GLES30.glUniform3fv(GLES30.glGetUniformLocation(skyProg, "uTop"), 1, f.skyTop, 0)
    GLES30.glUniform3fv(GLES30.glGetUniformLocation(skyProg, "uBottom"), 1, f.skyBottom, 0)
    GLES30.glBindVertexArray(skyVao)
    GLES30.glDrawArrays(GLES30.GL_TRIANGLES, 0, 3)
    GLES30.glEnable(GLES30.GL_DEPTH_TEST)
    GLES30.glEnable(GLES30.GL_CULL_FACE)
  }

  private fun setCommon(prog: Int, f: com.pogoascent.app.RenderFrame) {
    GLES30.glUniformMatrix4fv(GLES30.glGetUniformLocation(prog, "uVP"), 1, false, f.viewProj, 0)
    GLES30.glUniform3f(GLES30.glGetUniformLocation(prog, "uCam"), f.camX, f.camY, f.camZ)
    GLES30.glUniform3fv(GLES30.glGetUniformLocation(prog, "uFog"), 1, f.fog, 0)
    GLES30.glUniform3fv(GLES30.glGetUniformLocation(prog, "uLight"), 1, f.light, 0)
  }

  private fun drawStatic(f: com.pogoascent.app.RenderFrame) {
    if (staticIndexCount == 0) return
    GLES30.glUseProgram(staticProg)
    setCommon(staticProg, f)
    GLES30.glBindVertexArray(staticVao)
    GLES30.glDrawElements(GLES30.GL_TRIANGLES, staticIndexCount, GLES30.GL_UNSIGNED_SHORT, 0)
  }

  private fun drawScenery(b: com.pogoascent.render.RenderBatches, f: com.pogoascent.app.RenderFrame) {
    for (k in 0 until Meshes.KINDS) {
      val batch = b.batch(k)
      val vao = sceneryVaos[k] ?: continue
      if (batch.count == 0) continue
      GLES30.glUseProgram(instProg)
      setCommon(instProg, f)
      GLES30.glBindVertexArray(vao.id)
      GLES30.glDrawElementsInstanced(GLES30.GL_TRIANGLES, vao.indexCount, GLES30.GL_UNSIGNED_SHORT, 0, batch.count)
    }
  }

  private fun drawInstances(batch: InstanceBatch, vao: Vao?, f: com.pogoascent.app.RenderFrame) {
    if (vao == null || batch.count == 0) return
    uploadInstances(vao.instanceVbo, batch)
    GLES30.glUseProgram(instProg)
    setCommon(instProg, f)
    GLES30.glBindVertexArray(vao.id)
    GLES30.glDrawElementsInstanced(GLES30.GL_TRIANGLES, vao.indexCount, GLES30.GL_UNSIGNED_SHORT, 0, batch.count)
  }

  private fun uploadInstances(vbo: Int, batch: InstanceBatch) {
    val floats = batch.count * InstanceBatch.STRIDE
    if (instBuf.capacity() < floats) instBuf = ByteBuffer.allocateDirect(floats * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
    instBuf.clear(); instBuf.put(batch.data, 0, floats); instBuf.flip()
    GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vbo)
    GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, floats * 4, instBuf, GLES30.GL_STREAM_DRAW) // orphan + refill
  }

  private fun uploadScene(f: com.pogoascent.app.RenderFrame) {
    // static level mesh
    val m = f.staticMesh
    if (staticVao != 0) { GLES30.glDeleteVertexArrays(1, intArrayOf(staticVao), 0); GLES30.glDeleteBuffers(2, intArrayOf(staticVbo, staticIbo), 0) }
    if (m != null) {
      val ids = IntArray(1)
      GLES30.glGenVertexArrays(1, ids, 0); staticVao = ids[0]
      val bufs = IntArray(2)
      GLES30.glGenBuffers(2, bufs, 0); staticVbo = bufs[0]; staticIbo = bufs[1]
      GLES30.glBindVertexArray(staticVao)
      GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, staticVbo)
      GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, m.vertices.size * 4, floatBuffer(m.vertices), GLES30.GL_STATIC_DRAW)
      val stride = StaticMesh.FLOATS_PER_VERTEX * 4
      GLES30.glEnableVertexAttribArray(0); GLES30.glVertexAttribPointer(0, 3, GLES30.GL_FLOAT, false, stride, 0)
      GLES30.glEnableVertexAttribArray(1); GLES30.glVertexAttribPointer(1, 3, GLES30.GL_FLOAT, false, stride, 12)
      GLES30.glEnableVertexAttribArray(2); GLES30.glVertexAttribPointer(2, 3, GLES30.GL_FLOAT, false, stride, 24)
      GLES30.glBindBuffer(GLES30.GL_ELEMENT_ARRAY_BUFFER, staticIbo)
      GLES30.glBufferData(GLES30.GL_ELEMENT_ARRAY_BUFFER, m.indices.size * 2, shortBuffer(m.indices), GLES30.GL_STATIC_DRAW)
      staticIndexCount = m.indices.size
    } else { staticVao = 0; staticIndexCount = 0 }

    // static scenery instances (uploaded once per level)
    for (k in 0 until Meshes.KINDS) {
      sceneryVaos[k]?.let { GLES30.glDeleteVertexArrays(1, intArrayOf(it.id), 0); GLES30.glDeleteBuffers(3, intArrayOf(it.vbo, it.ibo, it.instanceVbo), 0) }
      val batch = f.scenery?.batch(k)
      if (batch == null || batch.count == 0) { sceneryVaos[k] = null; continue }
      val inst = createInstanceVbo()
      sceneryVaos[k] = buildMeshVao(k, inst)
      uploadInstances(inst, batch)
    }
    GLES30.glBindVertexArray(0)
    uploadedVersion = f.sceneVersion
  }

  private fun createInstanceVbo(): Int { val b = IntArray(1); GLES30.glGenBuffers(1, b, 0); return b[0] }

  /** VAO = unit mesh (pos+normal, locations 0/1) + per-instance model matrix (2..5) and colour (6). */
  private fun buildMeshVao(kind: Int, instanceVbo: Int): Vao {
    val mesh = Meshes.get(kind)
    val inter = FloatArray(mesh.vertexCount * 6)
    for (i in 0 until mesh.vertexCount) {
      for (k in 0 until 3) { inter[i * 6 + k] = mesh.positions[i * 3 + k]; inter[i * 6 + 3 + k] = mesh.normals[i * 3 + k] }
    }
    val vaoId = IntArray(1); GLES30.glGenVertexArrays(1, vaoId, 0)
    val bufs = IntArray(2); GLES30.glGenBuffers(2, bufs, 0)
    GLES30.glBindVertexArray(vaoId[0])
    GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, bufs[0])
    GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, inter.size * 4, floatBuffer(inter), GLES30.GL_STATIC_DRAW)
    GLES30.glEnableVertexAttribArray(0); GLES30.glVertexAttribPointer(0, 3, GLES30.GL_FLOAT, false, 24, 0)
    GLES30.glEnableVertexAttribArray(1); GLES30.glVertexAttribPointer(1, 3, GLES30.GL_FLOAT, false, 24, 12)
    GLES30.glBindBuffer(GLES30.GL_ELEMENT_ARRAY_BUFFER, bufs[1])
    GLES30.glBufferData(GLES30.GL_ELEMENT_ARRAY_BUFFER, mesh.indices.size * 2, shortBuffer(mesh.indices), GLES30.GL_STATIC_DRAW)
    GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, instanceVbo)
    val stride = InstanceBatch.STRIDE * 4
    for (c in 0 until 4) {
      GLES30.glEnableVertexAttribArray(2 + c)
      GLES30.glVertexAttribPointer(2 + c, 4, GLES30.GL_FLOAT, false, stride, c * 16)
      GLES30.glVertexAttribDivisor(2 + c, 1)
    }
    GLES30.glEnableVertexAttribArray(6)
    GLES30.glVertexAttribPointer(6, 4, GLES30.GL_FLOAT, false, stride, 64)
    GLES30.glVertexAttribDivisor(6, 1)
    GLES30.glBindVertexArray(0)
    return Vao(vaoId[0], bufs[0], bufs[1], mesh.indices.size, instanceVbo)
  }

  private fun floatBuffer(a: FloatArray): FloatBuffer =
    ByteBuffer.allocateDirect(a.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer().apply { put(a); position(0) }

  private fun shortBuffer(a: ShortArray): ShortBuffer =
    ByteBuffer.allocateDirect(a.size * 2).order(ByteOrder.nativeOrder()).asShortBuffer().apply { put(a); position(0) }

  companion object {
    private const val FOG = """
      vec3 applyFog(vec3 c, vec3 world) {
        float d = length(world - uCam);
        float f = clamp((d - 28.0) / 120.0, 0.0, 0.6);
        return mix(c, uFog, f);
      }
    """

    const val STATIC_VS = """#version 300 es
      layout(location=0) in vec3 aPos; layout(location=1) in vec3 aNormal; layout(location=2) in vec3 aColor;
      uniform mat4 uVP;
      out vec3 vNormal; out vec3 vColor; out vec3 vWorld;
      void main() { vNormal = aNormal; vColor = aColor; vWorld = aPos; gl_Position = uVP * vec4(aPos, 1.0); }
    """
    const val STATIC_FS = """#version 300 es
      precision mediump float;
      in vec3 vNormal; in vec3 vColor; in vec3 vWorld;
      uniform vec3 uCam; uniform vec3 uFog; uniform vec3 uLight;
      out vec4 outColor;
      $FOG
      void main() {
        vec3 n = normalize(vNormal);
        float d = max(dot(n, normalize(vec3(-0.35, 0.75, 0.55))), 0.0);
        vec3 lit = vColor * uLight * (0.42 + 0.58 * d);
        outColor = vec4(applyFog(lit, vWorld), 1.0);
      }
    """
    const val INST_VS = """#version 300 es
      layout(location=0) in vec3 aPos; layout(location=1) in vec3 aNormal;
      layout(location=2) in vec4 aM0; layout(location=3) in vec4 aM1; layout(location=4) in vec4 aM2; layout(location=5) in vec4 aM3;
      layout(location=6) in vec4 aColor;
      uniform mat4 uVP;
      out vec3 vNormal; out vec4 vColor; out vec3 vWorld;
      void main() {
        mat4 model = mat4(aM0, aM1, aM2, aM3);
        vec4 w = model * vec4(aPos, 1.0);
        // inverse-transpose of (rotation * scale): divide each column by its squared length
        mat3 nm = mat3(aM0.xyz / dot(aM0.xyz, aM0.xyz), aM1.xyz / dot(aM1.xyz, aM1.xyz), aM2.xyz / dot(aM2.xyz, aM2.xyz));
        vNormal = nm * aNormal; vColor = aColor; vWorld = w.xyz;
        gl_Position = uVP * w;
      }
    """
    const val INST_FS = """#version 300 es
      precision mediump float;
      in vec3 vNormal; in vec4 vColor; in vec3 vWorld;
      uniform vec3 uCam; uniform vec3 uFog; uniform vec3 uLight;
      out vec4 outColor;
      $FOG
      void main() {
        vec3 n = normalize(vNormal);
        float d = max(dot(n, normalize(vec3(-0.35, 0.75, 0.55))), 0.0);
        vec3 lit = vColor.rgb * uLight * (0.42 + 0.58 * d);
        outColor = vec4(applyFog(lit, vWorld), vColor.a);
      }
    """
    const val SKY_VS = """#version 300 es
      out float vT;
      void main() {
        vec2 p = vec2(float((gl_VertexID << 1) & 2), float(gl_VertexID & 2));
        vT = p.y * 0.5;
        gl_Position = vec4(p * 2.0 - 1.0, 0.999, 1.0);
      }
    """
    const val SKY_FS = """#version 300 es
      precision mediump float;
      in float vT;
      uniform vec3 uTop; uniform vec3 uBottom;
      out vec4 outColor;
      void main() { outColor = vec4(mix(uBottom, uTop, clamp(vT, 0.0, 1.0)), 1.0); }
    """
  }
}
