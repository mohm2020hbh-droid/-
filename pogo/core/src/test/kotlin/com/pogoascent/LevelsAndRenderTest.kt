package com.pogoascent

import com.pogoascent.debug.AutoPilot
import com.pogoascent.debug.PhysicsTestScene
import com.pogoascent.debug.ScenarioKind
import com.pogoascent.levels.GameSession
import com.pogoascent.levels.LevelLoader
import com.pogoascent.levels.LevelValidator
import com.pogoascent.levels.SessionPhase
import com.pogoascent.levels.WorldCatalog
import com.pogoascent.physics.PhysicsConfig
import com.pogoascent.render.DynamicScene
import com.pogoascent.render.Meshes
import com.pogoascent.render.CharacterStyle
import com.pogoascent.render.Palette
import com.pogoascent.render.SceneBuilder
import com.pogoascent.render.StaticMesh
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LevelsTest {
  private val worlds = WorldCatalog.load()
  private val physics = PhysicsConfig.load()

  /** Validation is the slow part (real-physics search); run it once per level and share the reports between tests. */
  private val reports: Map<String, com.pogoascent.levels.ValidationReport> by lazy {
    worlds.levelOrder.parallelStream().map { it to LevelValidator.validate(LevelLoader.loadData(it), surfaces, physics) }.toList().toMap()
  }

  @Test fun worldsReferenceExistingLevelsInRisingDifficulty() {
    assertTrue(worlds.worlds.size >= 4)
    var lastMax = 0
    var prevUnlock = -1
    for (w in worlds.worlds) {
      assertTrue(w.levels.isNotEmpty())
      assertTrue("world ${w.id} difficulty range", w.difficultyMin <= w.difficultyMax && w.difficultyMin >= lastMax - 1)
      lastMax = w.difficultyMax
      assertTrue("unlock requirement must not decrease", w.unlockLevelsRequired >= prevUnlock)
      prevUnlock = w.unlockLevelsRequired
      for (id in w.levels) {
        val d = LevelLoader.loadData(id)
        assertEquals(id, d.id); assertEquals(w.id, d.world)
        assertTrue("$id difficulty ${d.difficulty} within its world's range", d.difficulty in w.difficultyMin..w.difficultyMax)
        Palette(w.palette) // parses
      }
    }
    val before = worlds.levelOrder.take(3)
    assertEquals(listOf("level_01", "level_02", "level_03"), before)
    assertEquals(worlds.levelOrder.size, worlds.levelOrder.toSet().size)
  }

  @Test fun everyBundledLevelPassesStructuralAndReachabilityValidation() {
    for (id in worlds.levelOrder) {
      val report = reports.getValue(id)
      println(report)
      assertTrue(report.toString(), report.ok && report.goalReachable == true && report.errors.isEmpty())
      assertTrue("$id path", report.path.isNotEmpty())
    }
  }

  @Test fun anAutopilotFollowingTheValidatedRouteFinishesEveryLevelInARealSession() {
    for (id in worlds.levelOrder) {
      val data = LevelLoader.loadData(id)
      val report = reports.getValue(id)
      val session = GameSession(LevelLoader.load(data, surfaces), physics)
      val pilot = AutoPilot(session, report.path)
      var ticks = 0
      val max = (physics.fixedTimestepHz * 900).toInt()
      var events = 0
      session.addListener { events++ }
      while (session.phase != SessionPhase.COMPLETE && ticks++ < max) { pilot.drive(); session.update(physics.fixedDt) }
      assertEquals("$id: the autopilot should finish (jumps=${session.player.jumpCount}, falls=${session.falls}, resets=${session.resets})", SessionPhase.COMPLETE, session.phase)
      val r = session.result!!
      assertEquals(id, r.levelId)
      assertTrue(r.timeSec > 5 && r.jumps >= report.path.size - 1)
      assertTrue(events > 0)
    }
  }

  @Test fun sessionResetAndCheckpointRespawnWork() {
    val data = LevelLoader.loadData("level_01")
    val report = LevelValidator.validate(data, surfaces, physics)
    val session = GameSession(LevelLoader.load(data, surfaces), physics)
    val pilot = AutoPilot(session, report.path)
    // climb until a checkpoint has been touched
    var guard = 0
    while (!session.checkpointReached("cp1") && guard++ < 120_000) { pilot.drive(); session.update(physics.fixedDt) }
    assertTrue("checkpoint reached", session.checkpointReached("cp1"))
    val cp = data.checkpoints.first { it.id == "cp1" }
    session.respawn()
    assertEquals(1, session.resets)
    assertEquals(cp.respawnX, session.player.tipWorldX, 0.6)
    assertEquals(cp.respawnY, session.player.tipWorldY - physics.tipRadius, 0.5)
    session.resetLevel()
    assertEquals(0.0, session.timeSec, 0.0); assertEquals(SessionPhase.READY, session.phase); assertEquals(0, session.resets)
    assertEquals(data.start.x, session.player.tipWorldX, 0.2)
    assertFalse(session.checkpointReached("cp1"))
  }

  @Test fun fallingIntoTheAbyssRespawnsAtTheStart() {
    val session = GameSession(LevelLoader.load("level_01", surfaces), physics)
    session.player.spawn(60.0, 5.0) // beside the level, nothing to land on
    var guard = 0
    while (session.resets == 0 && guard++ < 6000) session.update(physics.fixedDt)
    assertEquals(1, session.resets)
    assertTrue(session.player.grounded)
  }

  @Test fun gameSpeedDoesNotDependOnFrameRate() {
    fun run(fps: Double): Double {
      val s = GameSession(LevelLoader.load("level_01", surfaces), physics)
      s.input.set(0.5, true)
      repeat((fps * 1.2).toInt()) { s.update(1.0 / fps) }
      s.input.set(0.5, false)
      repeat((fps * 1.0).toInt()) { s.update(1.0 / fps) }
      return s.player.y
    }
    val a = run(30.0); val b = run(60.0); val c = run(144.0)
    assertEquals(a, b, 0.6); assertEquals(b, c, 0.6)
  }
}

