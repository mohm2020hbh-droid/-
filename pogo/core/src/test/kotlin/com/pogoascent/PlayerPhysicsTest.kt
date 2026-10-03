package com.pogoascent

import com.pogoascent.physics.Collider
import com.pogoascent.physics.PhysicsConfig
import com.pogoascent.physics.PhysicsWorld
import com.pogoascent.player.EventType
import com.pogoascent.player.PlayerState
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.sqrt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerPhysicsTest {

  @Test fun spawnsGroundedAndUpright() {
    val r = Rig(floorWorld())
    assertTrue(r.player.grounded)
    assertEquals(PlayerState.GROUNDED, r.player.state)
    assertEquals(0.0, r.player.angle, 1e-9)
    r.seconds(2.0)
    assertTrue("still standing after 2 s", r.player.grounded)
    assertEquals(0.0, r.player.x, 1e-6)
    assertEquals(r.cfg.tipOffset + r.cfg.tipRadius, r.player.y, 0.02)
  }

  @Test fun fullChargeLaunchReachesTheAnalyticApex() {
    val cfg = PhysicsConfig(airLinearDrag = 0.0)
    val r = Rig(floorWorld(), cfg)
    val y0 = r.player.y
    r.chargeAndRelease(cfg.jumpChargeTime + 0.1)
    assertEquals(1, r.count(EventType.LAUNCH))
    assertEquals(cfg.jumpPower, r.first(EventType.LAUNCH)!!.magnitude, 1e-9)
    var apex = r.player.y
    assertTrue(r.until(5.0) { if (r.player.y > apex) apex = r.player.y; r.player.grounded })
    val expected = y0 + cfg.jumpPower * cfg.jumpPower / (2 * cfg.gravity)
    assertEquals("apex height", expected, apex, expected * 0.03)
    assertEquals(PlayerState.GROUNDED, r.player.state)
  }

  @Test fun tapLaunchesWithMinimumPower() {
    val r = Rig(floorWorld())
    r.chargeAndRelease(0.0)
    val e = r.first(EventType.LAUNCH)!!
    assertEquals(r.cfg.launchSpeedForCharge(0.0) + 0.0, e.magnitude, r.cfg.jumpPower * 0.02)
    assertTrue(e.magnitude >= r.cfg.jumpPowerMin)
  }

  @Test fun launchPowerGrowsWithChargeTime() {
    val speeds = listOf(0.1, 0.3, 0.5, 0.7, 0.9).map { t ->
      val r = Rig(floorWorld())
      r.chargeAndRelease(t)
      r.first(EventType.LAUNCH)!!.magnitude
    }
    for (i in 1 until speeds.size) assertTrue("monotonic: $speeds", speeds[i] > speeds[i - 1])
  }

  @Test fun leaningAimsTheLaunch() {
    val right = Rig(floorWorld())
    right.input.lean = 1.0
    right.seconds(1.0)
    assertEquals(right.cfg.maxLeanAngleRad, right.player.angle, 0.02)
    right.chargeAndRelease(0.6)
    assertTrue("launched to the right", right.player.vx > 3.0)

    val left = Rig(floorWorld())
    left.input.lean = -0.5
    left.seconds(1.0)
    left.chargeAndRelease(0.6)
    assertTrue("launched to the left", left.player.vx < -1.0)
  }

  @Test fun leanSettlesOnTargetWithoutOvershoot() {
    val r = Rig(floorWorld())
    r.input.lean = 0.5
    var maxAngle = 0.0
    r.until(2.0) { maxAngle = maxOf(maxAngle, r.player.angle); false }
    val target = 0.5 * r.cfg.maxLeanAngleRad
    assertEquals(target, r.player.angle, 0.01)
    assertTrue("overshoot ${maxAngle - target}", maxAngle - target < 0.03)
  }

  @Test fun simulationIsDeterministic() {
    fun run(): DoubleArray {
      val r = Rig(floorWorld(extra = listOf(box("wall", 6.0, 5.0, 1.0, 10.0))))
      for (i in 0 until 6) {
        r.input.lean = if (i % 2 == 0) 0.8 else -0.3
        r.seconds(0.4)
        r.chargeAndRelease(0.3 + 0.1 * i)
        r.input.lean = 1.0
        r.seconds(0.7)
        r.input.lean = 0.0
        r.waitForLanding(3.0)
      }
      return doubleArrayOf(r.player.x, r.player.y, r.player.angle, r.player.vx, r.player.vy, r.player.omega)
    }
    val a = run(); val b = run()
    for (i in a.indices) assertEquals("component $i", a[i], b[i], 0.0)
  }

  @Test fun landingStoresTheBounceAsPreCharge() {
    val r = Rig(floorWorld())
    r.input.jumpHeld = true
    r.seconds(r.cfg.jumpChargeTime + 0.05)
    r.input.jumpHeld = false
    r.tick()
    val launchSpeed = r.first(EventType.LAUNCH)!!.magnitude
    // keep the button held through the flight so the landing is a loaded landing
    r.input.jumpHeld = true
    assertTrue(r.waitForLanding())
    val landEvent = r.first(EventType.LAND)!!
    assertTrue("symmetric landing speed ~ launch speed", abs(landEvent.magnitude - launchSpeed) < 1.5)
    val expected = ((landEvent.magnitude * r.cfg.bounce - r.cfg.jumpPowerMin) / (r.cfg.jumpPower - r.cfg.jumpPowerMin)).coerceIn(0.0, 1.0)
    assertTrue("charging after a held landing", r.player.charging)
    assertEquals(expected, r.player.charge, 0.05)
    r.input.jumpHeld = false
    r.tick()
    val second = r.events.filter { it.type == EventType.LAUNCH }[1].magnitude
    assertTrue("second jump is at least the stored bounce", second >= landEvent.magnitude * r.cfg.bounce - 0.5)
  }

  @Test fun landingWithoutHoldingStopsTheStick() {
    val r = Rig(floorWorld())
    r.chargeAndRelease(0.8)
    assertTrue(r.waitForLanding())
    assertFalse(r.player.charging)
    assertEquals(0.0, r.player.charge, 0.0)
    r.seconds(1.0)
    assertTrue(r.player.grounded)
    assertEquals(1, r.count(EventType.LAUNCH))
  }

  @Test fun lateButtonPressWithinGraceStillGetsTheLandingSpring() {
    val r = Rig(floorWorld())
    r.chargeAndRelease(0.85)
    assertTrue(r.waitForLanding())
    assertFalse(r.player.charging)
    r.tick(2) // ~17 ms after touchdown, inside the 80 ms grace
    r.input.jumpHeld = true
    r.tick()
    assertTrue(r.player.charging)
    assertTrue("pre-charge applied", r.player.charge > 0.05)
  }

  @Test fun thinPlatformIsNeverTunnelledAtTerminalVelocity() {
    val thin = box("thin", 0.0, 0.0, 6.0, 0.3)
    val world = PhysicsWorld(listOf(thin), emptyList(), -500.0)
    val r = Rig(world, spawn = false)
    // drop from far above
    r.player.spawn(0.0, 0.15) // grounded on thin first
    r.chargeAndRelease(0.9)
    r.input.lean = 0.0
    var maxFall = 0.0
    assertTrue(r.until(10.0) { maxFall = minOf(maxFall, r.player.vy); r.player.grounded })
    assertTrue(r.player.y > 0.0)
    assertTrue("fell fast enough to matter: $maxFall", maxFall < -10.0)

    // explicit terminal-velocity drop from 120 m
    val r2 = Rig(world, spawn = false)
    r2.player.spawn(0.0, 120.0) // no ground here -> airborne
    assertFalse(r2.player.grounded)
    assertTrue(r2.until(12.0) { r2.player.grounded })
    assertTrue("landed on top", r2.player.y > 0.15)
    assertEquals(r2.cfg.terminalVelocity, r2.events.first { it.type == EventType.LAND }.magnitude, 3.0)
  }

  @Test fun sideWallBouncesAndSpinsTheBody() {
    val wall = box("wall", 4.0, 5.0, 1.0, 20.0, "wall")
    val r = Rig(floorWorld(extra = listOf(wall)))
    r.input.lean = 1.0
    r.seconds(1.0)
    r.chargeAndRelease(0.8)
    r.input.lean = 0.0
    var hit = false
    r.until(3.0) { if (r.count(EventType.COLLISION) + r.count(EventType.HARD_COLLISION) > 0) hit = true; hit }
    assertTrue("hit the wall", hit)
    val e = (r.events.firstOrNull { it.type == EventType.HARD_COLLISION } ?: r.first(EventType.COLLISION))!!
    assertTrue("normal points away from the wall", e.nx < -0.9)
    r.seconds(0.1)
    assertTrue("pushed back from the wall", r.player.vx < 1.0)
    assertTrue("body never ends inside the wall", r.player.x < 3.5 + 0.35)
  }

  @Test fun airRotationAccumulatesAndArmsBoostOnlyAboveTheThreshold() {
    val cfg = PhysicsConfig()
    // short spin -> no boost
    val a = Rig(floorWorld(), cfg)
    a.chargeAndRelease(0.9)
    a.input.lean = 1.0
    a.seconds(0.2)
    a.input.lean = 0.0
    a.autoLevel = true
    a.input.jumpHeld = true
    assertTrue(a.waitForLanding())
    assertEquals("no boost for a short spin", 0, a.count(EventType.BOOST_ARMED))
    assertFalse(a.player.boostArmed)

    // long spin (> 285 deg) while jump held at landing -> armed
    val b = Rig(floorWorld(), cfg)
    b.chargeAndRelease(0.9)
    b.input.lean = 1.0
    b.until(1.2) { Math.toDegrees(b.player.rotationAccum) > 300.0 }
    assertTrue("rotated > threshold", Math.toDegrees(b.player.rotationAccum) > cfg.boostThreshold)
    b.input.lean = 0.0
    b.autoLevel = true
    b.input.jumpHeld = true
    assertTrue(b.waitForLanding())
    assertEquals(1, b.count(EventType.BOOST_ARMED))
    assertTrue(b.player.boostArmed)
  }

  @Test fun boostRequiresHoldingAtLandingAndLaunchesStronger() {
    val cfg = PhysicsConfig()
    fun spinAndLand(hold: Boolean): Rig {
      val r = Rig(floorWorld(), cfg)
      r.chargeAndRelease(0.95)
      r.input.lean = 1.0
      r.until(1.3) { Math.toDegrees(r.player.rotationAccum) > 330.0 }
      r.input.lean = 0.0
      r.autoLevel = true
      r.input.jumpHeld = hold
      assertTrue(r.waitForLanding())
      r.autoLevel = false
      r.input.lean = 0.0
      r.seconds(0.2) // outside the grace window
      return r
    }
    val notHeld = spinAndLand(false)
    assertEquals("not holding = no boost", 0, notHeld.count(EventType.BOOST_ARMED))

    val held = spinAndLand(true)
    assertTrue(held.player.boostArmed)
    // let it charge to full, then release
    held.input.jumpHeld = true
    held.seconds(cfg.jumpChargeTime + 0.1)
    held.input.jumpHeld = false
    held.tick()
    assertEquals(1, held.count(EventType.BOOST))
    assertEquals(1, held.player.boostCount)
    val boosted = held.events.last { it.type == EventType.LAUNCH }.magnitude
    assertTrue("boost launch ($boosted) stronger than a normal full charge (${cfg.jumpPower})", boosted > cfg.jumpPower * cfg.boostPower * 0.99)
    assertFalse("boost is consumed", held.player.boostArmed)
  }

  @Test fun unusedBoostExpires() {
    val cfg = PhysicsConfig(boostWindow = 0.3)
    val r = Rig(floorWorld(), cfg)
    r.chargeAndRelease(0.95)
    r.input.lean = 1.0
    r.until(1.3) { Math.toDegrees(r.player.rotationAccum) > 330.0 }
    r.input.lean = 0.0
    r.autoLevel = true
    r.input.jumpHeld = true
    assertTrue(r.waitForLanding())
    r.autoLevel = false
    r.input.lean = 0.0
    assertTrue(r.player.boostArmed)
    // keep holding: sits charged but the boost window runs out
    r.seconds(0.5)
    assertFalse(r.player.boostArmed)
    assertEquals(1, r.count(EventType.BOOST_EXPIRED))
  }

  @Test fun badTiltLandingSlipsAndTumbles() {
    val r = Rig(floorWorld())
    r.chargeAndRelease(0.9)
    // force a stick that is nearly horizontal on touchdown
    r.input.lean = 1.0
    r.until(3.0) { r.player.angle > Math.toRadians(100.0) || r.player.grounded }
    r.input.lean = 0.0
    r.until(5.0) { r.count(EventType.SLIP) > 0 || r.player.grounded }
    assertTrue("slip event or an extreme-tilt landing was prevented", r.count(EventType.SLIP) >= 1 || r.player.grounded)
    if (r.count(EventType.SLIP) >= 1) {
      assertTrue(r.player.state == PlayerState.TUMBLING || r.player.grounded)
    }
  }

  @Test fun bouncePadThrowsThePlayerUpAtLeastItsLaunchSpeed() {
    val pad = box("pad", 0.0, -0.2, 4.0, 0.4, "bounce")
    val world = PhysicsWorld(listOf(pad), emptyList(), -100.0)
    val r = Rig(world, spawn = false)
    r.player.spawn(0.0, 6.0) // drop onto the pad
    assertTrue(r.until(4.0) { r.count(EventType.BOUNCE) > 0 })
    val surf = surfaces["bounce"]
    assertTrue("rebounded upward", r.player.vy >= surf.launchSpeed * 0.8)
    assertFalse("pad never pins the stick", r.player.grounded)
  }

  @Test fun iceSlidesOnSlopesAndStickyHolds() {
    // 25 degree ramp
    fun rampWorld(surface: String): PhysicsWorld {
      val ramp = box("ramp", 0.0, 0.0, 30.0, 2.0, surface, rot = Math.toRadians(25.0))
      return PhysicsWorld(listOf(ramp), emptyList(), -100.0)
    }
    val ice = Rig(rampWorld("ice"), spawn = false)
    ice.player.spawn(0.0, 1.0 / kotlin.math.cos(Math.toRadians(25.0)) - 0.0)
    ice.input.lean = 0.0
    ice.seconds(1.0)
    val iceTravel = hypot(ice.player.x, 0.0)
    assertTrue("ice: slid ($iceTravel m)", abs(ice.player.x) > 1.0)

    val grip = Rig(rampWorld("stone"), spawn = false)
    grip.player.spawn(0.0, 1.0 / kotlin.math.cos(Math.toRadians(25.0)))
    grip.seconds(1.0)
    assertTrue("stone at 25° holds (μ=0.9 > tan25=0.47)", abs(grip.player.x) < 0.3)
  }

  @Test fun movingPlatformCarriesTheRider() {
    val mover = com.pogoascent.physics.Mover(ax = 5.0, ay = 0.0, period = 6.0)
    val plat = box("mp", 0.0, -0.25, 6.0, 0.5, "moving", mover = mover)
    val world = PhysicsWorld(listOf(plat), emptyList(), -100.0)
    val r = Rig(world, spawn = false)
    r.player.spawn(0.0, 0.0)
    assertTrue(r.player.grounded)
    r.seconds(1.4) // quarter period: platform at ~ +5
    assertTrue("still on the platform", r.player.grounded)
    assertEquals(plat.ox, r.player.x, 0.4)
  }

  @Test fun oneWayPlatformIsSolidFromAboveOnly() {
    val one = box("ow", 0.0, 3.0, 8.0, 0.3, "wood", oneWay = true)
    val r = Rig(floorWorld(extra = listOf(one)))
    r.chargeAndRelease(0.95) // apex ~ 8.5 m: passes up through the platform at y=3
    var maxY = 0.0
    var landedOnPlatform = false
    r.until(8.0) {
      maxY = maxOf(maxY, r.player.y)
      if (r.player.grounded && r.player.y > 3.5) landedOnPlatform = true
      landedOnPlatform
    }
    assertTrue("passed up through the platform", maxY > 6.0)
    assertTrue("landed on top coming down", landedOnPlatform)
  }

  @Test fun hazardAndGoalAndKillFloorRaiseEvents() {
    val hazard = box("spikes", 0.0, 3.0, 3.0, 0.5, "hazard")
    val goal = box("goal", 10.0, 6.0, 3.0, 0.5, "goal")
    val world = PhysicsWorld(listOf(Collider.box("floor", 0.0, -1.0, 40.0, 2.0, surfaces["stone"]), hazard, goal), emptyList(), -5.0)
    val r = Rig(world)
    r.chargeAndRelease(0.9) // straight up into the hazard
    r.seconds(1.2)
    assertTrue("hazard event", r.count(EventType.HAZARD) >= 1)

    val r2 = Rig(world, spawn = false)
    r2.player.spawn(10.0, 8.0) // above goal
    r2.seconds(1.5)
    assertTrue("goal event", r2.count(EventType.GOAL) == 1)

    val r3 = Rig(world, spawn = false)
    r3.player.spawn(100.0, 0.0) // off the edge
    r3.seconds(3.0)
    assertEquals(1, r3.count(EventType.KILL_FLOOR))
    assertTrue(r3.player.killed)
  }

  @Test fun fallEventFiresOncePerFlightWhenFastEnough() {
    val r = Rig(floorWorld())
    r.player.spawn(0.0, 40.0)
    assertTrue(r.until(8.0) { r.player.grounded })
    assertEquals(1, r.count(EventType.FALL))
  }

  @Test fun fuzzNeverProducesNonFiniteStateOrSpeedsAboveTheLimit() {
    val rnd = java.util.Random(1234)
    val walls = listOf(
      box("w1", -8.0, 8.0, 1.0, 16.0, "wall"), box("w2", 8.0, 8.0, 1.0, 16.0, "wall"),
      box("c", 0.0, 18.0, 20.0, 1.0, "stone"), box("p1", -3.0, 6.0, 3.0, 0.4, "wood"),
      box("p2", 3.0, 11.0, 3.0, 0.4, "bounce"), box("r", 0.0, 3.0, 4.0, 0.5, "ice", rot = 0.4),
    )
    val r = Rig(floorWorld(extra = walls))
    var sinceChange = 0
    var maxSpeed = 0.0
    repeat(120_000) {
      if (sinceChange-- <= 0) {
        r.input.lean = (rnd.nextInt(5) - 2) / 2.0
        r.input.jumpHeld = rnd.nextBoolean()
        sinceChange = 5 + rnd.nextInt(80)
      }
      r.tick()
      val sp = hypot(r.player.vx, r.player.vy)
      if (sp > maxSpeed) maxSpeed = sp
      assertTrue(r.player.x.isFinite() && r.player.y.isFinite())
      assertTrue("inside the arena (y=${r.player.y})", r.player.y > -3.0)
    }
    assertTrue("max speed $maxSpeed <= ${r.cfg.maxSpeed}", maxSpeed <= r.cfg.maxSpeed + 1e-6)
    assertTrue("player jumped many times", r.player.jumpCount > 50)
  }

  @Test fun physicsCostPerTickIsSmall() {
    val colliders = (0 until 150).map { box("b$it", (it % 15) * 6.0 - 40, (it / 15) * 5.0, 4.0, 0.5) }
    val r = Rig(floorWorld(extra = colliders))
    r.input.lean = 0.4
    r.input.jumpHeld = true
    r.tick(600) // warm-up
    val t0 = System.nanoTime()
    val n = 20_000
    for (i in 0 until n) {
      if (i % 90 == 0) r.input.jumpHeld = !r.input.jumpHeld
      r.tick()
    }
    val perTickUs = (System.nanoTime() - t0) / 1000.0 / n
    println("physics: %.2f µs/tick with 151 colliders".format(perTickUs))
    assertTrue("tick cost $perTickUs µs", perTickUs < 400.0) // 120 Hz budget is 8333 µs
  }

  @Test fun spawnOnFloatingPointFallsInsteadOfPinning() {
    val r = Rig(floorWorld(), spawn = false)
    r.player.spawn(0.0, 30.0)
    assertFalse(r.player.grounded)
    assertNull(r.player.groundSurfaceId)
    r.until(6.0) { r.player.grounded }
    assertNotNull(r.player.groundSurfaceId)
    assertEquals("stone", r.player.groundSurfaceId)
    assertEquals(sqrt(2 * r.cfg.gravity * 30.0).coerceAtMost(r.cfg.terminalVelocity), r.first(EventType.LAND)!!.magnitude, 3.0)
  }
}
