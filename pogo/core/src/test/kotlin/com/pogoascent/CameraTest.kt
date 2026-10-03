package com.pogoascent

import com.pogoascent.camera.CameraConfig
import com.pogoascent.camera.CameraRig
import com.pogoascent.camera.CameraUserSettings
import com.pogoascent.player.EventType
import com.pogoascent.player.GameEvent
import com.pogoascent.render.Mat4
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CameraTest {
  private fun run(cam: CameraRig, seconds: Double, px: Double, py: Double, vx: Double = 0.0, vy: Double = 0.0, user: CameraUserSettings = CameraUserSettings()) {
    repeat((seconds * 60).toInt()) { cam.update(1.0 / 60, px, py, vx, vy, user) }
  }

  @Test fun bundledConfigIsValidAndMatchesDefaults() {
    assertEquals(CameraConfig(), CameraConfig.load())
    assertEquals(emptyList<String>(), CameraConfig().validate())
  }

  @Test fun settlesOnAStationaryPlayerWithTheVerticalBias() {
    val cam = CameraRig()
    cam.snapTo(0.0, 0.0)
    run(cam, 4.0, 10.0, 5.0)
    val c = cam.config
    // dead-zone: it stops once the player is inside the box, so within deadZone of the player
    assertEquals(10.0, cam.targetX, c.deadZoneX + 0.05)
    assertEquals(5.0 + c.verticalBias, cam.targetY, c.deadZoneY + 0.05)
  }

  @Test fun followsASteadyClimbWithoutFallingFarBehind() {
    val cam = CameraRig()
    cam.snapTo(0.0, 0.0)
    var y = 0.0
    var worstLag = 0.0
    repeat(60 * 6) {
      y += 8.0 / 60
      cam.update(1.0 / 60, 0.0, y, 0.0, 8.0)
      worstLag = maxOf(worstLag, abs(y + cam.config.verticalBias - cam.targetY))
    }
    assertTrue("player stays on screen (lag $worstLag m)", worstLag < cam.visibleHalfHeight() * 0.6)
  }

  @Test fun lookAheadShiftsTheViewInTheDirectionOfTravel() {
    val a = CameraRig(); a.snapTo(0.0, 0.0); run(a, 3.0, 0.0, 0.0, vx = 12.0)
    val b = CameraRig(); b.snapTo(0.0, 0.0); run(b, 3.0, 0.0, 0.0, vx = -12.0)
    assertTrue(a.targetX > 0.5 && b.targetX < -0.5)
    assertTrue(a.targetX <= a.config.lookAheadXMax + a.config.deadZoneX + 0.01)
  }

  @Test fun fallingLooksDownAndZoomsOut() {
    val cam = CameraRig(); cam.snapTo(0.0, 0.0)
    run(cam, 0.5, 0.0, 0.0)
    val d0 = cam.distance
    val y0 = cam.targetY
    repeat(120) { cam.update(1.0 / 60, 0.0, 0.0, 0.0, -35.0) }
    assertTrue("zoomed out while falling", cam.distance > d0 * 1.1)
    assertTrue("looking further down", cam.targetY < y0 - 1.0)
  }

  @Test fun landingDipIsBoundedAndRecovers() {
    val cam = CameraRig(); cam.snapTo(0.0, 0.0); run(cam, 2.0, 0.0, 0.0)
    val rest = cam.targetY
    cam.onEvent(GameEvent(EventType.LAND, 0.0, 0.0, 60.0))
    cam.update(1.0 / 60, 0.0, 0.0, 0.0, 0.0)
    val dipped = rest - cam.targetY
    assertTrue("dip $dipped within (0, max]", dipped > 0.05 && dipped <= cam.config.landingDipMax + 1e-6)
    run(cam, 2.0, 0.0, 0.0)
    assertEquals(rest, cam.targetY, 0.01)
  }

  @Test fun shakeIsBoundedDecaysAndCanBeSwitchedOff() {
    val cam = CameraRig(); cam.snapTo(0.0, 0.0); run(cam, 1.0, 0.0, 0.0)
    val restX = cam.targetX; val restY = cam.targetY
    repeat(5) { cam.onEvent(GameEvent(EventType.HARD_COLLISION, 0.0, 0.0, 40.0)) }
    var maxOff = 0.0
    var maxRoll = 0.0
    repeat(120) {
      cam.update(1.0 / 60, 0.0, 0.0, 0.0, 0.0)
      maxOff = maxOf(maxOff, abs(cam.targetX - restX), abs(cam.targetY - restY))
      maxRoll = maxOf(maxRoll, abs(cam.rollRad))
    }
    val c = cam.config
    assertTrue("shake visible ($maxOff)", maxOff > 0.02)
    assertTrue("shake bounded ($maxOff)", maxOff <= c.shakeMaxOffset * 1.0001)
    assertTrue("roll bounded", Math.toDegrees(maxRoll) <= c.shakeMaxRollDeg * 1.0001)
    run(cam, 3.0, 0.0, 0.0)
    assertTrue("trauma decayed", cam.shakeTrauma < 0.01)

    val off = CameraRig(); off.snapTo(0.0, 0.0); run(off, 1.0, 0.0, 0.0)
    val ox = off.targetX; val oy = off.targetY
    repeat(5) { off.onEvent(GameEvent(EventType.HARD_COLLISION, 0.0, 0.0, 40.0)) }
    repeat(60) {
      off.update(1.0 / 60, 0.0, 0.0, 0.0, 0.0, CameraUserSettings(shakeIntensity = 0.0))
      assertEquals(ox, off.targetX, 1e-9); assertEquals(oy, off.targetY, 1e-9); assertEquals(0.0, off.rollRad, 1e-12)
    }
  }

  @Test fun boostKicksFovAndZoomThenReturns() {
    val cam = CameraRig(); cam.snapTo(0.0, 0.0); run(cam, 1.0, 0.0, 0.0)
    val fov0 = cam.fovDeg; val d0 = cam.distance
    cam.onEvent(GameEvent(EventType.BOOST, 0.0, 0.0, 20.0))
    run(cam, 0.2, 0.0, 0.0)
    assertTrue(cam.fovDeg > fov0 + 1.0 && cam.distance > d0)
    run(cam, 3.0, 0.0, 0.0)
    assertEquals(fov0, cam.fovDeg, 1e-6)
    assertEquals(d0, cam.distance, 0.05)
  }

  @Test fun userZoomIsClamped() {
    val cam = CameraRig(); cam.snapTo(0.0, 0.0)
    run(cam, 1.0, 0.0, 0.0, user = CameraUserSettings(zoom = 99.0))
    assertEquals(cam.config.distance * cam.config.zoomMax, cam.distance, 0.01)
    run(cam, 1.0, 0.0, 0.0, user = CameraUserSettings(zoom = 0.001))
    assertEquals(cam.config.distance * cam.config.zoomMin, cam.distance, 0.01)
  }

  @Test fun projectsThePlayerTargetToTheScreenCentreAndStaysFinite() {
    val cam = CameraRig(); cam.snapTo(3.0, 40.0); run(cam, 2.0, 3.0, 40.0)
    val view = FloatArray(16); val proj = FloatArray(16); val vp = FloatArray(16)
    cam.viewMatrix(view); cam.projectionMatrix(proj, 16f / 9f); Mat4.multiply(vp, proj, view)
    assertTrue(vp.all { it.isFinite() })
    val clip = FloatArray(4)
    Mat4.transform(clip, vp, cam.targetX.toFloat(), cam.targetY.toFloat(), 0f)
    assertEquals(0.0, (clip[0] / clip[3]).toDouble(), 1e-4)
    assertEquals(0.0, (clip[1] / clip[3]).toDouble(), 1e-4)
    assertTrue(clip[3] > 0f)
    // a point to the right of the target lands on the right half of the screen
    Mat4.transform(clip, vp, cam.targetX.toFloat() + 2f, cam.targetY.toFloat(), 0f)
    assertTrue(clip[0] / clip[3] > 0f)
    Mat4.transform(clip, vp, cam.targetX.toFloat(), cam.targetY.toFloat() + 2f, 0f)
    assertTrue("up is up on screen", clip[1] / clip[3] > 0f)
  }

  @Test fun surviveHugeAndTinyFrameTimes() {
    val cam = CameraRig(); cam.snapTo(0.0, 0.0)
    for (dt in listOf(0.0, -1.0, 1e-9, 0.5, 5.0, 1.0 / 144)) cam.update(dt, 100.0, 100.0, 50.0, 50.0)
    assertTrue(cam.eyeX.isFinite() && cam.eyeY.isFinite() && cam.eyeZ.isFinite())
  }
}