class RenderDataTest {
  private val worlds = WorldCatalog.load()

  @Test fun primitiveMeshesAreOutwardWound() {
    for (kind in 0 until Meshes.KINDS) {
      val m = Meshes.get(kind)
      var i = 0
      while (i < m.indices.size) {
        val a = m.indices[i].toInt() * 3; val b = m.indices[i + 1].toInt() * 3; val c = m.indices[i + 2].toInt() * 3
        val ux = m.positions[b] - m.positions[a]; val uy = m.positions[b + 1] - m.positions[a + 1]; val uz = m.positions[b + 2] - m.positions[a + 2]
        val vx = m.positions[c] - m.positions[a]; val vy = m.positions[c + 1] - m.positions[a + 1]; val vz = m.positions[c + 2] - m.positions[a + 2]
        val nx = uy * vz - uz * vy; val ny = uz * vx - ux * vz; val nz = ux * vy - uy * vx
        val area2 = nx * nx + ny * ny + nz * nz
        if (area2 > 1e-10f) assertTrue("mesh $kind tri ${i / 3} winding", nx * m.normals[a] + ny * m.normals[a + 1] + nz * m.normals[a + 2] > 0f)
        i += 3
      }
    }
  }

  @Test fun staticLevelMeshesAreWellFormedAndOutwardWound() {
    for (id in worlds.levelOrder) {
      val world = worlds.worldOfLevel(id)!!
      val level = LevelLoader.load(id, surfaces)
      val mesh = SceneBuilder(level, Palette(world.palette)).staticMesh
      assertTrue(mesh.vertexCount in 4..65534)
      val v = mesh.vertices; val s = StaticMesh.FLOATS_PER_VERTEX
      for (idx in mesh.indices) assertTrue(idx.toInt() in 0 until mesh.vertexCount)
      var bad = 0
      var i = 0
      while (i < mesh.indices.size) {
        val a = mesh.indices[i] * s; val b = mesh.indices[i + 1] * s; val c = mesh.indices[i + 2] * s
        val ux = v[b] - v[a]; val uy = v[b + 1] - v[a + 1]; val uz = v[b + 2] - v[a + 2]
        val vx = v[c] - v[a]; val vy = v[c + 1] - v[a + 1]; val vz = v[c + 2] - v[a + 2]
        val nx = uy * vz - uz * vy; val ny = uz * vx - ux * vz; val nz = ux * vy - uy * vx
        if (nx * v[a + 3] + ny * v[a + 4] + nz * v[a + 5] <= 0f) bad++
        i += 3
      }
      assertEquals("$id: triangles wound against their normal", 0, bad)
      for (f in v) assertTrue(f.isFinite())
    }
  }

