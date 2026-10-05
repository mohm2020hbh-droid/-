import { describe, expect, it } from 'vitest';
import { readFileSync } from 'node:fs';
import { join } from 'node:path';
import { AUDIO_CONFIG, CONFIG_TAGS, configLeaves, volumeToGain } from '../../../src/audio/system/audioConfig';
import { AUDIO_EVENTS, recipeIds } from '../../../src/audio/system/AudioEvent';
import { AUDIO_EVENT } from '../../../src/audio/system/types';
import { RECIPES } from '../../../src/audio/system/synth/recipes';

const get = (path: string): number => path.split('.').reduce<unknown>((o, k) => (o as Record<string, unknown>)[k], AUDIO_CONFIG) as number;

describe('audioConfig: every number has an origin tag (POGOSTUCK_AUDIO_SYSTEM_SPEC §1)', () => {
  it('has a tag for every numeric leaf and no tag without a number', () => {
    const leaves = configLeaves().sort();
    expect(Object.keys(CONFIG_TAGS).sort()).toEqual(leaves);
  });
  it('uses only the three allowed tags', () => {
    for (const [k, t] of Object.entries(CONFIG_TAGS)) expect(['ORIGINAL', 'ORIGINAL-INSPIRED', 'DESIGN'], k).toContain(t);
  });
});

describe('ORIGINAL values are exactly the ones extracted from the original script (POGOSTUCK_AUDIO_ANALYSIS.md §3)', () => {
  const expected: Record<string, number> = {
    // pogoLoad2 call @3da0ba: volume 50, pitch 0.9 + random(0.1)
    'charge.volume': 50, 'charge.pitchMin': 0.9, 'charge.pitchMax': 1.0,
    // pogoLaunch2 call @3e1a7e: volume 50, pitch 0.9 + random(0.2)
    'launch.volume': 50, 'launch.pitchMin': 0.9, 'launch.pitchMax': 1.1,
    // pogoLaunch3 via effectPlayerPowerJumpInit @20411d: volume 90 + random(10), pitch 0.9 + random(0.2)
    'launch.powerVolumeMin': 90, 'launch.powerVolumeMax': 100, 'launch.powerPitchMin': 0.9, 'launch.powerPitchMax': 1.1,
    // bounce1–4 @3de957…: volume 40 + random(10), range 2
    'collision.volumeMin': 40, 'collision.volumeMax': 50, 'collision.range': 2,
    // break1 volume 90 @3269f1, break2 volume 100 @367c60
    'breakage.volume1': 90, 'breakage.volume2': 100,
    // ice slide modulation @3cc734…3cc83a, range 1.5 @3cc2e0, stop fade −50 ≈ 0.25 s
    'ice.speedBase': 0.675, 'ice.speedPerSlide': 0.01, 'ice.speedMax': 1, 'ice.volBase': 20, 'ice.volPerSlide': 1.5, 'ice.volMax': 70, 'ice.range': 1.5, 'ice.stopFadeSec': 0.25,
    // pogoTime @577146: volume 100
    'time.volume': 100,
    // kuSoundUpdateFrame: gain = clamp(range·1.25 − dist/W, 0, 1), pan = clamp(Δx/W · 0.2, −1, 1)
    'spatial.rangeScale': 1.25, 'spatial.panScale': 0.2,
  };
  for (const [path, v] of Object.entries(expected)) {
    it(`${path} = ${v}`, () => {
      expect(CONFIG_TAGS[path]).toBe('ORIGINAL');
      expect(get(path)).toBeCloseTo(v, 9);
    });
  }
  it('every ORIGINAL-tagged number is covered by this table', () => {
    const originals = Object.entries(CONFIG_TAGS).filter(([, t]) => t === 'ORIGINAL').map(([k]) => k).sort();
    expect(Object.keys(expected).sort()).toEqual(originals);
  });
  it('volumes are read linearly: 50 → 0.5, 100 → 1', () => {
    expect(volumeToGain(50)).toBe(0.5);
    expect(volumeToGain(100)).toBe(1);
    expect(volumeToGain(-5)).toBe(0);
  });
});

describe('catalogue is consistent', () => {
  it('has an entry for every audio event id, keyed by its own id', () => {
    for (const id of Object.values(AUDIO_EVENT)) expect(AUDIO_EVENTS[id].id).toBe(id);
  });
  it('every synth variant has a recipe, every recipe is used', () => {
    const ids = recipeIds();
    for (const r of ids) expect(RECIPES[r], r).toBeTruthy();
    for (const r of Object.keys(RECIPES)) expect(ids, r).toContain(r);
  });
  it('variant ids are unique across the catalogue', () => {
    const seen = new Set<string>();
    for (const def of Object.values(AUDIO_EVENTS)) for (const v of def.variants) { expect(seen.has(v.id), v.id).toBe(false); seen.add(v.id); }
  });
  it('the six events of the brief sit on the requested buses', () => {
    expect(AUDIO_EVENTS.POGO_CHARGE.bus).toBe('PLAYER');
    expect(AUDIO_EVENTS.POGO_LAUNCH.bus).toBe('PLAYER');
    expect(AUDIO_EVENTS.POGO_COLLISION.bus).toBe('PLAYER');
    expect(AUDIO_EVENTS.POGO_BREAK.bus).toBe('SURFACE');
    expect(AUDIO_EVENTS.ICE_SLIDE.bus).toBe('SURFACE');
    expect(AUDIO_EVENTS.TIME_EFFECT.bus).toBe('UI');
  });
});

describe('the spec document stays in sync with the catalogue', () => {
  const spec = readFileSync(join(process.cwd(), '..', 'POGOSTUCK_AUDIO_SYSTEM_SPEC.md'), 'utf8');
  it('names every event id and every recipe', () => {
    for (const id of Object.values(AUDIO_EVENT)) if (!id.startsWith('UI_')) expect(spec, id).toContain(id);
    for (const r of Object.keys(RECIPES)) expect(spec, r).toContain(r);
  });
  it('names every module of src/audio/system', () => {
    for (const m of ['AudioBus', 'AudioVariant', 'AudioEvent', 'AudioPool', 'AudioEmitter', 'AudioZone', 'AudioManager', 'SoundBank', 'WebAudioHost', 'IceSlide', 'PogoAudioDirector', 'MapAudioBridge']) expect(spec, m).toContain(m);
  });
});
