package com.pogoascent.android

import android.opengl.GLES30
import android.util.Log

object Gl {
  private const val TAG = "PogoGL"

  fun compile(type: Int, src: String): Int {
    val s = GLES30.glCreateShader(type)
    GLES30.glShaderSource(s, src.trimIndent())
    GLES30.glCompileShader(s)
    val ok = IntArray(1)
    GLES30.glGetShaderiv(s, GLES30.GL_COMPILE_STATUS, ok, 0)
    if (ok[0] == 0) {
      val log = GLES30.glGetShaderInfoLog(s)
      GLES30.glDeleteShader(s)
      Log.e(TAG, "shader compile failed: $log")
      throw RuntimeException("Shader compile failed: $log")
    }
    return s
  }

  fun program(vs: String, fs: String): Int {
    val v = compile(GLES30.GL_VERTEX_SHADER, vs)
    val f = compile(GLES30.GL_FRAGMENT_SHADER, fs)
    val p = GLES30.glCreateProgram()
    GLES30.glAttachShader(p, v); GLES30.glAttachShader(p, f)
    GLES30.glLinkProgram(p)
    val ok = IntArray(1)
    GLES30.glGetProgramiv(p, GLES30.GL_LINK_STATUS, ok, 0)
    if (ok[0] == 0) {
      val log = GLES30.glGetProgramInfoLog(p)
      GLES30.glDeleteProgram(p)
      Log.e(TAG, "program link failed: $log")
      throw RuntimeException("Program link failed: $log")
    }
    GLES30.glDeleteShader(v); GLES30.glDeleteShader(f)
    return p
  }
}
