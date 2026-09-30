package com.carom.core.game

import com.carom.core.level.ElementKind
import com.carom.core.level.LevelData
import com.carom.core.math.Vec2
import com.carom.core.physics.Ball
import com.carom.core.physics.CircleTrigger
import com.carom.core.physics.PhysicsWorld
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * The rules of one level: throwing, forces, portals, counting bounces, winning and losing.
 *
 * The ball is a body with a velocity. Every step it loses speed to drag, is pushed by the force zones it is in,
 * has its speed capped once (whatever pushed it), and is moved along its velocity with continuous collision.
 * Simulation runs at a fixed [STEP] regardless of frame rate, so a given throw always ends the same way on
 * every device; game time inside slow motion runs slower by shrinking the step, not by skipping steps.
 * Rendering reads [BallState.renderX]/[renderY], which interpolate between steps for smooth motion.
 *
 * Nothing is created while playing: balls come from a pool, and every list is built once.
 */
class GameSession(val level: LevelData, val tuning: GameTuning = GameTuning.DEFAULT) {

    enum class State { AIMING, MOVING, WON, FAILED }

    enum class FailReason {
        /** Hit a wall with no bounces left. */
        OUT_OF_BOUNCES,

        /** Ran out of speed (outside a touch zone) before reaching the exit. */
        STOPPED,

        /** Touched a death zone or a deadly barrier. */
        DEATH_ZONE,
    }

    enum class ElementEvent { BROKEN, SWITCHED, OPENED }

    /**
     * One wall contact: the ball's centre at the moment of contact, the wall's unit normal (pointing towards the
     * ball) and how hard the ball hit, from 0 (grazing) to 1 (head-on at full speed). [progress] is how far
     * through its bounces the ball was (0 on the first hit). A [fatal] hit is one that breaks the ball (no bounces
     * left, or a deadly barrier): it is reported through [Listener.onBallLost] and [lastImpact], not [Listener.onBounce].
     */
    class Impact(
        val x: Double, val y: Double, val nx: Double, val ny: Double, val strength: Double,
        val ball: Int = 0, val element: Int = -1, val kind: ElementKind? = null, val progress: Double = 0.0,
        val fatal: Boolean = false,
    )

    /** Presentation hooks (sound, haptics, effects). The rules never depend on them. */
    interface Listener {
        /** The first swipe of the attempt sent the ball off. */
        fun onLaunch() {}

        /** A counted collision. [bouncesLeft] is the ball's count after it. */
        fun onBounce(impact: Impact, bouncesLeft: Int) {}
        fun onWin(x: Double, y: Double) {}

        /** Every ball is gone. For [FailReason.OUT_OF_BOUNCES], [GameSession.lastImpact] is the hit that ended it. */
        fun onFail(reason: FailReason, x: Double, y: Double) {}

        /** One ball was lost (its explosion), whether or not others remain. */
        fun onBallLost(ball: Int, reason: FailReason, x: Double, y: Double) {}

        /** A ball entered the exit but the exit still needs more: [count] of [needed]. */
        fun onExitPartial(count: Int, needed: Int, x: Double, y: Double) {}
        fun onPortal(ball: Int, fromX: Double, fromY: Double, toX: Double, toY: Double) {}
        fun onSlowMo(active: Boolean) {}
        fun onBallSpawned(ball: Int, x: Double, y: Double) {}
        fun onElement(element: Int, event: ElementEvent) {}

        /** A swipe pushed a ball that was already moving. */
        fun onImpulse(ball: Int) {}
    }

    /** One ball in play; slots are reused, never created during a level. */
    class BallState(val index: Int, radius: Double) {
        val body = Ball(radius)
        var alive = false
            internal set

        /** Went into the exit (counted, no longer in play). */
        var absorbed = false
            internal set

        /** Bounces this ball has left, and started with. */
        var left = 0
            internal set
        var total = 0
            internal set

        /** 1 normally; fades to 0 as the ball slows to a stop. */
        var alpha = 1.0
            internal set
        var inTouchZone = false
            internal set
        internal var prevX = 0.0
        internal var prevY = 0.0
        internal var lockPortal = -1
        internal var lockUntil = 0.0
        internal var lastSeg = -1
        internal var lastContactStep = -100
        internal var lastHitTime = -1e9
        internal var lastHitX = 0.0
        internal var lastHitY = 0.0
        internal var acc = 0.0

