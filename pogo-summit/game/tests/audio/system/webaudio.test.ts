import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { WebAudioHost } from '../../../src/audio/system/WebAudioHost';
import type { VoiceParams } from '../../../src/audio/system/types';

class P { value = 0; calls: unknown[][] = [];
  setValueAtTime(v: number, t: number) { this.value = v; this.calls.push(['set', v, t]); return this; }
  linearRampToValueAtTime(v: number, t: number) { this.calls.push(['ramp', v, t]); return this; }
  setTargetAtTime(v: number, t: number, tc: number) { this.value = v; this.calls.push(['target', v, t, tc]); return this; }
  cancelScheduledValues(t: number) { this.calls.push(['cancel', t]); return this; } }
class N { out: N[] = []; connect(n: N) { this.out.push(n); return n; } disconnect() { this.out = []; } }
class G extends N { gain = new P(); }
class Pan extends N { pan = new P(); }
class Comp extends N { threshold = new P(); knee = new P(); ratio = new P(); attack = new P(); release = new P(); }
class Src extends N { buffer: unknown = null; loop = false; playbackRate = new P(); onended: (() => void) | null = null; startArgs: number[] | null = null; stopArgs: (number | undefined)[] | null = null;
  start(t: number, off: number) { this.startArgs = [t, off]; } stop(t?: number) { this.stopArgs = [t]; } end() { this.onended?.(); } }
class Buf { duration: number; data: Float32Array; constructor(public ch: number, public length: number, public sampleRate: number) { this.duration = length / sampleRate; this.data = new Float32Array(length); }
  getChannelData() { return this.data; } copyToChannel(d: Float32Array) { this.data.set(d); } }
class Ctx { static count = 0; static last: Ctx | null = null;
  state = 'suspended'; currentTime = 0; sampleRate = 48000; destination = new N(); onstatechange: (() => void) | null = null;
  gains: G[] = []; panners: Pan[] = []; sources: Src[] = []; resumes = 0; suspends = 0; comp = new Comp();
  constructor() { Ctx.count++; Ctx.last = this; }
  createGain() { const g = new G(); this.gains.push(g); return g; }
  createDynamicsCompressor() { return this.comp; }
  createStereoPanner() { const p = new Pan(); this.panners.push(p); return p; }
  createBufferSource() { const s = new Src(); this.sources.push(s); return s; }
  createBuffer(ch: number, len: number, sr: number) { return new Buf(ch, len, sr); }
  resume() { this.resumes++; this.state = 'running'; this.onstatechange?.(); return Promise.resolve(); }
  suspend() { this.suspends++; this.state = 'suspended'; this.onstatechange?.(); return Promise.resolve(); }
  decodeAudioData(b: ArrayBuffer) { return Promise.resolve(new Buf(1, b.byteLength, 44100)); } }

const params = (o: Partial<VoiceParams> = {}): VoiceParams => ({ bus: 'PLAYER', gain: 0.5, pitch: 1, pan: 0, loop: false, ...o });
const make = (maxChannels?: number) => new WebAudioHost({ createContext: () => new Ctx() as unknown as AudioContext, maxChannels });
const running = async (h: WebAudioHost) => { h.unlock(); await Promise.resolve(); await Promise.resolve(); return h; };

beforeEach(() => { Ctx.count = 0; WebAudioHost.resetShared(); });
afterEach(() => { vi.unstubAllGlobals(); });

