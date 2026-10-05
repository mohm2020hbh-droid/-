import { describe, expect, it } from 'vitest';
import { AudioManager } from '../../../src/audio/AudioManager';
import { AUDIO_EVENTS, type AudioEventDef } from '../../../src/audio/system/AudioEvent';
import type { AudioVariantDef } from '../../../src/audio/system/AudioVariant';
import { SoundBank } from '../../../src/audio/system/SoundBank';
import { mulberry32 } from '../../../src/audio/system/rng';
import type { Recipe } from '../../../src/audio/system/synth/recipes';
import { AUDIO_EVENT, type AudioEventId } from '../../../src/audio/system/types';
import { FakeHost, makeRig } from './helpers';

const synth = (id: string, recipe = id): AudioVariantDef => ({ id, source: { kind: 'synth', recipe } });
const withVariants = (id: AudioEventId, variants: AudioVariantDef[], select: AudioEventDef['select'] = 'random'): Record<AudioEventId, AudioEventDef> =>
  ({ ...AUDIO_EVENTS, [id]: { ...AUDIO_EVENTS[id], variants, select, variantCooldownMs: 0 } });

describe('missing audio never throws and never crashes the game (original behaviour AU-05: a source that cannot play returns 0)', () => {
  it('an unknown event id is reported, not thrown', () => {
    const { audio, host } = makeRig();
    const r = audio.emit('NOT_AN_EVENT' as AudioEventId);
    expect(r).toEqual({ played: false, reason: 'unknown-event', variants: [], gain: 0, pitch: 1 });
    expect(audio.startLoop('NOT_AN_EVENT' as AudioEventId)).toBeNull();
    expect(host.started).toHaveLength(0);
  });
  it('emit() of a loop event and startLoop() of a one-shot are refused cleanly', () => {
    const { audio } = makeRig();
    expect(audio.emit(AUDIO_EVENT.ICE_SLIDE).reason).toBe('unknown-event');
    expect(audio.startLoop(AUDIO_EVENT.POGO_CHARGE)).toBeNull();
  });
  it('a host that is not ready (locked / suspended) plays nothing and counts the skip', () => {
    const { audio, host } = makeRig();
    host.ready = false;
    const r = audio.emit(AUDIO_EVENT.POGO_CHARGE);
    expect(r.reason).toBe('not-ready');
    expect(audio.startLoop(AUDIO_EVENT.ICE_SLIDE)).toBeNull();
    expect(audio.stats.skipped['not-ready']).toBe(2);
    expect(host.started).toHaveLength(0);
  });
  it('a disabled manager is silent', () => {
    const { audio, host } = makeRig();
    audio.enabled = false;
    expect(audio.emit(AUDIO_EVENT.POGO_CHARGE).reason).toBe('disabled');
    expect(host.started).toHaveLength(0);
  });
  it('a variant without a recipe is "missing": counted once, reported once, and the event is skipped', () => {
    const host = new FakeHost();
    const audio = new AudioManager(host, { rng: mulberry32(1), events: withVariants(AUDIO_EVENT.POGO_BREAK, [synth('ghost', 'no_such_recipe')]) });
    const reports: [string, string][] = [];
    audio.bank.onMissing = (id, why) => reports.push([id, why]);
    for (let i = 0; i < 3; i++) { host.advance(1); const r = audio.emit(AUDIO_EVENT.POGO_BREAK); expect(r.played).toBe(false); expect(r.reason).toBe('missing'); }
    expect(audio.bank.stats.missing).toBe(1);
    expect(reports).toEqual([['ghost', 'no recipe']]);
    expect(audio.stats.skipped.missing).toBe(3);
    expect(host.started).toHaveLength(0);
    expect(audio.pool.count).toBe(0);
  });
  it('falls back to another variant of the same event when one cannot be produced', () => {
    const host = new FakeHost();
    const audio = new AudioManager(host, { rng: mulberry32(1), events: withVariants(AUDIO_EVENT.POGO_BREAK, [synth('ghost', 'no_such_recipe'), synth('break_1')]) });
    let played = 0;
    for (let i = 0; i < 20; i++) { host.advance(1); const r = audio.emit(AUDIO_EVENT.POGO_BREAK); if (r.played) { played++; expect(r.variants[0]).toBe('break_1'); } }
    expect(played).toBe(20);                                  // every emit found the working variant
    expect(audio.bank.stats.missing).toBe(1);
  });
  it('a recipe that throws while rendering is contained', () => {
    const host = new FakeHost();
    const bad: Recipe = { id: 'bad', analog: 'x', targets: { durationSec: 1 }, render: () => { throw new Error('boom'); } };
    const audio = new AudioManager(host, { rng: mulberry32(1), events: withVariants(AUDIO_EVENT.POGO_BREAK, [synth('bad')]), bank: new SoundBank(host, { bad }) });
    const reports: string[] = [];
    audio.bank.onMissing = (_id, why) => reports.push(why);
    expect(audio.emit(AUDIO_EVENT.POGO_BREAK).reason).toBe('missing');
    expect(reports[0]).toContain('render failed: boom');
  });
  it('a host that refuses to start a voice (context closed, channel cap) is contained and frees the pool slot', () => {
    const { audio, host } = makeRig();
    host.canStart = false;
    const r = audio.emit(AUDIO_EVENT.POGO_BREAK);
    expect(r.played).toBe(false);
    expect(audio.pool.count).toBe(0);
    host.canStart = true;
    host.advance(1);
    expect(audio.emit(AUDIO_EVENT.POGO_BREAK).played).toBe(true);
  });
  it('a legacy variant without a legacy player is "missing", not a crash', () => {
    const host = new FakeHost();
    const audio = new AudioManager(host, { rng: mulberry32(1) });
    const r = audio.emit(AUDIO_EVENT.HAZARD);
    expect(r.played).toBe(false); expect(r.reason).toBe('missing');
    expect(audio.pool.count).toBe(0);
  });
  it('an event whose variants are all filtered out by `when` is "missing"', () => {
    const host = new FakeHost();
    const only: AudioVariantDef = { ...synth('break_1'), when: () => false };
    const audio = new AudioManager(host, { rng: mulberry32(1), events: withVariants(AUDIO_EVENT.POGO_BREAK, [only]) });
    expect(audio.emit(AUDIO_EVENT.POGO_BREAK).reason).toBe('missing');
  });
  it('NaN / negative context numbers do not crash or make noise', () => {
    const { audio, host } = makeRig();
    expect(() => audio.emit(AUDIO_EVENT.POGO_COLLISION, { intensity: NaN, x: NaN, y: NaN })).not.toThrow();
    expect(() => audio.emit(AUDIO_EVENT.POGO_LAUNCH, { intensity: -3 })).not.toThrow();
    expect(host.started.every(v => Number.isFinite(v.params.gain) && Number.isFinite(v.params.pitch))).toBe(true);
  });
});

