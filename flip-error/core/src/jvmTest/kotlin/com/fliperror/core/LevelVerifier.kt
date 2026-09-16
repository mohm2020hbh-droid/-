package com.fliperror.core

/**
 * Offline proof that a level can actually be beaten, and by how much margin.
 *
 * x advances at a constant speed, so the frame index fully determines x. That
 * turns "is this level possible" into a DAG over the frames where the runner is
 * on the ground: at each one it may WAIT, JUMP, or JUMP AND BOOST - and for the
 * boost, at any legal moment of the flight.
 *
 * Ground frames come in stretches (a landing, then running until the next jump).
 * The slack a human actually gets at a stretch is the longest contiguous run of
 * frames inside it that a jump can still be won from. Consecutive jumps are
 * coupled - taking one late shortens the next stretch - so the level's real
 * fairness number is the maximin: the best reachable value of the tightest
 * window on the path.
 */
class LevelVerifier(private val level: Level, private val allowBoost: Boolean = true) {

    private companion object {
        /** No boost is legal before this air frame, or after that one. */
        val FIRST_BOOST = Math.ceil(Tuning.DOUBLE_LOCKOUT / Tuning.FIXED_DT).toInt()
        val LAST_BOOST = (Tuning.DOUBLE_WINDOW_END / Tuning.FIXED_DT).toInt()
        /**
         * Every legal boost frame is searched. Sampling looked affordable, but a
         * sampled timing lands on a frame the graph has never visited, and an
         * unvisited node defaults to unsolvable - which silently reported every
         * boost in the game as frame perfect. The window has to come out of the
         * explored graph, so the graph has to contain every option.
         */
        const val BOOST_STEP = 1
        const val NO_BOOST = -1
    }

    private class JumpEdge(val boostAfter: Int, val to: Node?, val wins: Boolean) {
        val taps get() = if (boostAfter == NO_BOOST) 1 else 2
        val boosts get() = if (boostAfter == NO_BOOST) 0 else 1
        val viable get() = wins || to?.solvable == true
    }

    /**
     * Cost is lexicographic: fewest taps first, then fewest boosts. Two lines
     * that ask for the same number of taps are not equally easy - a boost has a
     * timing window of its own, so the line that does not need one is the line a
     * player will actually find.
     */
    private fun cost(taps: Int, boosts: Int) = taps * 1000 + boosts

    private class Node(val frame: Int, val snap: Game.Snapshot) {
        var waitTo: Node? = null
        var waitWins = false
        val jumps = ArrayList<JumpEdge>()
        var solvable = false
        val jumpViable get() = jumps.any { it.viable }
    }

    private val sim = Game(level)
    private val nodes = LinkedHashMap<Long, Node>()

    private fun key(frame: Int, y: Double) = frame.toLong() * 100_003L + Math.round(y * 1000.0)

    private fun node(frame: Int): Node =
        nodes.getOrPut(key(frame, sim.y)) { Node(frame, sim.snapshot()) }

    /** Run one frame of waiting from [n]. */
    private fun advanceWait(n: Node): Pair<Node?, Boolean> {
        sim.restore(n.snap)
        var frame = n.frame
        sim.stepFixed(); frame++
        if (sim.state == GameState.COMPLETE) return null to true
        if (sim.state == GameState.DEAD) return null to false
        if (sim.grounded) return node(frame) to false
        // Walked off an edge: fly it out so the fall is a real option too.
        var guard = 0
        while (sim.state == GameState.RUNNING && !sim.grounded && guard++ < 4000) { sim.stepFixed(); frame++ }
        if (sim.state == GameState.COMPLETE) return null to true
        if (sim.state == GameState.DEAD || !sim.grounded) return null to false
        return node(frame) to false
    }

    /** Jump from [n], optionally boosting [boostAfter] air frames in, and land. */
    private fun advanceJump(n: Node, boostAfter: Int): Triple<Node?, Boolean, Boolean> {
        sim.restore(n.snap)
        sim.onTap()
        var frame = n.frame
        var air = 0
        var boosted = false
        sim.stepFixed(); frame++; air++
        if (sim.state == GameState.COMPLETE) return Triple(null, true, boosted)
        if (sim.state == GameState.DEAD) return Triple(null, false, boosted)
        var guard = 0
        while (sim.state == GameState.RUNNING && !sim.grounded && guard++ < 4000) {
            if (!boosted && boostAfter != NO_BOOST && air >= boostAfter) {
                if (!sim.canDoubleJump) return Triple(null, false, false)   // window already shut
                sim.onTap()
                boosted = true
            }
            sim.stepFixed(); frame++; air++
        }
        if (sim.state == GameState.COMPLETE) return Triple(null, true, boosted)
        if (sim.state == GameState.DEAD || !sim.grounded) return Triple(null, false, boosted)
        if (boostAfter != NO_BOOST && !boosted) return Triple(null, false, false)  // never got to boost
        return Triple(node(frame), false, boosted)
    }