        val x: Double get() = body.x
        val y: Double get() = body.y

        /** How far through its bounces the ball is (0 = none used). */
        val progress: Double get() = if (total > 0) (total - left).toDouble() / total else 1.0
    }

    var listener: Listener? = null

    /** The most recent counted wall contact of this attempt, or null before the first one. */
    var lastImpact: Impact? = null
        private set

    val elements: List<ElementRuntime> = WorldBuilder.runtimes(level)
    private val world: PhysicsWorld = WorldBuilder.build(level, elements)

    /** The balls (a pool of [GameTuning.maxBalls]); the first is the ball that starts at the level's start point. */
    val balls: List<BallState> = List(tuning.maxBalls.coerceAtLeast(1)) { BallState(it, level.ballRadius) }

    /** The first ball. */
    val ball: Ball get() = balls[0].body

    var state = State.AIMING
        private set
    val bouncesLeft: Int get() = balls[0].left
    val bouncesUsed: Int get() = balls[0].total - balls[0].left
    var failReason: FailReason? = null
        private set

    /** Balls that have gone into the exit, out of [LevelData.exitRequired]. */
    var exitCount = 0
        private set

    /** Seconds of real time since the first swipe. */
    var flightTime = 0.0
        private set

    /** Game time since the first swipe (slow motion makes it run slower than [flightTime]). Elements move by this clock. */
    var gameTime = 0.0
        private set

    /** 1 normally; [GameTuning.slowMoScale] while a ball is in a slow-motion zone. */
    var timeScale = 1.0
        private set
    val isSlowMotion: Boolean get() = timeScale < 1.0

    private val currentPath = ArrayList<Vec2>()
    private var previousPath: List<Vec2> = emptyList()

    /** Corners of the first ball's current shot: launch point, then every bounce (and the end point once over). */
    val path: List<Vec2> get() = currentPath

    /** The path of the previous attempt, kept after a restart as a reference for the next aim. */
    val lastShotPath: List<Vec2> get() = previousPath

    private var accumulator = 0.0
    private var stepCounter = 0
    val renderFraction: Double get() = accumulator / STEP
    val renderX: Double get() = renderX(balls[0])
    val renderY: Double get() = renderY(balls[0])
    fun renderX(b: BallState): Double = b.prevX + (b.body.x - b.prevX) * renderFraction
    fun renderY(b: BallState): Double = b.prevY + (b.body.y - b.prevY) * renderFraction

    private val refToWorld = tuning.refToWorld(level.maxSpeed)
    private val stopSpeed = tuning.stopSpeedRef * refToWorld
    private val fadeSpeed = tuning.fadeSpeedRef * refToWorld

    // Elements sorted by what they do, once, so a step only visits what matters.
    private val forces = elements.filter { it.data.kind in FORCE_KINDS }
    private val portals = elements.filter { it.data.kind == ElementKind.PORTAL }
    private val switches = elements.filter { it.data.kind == ElementKind.SWITCH }
    private val deadly = elements.filter { !it.data.physical && it.data.deathTrigger }
    private val touchZones = elements.filter { it.data.kind == ElementKind.TOUCH_ZONE || it.data.kind == ElementKind.SLOWMO_ZONE }
    private val slowZones = elements.filter { it.data.kind == ElementKind.SLOWMO_ZONE }
    private val switched = elements.filter { it.data.switched }
    private val portalTarget = IntArray(elements.size) { i ->
        val link = elements[i].data.link
        if (elements[i].data.kind != ElementKind.PORTAL || link.isEmpty()) -1 else elements.indexOfFirst { it.data.id == link && it.data.kind == ElementKind.PORTAL }
    }
    private val channels = BooleanArray(CHANNELS)
    private val armed = BooleanArray(elements.size) { true }

    /** The ball that scored (the first ball, until one has). */
    val winnerBall: BallState get() = winner

    /** Whether switch line [channel] has been flipped on (an odd number of times) in this attempt. */
    fun channelOn(channel: Int): Boolean = channels[channel.coerceIn(0, CHANNELS - 1)]

