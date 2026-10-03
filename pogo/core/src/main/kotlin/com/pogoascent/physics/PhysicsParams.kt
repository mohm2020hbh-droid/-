package com.pogoascent.physics

/** Evidence grade (from the task): A game files · B developer/docs · C gameplay measurement · D unconfirmed. */
enum class Confidence { A, B, C, D }

/**
 * Metadata for one [PhysicsConfig] field – the row that ends up in PHYSICS_MASTER.md.
 * [category] uses the task's own vocabulary (Gravity, Jump Force, Bounce, …).
 */
class ParamSpec(
  val key: String,
  val category: String,
  val unit: String,
  val source: String,
  val confidence: Confidence,
  val editable: String,
  val notes: String,
  val read: (PhysicsConfig) -> Double,
)

object PhysicsParams {
  private const val NO_DATA = "Design default – no analysis data in repo (DECISIONS D-001)"
  private const val ENGINEERING = "Engineering decision – not a game measurement"
  private const val LIVE = "Yes – JSON / debug scene (live)"
  private const val RESTART = "Yes – JSON (applies on level restart)"

  private fun p(
    key: String,
    category: String,
    unit: String,
    notes: String,
    source: String = NO_DATA,
    confidence: Confidence = Confidence.D,
    editable: String = LIVE,
    read: (PhysicsConfig) -> Double,
  ) = ParamSpec(key, category, unit, source, confidence, editable, notes, read)

