package com.pogoascent.ui

/** Every screen of the app. The Android layer maps each id to a view; navigation rules live here so they can be tested. */
enum class ScreenId {
  MAIN_MENU, WORLD_SELECT, LEVEL_SELECT, PLAYING, PAUSE, LEVEL_COMPLETE,
  SETTINGS, SETTINGS_AUDIO, SETTINGS_CONTROLS, SETTINGS_GRAPHICS, SETTINGS_GAMEPLAY,
  WARDROBE, LEADERBOARD, HOW_TO_PLAY, CREDITS, PHYSICS_TEST,
}

/**
 * Back-stack navigation. `Back` semantics: Pause → resume (pop), Level Complete → level select, Playing → Pause,
 * Main menu → exit request. Overlays (Pause/Level Complete) are drawn over the game view.
 */
class Navigator(start: ScreenId = ScreenId.MAIN_MENU) {
  private val stack = ArrayList<ScreenId>().apply { add(start) }
  val current: ScreenId get() = stack.last()
  val depth: Int get() = stack.size
  val isOverlay: Boolean get() = current == ScreenId.PAUSE || current == ScreenId.LEVEL_COMPLETE
  val isGameVisible: Boolean get() = current == ScreenId.PLAYING || isOverlay || current == ScreenId.PHYSICS_TEST

  fun contains(s: ScreenId): Boolean = s in stack

  fun push(s: ScreenId) { if (current != s) stack.add(s) }

  /** Replace the top of the stack. */
  fun replace(s: ScreenId) { stack[stack.size - 1] = s }

  /** Start a level: clear back to the world/level select that launched it, then show gameplay. */
  fun startPlaying() {
    while (stack.isNotEmpty() && (current == ScreenId.PLAYING || current == ScreenId.PAUSE || current == ScreenId.LEVEL_COMPLETE)) stack.removeAt(stack.size - 1)
    if (stack.isEmpty()) stack.add(ScreenId.MAIN_MENU)
    stack.add(ScreenId.PLAYING)
  }

  /** Go to the main menu and forget everything above it. */
  fun home() { stack.clear(); stack.add(ScreenId.MAIN_MENU) }

  /** @return false when "back" means leave the app. */
  fun back(): Boolean {
    when (current) {
      ScreenId.PLAYING -> { stack.add(ScreenId.PAUSE); return true }
      ScreenId.MAIN_MENU -> return false
      else -> {}
    }
    if (stack.size <= 1) return false
    stack.removeAt(stack.size - 1)
    return true
  }
}