    data class Jump(val x: Double, val window: Double, val percent: Double,
                    val boosted: Boolean, val boostWindow: Double,
                    /** Where the runner is when the second tap lands. Frame-rate free,
                     *  so a 60Hz shell can reproduce a line solved at 240Hz. */
                    val boostX: Double)

    data class Report(
        val solvable: Boolean,
        val jumps: List<Jump>,
        val seconds: Double,
        val nodesExplored: Int,
        /** Furthest x any line reached. On an unsolvable level this is the wall. */
        val furthestX: Double = 0.0,
    ) {
        val taps get() = jumps.fold(0) { acc, j -> acc + if (j.boosted) 2 else 1 }
        val boosts get() = jumps.count { it.boosted }
        val minWindow get() = jumps.minOfOrNull { it.window } ?: 0.0
        /** Longest stretch of level time that asks for no input at all. */
        fun longestRest(levelSeconds: Double): Double {
            if (jumps.isEmpty()) return levelSeconds
            val times = jumps.map { it.x / Tuning.RUN_SPEED }
            var best = times.first()
            for (i in 1 until times.size) best = maxOf(best, times[i] - times[i - 1])
            return maxOf(best, levelSeconds - times.last())
        }
    }

    // --- stretch analysis -------------------------------------------------
    //
    // Ground frames come in stretches: a landing, then running until waiting
    // one more frame would be fatal. Exactly one jump is owed per stretch.
    // The line a real player follows is the one that needs the fewest taps, so
    // the DP minimises taps first and only then maximises the tightest window.

    private class Stretch(val head: Node) {
        val frames = ArrayList<Node>()
        var winsByWaiting = false
        var taps = Int.MAX_VALUE
        var boosts = 0
        var cost = Int.MAX_VALUE
        var minWindow = 0.0
        var window = 0.0
        var chosen: Node? = null
        var chosenEdge: JumpEdge? = null
        var next: Node? = null
    }

    private val stretches = LinkedHashMap<Node, Stretch>()

    private fun buildStretch(head: Node): Stretch {
        val s = Stretch(head)
        var cur: Node? = head
        while (cur != null) {
            s.frames += cur
            if (cur.waitWins) { s.winsByWaiting = true; break }
            cur = cur.waitTo
        }
        return s
    }

    /** What taking [e] costs for the rest of the run, or null if it leads nowhere. */
    private fun edgeCost(e: JumpEdge): Int? {
        if (!e.viable) return null
        var downTaps = 0
        var downBoosts = 0
        if (!e.wins) {
            val d = stretches[e.to] ?: return null
            if (d.taps == Int.MAX_VALUE) return null
            downTaps = d.taps; downBoosts = d.boosts
        }
        return cost(e.taps + downTaps, e.boosts + downBoosts)
    }

    /** Cheapest way out of [f], as (cost, edge), or null if nothing survives. */
    private fun bestFrom(f: Node): Pair<Int, JumpEdge>? {
        var bestCost = Int.MAX_VALUE
        var bestEdge: JumpEdge? = null
        for (e in f.jumps) {
            val c = edgeCost(e) ?: continue
            if (c < bestCost) { bestCost = c; bestEdge = e }
        }
        return bestEdge?.let { bestCost to it }
    }

