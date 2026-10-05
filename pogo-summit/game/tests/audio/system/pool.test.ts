import { describe, expect, it } from 'vitest';
import { AudioPool, type AcquireRequest } from '../../../src/audio/system/AudioPool';
import { AUDIO_CONFIG } from '../../../src/audio/system/audioConfig';
import { AUDIO_EVENT, type AudioEventId } from '../../../src/audio/system/types';
import { makeRig } from './helpers';

const req = (event: AudioEventId, priority = 5, o: Partial<AcquireRequest> = {}): AcquireRequest => ({ event, priority, gain: 0.5, maxPerEvent: 8, atCap: 'steal-oldest', ...o });

describe('AudioPool — bookkeeping', () => {
  it('hands out voices up to the capacity, then steals the lowest priority first', () => {
    const pool = new AudioPool(3);
    const a = pool.acquire(req(AUDIO_EVENT.POGO_BREAK, 5), 0);
    const b = pool.acquire(req(AUDIO_EVENT.SURFACE_LAND, 3), 1);
    const c = pool.acquire(req(AUDIO_EVENT.POGO_COLLISION, 6), 2);
    expect([a, b, c].every(r => r.ok)).toBe(true);
    expect(pool.count).toBe(3);
    const stopped: number[] = [];
    for (const r of [a, b, c]) if (r.ok) r.voice.stop = () => { stopped.push(r.voice.id); };
    const d = pool.acquire(req(AUDIO_EVENT.POGO_LAUNCH, 8), 3);
    expect(d.ok).toBe(true);
    expect(pool.count).toBe(3);
    expect(stopped).toEqual([b.ok ? b.voice.id : -1]);                // priority 3 went
    expect(pool.stats.stolen).toBe(1);
  });
  it('ties on priority go to the quieter, then the older voice', () => {
    const pool = new AudioPool(2);
    const a = pool.acquire(req(AUDIO_EVENT.POGO_BREAK, 5, { gain: 0.9 }), 0);
    const b = pool.acquire(req(AUDIO_EVENT.SURFACE_LAND, 5, { gain: 0.2 }), 1);
    const gone: number[] = [];
    if (a.ok) a.voice.stop = () => gone.push(a.voice.id);
    if (b.ok) b.voice.stop = () => gone.push(b.voice.id);
    pool.acquire(req(AUDIO_EVENT.POGO_COLLISION, 5), 2);
    expect(gone).toEqual([b.ok ? b.voice.id : -1]);                   // the quieter one
  });
  it('a request that is not more important than anything live is rejected when the pool is full', () => {
    const pool = new AudioPool(2);
    pool.acquire(req(AUDIO_EVENT.FINISH, 9), 0);
    pool.acquire(req(AUDIO_EVENT.CHECKPOINT, 9), 0);
    const r = pool.acquire(req(AUDIO_EVENT.POGO_COLLISION, 6), 1);
    expect(r.ok).toBe(false);
    expect(pool.count).toBe(2);
    expect(pool.stats.rejected).toBe(1);
  });
  it('an event at its own cap: `ignore` rejects, `steal-oldest` replaces its oldest voice', () => {
    const pool = new AudioPool(10);
    const first = pool.acquire(req(AUDIO_EVENT.POGO_CHARGE, 7, { maxPerEvent: 1, atCap: 'ignore' }), 0);
    expect(first.ok).toBe(true);
    expect(pool.acquire(req(AUDIO_EVENT.POGO_CHARGE, 7, { maxPerEvent: 1, atCap: 'ignore' }), 1).ok).toBe(false);
    expect(pool.countFor(AUDIO_EVENT.POGO_CHARGE)).toBe(1);

    const stolen: number[] = [];
    const a = pool.acquire(req(AUDIO_EVENT.POGO_COLLISION, 6, { maxPerEvent: 2 }), 2);
    const b = pool.acquire(req(AUDIO_EVENT.POGO_COLLISION, 6, { maxPerEvent: 2 }), 3);
    if (a.ok) a.voice.stop = () => stolen.push(a.voice.id);
    if (b.ok) b.voice.stop = () => stolen.push(b.voice.id);
    pool.acquire(req(AUDIO_EVENT.POGO_COLLISION, 6, { maxPerEvent: 2 }), 4);
    expect(stolen).toEqual([a.ok ? a.voice.id : -1]);
    expect(pool.countFor(AUDIO_EVENT.POGO_COLLISION)).toBe(2);
  });
  it('release frees the slot (twice is harmless); expire frees voices without an end callback', () => {
    const pool = new AudioPool(4);
    const a = pool.acquire(req(AUDIO_EVENT.POGO_BREAK), 0);
    const l = pool.acquire(req(AUDIO_EVENT.HAZARD, 4, { endsAt: 1 }), 0);
    expect(pool.count).toBe(2);
    if (a.ok) { pool.release(a.voice.id); pool.release(a.voice.id); }
    expect(pool.count).toBe(1);
    pool.expire(0.5); expect(pool.count).toBe(1);
    pool.expire(1.0); expect(pool.count).toBe(0);
    expect(l.ok).toBe(true);
    expect(pool.stats.released).toBe(2);
  });
  it('stopAll stops every voice (or one event\'s) with the fade and empties the pool', () => {
    const pool = new AudioPool(8);
    const fades: number[] = [];
    for (const e of [AUDIO_EVENT.POGO_BREAK, AUDIO_EVENT.POGO_BREAK, AUDIO_EVENT.SURFACE_LAND]) { const r = pool.acquire(req(e), 0); if (r.ok) r.voice.stop = f => { fades.push(f); }; }
    expect(pool.stopAll(AUDIO_EVENT.POGO_BREAK, 0.07)).toBe(2);
    expect(fades).toEqual([0.07, 0.07]);
    expect(pool.count).toBe(1);
    expect(pool.stopAll()).toBe(1);
    expect(pool.count).toBe(0);
  });
  it('tracks the peak', () => {
    const pool = new AudioPool(8);
    const ids = [1, 2, 3].map(() => pool.acquire(req(AUDIO_EVENT.POGO_BREAK), 0)).map(r => (r.ok ? r.voice.id : -1));
    ids.forEach(i => pool.release(i));
    expect(pool.stats.peak).toBe(3);
  });
});

