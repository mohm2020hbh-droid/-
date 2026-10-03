package com.pogoascent.player

import com.pogoascent.physics.Collider
import com.pogoascent.physics.ContactFilter
import com.pogoascent.physics.ContactResult
import com.pogoascent.physics.PhysicsConfig
import com.pogoascent.physics.PhysicsWorld
import com.pogoascent.physics.SensorKind
import com.pogoascent.physics.SurfaceType
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sign
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * The pogo-stick rider: a rigid body (centre of mass, angle, velocity, angular velocity) whose foot is the
 * only part that can *land*. Cycle implemented here:
 *
 * Input → Charge → Release → Calculate Launch → Apply Force → Air Control → Collision →
 * Calculate Landing Velocity → Calculate Next Jump → Repeat
 *
 * Conventions: +x right, +y up, angle 0 = upright, **positive angle = leaning right** (head towards +x).
 * Stick axis (foot → head) is u = (sin θ, cos θ). Mass = 1. No tuning constant is hard-coded here – see [PhysicsConfig].
 * The simulation is deterministic: the same [PlayerInput] sequence always produces the same trajectory.
 */
class PogoPlayer(var config: PhysicsConfig, var world: PhysicsWorld, private val sink: EventSink) {
  // ---- pose / velocity (centre of mass) -----------------------------------------------------------
  var x = 0.0; private set
  var y = 0.0; private set
  var angle = 0.0; private set
  var vx = 0.0; private set
  var vy = 0.0; private set
  var omega = 0.0; private set

  // previous tick (render interpolation)
  var prevX = 0.0; private set
  var prevY = 0.0; private set
  var prevAngle = 0.0; private set

  // ---- spring / jump ------------------------------------------------------------------------------
  /** 0..1 spring charge. */
  var charge = 0.0; private set
  var charging = false; private set
  private var chargeFullSent = false

  // ---- mode ---------------------------------------------------------------------------------------
  var grounded = false; private set
  var tumbling = false; private set
  private var groundCollider: Collider? = null
  private var gnx = 0.0
  private var gny = 1.0
  private var tipX = 0.0
  private var tipY = 0.0
  private var slide = 0.0

  // ---- landing grace (touch forgiveness) ----------------------------------------------------------
  private var graceTimer = 0.0
  private var pendingSpeed = 0.0
  private var pendingBoost = false
  private var pendingAngularFactor = 0.0
  private var pendingRotationRad = 0.0

  // ---- boost --------------------------------------------------------------------------------------
  /** Signed rotation accumulated during the current flight (radians). */
  var rotationAccum = 0.0; private set
  var boostArmed = false; private set
  private var boostTimer = 0.0
  private var boostAngularFactor = 0.0

  // ---- bookkeeping --------------------------------------------------------------------------------
  var simTime = 0.0; private set
  var jumpCount = 0; private set
  var boostCount = 0; private set
  var maxHeight = 0.0; private set
  var falling = false; private set
  var goalReached = false; private set
  var killed = false; private set
  var lastCollisionNx = 0.0; private set
  var lastCollisionNy = 1.0; private set
  private var lastCollisionTime = -10.0
  private var lastHazardTime = -10.0
  private var jumpHeld = false
  private val visitedCheckpoints = HashSet<String>()

  // scratch
  private val contact = ContactResult()
  private val probe = ContactResult()
  private var footPrevBottomY = 0.0

  val state: PlayerState
    get() = when {
      !grounded && tumbling -> PlayerState.TUMBLING
      !grounded -> PlayerState.AIRBORNE
      charging -> PlayerState.CHARGING
      else -> PlayerState.GROUNDED
    }

  // ==================================================================================================
  // Placement
  // ==================================================================================================

  /** Resets all state and stands the stick with its tip on the surface point ([surfaceX], [surfaceY]). */
  fun spawn(surfaceX: Double, surfaceY: Double, startAngle: Double = 0.0) =
    spawnPinned(surfaceX, surfaceY + config.tipRadius, startAngle)