    private var current: BallState = balls[0]
    private var winner: BallState = balls[0]
    private var aliveCount = 0
    private var lastLoss = FailReason.STOPPED
    private val tmp = DoubleArray(2)
    private val surface = DoubleArray(2)

    private val rules = object : PhysicsWorld.Listener {
        override fun onWallContact(x: Double, y: Double, nx: Double, ny: Double): Boolean = wallContact(x, y, nx, ny)

        override fun onTrigger(trigger: CircleTrigger, x: Double, y: Double): Boolean = exitReached(x, y)

        override fun surfaceVelocity(world: PhysicsWorld, x: Double, y: Double, out: DoubleArray): Boolean =
            surfaceVelocityAt(world.contactSegment, x, y, out)
    }

    init {
        reset()
    }

    // ------------------------------------------------------------------ the player's input

    /**
     * Fires the ball in direction (dirX, dirY) with [power] in 0..1. Power maps to speed as a square root so that
     * the distance travelled grows linearly with how far the player pulled. (The swipe control uses [applyImpulse].)
     */
    fun launch(dirX: Double, dirY: Double, power: Double): Boolean {
        val len = hypot(dirX, dirY)
        if (state != State.AIMING || len == 0.0 || power <= 0.0) return false
        val b = balls[0]
        b.body.dirX = dirX / len
        b.body.dirY = dirY / len
        b.body.speed = level.maxSpeed * sqrt(power.coerceAtMost(1.0))
        startFlight()
        return true
    }

    /** A swipe of (dxDp, dyDp) dp: delta × sensibility is the impulse. False if nothing was in a state to take it. */
    fun swipe(dxDp: Double, dyDp: Double): Boolean {
        val len = hypot(dxDp, dyDp)
        if (len < tuning.swipeTolerance) return false
        val speed = len * tuning.touchSensibility * refToWorld
        return applyImpulse(dxDp / len * speed, dyDp / len * speed)
    }

    /** Whether a swipe would do anything now: before the first throw, or with a ball in a touch zone. */
    val canSwipe: Boolean
        get() = when (state) {
            State.AIMING -> true
            State.MOVING -> balls.any { it.alive && it.inTouchZone }
            else -> false
        }

    /**
     * Pushes with an impulse of (ix, iy) world units per second (mass 1, so it is also the change of velocity).
     * Before the first throw it sets the ball off; afterwards it pushes every ball that is in a touch zone.
     */
    fun applyImpulse(ix: Double, iy: Double): Boolean {
        val len = hypot(ix, iy)
        if (len <= 0.0) return false
        val dv = len / tuning.mass
        if (state == State.AIMING) {
            val b = balls[0]
            b.body.dirX = ix / len
            b.body.dirY = iy / len
            b.body.speed = min(dv, level.maxSpeed)
            startFlight()
            return true
        }
        if (state != State.MOVING) return false
        var pushed = false
        for (i in balls.indices) {
            val b = balls[i]
            if (!b.alive || !b.inTouchZone) continue
            val vx = b.body.dirX * b.body.speed + ix / len * dv
            val vy = b.body.dirY * b.body.speed + iy / len * dv
            setVelocity(b, vx, vy)
            pushed = true
            listener?.onImpulse(b.index)
        }
        return pushed
    }

    /**
     * Before a throw, moves the ball as close to (x, y) as it can go: within the level's control zone (its whole body
     * stays inside the zone's line), inside the level, and clear of every wall. (The hold-and-throw control; the
     * swipe control does not move the ball.) Returns true if the ball moved.
     */
    fun placeBall(x: Double, y: Double): Boolean {
        if (state != State.AIMING) return false
        val b = balls[0]
        val r = b.body.radius
        level.zone.nearestCentre(x, y, r, zonePoint)
        val tx = zonePoint[0].coerceIn(r, level.width - r)
        val ty = zonePoint[1].coerceIn(r, level.height - r)
        val dist = hypot(tx - b.body.x, ty - b.body.y)
        if (dist < 1e-9) return false
        // Slide a probe from the ball towards the target: it stops at the first wall in the way,
        // so the ball can be pushed against a wall but never through it.
        probe.x = b.body.x
        probe.y = b.body.y
        probe.dirX = (tx - b.body.x) / dist
        probe.dirY = (ty - b.body.y) / dist
        world.move(probe, dist, stopAtWalls)
        if (probe.x == b.body.x && probe.y == b.body.y) return false
        b.body.place(probe.x, probe.y)
        b.prevX = probe.x
        b.prevY = probe.y
        return true
    }

