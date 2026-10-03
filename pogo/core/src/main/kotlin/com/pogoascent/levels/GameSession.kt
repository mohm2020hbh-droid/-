package com.pogoascent.levels

import com.pogoascent.camera.CameraConfig
import com.pogoascent.camera.CameraRig
import com.pogoascent.camera.CameraUserSettings
import com.pogoascent.core.FixedStepper
import com.pogoascent.physics.PhysicsConfig
import com.pogoascent.player.EventType
import com.pogoascent.player.GameEvent
import com.pogoascent.player.PlayerInput
import com.pogoascent.player.PogoPlayer
import kotlin.math.abs
import kotlin.math.hypot

enum class SessionPhase { READY, PLAYING, COMPLETE }

/** What a finished level reports to the progression / save systems. */
class LevelResult(
  val levelId: String,
  val timeSec: Double,
  val jumps: Int,
  val boosts: Int,
  val falls: Int,
  val resets: Int,
  val bestHeight: Double,
)

/**
 * One play-through of one level: world + rider + camera + rules + stats. UI code only sets [input] and calls [update];
 * everything else (audio, haptics, particles, HUD) listens to [GameEvent]s via [addListener].
 */
class GameSession(
  val level: LoadedLevel,
  physics: PhysicsConfig,
  cameraConfig: CameraConfig = CameraConfig(),
) {
  var physics: PhysicsConfig = physics
    set(value) {
      field = value
      player.config = value
      stepper.setHz(value.fixedTimestepHz)
    }

  val input = PlayerInput()
  val camera = CameraRig(cameraConfig)
  var cameraUser = CameraUserSettings()
  var phase = SessionPhase.READY; private set
  var paused = false

  /** Seconds since the first jump of this attempt. */
  var timeSec = 0.0; private set
  var falls = 0; private set
  var resets = 0; private set
  var result: LevelResult? = null; private set

  private val listeners = ArrayList<(GameEvent) -> Unit>()
  private val stepper = FixedStepper(physics.fixedTimestepHz, physics.maxFrameDelta, physics.maxStepsPerFrame)
  val player: PogoPlayer = PogoPlayer(physics, level.world) { onPlayerEvent(it) }

  private var respawnRequested = false
  private var respawnX = level.startX
  private var respawnY = level.startY
  private var bestYSinceCheckpoint = level.startY
  private var stillTimer = 0.0
  private var checkpointIds = HashSet<String>()

  init { resetLevel() }

  val renderAlpha: Double get() = stepper.alpha

  /** 0..1 climb progress of the current position / the best position this attempt. */
  val progress: Double get() = level.progressAt(player.tipWorldY)
  var bestProgress = 0.0; private set

  fun checkpointReached(id: String): Boolean = id in checkpointIds

  fun addListener(l: (GameEvent) -> Unit) { listeners += l }
  fun removeListener(l: (GameEvent) -> Unit) { listeners -= l }

  /** Back to the start with a fresh timer and stats. */
  fun resetLevel() {
    level.world.reset()
    checkpointIds = HashSet()
    respawnX = level.startX; respawnY = level.startY
    phase = SessionPhase.READY
    timeSec = 0.0; falls = 0; resets = 0; result = null; bestProgress = 0.0
    bestYSinceCheckpoint = level.startY
    stillTimer = 0.0
    respawnRequested = false
    player.resetStats()
    player.spawn(respawnX, respawnY)
    stepper.reset()
    camera.snapTo(player.x, player.y)
  }

  /** Back to the last checkpoint; the clock keeps running. */
  fun respawn() {
    resets++
    player.spawn(respawnX, respawnY)
    bestYSinceCheckpoint = respawnY
    stillTimer = 0.0
    respawnRequested = false
    camera.snapTo(player.x, player.y)
    stepper.reset()
  }

  /**
   * Advance by a real frame time. Runs as many fixed physics ticks as needed, then updates the camera with the
   * interpolated pose. Safe to call every frame at any frame rate.
   */
  fun update(frameDt: Double) {
    if (paused || phase == SessionPhase.COMPLETE) return
    val ticks = stepper.advance(frameDt)
    for (i in 0 until ticks) {
      player.tick(input)
      if (phase == SessionPhase.PLAYING) timeSec += physics.fixedDt
      watchStuck()
      if (respawnRequested || player.killed) { respawn(); break }
      if (phase == SessionPhase.COMPLETE) break
    }
    val a = stepper.alpha
    val px = player.renderX(a)
    val py = player.renderY(a)
    camera.update(frameDt, px, py, player.vx, player.vy, cameraUser)
    val p = level.progressAt(player.tipWorldY)
    if (p > bestProgress) bestProgress = p
  }

  private fun watchStuck() {
    if (!player.grounded && hypot(player.vx, player.vy) < 0.4 && abs(player.omega) < 0.5) stillTimer += physics.fixedDt else stillTimer = 0.0
    if (stillTimer > level.data.rules.stuckSeconds) respawnRequested = true
  }

  private fun onPlayerEvent(e: GameEvent) {
    when (e.type) {
      EventType.LAUNCH -> if (phase == SessionPhase.READY) phase = SessionPhase.PLAYING
      EventType.LAND -> {
        val y = player.tipWorldY
        if (y > bestYSinceCheckpoint) bestYSinceCheckpoint = y
        else if (bestYSinceCheckpoint - y >= level.data.rules.fallLossHeight) { falls++; bestYSinceCheckpoint = y }
      }
      EventType.CHECKPOINT -> {
        val cp = level.data.checkpoints.firstOrNull { it.id == e.tag }
        if (cp != null && checkpointIds.add(cp.id)) {
          respawnX = cp.respawnX; respawnY = cp.respawnY
          bestYSinceCheckpoint = maxOf(bestYSinceCheckpoint, cp.respawnY)
        }
      }
      EventType.HAZARD -> respawnRequested = true
      EventType.GOAL -> if (phase != SessionPhase.COMPLETE) {
        phase = SessionPhase.COMPLETE
        result = LevelResult(level.data.id, timeSec, player.jumpCount, player.boostCount, falls, resets, player.maxHeight)
      }
      else -> {}
    }
    camera.onEvent(e)
    for (i in listeners.indices) listeners[i](e)
  }
}