  /** Like [spawn] but the tip *circle centre* is given (used to resume exactly where a previous landing happened). */
  fun spawnPinned(tipCenterX: Double, tipCenterY: Double, startAngle: Double = 0.0) {
    simTime = world.time
    angle = startAngle; omega = 0.0; vx = 0.0; vy = 0.0
    charge = 0.0; charging = false; chargeFullSent = false
    grounded = false; tumbling = false; groundCollider = null; slide = 0.0
    graceTimer = 0.0; boostArmed = false; boostTimer = 0.0; rotationAccum = 0.0
    falling = false; goalReached = false; killed = false
    jumpHeld = false
    lastCollisionTime = -10.0
    tipX = tipCenterX; tipY = tipCenterY
    x = tipX + sin(angle) * config.tipOffset
    y = tipY + cos(angle) * config.tipOffset
    if (probeGround()) {
      grounded = true
      syncBodyFromTip()
    }
    maxHeight = y
    prevX = x; prevY = y; prevAngle = angle
  }

  fun resetStats() { jumpCount = 0; boostCount = 0; visitedCheckpoints.clear() }

  // ==================================================================================================
  // Tick
  // ==================================================================================================

  /** Advance one fixed physics tick of [PhysicsConfig.fixedDt] seconds. */
  fun tick(input: PlayerInput) {
    val dt = config.fixedDt
    prevX = x; prevY = y; prevAngle = angle
    simTime += dt
    world.advanceTo(simTime)
    jumpHeld = input.jumpHeld
    val lean = input.lean.coerceIn(-1.0, 1.0)
    if (grounded) stepGrounded(lean, dt) else stepAirborne(lean, dt)
    checkSensors()
    if (y > maxHeight) maxHeight = y
    sanitize()
  }

  /** Guards the simulation against NaN/Inf from a pathological config or contact. */
  private fun sanitize() {
    if (x.isNaN() || y.isNaN() || angle.isNaN() || vx.isNaN() || vy.isNaN() || omega.isNaN() ||
      x.isInfinite() || y.isInfinite()
    ) {
      error("PogoPlayer state became non-finite: x=$x y=$y a=$angle v=($vx,$vy) w=$omega")
    }
  }

  // ==================================================================================================
  // Airborne
  // ==================================================================================================

  private fun stepAirborne(lean: Double, dt: Double) {
    val c = config
    // --- air control: rotation ---
    val authority = c.airControl
    if (lean != 0.0 && authority > 0.0) {
      val target = lean * c.rotationSpeed * authority
      val a = c.airTurnAccel * authority * dt
      omega += (target - omega).coerceIn(-a, a)
    } else {
      omega *= exp(-c.airAngularDrag * dt)
    }
    omega = omega.coerceIn(-c.maxAngularVelocity, c.maxAngularVelocity)

    // --- gravity, drag, optional horizontal push ---
    val g = c.gravity * (if (vy < 0.0) c.fallGravityMultiplier else 1.0)
    vy -= g * dt
    vx += lean * c.airHorizontalAccel * dt
    val drag = (1.0 - c.airLinearDrag * dt).coerceAtLeast(0.0)
    vx *= drag; vy *= drag
    clampVelocity()

    // --- continuous-collision sub-stepping ---
    val travel = hypot(vx, vy) * dt + abs(omega) * dt * c.headOffset
    val n = ceil(travel / c.maxSubstepTravel).toInt().coerceIn(1, 16)
    val h = dt / n
    for (i in 0 until n) {
      val footY = y - cos(angle) * c.tipOffset
      footPrevBottomY = footY - c.tipRadius
      x += vx * h
      y += vy * h
      val dAngle = omega * h
      angle += dAngle
      rotationAccum += dAngle
      resolveAirContacts()
      if (grounded) break
    }

    // --- flight bookkeeping ---
    if (!grounded) {
      if (!falling && vy < -c.fallSpeed) {
        falling = true
        emit(EventType.FALL, -vy)
      }
      if (graceTimer > 0.0) graceTimer = 0.0
    }
  }

  private fun clampVelocity() {
    val c = config
    if (vy < -c.terminalVelocity) vy = -c.terminalVelocity
    val sp = hypot(vx, vy)
    if (sp > c.maxSpeed) { val k = c.maxSpeed / sp; vx *= k; vy *= k }
  }

  private fun footCx() = x - sin(angle) * config.tipOffset
  private fun footCy() = y - cos(angle) * config.tipOffset