    private val zonePoint = DoubleArray(2)
    private val probe = Ball(level.ballRadius)
    private val stopAtWalls = object : PhysicsWorld.Listener {
        override fun onWallContact(x: Double, y: Double, nx: Double, ny: Double) = false
        override fun onTrigger(trigger: CircleTrigger, x: Double, y: Double) = true
    }

    // ------------------------------------------------------------------ time

    /** Advances by one frame's worth of time, running as many fixed steps as fit. */
    fun advance(frameSeconds: Double) {
        if (state != State.MOVING) return
        accumulator += min(frameSeconds, MAX_FRAME)
        // The small margin keeps the step count the same at every frame rate: 144 frames of 1/144 s must make
        // 120 steps even though their sum is a few billionths short of a second.
        while (accumulator >= STEP - EPSILON && state == State.MOVING) {
            step()
            accumulator = maxOf(0.0, accumulator - STEP)
        }
    }

    /**
     * One fixed simulation step. It lasts [STEP] of real time and `STEP × timeScale` of game time, so slow motion
     * makes the step smaller (a finer simulation) instead of making steps rarer.
     */
    fun step() {
        if (state != State.MOVING) return
        val dt = STEP * timeScale
        gameTime += dt
        flightTime += STEP
        stepCounter++
        for (i in elements.indices) elements[i].animate(gameTime, dt)
        for (i in balls.indices) {
            balls[i].prevX = balls[i].body.x
            balls[i].prevY = balls[i].body.y
        }
        for (i in balls.indices) {
            val b = balls[i]
            if (!b.alive) continue
            stepBall(b, dt)
            if (state != State.MOVING) return
        }
        updateSlowMotion()
        rearmSwitches()
        if (flightTime >= MAX_FLIGHT_SECONDS) {
            for (i in balls.indices) if (balls[i].alive) kill(balls[i], FailReason.STOPPED)
        }
        if (state == State.MOVING && aliveCount == 0) finish(State.FAILED, lastLoss)
    }

    private fun stepBall(b: BallState, dt: Double) {
        val body = b.body
        // A portal jump locks portals for this ball until it has left the exit portal and the cooldown is over.
        if (b.lockPortal >= 0 && gameTime >= b.lockUntil && !elements[b.lockPortal].contains(body.x, body.y)) b.lockPortal = -1

        var vx = body.dirX * body.speed
        var vy = body.dirY * body.speed
        val v0 = body.speed

        // Drag, and the level's constant friction if it has any.
        val drag = level.drag
        if (drag > 0.0) {
            val k = exp(-drag * dt)
            vx *= k
            vy *= k
        }
        var stoppedByFriction = false
        if (level.friction > 0.0) {
            val sp = hypot(vx, vy)
            val keep = sp - level.friction * dt
            if (keep <= 0.0) {
                vx = 0.0
                vy = 0.0
                stoppedByFriction = true
            } else if (sp > 0.0) {
                vx *= keep / sp
                vy *= keep / sp
            }
        }

        // Force zones, acting on the velocity while the ball's centre is inside them.
        for (fi in forces.indices) {
            val f = forces[fi]
            if (!f.active || !f.contains(body.x, body.y)) continue
            val d = f.data
            when (d.kind) {
                ElementKind.BOOSTER -> {
                    direction(f)
                    val a = d.force * tuning.boosterGain * refToWorld / tuning.mass * dt
                    vx += tmp[0] * a
                    vy += tmp[1] * a
                }
                ElementKind.ATTRACTIVE, ElementKind.REPULSIVE -> {
                    var dx = f.x - body.x
                    var dy = f.y - body.y
                    val dist = hypot(dx, dy)
                    if (dist > 1e-6) {
                        dx /= dist
                        dy /= dist
                        val sign = if (d.kind == ElementKind.ATTRACTIVE) 1.0 else -1.0
                        val a = d.force * tuning.fieldGain * refToWorld / tuning.mass * dt * sign
                        vx += dx * a
                        vy += dy * a
                    }
                }
                else -> { // SLOWER: velocity = lerp(velocity, 0, force × dt × gain), which never overshoots zero
                    val k = (d.force * tuning.slowerGain * dt).coerceIn(0.0, 1.0)
                    vx -= vx * k
                    vy -= vy * k
                }
            }
        }

        // One speed limit for everything that pushed the ball.
        setVelocity(b, vx, vy)

        // Move along the velocity (trapezoid rule: half the speed before and after, exact for constant friction).
        val v1 = body.speed
        val distance = if (stoppedByFriction && v0 > 0.0) v0 * v0 / (2.0 * level.friction) else (v0 + v1) / 2.0 * dt
        current = b
        val moved = world.move(body, distance, rules)
        if (!moved || !b.alive) return

        // Events along the path of this step.
        if (checkDeadly(b)) return
        checkPortals(b)
        checkSwitches(b)
        b.inTouchZone = false
        for (zi in touchZones.indices) if (touchZones[zi].active && touchZones[zi].contains(body.x, body.y)) {
            b.inTouchZone = true
            break
        }

        // Standing still is death, unless the player can swipe the ball again from where it is.
        val sp = body.speed
        if (b.inTouchZone) {
            b.alpha = 1.0
        } else if (sp <= stopSpeed) {
            kill(b, FailReason.STOPPED)
        } else {
            b.alpha = ((sp - stopSpeed) / (fadeSpeed - stopSpeed)).coerceIn(0.0, 1.0)
        }
    }