describe('WebAudioHost — mobile-safe initialisation and the context singleton', () => {
  it('creates nothing until the first user gesture', () => {
    const h = make();
    expect(h.ctx).toBeNull(); expect(h.ready).toBe(false); expect(Ctx.count).toBe(0);
    expect(h.start({}, params(), () => {})).toBeNull();                // no sound before unlock
  });
  it('unlock() creates one context, resumes it, fires onUnlock once, and repeated gestures only resume', async () => {
    const h = make(); let unlocked = 0; h.onUnlock = () => unlocked++;
    await running(h);
    expect(Ctx.count).toBe(1); expect(h.ready).toBe(true); expect(unlocked).toBe(1);
    h.unlock(); h.unlock();
    expect(Ctx.count).toBe(1);
    expect(unlocked).toBe(1);
  });
  it('is a page-wide singleton: a second host reuses the same context (default factory)', () => {
    vi.stubGlobal('window', { AudioContext: Ctx });
    const a = new WebAudioHost(), b = new WebAudioHost();
    a.unlock(); b.unlock();
    expect(Ctx.count).toBe(1);
    expect(a.ctx).toBe(b.ctx);
    expect(a.stats.contextsCreated + b.stats.contextsCreated).toBe(1);
  });
  it('builds the bus graph: buses → MASTER → compressor → destination', async () => {
    const h = await running(make()), ctx = h.ctx as unknown as Ctx;
    expect(ctx.comp.out).toContain(ctx.destination);
    expect((h.master as unknown as G).out).toContain(ctx.comp);
    const sfx = h.busNode('SFX') as unknown as G, player = h.busNode('PLAYER') as unknown as G, surface = h.busNode('SURFACE') as unknown as G;
    expect(player.out).toContain(sfx); expect(surface.out).toContain(sfx);
    expect(sfx.out).toContain(h.master as unknown as G);
    for (const b of ['AMBIENT', 'UI', 'MUSIC'] as const) expect((h.busNode(b) as unknown as G).out).toContain(h.master as unknown as G);
    expect(h.buses.sfx).toBe(h.busNode('SFX')); expect(h.buses.music).toBe(h.busNode('MUSIC')); expect(h.buses.ambient).toBe(h.busNode('AMBIENT'));
  });
  it('bus gains requested before the context exists are applied when it is created', async () => {
    const h = make();
    h.setBusGain('SFX', 0.4);
    await running(h);
    const calls = (h.busNode('SFX') as unknown as G).gain.calls;
    expect(calls.some(c => c[0] === 'target' && c[1] === 0.4)).toBe(true);
  });
  it('an interruption is noticed and the next gesture resumes the context', async () => {
    const h = await running(make()), ctx = h.ctx as unknown as Ctx;
    ctx.state = 'interrupted'; ctx.onstatechange?.();
    expect(h.ready).toBe(false); expect(h.needsResume).toBe(true);
    h.unlock();
    expect(ctx.resumes).toBeGreaterThanOrEqual(2);
    expect(h.ready).toBe(true); expect(h.needsResume).toBe(false);
  });
  it('is suspended when the page is hidden and resumed when it is visible again', async () => {
    const handlers: (() => void)[] = [];
    const doc = { hidden: false, addEventListener: (_: string, f: () => void) => handlers.push(f) };
    vi.stubGlobal('document', doc);
    const h = await running(make()), ctx = h.ctx as unknown as Ctx;
    doc.hidden = true; handlers.forEach(f => f());
    expect(ctx.suspends).toBe(1); expect(h.ready).toBe(false);
    doc.hidden = false; handlers.forEach(f => f());
    expect(h.ready).toBe(true);
  });
});