  private fun resolveAirContacts() {
    val c = config
    for (iter in 0 until 4) {
      var any = false
      if (world.deepestContact(footCx(), footCy(), c.tipRadius, ContactFilter.ANY, c.groundContactNormalMinY, true, footPrevBottomY, contact)) {
        any = true
        handleFootContact()
        if (grounded) return
      }
      for (k in 0 until 3) {
        val off = when (k) { 0 -> -c.springOffset; 1 -> 0.0; else -> c.headOffset }
        val r = when (k) { 0 -> c.springRadius; 1 -> c.torsoRadius; else -> c.headRadius }
        val cx = x + sin(angle) * off
        val cy = y + cos(angle) * off
        if (world.deepestContact(cx, cy, r, ContactFilter.ANY, c.groundContactNormalMinY, false, 0.0, contact)) {
          any = true
          bodyContact()
        }
      }
      if (!any) break
    }
  }

  /** Foot circle touches something while airborne: land, slip, bounce pad, or plain wall hit. */
  private fun handleFootContact() {
    val c = config
    val col = contact.collider!!
    val surf = col.surface
    val nx = contact.nx
    val ny = contact.ny
    // velocity of the foot point relative to the collider (includes the sweep of the spinning stick)
    val armX = -sin(angle) * c.tipOffset
    val armY = -cos(angle) * c.tipOffset
    val fvx = vx - omega * armY - col.vx
    val fvy = vy + omega * armX - col.vy
    val vn = -(fvx * nx + fvy * ny)
    if (vn <= 0.0) { pushOut(); return }
    val vt = fvx * ny + fvy * -nx // along tangent t = (ny, -nx)

    if (surf.hazard) emitHazard()

    if (surf.type == SurfaceType.BOUNCE || surf.launchSpeed > 0.0) {
      impulse(surf.bounce, surf.friction * c.friction)
      pushOut()
      if (surf.launchSpeed > 0.0) {
        val vAlong = vx * nx + vy * ny
        if (vAlong < surf.launchSpeed) { vx += nx * (surf.launchSpeed - vAlong); vy += ny * (surf.launchSpeed - vAlong) }
        clampVelocity()
      }
      emitAt(EventType.BOUNCE, contact.px, contact.py, vn, surf)
      return
    }

    val standable = ny >= c.groundContactNormalMinY
    if (!standable) { bodyContact(); return }

    val ux = sin(angle)
    val uy = cos(angle)
    val tilt = acos((ux * nx + uy * ny).coerceIn(-1.0, 1.0))
    if (tilt > c.maxLandingTiltRad) {
      // Bad landing: the tip slips. Rigid-body response plus a spin kick proportional to sideways speed.
      impulse(c.slipRestitution, surf.friction * c.friction)
      omega += c.slipSpinFactor * vt / c.tipOffset
      omega = omega.coerceIn(-c.maxAngularVelocity, c.maxAngularVelocity)
      pushOut()
      tumbling = true
      rotationAccum = 0.0
      emitAt(EventType.SLIP, contact.px, contact.py, vn, surf)
      return
    }
    land(vn, vt)
  }

  /** Good landing: pin the tip, keep part of the sideways speed as slide, store the spring energy. */
  private fun land(vn: Double, vt: Double) {
    val c = config
    val col = contact.collider!!
    val surf = col.surface
    gnx = contact.nx; gny = contact.ny
    groundCollider = col
    tipX = footCx() + contact.nx * contact.depth
    tipY = footCy() + contact.ny * contact.depth
    slide = vt * c.landingTangentialRetention
    val omegaAtTouchdown = omega
    omega = omega.coerceIn(-c.turnSpeed, c.turnSpeed)
    grounded = true
    tumbling = false
    falling = false
    charge = 0.0; charging = false; chargeFullSent = false

    val rotationRad = abs(rotationAccum)
    val eligible = rotationRad >= c.boostThresholdRad
    rotationAccum = 0.0
    val angularFactor = min(1.0, abs(omegaAtTouchdown) / c.rotationSpeed)
    val stored = if (vn >= c.bounceMinSpeed) vn * c.bounce else 0.0

    syncBodyFromTip()
    emitAt(EventType.LAND, tipX - contact.nx * c.tipRadius, tipY - contact.ny * c.tipRadius, vn, surf)

    // free rebound (off by default)
    val passive = vn * c.passiveBounce
    if (!jumpHeld && passive >= c.bounceMinSpeed && c.passiveBounce > 0.0) {
      launch(passive, noBoost = true)
      emitAt(EventType.BOUNCE, tipX, tipY, passive, surf)
      return
    }

    if (jumpHeld) {
      applyLandingSpring(stored, eligible, angularFactor, rotationRad)
      graceTimer = 0.0
    } else {
      pendingSpeed = stored; pendingBoost = eligible; pendingAngularFactor = angularFactor; pendingRotationRad = rotationRad
      graceTimer = c.jumpBufferTime
    }
  }