    /** Sets the velocity, with the one speed limit applied. */
    private fun setVelocity(b: BallState, vx: Double, vy: Double) {
        val sp = hypot(vx, vy)
        val body = b.body
        if (sp <= 1e-12) {
            body.speed = 0.0
            return
        }
        body.dirX = vx / sp
        body.dirY = vy / sp
        body.speed = min(sp, level.maxSpeed)
    }

    /** The direction a force zone pushes: its vector turned with the zone, or its own +x axis, written to [tmp]. */
    private fun direction(f: ElementRuntime) {
        val v = f.data.vector
        if (v.x == 0.0 && v.y == 0.0) f.rotated(1.0, 0.0, tmp) else f.rotated(v.x, v.y, tmp)
        val len = hypot(tmp[0], tmp[1])
        if (len > 0.0) {
            tmp[0] /= len
            tmp[1] /= len
        }
    }

    // ------------------------------------------------------------------ collisions

    private fun wallContact(x: Double, y: Double, nx: Double, ny: Double): Boolean {
        val b = current
        val segIndex = world.contactSegment
        val owner = if (segIndex >= 0) world.segments[segIndex].owner else -1
        val el = if (owner >= 0) elements[owner] else null

        // How hard the hit is: the speed into the wall (as seen from the wall), relative to the top speed.
        var vx = b.body.dirX * b.body.speed
        var vy = b.body.dirY * b.body.speed
        if (surfaceVelocityAt(segIndex, x, y, surface)) {
            vx -= surface[0]
            vy -= surface[1]
        }
        val strength = (abs(vx * nx + vy * ny) / level.maxSpeed).coerceIn(0.0, 1.0)

        // The same collision must not be counted twice (a graze, a corner, two contact points at once, a ball
        // squeezed against a barrier): the ball still bounces, but nothing is counted or reported. A contact
        // with the wall just counted, in the very next steps or within the debounce time, is that same
        // collision; so is one at the same spot. Real bounces in a row are far apart in time and space.
        val sameWall = segIndex == b.lastSeg &&
            (stepCounter - b.lastContactStep <= CONTINUOUS_STEPS || gameTime - b.lastHitTime < tuning.debounceTime)
        val sameSpot = hypot(x - b.lastHitX, y - b.lastHitY) < tuning.debounceTravel && gameTime - b.lastHitTime < 2 * tuning.debounceTime
        if (segIndex == b.lastSeg) b.lastContactStep = stepCounter
        if (sameWall || sameSpot) return true
        b.lastSeg = segIndex
        b.lastContactStep = stepCounter
        b.lastHitTime = gameTime
        b.lastHitX = x
        b.lastHitY = y

        val deadly = el != null && el.data.deathTrigger
        val counts = el == null || el.data.countsAsBounce
        val impact = Impact(x, y, nx, ny, strength, b.index, owner, el?.data?.kind, b.progress, fatal = deadly || (counts && b.left == 0))
        lastImpact = impact
        if (deadly) {
            kill(b, FailReason.DEATH_ZONE)
            return false
        }
        if (counts) {
            if (b.left == 0) {
                kill(b, FailReason.OUT_OF_BOUNCES)
                return false
            }
            b.left--
        }
        if (b.index == 0) currentPath.add(Vec2(x, y))
        if (el != null) touchElement(el, b, nx, ny)
        listener?.onBounce(impact, b.left)
        return true
    }