  val all: List<ParamSpec> = listOf(
    // ---- Timestep ---------------------------------------------------------------------------
    p("fixedTimestepHz", "Timestep", "Hz", "Physics tick rate. Game speed never depends on FPS; the renderer interpolates.", ENGINEERING, editable = RESTART) { it.fixedTimestepHz },
    p("maxFrameDelta", "Timestep", "s", "A frame longer than this is clamped (prevents the spiral of death after a pause).", ENGINEERING) { it.maxFrameDelta },
    p("maxStepsPerFrame", "Timestep", "ticks", "Hard cap of physics ticks per rendered frame; backlog beyond it is dropped.", ENGINEERING) { it.maxStepsPerFrame.toDouble() },
    p("maxSubstepTravel", "Timestep", "m", "Continuous-collision guard: a tick is split so nothing moves further than this per sub-step (so thin platforms are never tunnelled).", ENGINEERING) { it.maxSubstepTravel },

    // ---- Gravity & limits -------------------------------------------------------------------
    p("gravity", "Gravity", "m/s²", "Downward acceleration (positive number). Higher = snappier, shorter jumps.") { it.gravity },
    p("fallGravityMultiplier", "Gravity", "×", "Extra gravity while moving downward (1.0 = symmetric arc).") { it.fallGravityMultiplier },
    p("terminalVelocity", "Terminal Velocity", "m/s", "Maximum downward speed.") { it.terminalVelocity },
    p("maxSpeed", "Maximum Velocity", "m/s", "Magnitude clamp on the velocity vector in any direction.") { it.maxSpeed },
    p("fallSpeed", "Fall Speed", "m/s", "Downward speed above which the player is flagged as falling (fall camera, fall audio).") { it.fallSpeed },

    // ---- Jump charge / release --------------------------------------------------------------
    p("jumpChargeTime", "Jump Charge", "s", "Time to charge the spring from 0 to 100 % while the jump input is held.") { it.jumpChargeTime },
    p("jumpChargeCurve", "Jump Charge", "exp", "Launch speed = min + (max-min)·charge^curve. 1 = linear.") { it.jumpChargeCurve },
    p("chargeCompression", "Jump Charge", "m", "How far the body sinks toward the pinned tip at 100 % charge (visual + head clearance).") { it.chargeCompression },
    p("jumpPowerMin", "Jump Power", "m/s", "Launch speed of a tap (0 % charge). Mass = 1, so launch 'force' is expressed as speed.") { it.jumpPowerMin },
    p("jumpPower", "Jump Force", "m/s", "Launch speed at 100 % charge (= the full-charge jump power). Apex height ≈ v²/2g.") { it.jumpPower },
    p("jumpBufferTime", "Jump Release", "s", "Touch forgiveness: a press up to this long AFTER touchdown still receives the landing-spring pre-charge (and can still arm a boost).") { it.jumpBufferTime },
    p("maxLaunchAngleFromNormalDeg", "Jump Release", "deg", "A launch is rotated back inside this cone around the ground normal (stops launching into the ground).") { it.maxLaunchAngleFromNormalDeg },
    p("launchNormalBlend", "Jump Release", "0..1", "0 = launch exactly along the stick; 1 = along the surface normal.") { it.launchNormalBlend },
    p("momentumInherit", "Momentum", "0..1+", "Fraction of the tip's sideways slide speed added to the launch (a moving platform's velocity is always inherited in full) – so the launch depends on the state before release.") { it.momentumInherit },

    // ---- Ground control ---------------------------------------------------------------------
    p("maxLeanAngleDeg", "Ground Control", "deg", "Largest tilt from vertical while standing on the tip (lean input 1.0 = this angle).") { it.maxLeanAngleDeg },
    p("turnSpeed", "Turn Speed", "rad/s", "Maximum lean rate while grounded.") { it.turnSpeed },
    p("acceleration", "Acceleration", "rad/s²", "Angular acceleration of the lean toward the target angle (ground).") { it.acceleration },
    p("deceleration", "Deceleration", "rad/s²", "Angular braking of the lean when it overshoots / input is released (ground).") { it.deceleration },
    p("groundContactNormalMinY", "Ground Contact", "n.y", "A foot contact whose surface normal has y ≥ this is standable ground (0.35 ≈ 69° slope); steeper = wall.") { it.groundContactNormalMinY },
    p("friction", "Friction", "×", "Global multiplier on every surface's friction coefficient.") { it.friction },
    p("slideKineticRatio", "Friction", "0..1", "Kinetic friction = this × static friction (used while the tip slides).") { it.slideKineticRatio },

    // ---- Air control ------------------------------------------------------------------------
    p("airControl", "Air Control", "×", "Scales air rotation authority (target rate and acceleration). 0 = no steering in the air.") { it.airControl },
    p("airHorizontalAccel", "Air Control", "m/s²", "Optional direct horizontal push in the air (0 = steering only by rotation).") { it.airHorizontalAccel },
    p("airLinearDrag", "Momentum", "1/s", "Linear air drag (velocity *= 1 - drag·dt).") { it.airLinearDrag },
    p("rotationSpeed", "Rotation", "rad/s", "Target angular speed in the air at full input.") { it.rotationSpeed },
    p("airTurnAccel", "Rotation", "rad/s²", "How fast the air spin reaches the target (before the airControl multiplier).") { it.airTurnAccel },
    p("airAngularDrag", "Angular Velocity", "1/s", "Spin decay in the air when there is no rotate input (2.0 → a spin dies out within ~0.5 s, so a boost needs a deliberate, sustained spin).") { it.airAngularDrag },
    p("maxAngularVelocity", "Angular Velocity", "rad/s", "Hard clamp of angular speed (collisions can kick spin above rotationSpeed).") { it.maxAngularVelocity },

    // ---- Landing / bounce -------------------------------------------------------------------
    p("bounce", "Bounce", "0..1+", "Spring rebound: share of the landing speed stored as pre-charge when the jump is HELD at touchdown (bounce chain).") { it.bounce },
    p("passiveBounce", "Bounce", "0..1+", "Free rebound without holding: the stick re-launches at landing speed × this when ≥ bounceMinSpeed (0 = the tip just sticks).") { it.passiveBounce },
    p("bounceMinSpeed", "Bounce", "m/s", "Landing speeds below this store no charge (lets the stick settle).") { it.bounceMinSpeed },
    p("landingTangentialRetention", "Landing Response", "0..1", "Share of sideways speed kept as tip slide after a good landing.") { it.landingTangentialRetention },
    p("maxLandingTiltDeg", "Landing Response", "deg", "Angle between stick axis and surface normal beyond which the tip slips (bad landing → tumble).") { it.maxLandingTiltDeg },
    p("slipRestitution", "Landing Response", "0..1+", "Restitution of a slipped (bad) landing.") { it.slipRestitution },
    p("slipSpinFactor", "Landing Response", "×", "Angular kick of a slipped landing, proportional to the sideways speed.") { it.slipSpinFactor },

    // ---- Collision response -----------------------------------------------------------------
    p("collisionRestitutionScale", "Collision Response", "×", "Global multiplier on every surface's bounce for BODY collisions (head, torso, spring).") { it.collisionRestitutionScale },
    p("inertiaFactor", "Collision Response", "×L²", "Moment of inertia = factor·tipOffset² (mass 1). Lower = body spins more when it hits something.") { it.inertiaFactor },
    p("hardCollisionSpeed", "Collision Response", "m/s", "Impact speed at which a body hit counts as 'hard' (camera shake, haptic, particles).") { it.hardCollisionSpeed },
    p("penetrationSlop", "Collision Response", "m", "Allowed overlap before positional correction (avoids jitter).", ENGINEERING) { it.penetrationSlop },

    // ---- Boost -------------------------------------------------------------------------------
    p("boostThreshold", "Boost Rotation", "deg", "Rotation accumulated in one flight that arms a boost. The task text quotes 285° as an example; no data file backs it, so it stays grade D.", "Task prompt example – no data file") { it.boostThreshold },
    p("boostPower", "Boost", "×", "Launch-speed multiplier of an armed boost jump (clamped by maxSpeed).") { it.boostPower },
    p("boostWindow", "Boost", "s", "After the boost-arming landing the player has this long to release the jump or the boost expires.") { it.boostWindow },
    p("boostAngularBonus", "Boost", "×", "Extra boost multiplier proportional to |angular velocity| at touchdown (skill reward).") { it.boostAngularBonus },

    // ---- Player collision shape --------------------------------------------------------------
    p("tipOffset", "Ground Contact", "m", "Centre of mass → pogo tip, along the stick axis.", editable = RESTART) { it.tipOffset },
    p("tipRadius", "Ground Contact", "m", "Radius of the foot circle (the only circle that can 'land').", editable = RESTART) { it.tipRadius },
    p("springOffset", "Ground Contact", "m", "Centre of mass → spring collision circle.", editable = RESTART) { it.springOffset },
    p("springRadius", "Ground Contact", "m", "Radius of the spring circle.", editable = RESTART) { it.springRadius },
    p("torsoRadius", "Ground Contact", "m", "Radius of the torso circle (at the centre of mass).", editable = RESTART) { it.torsoRadius },
    p("headOffset", "Ground Contact", "m", "Centre of mass → head circle.", editable = RESTART) { it.headOffset },
    p("headRadius", "Ground Contact", "m", "Radius of the head circle.", editable = RESTART) { it.headRadius },
  )

  val byKey: Map<String, ParamSpec> = all.associateBy { it.key }

  /** Categories required by the task, in the order they should be checked off. */
  val requiredCategories: List<String> = listOf(
    "Gravity", "Jump Force", "Jump Power", "Bounce", "Friction", "Acceleration", "Deceleration", "Air Control",
    "Ground Control", "Angular Velocity", "Rotation", "Turn Speed", "Momentum", "Collision Response",
    "Landing Response", "Boost", "Boost Rotation", "Maximum Velocity", "Terminal Velocity", "Fall Speed",
    "Jump Charge", "Jump Release", "Ground Contact",
  )

  fun format(v: Double): String {
    if (v == Math.rint(v) && Math.abs(v) < 1e9) return v.toLong().toString()
    return java.math.BigDecimal(v).round(java.math.MathContext(6)).stripTrailingZeros().toPlainString()
  }
}