  /** Landing energy → pre-charge (so a quick release already launches with the bounce), and arm a boost if earned. */
  private fun applyLandingSpring(storedSpeed: Double, boostEligible: Boolean, angularFactor: Double, rotationRad: Double) {
    val c = config
    charging = true
    charge = chargeForSpeed(storedSpeed)
    if (charge >= 1.0) { chargeFullSent = true }
    emit(EventType.CHARGE_START, charge)
    if (boostEligible) {
      boostArmed = true
      boostTimer = c.boostWindow
      boostAngularFactor = angularFactor
      emit(EventType.BOOST_ARMED, Math.toDegrees(rotationRad))
    }
    syncBodyFromTip()
  }

  private fun chargeForSpeed(speed: Double): Double {
    val c = config
    if (speed <= c.jumpPowerMin) return 0.0
    val f = ((speed - c.jumpPowerMin) / (c.jumpPower - c.jumpPowerMin)).coerceIn(0.0, 1.0)
    return f.pow(1.0 / c.jumpChargeCurve)
  }

  // ==================================================================================================
  // Body collisions (rigid body impulses)
  // ==================================================================================================

  /** Resolves the current [contact] for a body circle: impulse + positional correction + events. */
  private fun bodyContact() {
    val col = contact.collider!!
    val surf = col.surface
    val c = config
    if (surf.hazard) emitHazard()
    val impact = impulse(surf.bounce * c.collisionRestitutionScale, surf.friction * c.friction)
    pushOut()
    if (impact > 0.0) {
      lastCollisionNx = contact.nx; lastCollisionNy = contact.ny
      if (simTime - lastCollisionTime > 0.06) {
        lastCollisionTime = simTime
        if (impact >= c.hardCollisionSpeed) emitAt(EventType.HARD_COLLISION, contact.px, contact.py, impact, surf)
        else if (impact >= 2.0) emitAt(EventType.COLLISION, contact.px, contact.py, impact, surf)
      }
    }
  }

  /** Applies normal + friction impulses at [contact]; returns the approach speed (0 when separating). */
  private fun impulse(restitution: Double, mu: Double): Double {
    val col = contact.collider!!
    val inertia = config.inertia
    val nx = contact.nx
    val ny = contact.ny
    val ax = contact.px - x
    val ay = contact.py - y
    val cvx = vx - omega * ay - col.vx
    val cvy = vy + omega * ax - col.vy
    val vn = cvx * nx + cvy * ny
    if (vn >= 0.0) return 0.0
    val rn = ax * ny - ay * nx
    val kN = 1.0 + rn * rn / inertia
    val e = if (-vn < RESTING_SPEED) 0.0 else restitution
    val jn = -(1.0 + e) * vn / kN
    vx += jn * nx; vy += jn * ny; omega += jn * rn / inertia

    val tx = ny
    val ty = -nx
    val cvx2 = vx - omega * ay - col.vx
    val cvy2 = vy + omega * ax - col.vy
    val vt = cvx2 * tx + cvy2 * ty
    val rt = ax * ty - ay * tx
    val kT = 1.0 + rt * rt / inertia
    val maxF = mu * jn
    val jt = (-vt / kT).coerceIn(-maxF, maxF)
    vx += jt * tx; vy += jt * ty; omega += jt * rt / inertia

    omega = omega.coerceIn(-config.maxAngularVelocity, config.maxAngularVelocity)
    clampVelocity()
    return -vn
  }

  private fun pushOut() {
    val d = contact.depth - config.penetrationSlop
    if (d > 0.0) { x += contact.nx * d; y += contact.ny * d }
  }

  // ==================================================================================================
  // Grounded
  // ==================================================================================================