    private fun surfaceVelocityAt(segIndex: Int, x: Double, y: Double, out: DoubleArray): Boolean {
        if (segIndex < 0) return false
        val owner = world.segments[segIndex].owner
        if (owner < 0) return false
        val e = elements[owner]
        if (e.vx == 0.0 && e.vy == 0.0 && e.omega == 0.0) return false
        out[0] = e.vx - e.omega * (y - e.y)
        out[1] = e.vy + e.omega * (x - e.x)
        return true
    }

    /** What hitting a barrier does besides bounce: break it, or let out the balls it holds. */
    private fun touchElement(el: ElementRuntime, b: BallState, nx: Double, ny: Double) {
        when (el.data.kind) {
            ElementKind.DESTRUCTIBLE -> if (el.hit()) listener?.onElement(el.index, ElementEvent.BROKEN)
            ElementKind.BALL_CONTAINER -> if (!el.spent) {
                el.spent = true
                releaseBalls(el, b, nx, ny)
                listener?.onElement(el.index, ElementEvent.OPENED)
            }
            else -> {}
        }
    }

    /**
     * A container lets out [Element.value] balls, fanned out around a direction: the container's own `vector`
     * (turned with it) and out of that side of it, or else the side that was hit, along the hit's normal.
     */
    private fun releaseBalls(el: ElementRuntime, from: BallState, nx: Double, ny: Double) {
        val count = el.data.value.toInt().coerceAtLeast(1)
        val speed = if (el.data.force > 0.0) el.data.force * refToWorld else from.body.speed
        val bounces = if (el.data.ballBounces >= 0) el.data.ballBounces else level.bounces
        val v = el.data.vector
        val fixed = v.x != 0.0 || v.y != 0.0
        var ox = nx
        var oy = ny
        var originX = from.body.x
        var originY = from.body.y
        var reach = level.ballRadius * 2 + 4.0
        if (fixed) {
            el.rotated(v.x, v.y, tmp)
            val len = hypot(tmp[0], tmp[1])
            ox = tmp[0] / len
            oy = tmp[1] / len
            originX = el.x
            originY = el.y
            reach = maxOf(el.sx, el.sy) / 2 + level.ballRadius + 6.0
        }
        val base = kotlin.math.atan2(oy, ox)
        for (i in 0 until count) {
            val slot = balls.firstOrNull { !it.alive } ?: return
            val angle = base + (i - (count - 1) / 2.0) * SPREAD
            slot.body.place(originX + ox * reach, originY + oy * reach)
            slot.body.dirX = cos(angle)
            slot.body.dirY = sin(angle)
            slot.body.speed = min(speed, level.maxSpeed)
            arm(slot, bounces)
            slot.prevX = slot.body.x
            slot.prevY = slot.body.y
            listener?.onBallSpawned(slot.index, slot.body.x, slot.body.y)
        }
    }

    private fun exitReached(x: Double, y: Double): Boolean {
        val b = current
        b.body.x = x
        b.body.y = y
        b.alive = false
        b.absorbed = true
        b.body.speed = 0.0
        aliveCount--
        exitCount++
        winner = b
        if (b.index == 0) currentPath.add(Vec2(x, y))
        if (exitCount >= level.exitRequired) {
            finish(State.WON, null)
        } else {
            listener?.onExitPartial(exitCount, level.exitRequired, x, y)
        }
        return false
    }