describe('optional sample banks: a bad file never silences the game', () => {
  const manifest = { variants: { break_1: { url: 'audio/break1.wav', gain: 0.8 } } };
  const flush = async () => { for (let i = 0; i < 6; i++) await Promise.resolve(); };
  it('plays the recipe immediately, then the licensed file once it has loaded', async () => {
    const host = new FakeHost();
    const audio = new AudioManager(host, { rng: mulberry32(1), events: withVariants(AUDIO_EVENT.POGO_BREAK, [{ id: 'break_1', source: { kind: 'sample', url: 'audio/break1.wav', recipe: 'break_1' } }]) });
    audio.useSamples(manifest, { fetchBytes: async () => new ArrayBuffer(44100) });
    expect(audio.emit(AUDIO_EVENT.POGO_BREAK).played).toBe(true);
    expect(host.started[0].buffer.duration).toBeCloseTo(0.52, 2);                 // the recipe
    await flush();
    expect(audio.bank.stats.samplesLoaded).toBe(1);
    host.advance(1);
    audio.emit(AUDIO_EVENT.POGO_BREAK);
    expect(host.started[1].buffer.duration).toBeCloseTo(1, 2);                    // the file (1 s)
    expect(host.started[1].params.gain).toBeCloseTo(0.8, 2);                      // the manifest gain applies (the event gain is 1 here)
  });
  it('a 404 / decode failure is counted once and the recipe keeps playing', async () => {
    const host = new FakeHost();
    const audio = new AudioManager(host, { rng: mulberry32(1), events: withVariants(AUDIO_EVENT.POGO_BREAK, [{ id: 'break_1', source: { kind: 'sample', url: 'x', recipe: 'break_1' } }]) });
    const reports: string[] = [];
    audio.bank.onMissing = (id, why) => reports.push(`${id}: ${why}`);
    audio.useSamples(manifest, { fetchBytes: async () => { throw new Error('404'); } });
    expect(audio.emit(AUDIO_EVENT.POGO_BREAK).played).toBe(true);
    await flush();
    for (let i = 0; i < 3; i++) { host.advance(1); expect(audio.emit(AUDIO_EVENT.POGO_BREAK).played).toBe(true); }
    await flush();
    expect(audio.bank.stats.samplesLoaded).toBe(0);
    expect(reports.filter(r => r.startsWith('sample:break_1')).length).toBeGreaterThanOrEqual(1);
    expect(host.started.every(v => Math.abs(v.buffer.duration - 0.52) < 0.05)).toBe(true);
  });
  it('a decode that rejects is handled the same way', async () => {
    const host = new FakeHost();
    host.decodeImpl = () => Promise.reject(new Error('bad data'));
    const audio = new AudioManager(host, { rng: mulberry32(1), events: withVariants(AUDIO_EVENT.POGO_BREAK, [{ id: 'break_1', source: { kind: 'sample', url: 'x', recipe: 'break_1' } }]) });
    audio.useSamples(manifest, { fetchBytes: async () => new ArrayBuffer(10) });
    expect(audio.emit(AUDIO_EVENT.POGO_BREAK).played).toBe(true);
    await flush();
    expect(audio.bank.stats.missing).toBe(1);
    host.advance(1);
    expect(audio.emit(AUDIO_EVENT.POGO_BREAK).played).toBe(true);
  });
  it('without a manifest nothing is ever fetched', () => {
    let fetched = 0;
    const { audio } = makeRig();
    audio.bank.loader = { fetchBytes: async () => { fetched++; return new ArrayBuffer(1); } };
    audio.emit(AUDIO_EVENT.POGO_BREAK);
    expect(fetched).toBe(0);
  });
});