  private fun stepGrounded(lean: Double, dt: Double) {
    val c = config
    val col = groundCollider!!
    // ride moving platforms
    tipX += col.dx
    tipY += col.dy

    // late press right after touchdown still receives the landing spring
    if (graceTimer > 0.0) {
      graceTimer -= dt
      if (jumpHeld && !charging) {
        applyLandingSpring(pendingSpeed, pendingBoost, pendingAngularFactor, pendingRotationRad)
        graceTimer = 0.0
      }
    }

    if (jumpHeld) {
      if (!charging) { charging = true; charge = 0.0; chargeFullSent = false; emit(EventType.CHARGE_START, 0.0) }
      charge = min(1.0, charge + dt / c.jumpChargeTime)
      if (charge >= 1.0 && !chargeFullSent) { chargeFullSent = true; emit(EventType.CHARGE_FULL, 1.0) }
    } else if (charging) {
      launch(c.launchSpeedForCharge(charge), noBoost = false)
      return
    }

    if (boostArmed) {
      boostTimer -= dt
      if (boostTimer <= 0.0) { boostArmed = false; emit(EventType.BOOST_EXPIRED, 0.0) }
    }

    updateLean(lean, dt)
    updateSlide(dt)
    if (grounded) syncBodyFromTip()
  }

  private fun updateLean(lean: Double, dt: Double) {
    val c = config
    val maxLean = c.maxLeanAngleRad
    val target = lean * maxLean
    val err = target - angle
    // braking-curve controller: fast when far, eases in so it stops on the target without overshoot
    val brakeSpeed = sqrt(2.0 * c.deceleration * abs(err) * 0.9)
    val desired = sign(err) * min(min(c.turnSpeed, brakeSpeed), abs(err) / dt)
    val accelerating = desired * omega >= 0.0 && abs(desired) > abs(omega)
    val a = (if (accelerating) c.acceleration else c.deceleration) * dt
    omega += (desired - omega).coerceIn(-a, a)
    if (omega == 0.0) return

    var newAngle = angle + omega * dt
    var newOmega = omega
    if (newAngle > maxLean) { newAngle = maxLean; newOmega = 0.0 }
    if (newAngle < -maxLean) { newAngle = -maxLean; newOmega = 0.0 }
    if (bodyBlocked(newAngle)) {
      omega = 0.0
      return
    }
    angle = newAngle
    omega = newOmega
  }

  /** Would the (pinned-tip) body overlap a solid at [testAngle]? */
  private fun bodyBlocked(testAngle: Double): Boolean {
    val c = config
    val len = c.tipOffset - charge * c.chargeCompression
    val s = sin(testAngle)
    val k = cos(testAngle)
    val cx = tipX + s * len
    val cy = tipY + k * len
    return world.anyPenetration(cx + s * -c.springOffset, cy + k * -c.springOffset, c.springRadius, c.penetrationSlop) ||
      world.anyPenetration(cx, cy, c.torsoRadius, c.penetrationSlop) ||
      world.anyPenetration(cx + s * c.headOffset, cy + k * c.headOffset, c.headRadius, c.penetrationSlop)
  }

  private fun updateSlide(dt: Double) {
    val c = config
    val col = groundCollider!!
    val surf = col.surface
    val mu = surf.friction * c.friction
    val gT = c.gravity * gnx // gravity along +t
    val normalLoad = c.gravity * gny
    val kinetic = mu * c.slideKineticRatio * normalLoad
    if (slide == 0.0) {
      if (abs(gT) > mu * normalLoad) slide = (gT - sign(gT) * kinetic) * dt
    } else {
      var a = gT - sign(slide) * kinetic
      var ns = slide + a * dt
      if (ns * slide < 0.0) {
        // friction cannot reverse the motion: stop, unless the slope itself still wins
        ns = if (abs(gT) > mu * normalLoad) {
          a = gT - sign(gT) * kinetic
          a * dt
        } else 0.0
      }
      slide = ns
    }
    if (slide != 0.0) {
      val tx = gny
      val ty = -gnx
      tipX += tx * slide * dt
      tipY += ty * slide * dt
    }
    if (!probeGround()) leaveGround()
  }

