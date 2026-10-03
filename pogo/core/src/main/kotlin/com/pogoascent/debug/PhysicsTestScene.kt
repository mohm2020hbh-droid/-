package com.pogoascent.debug

import com.pogoascent.core.FixedStepper
import com.pogoascent.levels.GameSession
import com.pogoascent.levels.GoalDef
import com.pogoascent.levels.LevelData
import com.pogoascent.levels.LevelLoader
import com.pogoascent.levels.PointDef
import com.pogoascent.levels.SolidDef
import com.pogoascent.physics.PhysicsConfig
import com.pogoascent.physics.SurfaceCatalog
import com.pogoascent.player.DebugSnapshot
import com.pogoascent.player.EventType
import com.pogoascent.player.PlayerInput
import kotlin.math.PI
import kotlin.math.hypot

enum class ScenarioKind(val label: String) {
  TEST_JUMP("Test Jump"), TEST_BOOST("Test Boost"), TEST_BOUNCE("Test Bounce"), TEST_FALL("Test Fall"),
}

/** Numbers measured by a scenario, in a form that can be shown on screen, printed by the CLI and asserted in tests. */
class ScenarioResult(val kind: ScenarioKind, val values: LinkedHashMap<String, Double>, val notes: List<String>) {
  operator fun get(key: String): Double = values[key] ?: Double.NaN
  override fun toString(): String = buildString {
    append(kind.label).append(":")
    for ((k, v) in values) append("\n  ").append(k).append(" = ").append("%.3f".format(v))
    for (n in notes) append("\n  note: ").append(n)
  }
}

/**
 * The "Physics Test Scene": an arena with a floor, walls, a bounce pad and a tall tower, a manual mode, and four scripted
 * scenarios (Test Jump / Boost / Bounce / Fall) that drive the real player through the same input path as a human. The
 * live [DebugSnapshot] exposes everything needed to compare the game against analysis data once it exists.
 */
class PhysicsTestScene(private val surfaces: SurfaceCatalog, physics: PhysicsConfig) {
  companion object {
    const val PAD_X = -10.0
    const val WALL_X = 17.0
    const val TOWER_X = 10.0
    const val TOWER_TOP = 24.0

    fun arena(): LevelData = LevelData(
      id = "physics_test", name = "Physics Test Scene", world = "world_1", theme = "meadow", difficulty = 1,
      start = PointDef(0.0, 0.0), goal = GoalDef(0.0, 90.0, 4.0), killY = -30.0,
      platforms = listOf(
        SolidDef("floor", x = 0.0, y = -1.0, w = 60.0, h = 2.0, surface = "stone"),
        SolidDef("tower", x = TOWER_X, y = TOWER_TOP / 2 - 0.5, w = 4.0, h = TOWER_TOP + 1.0, surface = "stone"),
        SolidDef("ice_patch", x = -20.0, y = 0.3, w = 6.0, h = 0.6, surface = "ice"),
      ),
      obstacles = listOf(
        SolidDef("wall_r", x = WALL_X + 1.5, y = 20.0, w = 3.0, h = 44.0, surface = "wall"),
        SolidDef("wall_l", x = -29.0, y = 20.0, w = 3.0, h = 44.0, surface = "wall"),
      ),
      bounceObjects = listOf(SolidDef("pad", x = PAD_X, y = 0.3, w = 4.0, h = 0.6, surface = "bounce")),
      notes = "Arena for measuring jump, boost, bounce, fall and wall response.",
    )
  }

  var session = GameSession(LevelLoader.load(arena(), surfaces), physics); private set
  val manualInput = PlayerInput()
  private val stepper = FixedStepper(physics.fixedTimestepHz, physics.maxFrameDelta, physics.maxStepsPerFrame)
  private var scenario: Scenario? = null
  var lastResult: ScenarioResult? = null; private set
  var status = "Manual"; private set
  val activeScenario: ScenarioKind? get() = scenario?.kind

  val physics: PhysicsConfig get() = session.physics

  /** Live physics tuning from the debug UI. */
  fun setPhysics(p: PhysicsConfig) { session.physics = p; stepper.setHz(p.fixedTimestepHz) }

  fun reset() {
    scenario = null
    status = "Manual"
    session.resetLevel()
    session.player.spawn(0.0, 0.0)
    session.camera.snapTo(session.player.x, session.player.y)
  }

