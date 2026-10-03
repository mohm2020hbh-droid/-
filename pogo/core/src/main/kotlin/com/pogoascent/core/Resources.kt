package com.pogoascent.core

import java.io.FileNotFoundException

/** Reads bundled data files (works identically on the JVM and inside an Android APK). */
object Resources {
  fun readText(path: String): String {
    val stream = Resources::class.java.classLoader?.getResourceAsStream(path)
      ?: throw FileNotFoundException("Bundled resource not found: $path")
    return stream.use { it.readBytes().toString(Charsets.UTF_8) }
  }

  fun exists(path: String): Boolean = Resources::class.java.classLoader?.getResource(path) != null
}
