package com.pogoascent

import com.pogoascent.audio.AmbientDirector
import com.pogoascent.audio.AmbientGenerator
import com.pogoascent.audio.AudioBackend
import com.pogoascent.audio.AudioCatalog
import com.pogoascent.audio.AudioCategory
import com.pogoascent.audio.AudioManager
import com.pogoascent.audio.MusicGenerator
import com.pogoascent.audio.MusicStyles
import com.pogoascent.audio.SoundSynth
import com.pogoascent.feedback.FeedbackRouter
import com.pogoascent.haptics.HapticBackend
import com.pogoascent.haptics.HapticEventDef
import com.pogoascent.haptics.HapticManager
import com.pogoascent.particles.ParticleSystem
import com.pogoascent.physics.PhysicsConfig
import com.pogoascent.player.EventType
import com.pogoascent.player.GameEvent
import com.pogoascent.render.RenderBatches
import com.pogoascent.settings.AudioSettings
import java.util.Random
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FakeAudio : AudioBackend {
  class Play(val clip: String, val volume: Float, val rate: Float, val pan: Float, val loop: Boolean, val handle: Int)
  val registered = HashMap<String, ShortArray>()
  val plays = ArrayList<Play>()
  val stopped = ArrayList<Int>()
  val volumes = HashMap<Int, Float>()
  var stopAllCalls = 0
  private var next = 0
  override fun register(clipId: String, pcm: ShortArray, sampleRate: Int) { registered[clipId] = pcm }
  override fun play(clipId: String, volume: Float, rate: Float, pan: Float, loop: Boolean): Int {
    val h = next++; plays += Play(clipId, volume, rate, pan, loop, h); volumes[h] = volume; return h
  }
  override fun setVolume(handle: Int, volume: Float) { volumes[handle] = volume }
  override fun stop(handle: Int) { stopped += handle }
  override fun stopAll() { stopAllCalls++ }
  override fun release() {}
}

class FakeHaptics(override val supported: Boolean = true) : HapticBackend {
  class Pulse(val ms: Int, val amp: Int)
  val pulses = ArrayList<Pulse>()
  override fun vibrate(durationMs: Int, amplitude: Int) { pulses += Pulse(durationMs, amplitude) }
  override fun cancel() {}
}

class AudioTest {
  private val catalog = AudioCatalog.load()

  @Test fun everyRecipeRendersAudibleUnclippedSoundOfTheDeclaredLength() {
    for ((id, r) in catalog.recipes) {
      val pcm = SoundSynth.render(r)
      val expected = SoundSynth.SAMPLE_RATE * r.durationMs / 1000
      assertEquals("length of $id", expected.toDouble(), pcm.size.toDouble(), 2.0)
      val peak = SoundSynth.peak(pcm)
      assertTrue("$id is silent (peak $peak)", peak > 1500)
      assertTrue("$id clips (peak $peak)", peak < 32767)
      assertTrue("$id ends near silence", abs(pcm.last().toInt()) < 400)
    }
  }

  @Test fun synthesisIsDeterministic() {
    val r = catalog.recipes.getValue("collision_hard")
    assertTrue(SoundSynth.render(r).contentEquals(SoundSynth.render(r)))
  }

  @Test fun allTaskCategoriesAreUsedAndAllSurfaceSoundsExist() {
    val used = catalog.events.values.map { it.category }.toSet()
    for (c in AudioCategory.values()) if (c != AudioCategory.MUSIC && c != AudioCategory.SFX) assertTrue("category $c has no event", c in used)
    for (s in surfaces.all) assertTrue("surface '${s.id}' sound '${s.sound}' missing", s.sound in catalog.events)
  }

  private class Clock { var t = 0.0 }

  private fun manager(clock: Clock, backend: FakeAudio = FakeAudio(), voices: Int = 10) = AudioManager(catalog, backend, { clock.t }, Random(1), voices)