  @Test fun dynamicSceneStaysWithinItsBatchesAndIsAllocationFree() {
    val level = LevelLoader.load("level_03", surfaces)
    val world = worlds.worldOfLevel("level_03")!!
    val builder = SceneBuilder(level, Palette(world.palette))
    val dyn = DynamicScene(builder)
    val session = GameSession(level, PhysicsConfig())
    session.input.set(0.4, true)
    repeat(200) { session.update(1.0 / 60); dyn.build(session, CharacterStyle(), it / 60.0, 1.0 / 60) }
    assertTrue(dyn.batches.totalInstances in 20..700)
    assertTrue(dyn.batches.cube.count <= dyn.batches.cube.capacity)
    // every instance matrix and colour is finite
    for (b in listOf(dyn.batches.cube, dyn.batches.cylinder, dyn.batches.sphere)) for (i in 0 until b.count * 20) assertTrue(b.data[i].isFinite())
  }
}

class ScenarioTest {
  private fun scene() = PhysicsTestScene(surfaces, PhysicsConfig())

  @Test fun testJumpMatchesTheAnalyticArcWithinTolerance() {
    val r = scene().runToCompletion(ScenarioKind.TEST_JUMP)
    println(r)
    assertEquals(PhysicsConfig().jumpPower, r["launchSpeed"], 0.05)
    assertEquals(r["analyticApex"], r["apexHeight"], r["analyticApex"] * 0.05)
    assertEquals(2 * PhysicsConfig().jumpPower / PhysicsConfig().gravity, r["flightTime"], 0.12)
    assertEquals(r["launchSpeed"], r["landingSpeed"], 1.2)
  }

  @Test fun testBoostArmsAboveTheThresholdAndLaunchesStronger() {
    val r = scene().runToCompletion(ScenarioKind.TEST_BOOST)
    println(r)
    assertEquals(1.0, r["boostCount"], 0.0)
    assertTrue(r["boostArmedRotationDeg"] >= PhysicsConfig().boostThreshold)
    assertTrue(r["boostLaunchSpeed"] > PhysicsConfig().jumpPower * 1.2)
    assertTrue(r["boostMultiplier"] <= PhysicsConfig().boostPower + PhysicsConfig().boostAngularBonus + 1e-6)
  }

  @Test fun testBounceThrowsUpAtLeastThePadLaunchSpeed() {
    val r = scene().runToCompletion(ScenarioKind.TEST_BOUNCE)
    println(r)
    assertTrue(r["reboundSpeedAtLaunch"] >= surfaces["bounce"].launchSpeed * 0.9)
    assertTrue(r["reboundApexHeight"] > 5.0)
  }

  @Test fun testFallStaysUnderTerminalVelocity() {
    val r = scene().runToCompletion(ScenarioKind.TEST_FALL)
    println(r)
    assertTrue(r["maxFallSpeed"] <= PhysicsConfig().terminalVelocity + 1e-6)
    assertTrue(r["maxFallSpeed"] > PhysicsConfig().terminalVelocity * 0.9)
    assertEquals(1.0, r["fallEvents"], 0.0)
  }

  @Test fun resetReturnsToManualMode() {
    val s = scene()
    s.start(ScenarioKind.TEST_JUMP)
    repeat(200) { s.tickOnce() }
    s.reset()
    assertEquals(null, s.activeScenario)
    assertTrue(s.session.player.grounded)
    s.manualInput.set(0.0, true)
    repeat(60) { s.tickOnce() }
    assertTrue(s.snapshot().charge > 0.3)
    assertEquals("Manual", s.status)
  }

  @Test fun liveSnapshotExposesEverythingTheTaskAsksFor() {
    val s = scene()
    s.manualInput.set(1.0, true)
    repeat(120) { s.tickOnce() }
    val d = s.snapshot()
    assertTrue(d.grounded && d.charge > 0.8)
    assertTrue(d.angleDeg > 60)
    assertEquals(PhysicsConfig().gravity, d.gravity, 0.0)
    assertTrue(d.jumpPower > PhysicsConfig().jumpPowerMin)
    assertTrue(d.height > 0.1)
  }
}