    fun analyse(): Report {
        sim.reset()
        val start = Node(0, sim.snapshot())
        nodes[key(0, sim.y)] = start

        // With allowBoost off the search is a single-jump player, which is how a
        // level proves it genuinely REQUIRES the second jump rather than merely
        // rewarding it: that run must come back unsolvable.
        val boostOptions = ArrayList<Int>()
        boostOptions += NO_BOOST
        if (allowBoost) {
            var b = FIRST_BOOST
            while (b <= LAST_BOOST) { boostOptions += b; b += BOOST_STEP }
        }

        val stack = ArrayDeque<Node>()
        stack.addLast(start)
        val seen = HashSet<Node>()
        seen += start
        while (stack.isNotEmpty()) {
            val n = stack.removeLast()
            val (w, wWin) = advanceWait(n); n.waitTo = w; n.waitWins = wWin
            w?.let { if (seen.add(it)) stack.addLast(it) }
            for (opt in boostOptions) {
                val (to, wins, boosted) = advanceJump(n, opt)
                if (to == null && !wins) continue
                // Only keep an edge that did what it was asked to do.
                if (opt != NO_BOOST && !boosted) continue
                n.jumps += JumpEdge(opt, to, wins)
                to?.let { if (seen.add(it)) stack.addLast(it) }
            }
        }

        for (n in nodes.values.sortedByDescending { it.frame }) {
            n.solvable = n.waitWins || n.waitTo?.solvable == true ||
                n.jumps.any { it.wins || it.to?.solvable == true }
        }
        val furthest = nodes.values.maxOf { it.frame } * Tuning.FIXED_DT * Tuning.RUN_SPEED
        if (!start.solvable) return Report(false, emptyList(), 0.0, nodes.size, furthest)

        val heads = LinkedHashSet<Node>()
        heads += start
        nodes.values.forEach { n -> n.jumps.forEach { e -> e.to?.let { heads += it } } }
        heads.forEach { stretches[it] = buildStretch(it) }

        // Solve the DAG from the back: frames only ever increase along an edge.
        for (head in heads.sortedByDescending { it.frame }) {
            val s = stretches[head]!!
            if (s.winsByWaiting) { s.taps = 0; s.boosts = 0; s.cost = 0; s.minWindow = Double.MAX_VALUE; continue }

            var bestCost = Int.MAX_VALUE
            for (f in s.frames) bestFrom(f)?.let { if (it.first < bestCost) bestCost = it.first }
            if (bestCost == Int.MAX_VALUE) continue      // dead branch

            // The window is the run of take-off frames that keep the optimal line.
            var run = 0; var best = 0; var bestEnd = -1
            for ((i, f) in s.frames.withIndex()) {
                val onLine = bestFrom(f)?.first == bestCost
                if (onLine) { run++; if (run > best) { best = run; bestEnd = i } } else run = 0
            }
            s.cost = bestCost
            s.taps = bestCost / 1000
            s.boosts = bestCost % 1000
            s.window = best * Tuning.FIXED_DT

            // Among the take-offs that keep the optimal line, prefer the one whose
            // whole remaining run is most forgiving - and the boost this jump may
            // need counts as part of that. Choosing on downstream slack alone
            // picks take-offs where the second tap is frame perfect, which is a
            // line no player can fly.
            var restBest = 0.0
            var pick: Node? = null
            var pickEdge: JumpEdge? = null
            for (i in (bestEnd - best + 1)..bestEnd) {
                val f = s.frames[i]
                val (_, e) = bestFrom(f) ?: continue
                val boost = if (e.boostAfter == NO_BOOST) Double.MAX_VALUE else boostWindow(f, e)
                val rest = if (e.wins) Double.MAX_VALUE else stretches[e.to]!!.minWindow
                val score = minOf(boost, rest)
                if (pick == null || score > restBest) { restBest = score; pick = f; pickEdge = e }
            }
            s.chosen = pick
            s.chosenEdge = pickEdge
            s.next = pickEdge?.to
            s.minWindow = minOf(s.window, restBest)
        }

        // Walk the chosen line.
        val jumps = ArrayList<Jump>()
        var head: Node? = start
        val guard = HashSet<Node>()
        while (head != null && guard.add(head)) {
            val s = stretches[head]!!
            val take = s.chosen ?: break
            val edge = s.chosenEdge!!
            val x = take.frame * Tuning.FIXED_DT * Tuning.RUN_SPEED
            jumps += Jump(
                x = x,
                window = s.window,
                percent = 100.0 * x / level.finishX,
                boosted = edge.boostAfter != NO_BOOST,
                boostWindow = if (edge.boostAfter == NO_BOOST) 0.0 else boostWindow(take, edge),
                boostX = if (edge.boostAfter == NO_BOOST) 0.0
                         else x + edge.boostAfter * Tuning.FIXED_DT * Tuning.RUN_SPEED,
            )
            head = s.next
        }
        return Report(true, jumps, level.durationSeconds, nodes.size, furthest)
    }

    /**
     * How long the second tap could have been left for and still finished the
     * level. Note this is a looser test than the take-off window above, and
     * deliberately: a take-off that leaves the optimal line usually costs taps
     * the player cannot afford, but a boost landing a frame either side only
     * changes where they land, and finishing from there is all that matters.
     * Measuring it against the optimal line instead reports every boost in the
     * game as a one-frame trick, which a brute-force sweep says it is not.
     */
    private fun boostWindow(take: Node, edge: JumpEdge): Double {
        val ok = HashSet<Int>()
        for (e in take.jumps) {
            if (e.boostAfter == NO_BOOST) continue
            if (e.viable) ok += e.boostAfter
        }
        var lo = edge.boostAfter
        while (ok.contains(lo - 1)) lo--
        var hi = edge.boostAfter
        while (ok.contains(hi + 1)) hi++
        return (hi - lo + 1) * Tuning.FIXED_DT
    }
}