  @Test fun cooldownSuppressesRapidRepeats() {
    val clock = Clock(); val fake = FakeAudio(); val m = manager(clock, fake)
    assertTrue(m.trigger("goal_fanfare"))
    clock.t = 0.5
    assertFalse("inside the 2 s cooldown", m.trigger("goal_fanfare"))
    clock.t = 2.1
    assertTrue(m.trigger("goal_fanfare"))
    assertEquals(2, fake.plays.size)
    assertEquals(1, m.droppedByCooldown)
  }

  @Test fun volumeTreeMultipliesMasterAndCategory() {
    val clock = Clock(); val fake = FakeAudio(); val m = manager(clock, fake)
    m.settings = AudioSettings(master = 0.5, sfx = 0.5, music = 1.0, ambient = 1.0)
    m.trigger("ui_click", 1.0)
    val ev = catalog.events.getValue("ui_click")
    assertEquals((ev.volume * 0.5 * 0.5).toFloat(), fake.plays.last().volume, 1e-4f)
    m.settings = AudioSettings(master = 0.0)
    m.trigger("ui_back")
    assertEquals(0f, fake.plays.last().volume, 0f)
  }

  @Test fun randomVariationStaysInsideTheDeclaredRangeAndActuallyVaries() {
    val clock = Clock(); val fake = FakeAudio(); val m = manager(clock, fake)
    val ev = catalog.events.getValue("jump_launch")
    val rates = HashSet<Float>()
    repeat(60) { clock.t += 1.0; m.trigger("jump_launch"); rates += fake.plays.last().rate }
    assertTrue(rates.size > 20)
    for (r in rates) assertTrue(r in (ev.pitch * (1 - ev.pitchVariation)).toFloat()..(ev.pitch * (1 + ev.pitchVariation)).toFloat())
  }

  @Test fun spatialEventsPanTowardsTheirSideAndFadeWithDistance() {
    val clock = Clock(); val fake = FakeAudio(); val m = manager(clock, fake)
    m.listenerX = 0.0; m.listenerY = 0.0
    m.trigger("collision_hard", 1.0, x = 10.0, y = 0.0); clock.t += 1
    val right = fake.plays.last()
    m.trigger("collision_hard", 1.0, x = -10.0, y = 0.0); clock.t += 1
    val left = fake.plays.last()
    m.trigger("collision_hard", 1.0, x = 0.0, y = 60.0); clock.t += 1
    val far = fake.plays.last()
    assertTrue(right.pan > 0.5f && left.pan < -0.5f)
    assertTrue("far is quieter", far.volume < right.volume)
    // non-spatial events ignore position
    m.trigger("ui_click", 1.0, x = 50.0, y = 0.0)
    assertEquals(0f, fake.plays.last().pan, 0f)
  }

  @Test fun voiceStealingFavoursHigherPriority() {
    val clock = Clock(); val fake = FakeAudio(); val m = manager(clock, fake, voices = 2)
    assertTrue(m.trigger("env_chirp"))
    assertTrue(m.trigger("env_drip")) // same instant: both voices are busy
    assertFalse("equal priority cannot steal", m.trigger("env_gust"))
    assertTrue("high priority steals a voice", m.trigger("goal_fanfare"))
    assertEquals(1, fake.stopped.size)
    assertTrue(m.activeVoices <= 2)
  }

  @Test fun finishedVoicesFreeTheirSlots() {
    val clock = Clock(); val fake = FakeAudio(); val m = manager(clock, fake, voices = 1)
    assertTrue(m.trigger("ui_click"))
    clock.t = 5.0
    assertTrue(m.trigger("ui_back"))
  }

  @Test fun pausingSilencesEverything() {
    val clock = Clock(); val fake = FakeAudio(); val m = manager(clock, fake)
    m.trigger("ui_click"); clock.t += 1
    m.paused = true
    assertEquals(1, fake.stopAllCalls)
    assertFalse(m.trigger("ui_back"))
  }