  fun start(kind: ScenarioKind) {
    reset()
    status = "Running ${kind.label}"
    scenario = when (kind) {
      ScenarioKind.TEST_JUMP -> JumpScenario(this)
      ScenarioKind.TEST_BOOST -> BoostScenario(this)
      ScenarioKind.TEST_BOUNCE -> BounceScenario(this)
      ScenarioKind.TEST_FALL -> FallScenario(this)
    }
  }

  /** Real-time update (render loop). */
  fun update(frameDt: Double) {
    val ticks = stepper.advance(frameDt)
    repeat(ticks) { tickOnce() }
  }

  /** Deterministic single tick (headless runs and tests). */
  fun tickOnce() {
    val sc = scenario
    if (sc != null) {
      sc.drive(session.input)
      session.update(session.physics.fixedDt)
      sc.observe()
      if (sc.finished) {
        lastResult = sc.result()
        status = "Done: ${sc.kind.label}"
        scenario = null
        session.input.clear()
      }
    } else {
      session.input.set(manualInput.lean, manualInput.jumpHeld)
      session.update(session.physics.fixedDt)
    }
  }

  /** Runs [kind] to completion headlessly and returns its measurements. */
  fun runToCompletion(kind: ScenarioKind, maxSeconds: Double = 30.0): ScenarioResult {
    start(kind)
    val limit = (maxSeconds * session.physics.fixedTimestepHz).toInt()
    var n = 0
    while (scenario != null && n++ < limit) tickOnce()
    return lastResult ?: ScenarioResult(kind, LinkedHashMap(), listOf("timed out after $maxSeconds s"))
  }

  fun snapshot(): DebugSnapshot = session.player.debugSnapshot()

  // ---- scenarios ------------------------------------------------------------------------------

  private abstract class Scenario(val kind: ScenarioKind, val scene: PhysicsTestScene) {
    var finished = false
    val values = LinkedHashMap<String, Double>()
    val notes = ArrayList<String>()
    protected val player get() = scene.session.player
    protected val cfg get() = scene.session.physics
    protected var tick = 0
    abstract fun drive(input: PlayerInput)
    open fun observe() { tick++ }
    fun result() = ScenarioResult(kind, values, notes)
    protected fun wrap(a: Double): Double { var w = a % (2 * PI); if (w > PI) w -= 2 * PI; if (w < -PI) w += 2 * PI; return w }
    protected fun levelLean(): Double = (-(wrap(player.angle) * 4.0 + player.omega) / cfg.rotationSpeed).coerceIn(-1.0, 1.0)
  }

  private class JumpScenario(scene: PhysicsTestScene) : Scenario(ScenarioKind.TEST_JUMP, scene) {
    private var phase = 0
    private var apex = 0.0
    private var startY = 0.0
    private var launchTick = 0
    private var landSpeed = 0.0
    init { scene.session.addListener { e ->
      if (e.type == EventType.LAUNCH && !values.containsKey("launchSpeed")) { values["launchSpeed"] = e.magnitude; launchTick = tick; startY = player.y }
      if (e.type == EventType.LAND && values.containsKey("launchSpeed") && !values.containsKey("landingSpeed")) landSpeed = e.magnitude
    } }
    override fun drive(input: PlayerInput) {
      when (phase) {
        0 -> { input.set(0.0, true); if (tick >= cfg.jumpChargeTime * cfg.fixedTimestepHz + 6) phase = 1 }
        1 -> { input.set(0.0, false); phase = 2 }
        else -> input.set(levelLean(), false)
      }
    }
    override fun observe() {
      super.observe()
      if (phase >= 2) {
        if (player.y > apex) apex = player.y
        if (player.grounded && tick > launchTick + 5) {
          values["apexHeight"] = apex - startY
          values["analyticApex"] = cfg.jumpPower * cfg.jumpPower / (2 * cfg.gravity)
          values["flightTime"] = (tick - launchTick) / cfg.fixedTimestepHz
          values["landingSpeed"] = landSpeed
          finished = true
        }
      }
    }
  }