    private fun kill(b: BallState, reason: FailReason) {
        if (!b.alive) return
        b.alive = false
        b.body.speed = 0.0
        aliveCount--
        lastLoss = reason
        if (b.index == 0) currentPath.add(Vec2(b.body.x, b.body.y))
        listener?.onBallLost(b.index, reason, b.body.x, b.body.y)
    }

    // ------------------------------------------------------------------ zones that trigger

    private fun checkDeadly(b: BallState): Boolean {
        for (i in deadly.indices) {
            val z = deadly[i]
            if (z.active && z.crosses(b.prevX, b.prevY, b.body.x, b.body.y)) {
                kill(b, FailReason.DEATH_ZONE)
                return true
            }
        }
        return false
    }

    private fun checkPortals(b: BallState) {
        if (b.lockPortal >= 0) return
        for (i in portals.indices) {
            val p = portals[i]
            if (!p.active || !p.crosses(b.prevX, b.prevY, b.body.x, b.body.y)) continue
            val ti = portalTarget[p.index]
            if (ti < 0 || !elements[ti].active) continue
            jump(b, p, elements[ti])
            return
        }
    }

    /** Portal: same speed (times the entered portal's boost, less its decay), turned by the difference between the two portals' rotations. */
    private fun jump(b: BallState, from: ElementRuntime, to: ElementRuntime) {
        val body = b.body
        val fromX = body.x
        val fromY = body.y
        var dx = body.dirX
        var dy = body.dirY
        val exit = to.data.vector
        if (exit.x != 0.0 || exit.y != 0.0) {
            to.rotated(exit.x, exit.y, tmp)
            val len = hypot(tmp[0], tmp[1])
            dx = tmp[0] / len
            dy = tmp[1] / len
        } else {
            val turn = Math.toRadians(to.rotation - from.rotation)
            val c = cos(turn)
            val s = sin(turn)
            val rx = dx * c - dy * s
            val ry = dx * s + dy * c
            dx = rx
            dy = ry
        }
        val speed = body.speed * from.data.boost * (1.0 - from.data.decay)
        body.x = to.x
        body.y = to.y
        b.prevX = body.x
        b.prevY = body.y
        setVelocity(b, dx * speed, dy * speed)
        b.lockPortal = to.index
        b.lockUntil = gameTime + tuning.portalCooldown
        if (b.index == 0) {
            currentPath.add(Vec2(fromX, fromY))
            currentPath.add(PATH_BREAK) // the picture lifts its pen here: the ball did not travel between the portals
            currentPath.add(Vec2(body.x, body.y))
        }
        listener?.onPortal(b.index, fromX, fromY, body.x, body.y)
    }

    private fun checkSwitches(b: BallState) {
        for (i in switches.indices) {
            val s = switches[i]
            if (!s.active || !armed[s.index] || !s.crosses(b.prevX, b.prevY, b.body.x, b.body.y)) continue
            armed[s.index] = false
            val ch = s.data.channel.coerceIn(0, CHANNELS - 1)
            channels[ch] = !channels[ch]
            for (k in switched.indices) if (switched[k].data.channel == ch) switched[k].setActive(!switched[k].active)
            listener?.onElement(s.index, ElementEvent.SWITCHED)
        }
    }

    /** A switch works again once no ball is standing in it. */
    private fun rearmSwitches() {
        for (i in switches.indices) {
            val s = switches[i]
            if (armed[s.index]) continue
            var occupied = false
            for (k in balls.indices) if (balls[k].alive && s.contains(balls[k].body.x, balls[k].body.y)) occupied = true
            if (!occupied) armed[s.index] = true
        }
    }

    private fun updateSlowMotion() {
        var slow = false
        for (i in slowZones.indices) {
            val z = slowZones[i]
            if (!z.active) continue
            for (k in balls.indices) if (balls[k].alive && z.contains(balls[k].body.x, balls[k].body.y)) slow = true
        }
        setTimeScale(if (slow) tuning.slowMoScale else 1.0)
    }