  @Test fun unknownEventsAreIgnored() {
    val m = manager(Clock())
    assertFalse(m.trigger("does_not_exist"))
  }

  @Test fun everyMusicStyleRendersALoopThatStartsAndEndsAtSilence() {
    for (style in MusicStyles.all) {
      val pcm = MusicGenerator.render(style, bars = 4)
      assertTrue("${style.id} is silent", SoundSynth.peak(pcm) > 2000)
      assertTrue("${style.id} clips", SoundSynth.peak(pcm) < 32767)
      assertTrue("${style.id} loop start", abs(pcm.first().toInt()) < 50)
      assertTrue("${style.id} loop end", abs(pcm.last().toInt()) < 50)
      assertEquals(pcm.size.toDouble(), (4 * 4 * 60.0 / style.bpm * SoundSynth.SAMPLE_RATE), 2.0)
    }
  }

  @Test fun everyAmbienceRendersAndLoopsSeamlessly() {
    for (id in AmbientGenerator.ids) {
      val pcm = AmbientGenerator.render(id, seconds = 4)
      assertTrue("$id silent", SoundSynth.peak(pcm) > 300)
      assertTrue("$id loop edge", abs(pcm.first().toInt()) < 50 && abs(pcm.last().toInt()) < 50)
    }
  }

  @Test fun ambientDirectorFiresSparseOneShotsOnATimer() {
    val clock = Clock(); val fake = FakeAudio(); val m = manager(clock, fake)
    val d = AmbientDirector(m, Random(3)); d.setAmbience("wind_birds")
    var plays = 0
    repeat(600) { clock.t += 0.1; d.update(0.1, 0.0, 0.0); }
    plays = fake.plays.size
    assertTrue("60 s of birdsong should have some chirps ($plays) but not constant spam", plays in 5..40)
  }
}

class HapticTest {
  private val defs = HapticManager.loadDefs()
  private class Clock { var ms = 0L }

  @Test fun everyTaskEventHasAHapticDefinition() {
    val ids = defs.map { it.id }.toSet()
    for (e in listOf("jump", "landing", "hard_collision", "boost", "goal", "fall", "ui_click")) assertTrue("missing haptic '$e'", e in ids)
    for (s in surfaces.all) assertTrue("surface haptic '${s.haptic}'", s.haptic in ids)
  }

  @Test fun cooldownIntensityAndSwitchesAreRespected() {
    val clock = Clock(); val b = FakeHaptics(); val h = HapticManager(defs, b) { clock.ms }
    assertTrue(h.trigger("hard_collision"))
    assertFalse("inside cooldown", h.trigger("hard_collision"))
    clock.ms += 500
    assertTrue(h.trigger("hard_collision", 0.5))
    assertTrue("scaled pulse is weaker", b.pulses[1].amp < b.pulses[0].amp)
    h.intensityScale = 0.0
    clock.ms += 500
    assertFalse(h.trigger("hard_collision"))
    h.intensityScale = 1.0; h.enabled = false
    assertFalse(h.trigger("goal"))
    assertEquals(2, b.pulses.size)
  }

  @Test fun unsupportedDevicesAndUnknownEventsAreSafe() {
    val h = HapticManager(defs, FakeHaptics(supported = false)) { 0L }
    assertFalse(h.trigger("jump"))
    assertFalse(HapticManager(defs, FakeHaptics()) { 0L }.trigger("nope"))
  }

  @Test fun amplitudeAlwaysInsideTheAndroidRange() {
    val b = FakeHaptics(); val clock = Clock()
    val h = HapticManager(listOf(HapticEventDef("x", 10, 1.0, 0)), b) { clock.ms }
    repeat(5) { clock.ms += 10; h.trigger("x", 99.0) }
    for (p in b.pulses) assertTrue(p.amp in 1..255)
  }
}

class ParticleTest {
  private val presets = ParticleSystem.loadPresets()