  private class BoostScenario(scene: PhysicsTestScene) : Scenario(ScenarioKind.TEST_BOOST, scene) {
    private var phase = 0
    private var firstLaunch = true
    private var boostLaunch = 0.0
    private var armedRotationDeg = 0.0
    private var apex = 0.0
    private var normalLaunch = 0.0
    private var waited = 0
    init { scene.session.addListener { e ->
      if (e.type == EventType.BOOST_ARMED) armedRotationDeg = e.magnitude
      if (e.type == EventType.LAUNCH) {
        if (firstLaunch) { firstLaunch = false; normalLaunch = e.magnitude } else boostLaunch = e.magnitude
      }
    } }
    override fun drive(input: PlayerInput) {
      when (phase) {
        0 -> { input.set(0.0, true); if (tick >= 0.95 * cfg.jumpChargeTime * cfg.fixedTimestepHz) phase = 1 }
        1 -> { input.set(0.0, false); phase = 2 }
        2 -> { // spin until we have comfortably passed the boost threshold, then level out while holding for the landing
          input.set(1.0, false)
          if (Math.toDegrees(player.rotationAccum) > cfg.boostThreshold + 20.0) phase = 3
        }
        3 -> { input.set(levelLean(), true); if (player.grounded) { phase = 4; waited = 0 } }
        4 -> { // charge to full and release
          input.set(0.0, true)
          waited++
          if (waited > cfg.jumpChargeTime * cfg.fixedTimestepHz + 6) phase = 5
        }
        5 -> { input.set(0.0, false); phase = 6; apex = player.y }
        else -> input.set(levelLean(), false)
      }
    }
    override fun observe() {
      super.observe()
      if (phase == 4 && !values.containsKey("boostArmedRotationDeg")) values["boostArmedRotationDeg"] = armedRotationDeg
      if (phase == 6) {
        if (player.y > apex) apex = player.y
        if (player.grounded || tick > 3000) {
          values["boostThresholdDeg"] = cfg.boostThreshold
          values["normalFullChargeLaunch"] = cfg.jumpPower
          values["boostLaunchSpeed"] = boostLaunch
          values["boostMultiplier"] = if (cfg.jumpPower > 0) boostLaunch / cfg.jumpPower else Double.NaN
          values["boostCount"] = player.boostCount.toDouble()
          if (player.boostCount == 0) notes += "boost never fired (rotation was not enough or the button was not held at touchdown)"
          values["firstJumpLaunch"] = normalLaunch
          finished = true
        }
      }
    }
  }

  private class BounceScenario(scene: PhysicsTestScene) : Scenario(ScenarioKind.TEST_BOUNCE, scene) {
    private var apex = 0.0
    private var bounced = false
    private var dropHeight = 0.0
    init {
      scene.session.player.spawn(PAD_X, 12.0) // drops onto the pad
      dropHeight = scene.session.player.y
      scene.session.addListener { e ->
        if (e.type == EventType.BOUNCE && !bounced) { bounced = true; values["impactSpeed"] = e.magnitude }
      }
    }
    override fun drive(input: PlayerInput) { input.set(0.0, false) }
    override fun observe() {
      super.observe()
      if (bounced) {
        if (player.y > apex) apex = player.y
        if (player.vy < -1.0 && tick > 10) {
          values["reboundApexHeight"] = apex - (0.6 + cfg.tipOffset)
          values["dropHeight"] = dropHeight - (0.6 + cfg.tipOffset)
          values["reboundSpeedAtLaunch"] = hypot(0.0, kotlin.math.sqrt(2 * cfg.gravity * (apex - (0.6 + cfg.tipOffset))))
          finished = true
        }
      } else if (tick > 1200) { notes += "never touched the pad"; finished = true }
    }
  }

  private class FallScenario(scene: PhysicsTestScene) : Scenario(ScenarioKind.TEST_FALL, scene) {
    private var maxFall = 0.0
    private var landing = 0.0
    private var landed = false
    private var fallEvents = 0
    private val dropFrom = 40.0
    init {
      scene.session.player.spawn(0.0, dropFrom)
      scene.session.addListener { e ->
        if (e.type == EventType.LAND && !landed) { landed = true; landing = e.magnitude }
        if (e.type == EventType.FALL) fallEvents++
      }
    }
    override fun drive(input: PlayerInput) { input.set(0.0, false) }
    override fun observe() {
      super.observe()
      if (-player.vy > maxFall) maxFall = -player.vy
      if (landed) {
        values["dropHeight"] = dropFrom
        values["maxFallSpeed"] = maxFall
        values["terminalVelocityLimit"] = cfg.terminalVelocity
        values["landingSpeed"] = landing
        values["fallEvents"] = fallEvents.toDouble()
        finished = true
      } else if (tick > 3000) { notes += "never landed"; finished = true }
    }
  }

}
