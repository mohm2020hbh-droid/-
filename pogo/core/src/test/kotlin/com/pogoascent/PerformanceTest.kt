package com.pogoascent

import com.pogoascent.app.AppCore
import com.pogoascent.levels.LevelLoader
import com.pogoascent.save.MemorySaveStorage
import java.lang.management.ManagementFactory
import org.junit.Assert.assertTrue
import org.junit.Test

/** Guards the performance rules of the task: fixed physics step, no per-frame garbage, cheap frames, pooled particles. */
class PerformanceTest {
  private fun allocatedBytes(): Long =
    (ManagementFactory.getThreadMXBean() as com.sun.management.ThreadMXBean).getThreadAllocatedBytes(Thread.currentThread().threadId())

  @Test fun aStandingRiderAllocatesNothingPerPhysicsTick() {
    val level = LevelLoader.load("level_03", surfaces)
    val r = Rig(level.world)
    r.tick(3000)
    val before = allocatedBytes()
    r.tick(20_000)
    val perTick = (allocatedBytes() - before) / 20_000.0
    println("physics idle: %.2f bytes/tick".format(perTick))
    assertTrue("idle tick allocates $perTick B", perTick < 1.0)
  }

  @Test fun anActiveRiderAllocatesOnlyForRareEvents() {
    val level = LevelLoader.load("level_03", surfaces)
    val r = Rig(level.world)
    fun pattern(i: Int) { r.input.lean = if ((i / 240) % 2 == 0) 0.4 else -0.4; r.input.jumpHeld = (i / 100) % 2 == 0 }
    for (i in 0 until 6000) { pattern(i); r.tick() }
    val before = allocatedBytes()
    for (i in 6000 until 26_000) { pattern(i); r.tick() }
    val perTick = (allocatedBytes() - before) / 20_000.0
    println("physics active: %.2f bytes/tick".format(perTick))
    assertTrue("active tick allocates $perTick B (events only)", perTick < 60.0)
  }

  @Test fun aRenderedFrameAllocatesLittleAndFitsTheFrameBudget() {
    var t = 0.0
    val core = AppCore(MemorySaveStorage(), FakeAudio(), FakeHaptics(), { t }, { (t * 1000).toLong() }, { 1L })
    core.controller.startLevel("level_06")
    core.controller.input.set(0.4, true)
    fun frame(i: Int) {
      t += 1.0 / 60
      if (i % 90 == 0) core.controller.input.set(if ((i / 90) % 2 == 0) 0.4 else -0.4, (i / 90) % 3 != 0)
      core.controller.update(1.0 / 60, 16f / 9f)
    }
    for (i in 0 until 600) frame(i) // warm-up (JIT, caches)
    val before = allocatedBytes()
    val t0 = System.nanoTime()
    val n = 3000
    for (i in 600 until 600 + n) frame(i)
    val ms = (System.nanoTime() - t0) / 1e6 / n
    val kb = (allocatedBytes() - before) / 1024.0 / n
    println("frame: %.3f ms/frame, %.2f KB/frame".format(ms, kb))
    assertTrue("frame ${ms} ms exceeds 4 ms desktop budget (60 fps = 16.7 ms on a phone)", ms < 4.0)
    assertTrue("frame allocates $kb KB", kb < 24.0)
  }

  @Test fun theParticlePoolNeverGrows() {
    var t = 0.0
    val core = AppCore(MemorySaveStorage(), null, null, { t }, { 0L }, { 1L })
    core.controller.startLevel("level_01")
    val cap = core.controller.particles.capacity
    for (i in 0 until 2000) {
      t += 1.0 / 60
      core.controller.input.set(0.5, i % 70 < 40)
      core.controller.update(1.0 / 60, 16f / 9f)
      assertTrue(core.controller.particles.alive <= cap)
      assertTrue(core.controller.frame.dynamic.totalInstances <= 640 + 256 + 640)
    }
  }
}