  @Test fun emissionIsBoundedByCapacityAndParticlesExpire() {
    val ps = ParticleSystem(presets, capacity = 64)
    repeat(100) { ps.emit("goal", 0.0, 0.0, 0.0, 1.0, 1.5) }
    ps.update(0.001)
    assertTrue(ps.alive <= 64)
    repeat(300) { ps.update(0.02) }
    assertEquals(0, ps.alive)
  }

  @Test fun densityZeroAndDisabledEmitNothing() {
    val ps = ParticleSystem(presets)
    ps.density = 0.0; ps.emit("boost", 0.0, 0.0); ps.update(0.01)
    assertEquals(0, ps.alive)
    ps.density = 1.0; ps.enabled = false; ps.emit("boost", 0.0, 0.0); ps.update(0.01)
    assertEquals(0, ps.alive)
    ps.enabled = true; ps.emit("boost", 0.0, 0.0); ps.update(0.01)
    assertTrue(ps.alive > 0)
  }

  @Test fun everySurfaceParticleIdExistsAndRenderWritesInstances() {
    val ps = ParticleSystem(presets)
    for (s in surfaces.all) assertTrue("particle '${s.particle}'", ps.has(s.particle))
    ps.emit("dust", 1.0, 2.0, 0.0, 1.0, 1.0); ps.update(0.016)
    val batches = RenderBatches()
    ps.render(batches)
    assertEquals(ps.alive, batches.cube.count + batches.sphere.count)
  }

  @Test fun particlesMoveAwayFromTheEmitDirection() {
    val ps = ParticleSystem(presets, random = Random(5))
    ps.emit("bounce", 0.0, 0.0, 0.0, 1.0, 1.0)
    repeat(5) { ps.update(0.02) }
    val b = RenderBatches(); ps.render(b)
    var up = 0; var n = 0
    for (i in 0 until b.sphere.count) { n++; if (b.sphere.data[i * 20 + 13] > 0f) up++ }
    assertTrue("emitted mostly upward ($up/$n)", n > 0 && up >= n * 3 / 4)
  }

  @Test fun feedbackRouterMapsGameplayEventsToAudioHapticsAndParticles() {
    val fakeA = FakeAudio(); var t = 0.0
    val audio = AudioManager(AudioCatalog.load(), fakeA, { t }, Random(1))
    val fakeH = FakeHaptics(); var ms = 0L
    val hap = HapticManager(HapticManager.loadDefs(), fakeH) { ms }
    val ps = ParticleSystem(presets)
    val router = FeedbackRouter({ PhysicsConfig() }, audio, hap, ps)
    fun fire(e: GameEvent) { t += 5; ms += 5000; router.onEvent(e) }
    fire(GameEvent(EventType.LAUNCH, 0.0, 0.0, 17.0, 0.0, 1.0, surfaces["stone"]))
    assertTrue(fakeA.plays.last().clip == "launch")
    fire(GameEvent(EventType.LAND, 0.0, 0.0, 16.0, 0.0, 1.0, surfaces["ice"]))
    assertTrue("surface sound for ice", fakeA.plays.any { it.clip == "surf_ice" })
    fire(GameEvent(EventType.HARD_COLLISION, 0.0, 0.0, 20.0, 1.0, 0.0, surfaces["wall"]))
    fire(GameEvent(EventType.BOOST, 0.0, 0.0, 23.0))
    fire(GameEvent(EventType.GOAL, 0.0, 0.0))
    assertTrue(fakeA.plays.any { it.clip == "goal" })
    assertTrue(fakeH.pulses.size >= 4)
    ps.update(0.01)
    assertTrue(ps.alive > 0)
    // charge ticks fire once per 10 % step, not per frame
    val before = fakeA.plays.size
    repeat(30) { t += 0.001; router.onChargeLevel(0.35, true) }
    assertEquals(before + 1, fakeA.plays.size)
    router.onChargeLevel(0.0, false)
  }
}