    private fun setTimeScale(scale: Double) {
        if (scale == timeScale) return
        val wasSlow = isSlowMotion
        timeScale = scale
        if (wasSlow != isSlowMotion) listener?.onSlowMo(isSlowMotion)
    }

    // ------------------------------------------------------------------ start, end, retry

    private fun startFlight() {
        state = State.MOVING
        gameTime = 0.0
        flightTime = 0.0
        currentPath.clear()
        currentPath.add(Vec2(balls[0].body.x, balls[0].body.y))
        for (i in elements.indices) elements[i].animate(0.0, 0.0)
        listener?.onLaunch()
    }

    /**
     * Puts everything back to the level's initial state — the ball, its bounces, every element (positions, hits,
     * what is broken, what is switched), the switches, portals' locks, extra balls, slow motion and the clock —
     * remembering the last shot's path.
     */
    fun restart() {
        // A shot cut short mid-flight still leaves its path so far as the reference.
        if (state == State.MOVING) currentPath.add(Vec2(balls[0].body.x, balls[0].body.y))
        if (currentPath.size > 1) previousPath = ArrayList(currentPath)
        reset()
    }

    private fun reset() {
        for (i in elements.indices) elements[i].reset()
        channels.fill(false)
        armed.fill(true)
        for (i in balls.indices) {
            val b = balls[i]
            b.alive = false
            b.absorbed = false
            b.left = 0
            b.total = 0
            b.alpha = 1.0
            b.inTouchZone = false
            b.lockPortal = -1
            b.lastSeg = -1
            b.lastContactStep = -100
            b.lastHitTime = -1e9
            b.body.speed = 0.0
        }
        val first = balls[0]
        first.body.place(level.ball.x, level.ball.y)
        first.body.dirX = 1.0
        first.body.dirY = 0.0
        first.prevX = first.body.x
        first.prevY = first.body.y
        aliveCount = 0
        arm(first, level.bounces)
        accumulator = 0.0
        state = State.AIMING
        failReason = null
        lastImpact = null
        exitCount = 0
        flightTime = 0.0
        gameTime = 0.0
        lastLoss = FailReason.STOPPED
        currentPath.clear()
        setTimeScale(1.0)
    }

    /** Makes [b] a live ball with [bounces] bounces. */
    private fun arm(b: BallState, bounces: Int) {
        b.alive = true
        b.absorbed = false
        b.left = bounces
        b.total = bounces
        b.alpha = 1.0
        b.inTouchZone = false
        b.lockPortal = -1
        b.lastSeg = -1
        b.lastContactStep = -100
        b.lastHitTime = -1e9
        aliveCount++
    }

    private fun finish(result: State, reason: FailReason?) {
        state = result
        failReason = reason
        for (i in balls.indices) {
            val b = balls[i]
            if (b.alive) b.body.speed = 0.0
            b.prevX = b.body.x
            b.prevY = b.body.y
        }
        accumulator = 0.0
        setTimeScale(1.0) // the win or the loss ends slow motion
        if (result == State.WON) {
            listener?.onWin(winner.body.x, winner.body.y)
        } else {
            val last = lastImpact
            listener?.onFail(reason!!, last?.x ?: balls[0].body.x, last?.y ?: balls[0].body.y)
        }
    }

    companion object {
        /** Simulation step: 120 Hz. */
        const val STEP = GameTuning.STEP

        private const val EPSILON = 1e-9

        /** Longest frame simulated at once (after a hitch the game slows down rather than skips). */
        const val MAX_FRAME = 0.1

        /** Safety limit: a ball still going after this many seconds is considered stopped. */
        const val MAX_FLIGHT_SECONDS = 90.0

        private const val CHANNELS = 16

        /** In [GameSession.path]: the line stops here and starts again at the next point (a portal jump). */
        val PATH_BREAK = Vec2(Double.NaN, Double.NaN)

        /** A wall touched again within this many steps of the last touch is still the same contact. */
        private const val CONTINUOUS_STEPS = 2
        private const val SPREAD = 0.6
        private val FORCE_KINDS = setOf(ElementKind.BOOSTER, ElementKind.ATTRACTIVE, ElementKind.REPULSIVE, ElementKind.SLOWER)
    }
}
