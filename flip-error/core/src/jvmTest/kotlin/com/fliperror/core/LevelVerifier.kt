package com.fliperror.core

/**
 * Offline proof that a level can actually be beaten, and by how much margin.
 *
 * x advances at a constant speed, so the frame index fully determines x. That
 * turns "is this level possible" into a DAG over the frames where the runner is
 * on the ground: at each one it may WAIT one frame or JUMP.
 *
 * Ground frames come in stretches (a landing, then running until the next jump).
 * The slack a human actually gets at a stretch is the longest contiguous run of
 * frames inside it that a jump can still be won from. Consecutive jumps are
 * coupled - taking one late shortens the next stretch - so the level's real
 * fairness number is the maximin: the best reachable value of the tightest
 * window on the path.
 */
class LevelVerifier(private val level: Level) {

    private enum class Edge { WAIT, JUMP }

    private class Node(val frame: Int, val snap: Game.Snapshot) {
        var waitTo: Node? = null
        var jumpTo: Node? = null
        var waitWins = false
        var jumpWins = false
        var solvable = false
        val jumpViable get() = jumpWins || jumpTo?.solvable == true
    }

    private val sim = Game(level)
    private val nodes = LinkedHashMap<Long, Node>()

    private fun key(frame: Int, y: Double) = frame.toLong() * 100_003L + Math.round(y * 1000.0)

    private fun node(frame: Int): Node =
        nodes.getOrPut(key(frame, sim.y)) { Node(frame, sim.snapshot()) }

    /** Runs [edge] from [n] until the runner is on the ground again, dies, or wins. */
    private fun advance(n: Node, edge: Edge): Pair<Node?, Boolean> {
        sim.restore(n.snap)
        if (edge == Edge.JUMP) sim.onTap()
        var frame = n.frame
        sim.stepFixed(); frame++
        if (sim.state == GameState.COMPLETE) return null to true
        if (sim.state == GameState.DEAD) return null to false
        if (edge == Edge.WAIT && sim.grounded) return node(frame) to false
        var guard = 0
        while (sim.state == GameState.RUNNING && !sim.grounded && guard++ < 4000) {
            sim.stepFixed(); frame++
        }
        if (sim.state == GameState.COMPLETE) return null to true
        if (sim.state == GameState.DEAD || !sim.grounded) return null to false
        return node(frame) to false
    }

    data class Jump(val x: Double, val window: Double, val percent: Double)

    data class Report(
        val solvable: Boolean,
        val jumps: List<Jump>,
        val seconds: Double,
        val nodesExplored: Int,
    ) {
        val taps get() = jumps.size
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
        var minWindow = 0.0
        var window = 0.0
        var chosen: Node? = null
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

    fun analyse(): Report {
        sim.reset()
        val start = Node(0, sim.snapshot())
        nodes[key(0, sim.y)] = start

        val stack = ArrayDeque<Node>()
        stack.addLast(start)
        val seen = HashSet<Node>()
        seen += start
        while (stack.isNotEmpty()) {
            val n = stack.removeLast()
            val (w, wWin) = advance(n, Edge.WAIT); n.waitTo = w; n.waitWins = wWin
            val (j, jWin) = advance(n, Edge.JUMP); n.jumpTo = j; n.jumpWins = jWin
            listOfNotNull(w, j).forEach { if (seen.add(it)) stack.addLast(it) }
        }

        for (n in nodes.values.sortedByDescending { it.frame }) {
            n.solvable = n.waitWins || n.jumpWins ||
                n.waitTo?.solvable == true || n.jumpTo?.solvable == true
        }
        if (!start.solvable) return Report(false, emptyList(), 0.0, nodes.size)

        // Every landing is the head of a stretch; so is the start of the run.
        val heads = LinkedHashSet<Node>()
        heads += start
        nodes.values.forEach { it.jumpTo?.let { t -> heads += t } }
        heads.forEach { stretches[it] = buildStretch(it) }

        // Solve the DAG from the back: frames only ever increase along an edge.
        for (head in heads.sortedByDescending { it.frame }) {
            val s = stretches[head]!!
            if (s.winsByWaiting) { s.taps = 0; s.minWindow = Double.MAX_VALUE; continue }

            var bestTaps = Int.MAX_VALUE
            for (f in s.frames) {
                if (!f.jumpViable) continue
                val t = if (f.jumpWins) 1 else 1 + (stretches[f.jumpTo!!]?.taps ?: Int.MAX_VALUE)
                if (t in 1 until bestTaps) bestTaps = t
            }
            if (bestTaps == Int.MAX_VALUE) continue      // dead branch

            // The window is the run of take-off frames that keep the optimal line.
            var run = 0; var best = 0; var bestEnd = -1
            for ((i, f) in s.frames.withIndex()) {
                val onLine = f.jumpViable &&
                    (if (f.jumpWins) 1 else 1 + (stretches[f.jumpTo!!]?.taps ?: Int.MAX_VALUE)) == bestTaps
                if (onLine) { run++; if (run > best) { best = run; bestEnd = i } } else run = 0
            }
            s.taps = bestTaps
            s.window = best * Tuning.FIXED_DT

            var restBest = 0.0
            var pick: Node? = null
            for (i in (bestEnd - best + 1)..bestEnd) {
                val f = s.frames[i]
                val rest = if (f.jumpWins) Double.MAX_VALUE else stretches[f.jumpTo!!]!!.minWindow
                if (pick == null || rest > restBest) { restBest = rest; pick = f }
            }
            s.chosen = pick
            s.next = pick?.jumpTo
            s.minWindow = minOf(s.window, restBest)
        }

        // Walk the chosen line.
        val jumps = ArrayList<Jump>()
        var head: Node? = start
        var lastFrame = 0
        val guard = HashSet<Node>()
        while (head != null && guard.add(head)) {
            val s = stretches[head]!!
            lastFrame = s.frames.last().frame
            val take = s.chosen ?: break
            lastFrame = take.frame
            jumps += Jump(
                x = take.frame * Tuning.FIXED_DT * Tuning.RUN_SPEED,
                window = s.window,
                percent = 100.0 * (take.frame * Tuning.FIXED_DT * Tuning.RUN_SPEED) / level.finishX,
            )
            head = s.next
        }
        return Report(true, jumps, level.durationSeconds, nodes.size)
    }
}
