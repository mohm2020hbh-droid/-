package com.pogoascent.render

import com.pogoascent.levels.PaletteDef

/** Parsed world palette (linear 0..1 RGB floats), looked up by surface colour role. */
class Palette(def: PaletteDef) {
  val skyTop = parse(def.skyTop)
  val skyBottom = parse(def.skyBottom)
  val fog = parse(def.fog)
  val player = parse(def.player)
  val light = parse(def.light)
  private val roles: Map<String, FloatArray> = mapOf(
    "ground" to parse(def.ground), "platform" to parse(def.platform), "wall" to parse(def.wall),
    "bounce" to parse(def.bounce), "hazard" to parse(def.hazard), "special" to parse(def.special),
    "goal" to parse(def.goal), "moving" to parse(def.moving), "accent" to parse(def.accent),
  )

  fun role(name: String): FloatArray = roles[name] ?: roles.getValue("ground")

  companion object {
    fun parse(hex: String): FloatArray {
      val h = hex.trim().removePrefix("#")
      require(h.length == 6) { "colour must be #RRGGBB, was '$hex'" }
      val v = h.toInt(16)
      return floatArrayOf(((v shr 16) and 0xFF) / 255f, ((v shr 8) and 0xFF) / 255f, (v and 0xFF) / 255f)
    }
  }
}
