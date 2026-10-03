package com.pogoascent

import com.pogoascent.core.FixedStepper
import com.pogoascent.physics.Confidence
import com.pogoascent.physics.PhysicsConfig
import com.pogoascent.physics.PhysicsParams
import com.pogoascent.tools.PhysicsDocs
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class PhysicsConfigTest {
  @Test fun defaultsAreValid() {
    assertEquals(emptyList<String>(), PhysicsConfig().validate())
  }

  @Test fun bundledJsonEqualsCodeDefaults() {
    // The JSON is what designers edit; it must be generated from (and equal to) the defaults.
    assertEquals(PhysicsConfig(), PhysicsConfig.load())
  }

  @Test fun partialJsonFallsBackToDefaults() {
    val cfg = PhysicsConfig.fromJson("""{"gravity": 30.0}""")
    assertEquals(30.0, cfg.gravity, 0.0)
    assertEquals(PhysicsConfig().jumpPower, cfg.jumpPower, 0.0)
  }

  @Test fun invalidJsonIsRejectedWithAClearMessage() {
    try {
      PhysicsConfig.fromJson("""{"gravity": -5.0}""")
      fail("expected rejection")
    } catch (e: IllegalArgumentException) {
      assertTrue(e.message!!.contains("gravity"))
    }
  }

  @Test fun everyConfigFieldHasExactlyOneRegistryEntry() {
    val fields = PhysicsConfig.serializer().descriptor.let { d -> (0 until d.elementsCount).map { d.getElementName(it) } }
    val keys = PhysicsParams.all.map { it.key }
    assertEquals("duplicate registry keys", keys.size, keys.toSet().size)
    assertEquals(fields.toSet(), keys.toSet())
  }

  @Test fun registryReadsTheRightField() {
    val cfg = PhysicsConfig(gravity = 31.5, jumpPower = 20.0, boostThreshold = 300.0)
    assertEquals(31.5, PhysicsParams.byKey.getValue("gravity").read(cfg), 0.0)
    assertEquals(20.0, PhysicsParams.byKey.getValue("jumpPower").read(cfg), 0.0)
    assertEquals(300.0, PhysicsParams.byKey.getValue("boostThreshold").read(cfg), 0.0)
  }

  @Test fun everyRequiredTaskCategoryIsCovered() {
    val have = PhysicsParams.all.map { it.category }.toSet()
    val missing = PhysicsParams.requiredCategories.filterNot { it in have }
    assertEquals("categories without a parameter", emptyList<String>(), missing)
  }

  @Test fun noValueIsEverGradedAboveD_becauseNoAnalysisDataExists() {
    // Guard for the "never promote C/D to fact" rule: if someone upgrades a grade they must also supply a real source.
    for (spec in PhysicsParams.all) {
      if (spec.confidence != Confidence.D) {
        assertFalse("${spec.key}: graded ${spec.confidence} but source is the placeholder", spec.source.contains("no analysis data"))
      }
    }
  }

  @Test fun checkedInPhysicsMasterMatchesTheCode() {
    val file = File("../PHYSICS_MASTER.md")
    assertTrue("PHYSICS_MASTER.md missing – run ./gradlew :core:generateDocs", file.exists())
    assertEquals(
      "PHYSICS_MASTER.md is stale – run ./gradlew :core:generateDocs",
      PhysicsDocs.renderMarkdown(PhysicsConfig.load()),
      file.readText(),
    )
  }

  @Test fun launchSpeedIsMonotonicAndBounded() {
    val c = PhysicsConfig()
    assertEquals(c.jumpPowerMin, c.launchSpeedForCharge(0.0), 1e-12)
    assertEquals(c.jumpPower, c.launchSpeedForCharge(1.0), 1e-12)
    assertEquals(c.jumpPower, c.launchSpeedForCharge(5.0), 1e-12)
    var prev = -1.0
    for (i in 0..20) {
      val v = c.launchSpeedForCharge(i / 20.0)
      assertTrue(v >= prev)
      prev = v
    }
  }
}

class FixedStepperTest {
  @Test fun runsTheRightNumberOfTicksIndependentOfFrameRate() {
    for (fps in listOf(30.0, 60.0, 144.0, 17.0)) {
      val s = FixedStepper(120.0, 0.1, 8)
      var ticks = 0
      repeat((fps * 10).toInt()) { ticks += s.advance(1.0 / fps) }
      assertEquals("fps=$fps", 1200.0, ticks.toDouble(), 2.0)
    }
  }

  @Test fun longFramesAreClampedAndBacklogDropped() {
    val s = FixedStepper(120.0, 0.1, 8)
    assertEquals(8, s.advance(5.0))
    assertTrue(s.alpha in 0.0..1.0)
    assertEquals(0, s.advance(0.0))
    assertEquals(0, s.advance(Double.NaN))
    assertEquals(0, s.advance(-1.0))
  }

  @Test fun alphaIsTheFractionOfATick() {
    val s = FixedStepper(100.0, 0.1, 8)
    assertEquals(1, s.advance(0.015))
    assertEquals(0.5, s.alpha, 1e-9)
  }
}