describe('WebAudioHost — pooled voice channels', () => {
  it('reuses channels instead of creating nodes per sound', async () => {
    const h = await running(make()), ctx = h.ctx as unknown as Ctx;
    const gainsBefore = ctx.gains.length;
    const ends: (() => void)[] = [];
    const first = [0, 1, 2].map(() => h.start(new Buf(1, 100, 48000), params(), () => {}));
    expect(first.every(Boolean)).toBe(true);
    expect(h.stats.channelsCreated).toBe(3);
    expect(ctx.gains.length).toBe(gainsBefore + 3);
    ctx.sources.slice(-3).forEach(s => s.end());
    for (let round = 0; round < 20; round++) {
      const v = h.start(new Buf(1, 100, 48000), params(), () => {});
      expect(v).toBeTruthy();
      ctx.sources[ctx.sources.length - 1].end();
    }
    ends.length = 0;
    expect(h.stats.channelsCreated).toBe(3);                              // no new gain/panner nodes for 20 more sounds
    expect(ctx.gains.length).toBe(gainsBefore + 3);
    expect(ctx.panners.length).toBe(3);
    expect(h.stats.channelReuses).toBeGreaterThanOrEqual(20);
    expect(h.stats.voicesStarted).toBe(23);
  });
  it('a busy channel is never handed out twice; at the channel cap a new voice is refused', async () => {
    const h = await running(make(2));
    const a = h.start(new Buf(1, 100, 48000), params(), () => {}), b = h.start(new Buf(1, 100, 48000), params(), () => {});
    expect(a && b).toBeTruthy();
    expect(h.start(new Buf(1, 100, 48000), params(), () => {})).toBeNull();
    (h.ctx as unknown as Ctx).sources[0].end();
    expect(h.start(new Buf(1, 100, 48000), params(), () => {})).toBeTruthy();       // the freed channel is reused
    expect(h.stats.channelsCreated).toBe(2);
  });
  it('re-routes a reused channel to the bus of its new voice', async () => {
    const h = await running(make());
    h.start(new Buf(1, 100, 48000), params({ bus: 'PLAYER' }), () => {});
    const ctx = h.ctx as unknown as Ctx;
    const chan = ctx.panners[0];
    expect(chan.out).toContain(h.busNode('PLAYER') as unknown as G);
    ctx.sources[0].end();
    h.start(new Buf(1, 100, 48000), params({ bus: 'SURFACE' }), () => {});
    expect(chan.out).toEqual([h.busNode('SURFACE') as unknown as G]);
  });
  it('starts the source with pitch, loop, offset and a 2 ms attack ramp; onEnded fires once and frees the channel', async () => {
    const h = await running(make()), ctx = h.ctx as unknown as Ctx;
    let ended = 0;
    const v = h.start(new Buf(1, 48000, 48000), params({ loop: true, pitch: 0.8, offset: 0.25, gain: 0.7, pan: -0.3 }), () => ended++);
    expect(v).toBeTruthy();
    const src = ctx.sources[ctx.sources.length - 1];
    expect(src.loop).toBe(true); expect(src.playbackRate.value).toBe(0.8); expect(src.startArgs![1]).toBe(0.25);
    const g = ctx.gains[ctx.gains.length - 1].gain.calls;
    expect(g).toContainEqual(['ramp', 0.7, 0.002]);
    src.end(); src.end();
    expect(ended).toBe(1);
  });
  it('set() ramps gain / pitch / pan; stop(fade) fades and stops, stop(0) stops now', async () => {
    const h = await running(make()), ctx = h.ctx as unknown as Ctx;
    const v = h.start(new Buf(1, 48000, 48000), params(), () => {})!;
    v.set({ gain: 0.2, pitch: 1.1, pan: 0.5 }, 0.09);
    const src = ctx.sources[0], gain = ctx.gains[ctx.gains.length - 1].gain;
    expect(gain.calls.some(c => c[0] === 'target' && c[1] === 0.2)).toBe(true);
    expect(src.playbackRate.calls.some(c => c[0] === 'target' && c[1] === 1.1)).toBe(true);
    v.stop(0.25);
    expect(gain.calls.some(c => c[0] === 'target' && c[1] === 0)).toBe(true);
    expect(src.stopArgs![0]).toBeGreaterThan(0.25);
    const v2 = h.start(new Buf(1, 48000, 48000), params(), () => {})!;
    v2.stop(0);
    expect(ctx.sources[1].stopArgs).toEqual([undefined]);
  });
  it('truncates a long stinger: gain fades to zero and the source stops at maxDuration', async () => {
    const h = await running(make()), ctx = h.ctx as unknown as Ctx;
    h.start(new Buf(1, 48000 * 6, 48000), params({ maxDuration: 3, fadeOut: 0.6, gain: 0.8 }), () => {});
    const src = ctx.sources[0], g = ctx.gains[ctx.gains.length - 1].gain.calls;
    expect(g).toContainEqual(['ramp', 0, 3]);
    expect(src.stopArgs![0]).toBeCloseTo(3.01, 5);
  });
  it('makeBuffer wraps mono PCM; decode goes through the context', async () => {
    const h = await running(make());
    const b = h.makeBuffer(new Float32Array([0.1, 0.2, 0.3]), 22050) as unknown as Buf;
    expect(b.length).toBe(3); expect(b.sampleRate).toBe(22050); expect(Array.from(b.data)).toEqual([expect.closeTo(0.1, 5), expect.closeTo(0.2, 5), expect.closeTo(0.3, 5)]);
    const d = await h.decode(new ArrayBuffer(8)) as unknown as Buf;
    expect(d.length).toBe(8);
    expect(h.durationOf(b)).toBeCloseTo(3 / 22050, 9);
  });
});