  /**
   * Re-find the ground under the pinned tip: resolve wall blocking, follow slopes, or report that the
   * support is gone. Updates the ground normal/collider on success.
   */
  private fun probeGround(): Boolean {
    val c = config
    // 1. walls beside the tip stop the slide
    if (world.deepestContact(tipX, tipY, c.tipRadius, ContactFilter.NON_GROUND_ONLY, c.groundContactNormalMinY, false, 0.0, probe)) {
      tipX += probe.nx * probe.depth
      tipY += probe.ny * probe.depth
      if (slide != 0.0) {
        val tx = gny
        val ty = -gnx
        if ((tx * probe.nx + ty * probe.ny) * slide < 0.0) slide = 0.0
      }
    }
    // 2. ground within a small snapping distance
    val snap = SNAP_DISTANCE
    if (!world.deepestContact(tipX, tipY, c.tipRadius + snap, ContactFilter.GROUND_ONLY, c.groundContactNormalMinY, true, tipY - c.tipRadius + 0.05, probe)) {
      return false
    }
    val shift = probe.depth - snap
    tipX += probe.nx * shift
    tipY += probe.ny * shift
    gnx = probe.nx; gny = probe.ny
    groundCollider = probe.collider
    return true
  }

  private fun leaveGround() {
    val col = groundCollider
    val tx = gny
    val ty = -gnx
    val len = config.tipOffset - charge * config.chargeCompression
    vx = slide * tx + (col?.vx ?: 0.0) + len * omega * cos(angle)
    vy = slide * ty + (col?.vy ?: 0.0) - len * omega * sin(angle)
    x = tipX + sin(angle) * config.tipOffset
    y = tipY + cos(angle) * config.tipOffset
    grounded = false
    groundCollider = null
    charge = 0.0; charging = false; chargeFullSent = false
    slide = 0.0
    boostArmed = false
    rotationAccum = 0.0
    falling = false
  }

  private fun syncBodyFromTip() {
    val c = config
    val len = c.tipOffset - charge * c.chargeCompression
    val s = sin(angle)
    val k = cos(angle)
    x = tipX + s * len
    y = tipY + k * len
    val col = groundCollider
    val tx = gny
    val ty = -gnx
    vx = slide * tx + (col?.vx ?: 0.0) + len * omega * k
    vy = slide * ty + (col?.vy ?: 0.0) - len * omega * s
  }

  // ==================================================================================================
  // Launch
  // ==================================================================================================

  /**
   * Release: direction = stick axis (optionally blended toward the surface normal and always inside the launch cone),
   * speed from the charge (× boost, × surface multiplier) plus the momentum the player already had.
   */
  private fun launch(baseSpeed: Double, noBoost: Boolean) {
    val c = config
    val col = groundCollider
    val surf = col?.surface
    var dx = sin(angle)
    var dy = cos(angle)
    if (c.launchNormalBlend > 0.0) {
      dx = dx * (1.0 - c.launchNormalBlend) + gnx * c.launchNormalBlend
      dy = dy * (1.0 - c.launchNormalBlend) + gny * c.launchNormalBlend
      val l = hypot(dx, dy)
      dx /= l; dy /= l
    }
    // keep the direction inside the cone around the normal
    val signed = atan2(gnx * dy - gny * dx, gnx * dx + gny * dy) // angle from n to dir
    val limit = c.maxLaunchAngleFromNormalRad
    if (abs(signed) > limit) {
      val a = sign(signed) * limit
      val ca = cos(a)
      val sa = sin(a)
      dx = gnx * ca - gny * sa
      dy = gnx * sa + gny * ca
    }

    var speed = baseSpeed
    var boosted = false
    if (boostArmed && !noBoost) {
      speed *= c.boostPower + c.boostAngularBonus * boostAngularFactor
      boosted = true
    }
    speed *= surf?.velocityMultiplier ?: 1.0
    speed = min(speed, c.maxSpeed)

    val tx = gny
    val ty = -gnx
    val platVx = col?.vx ?: 0.0
    val platVy = col?.vy ?: 0.0
    vx = dx * speed + tx * slide * c.momentumInherit + platVx
    vy = dy * speed + ty * slide * c.momentumInherit + platVy

    // lift the foot clear of the surface and extend the spring
    val lift = 0.004
    x = tipX + gnx * lift + sin(angle) * c.tipOffset
    y = tipY + gny * lift + cos(angle) * c.tipOffset
    grounded = false
    tumbling = false
    groundCollider = null
    slide = 0.0
    charge = 0.0; charging = false; chargeFullSent = false
    boostArmed = false
    rotationAccum = 0.0
    falling = false
    jumpCount++
    clampVelocity()
    sink.emit(GameEvent(EventType.LAUNCH, tipX, tipY, speed, gnx, gny, surf, if (boosted) "boost" else null))
    if (boosted) {
      boostCount++
      emit(EventType.BOOST, speed)
    }
  }