describe('pooling through the manager', () => {
  it('voices are freed when the platform reports they ended, so the pool returns to empty', () => {
    const { audio, host } = makeRig();
    audio.emit(AUDIO_EVENT.POGO_BREAK); host.advance(0.2);
    audio.emit(AUDIO_EVENT.POGO_CHARGE);
    expect(audio.pool.count).toBeGreaterThan(0);
    host.advance(5);
    expect(audio.pool.count).toBe(0);
    expect(host.live()).toHaveLength(0);
  });
  it('the global capacity is 16 (10 on low quality) and a flood never exceeds it', () => {
    expect(makeRig().audio.pool.capacity).toBe(AUDIO_CONFIG.pool.capacity);
    expect(makeRig(1, { lowQuality: true }).audio.pool.capacity).toBe(AUDIO_CONFIG.pool.capacityLow);
    const { audio, host } = makeRig();
    const ev = [AUDIO_EVENT.POGO_BREAK, AUDIO_EVENT.POGO_COLLISION, AUDIO_EVENT.POGO_LAUNCH, AUDIO_EVENT.TIME_EFFECT, AUDIO_EVENT.POGO_CHARGE];
    for (let i = 0; i < 400; i++) { audio.emit(ev[i % ev.length], { intensity: 0.8, power: true }); host.advance(0.02); expect(audio.pool.count).toBeLessThanOrEqual(16); }
    expect(audio.pool.stats.peak).toBeLessThanOrEqual(16);
  });
  it('a full pool lets an important event steal a less important voice (finish beats a collision)', () => {
    const { audio, host } = makeRig(1, { lowQuality: true });
    audio.pool.capacity = 2;
    audio.emit(AUDIO_EVENT.POGO_COLLISION, { intensity: 1 });
    host.advance(0.1);
    audio.emit(AUDIO_EVENT.POGO_BREAK);
    host.advance(0.2);
    expect(audio.pool.count).toBe(2);
    const r = audio.emit(AUDIO_EVENT.POGO_LAUNCH, { intensity: 1 });       // priority 8 > 6 and 5
    expect(r.played).toBe(true);
    expect(audio.pool.count).toBeLessThanOrEqual(2);
    expect(host.started.some(v => v.stopped)).toBe(true);
  });
  it('a stopped event leaves nothing behind in the pool', () => {
    const { audio, host } = makeRig();
    audio.emit(AUDIO_EVENT.POGO_COLLISION, { intensity: 1 }); host.advance(0.1);
    audio.emit(AUDIO_EVENT.POGO_COLLISION, { intensity: 1 });
    expect(audio.cut(AUDIO_EVENT.POGO_COLLISION, 0.05)).toBe(2);
    expect(audio.pool.countFor(AUDIO_EVENT.POGO_COLLISION)).toBe(0);
    audio.silence();
    expect(audio.pool.count).toBe(0);
  });
});
