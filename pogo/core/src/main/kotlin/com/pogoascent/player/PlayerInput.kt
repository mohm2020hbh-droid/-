package com.pogoascent.player

/** One tick of player intent. [lean] is -1 (left) .. +1 (right); [jumpHeld] is the charge/launch button. Mutable and reused. */
class PlayerInput(var lean: Double = 0.0, var jumpHeld: Boolean = false) {
  fun set(lean: Double, jumpHeld: Boolean) { this.lean = lean; this.jumpHeld = jumpHeld }
  fun clear() { lean = 0.0; jumpHeld = false }
}