  // ==================================================================================================
  // Sensors
  // ==================================================================================================

  private fun checkSensors() {
    val c = config
    val fx = if (grounded) tipX else footCx()
    val fy = if (grounded) tipY else footCy()
    val hx = x + sin(angle) * c.headOffset
    val hy = y + cos(angle) * c.headOffset

    if (!killed && (y < world.killY || world.sensorAt(fx, fy, c.tipRadius, SensorKind.KILL) != null)) {
      killed = true
      emit(EventType.KILL_FLOOR, 0.0)
    }
    val cp = world.sensorAt(fx, fy, c.tipRadius, SensorKind.CHECKPOINT)
      ?: world.sensorAt(x, y, c.torsoRadius, SensorKind.CHECKPOINT)
    if (cp != null && visitedCheckpoints.add(cp.id)) emit(EventType.CHECKPOINT, 0.0, tag = cp.id)

    if (!goalReached) {
      val hit = world.sensorAt(fx, fy, c.tipRadius, SensorKind.GOAL) != null ||
        world.sensorAt(x, y, c.torsoRadius, SensorKind.GOAL) != null ||
        world.sensorAt(hx, hy, c.headRadius, SensorKind.GOAL) != null ||
        world.goalColliderAt(fx, fy, c.tipRadius + 0.02) != null
      if (hit) { goalReached = true; emit(EventType.GOAL, 0.0) }
    }
  }

  // ==================================================================================================
  // Events / debug
  // ==================================================================================================

  private fun emit(type: EventType, magnitude: Double, tag: String? = null) {
    sink.emit(GameEvent(type, x, y, magnitude, 0.0, 1.0, null, tag))
  }

  private fun emitAt(type: EventType, px: Double, py: Double, magnitude: Double, surf: com.pogoascent.physics.SurfaceDef?, tag: String? = null) {
    sink.emit(GameEvent(type, px, py, magnitude, contact.nx, contact.ny, surf, tag))
  }

  private fun emitHazard() {
    if (simTime - lastHazardTime < 0.5) return
    lastHazardTime = simTime
    emitAt(EventType.HAZARD, contact.px, contact.py, 0.0, contact.collider?.surface)
  }

  /** Interpolated pose for rendering: alpha 0 = previous tick, 1 = current tick. */
  fun renderX(alpha: Double) = prevX + (x - prevX) * alpha
  fun renderY(alpha: Double) = prevY + (y - prevY) * alpha
  fun renderAngle(alpha: Double) = prevAngle + (angle - prevAngle) * alpha

  /** Current distance from centre of mass to the tip, including spring compression. */
  val tipDistance: Double get() = if (grounded) config.tipOffset - charge * config.chargeCompression else config.tipOffset
  val tipWorldX: Double get() = x - sin(angle) * tipDistance
  val tipWorldY: Double get() = y - cos(angle) * tipDistance
  val tipCenterX: Double get() = tipX
  val tipCenterY: Double get() = tipY
  val groundColliderRef: Collider? get() = groundCollider
  val groundNormalX: Double get() = gnx
  val groundNormalY: Double get() = gny
  val groundSurfaceId: String? get() = groundCollider?.surface?.id

  fun debugSnapshot(): DebugSnapshot {
    val c = config
    val boostMult = if (boostArmed) c.boostPower + c.boostAngularBonus * boostAngularFactor else 1.0
    return DebugSnapshot(
      vx = vx, vy = vy, speed = hypot(vx, vy),
      horizontalSpeed = abs(vx), verticalSpeed = vy,
      angularVelocity = omega, angleDeg = Math.toDegrees(angle),
      jumpPower = c.launchSpeedForCharge(charge) * boostMult, charge = charge,
      gravity = c.gravity, grounded = grounded, airControl = c.airControl,
      collisionNx = lastCollisionNx, collisionNy = lastCollisionNy,
      boostAngleDeg = Math.toDegrees(abs(rotationAccum)), boostPower = boostMult, boostArmed = boostArmed,
      height = y, state = state,
    )
  }

  companion object {
    /** Below this approach speed a body hit does not bounce (avoids micro-jitter at rest). */
    private const val RESTING_SPEED = 0.6

    /** How far below the tip the ground may be and still hold the pinned tip (slope following). */
    private const val SNAP_DISTANCE = 0.06
  }
}
