package com.pogoascent.ui

object Format {
  /** 83.4 → "1:23.4". */
  fun time(sec: Double): String {
    val t = if (sec.isNaN() || sec < 0) 0.0 else sec
    val total = (t * 10).toLong()
    val tenths = total % 10
    val s = (total / 10) % 60
    val m = total / 600
    return "$m:${if (s < 10) "0" else ""}$s.$tenths"
  }

  fun timeMs(ms: Long): String = if (ms <= 0) "--:--" else time(ms / 1000.0)

  fun percent(f: Double): String = "${(f.coerceIn(0.0, 1.0) * 100).toInt()}%"

  fun meters(m: Double): String = "%.0f m".format(m)
}
