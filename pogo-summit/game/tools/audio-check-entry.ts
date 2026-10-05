/**
 * Browser entry of `tools/audio-browser-check.mjs`: exercises the REAL Web Audio API (not a fake) with the game's audio system.
 *
 *   live    — a real AudioContext: unlock, warm-up timing, every event, channel reuse, pool bookkeeping, no exception
 *   offline — a scripted "play session" (charge → power launch → wall hits → ice slide with modulation → breaks → time sting)
 *             rendered through the real bus graph / compressor by an OfflineAudioContext, returned as PCM to be analysed
 */
import { AudioManager } from '../src/audio/AudioManager';
import { WebAudioHost } from '../src/audio/system/WebAudioHost';
import { mulberry32 } from '../src/audio/system/rng';
import { PogoAudioDirector } from '../src/audio/system/PogoAudioDirector';
import { AUDIO_EVENT } from '../src/audio/system/types';
import { makeEvent, type SimEvent } from '../src/sim/events';

type Json = Record<string, unknown>;
const SR = 48000;

const sim = (type: SimEvent['type'], tick: number, intensity: number, o: Partial<SimEvent> = {}): SimEvent => ({ ...makeEvent(type, tick, o.x ?? 0, o.y ?? 0, intensity), ...o });

async function live(): Promise<Json> {
  const out: Json = {};
  const audio = new AudioManager(undefined, { rng: mulberry32(11) });
  const host = audio.host as WebAudioHost;
  const t0 = performance.now();
  audio.unlock();
  for (let i = 0; i < 100 && !audio.ready; i++) await new Promise(r => setTimeout(r, 20));
  out.unlockMs = Math.round(performance.now() - t0);
  out.ready = audio.ready; out.state = host.ctx?.state; out.sampleRate = host.ctx?.sampleRate; out.baseLatency = host.ctx?.baseLatency;
  if (!audio.ready) return out;
  // render cost of every recipe on this engine
  const costs: Record<string, number> = {};
  const { RECIPES, WARM_ORDER } = await import('../src/audio/system/synth/recipes');
  for (const id of WARM_ORDER) { const a = performance.now(); audio.bank.warm(id); costs[id] = +(performance.now() - a).toFixed(1); }
  out.renderMs = costs; out.renderTotalMs = +Object.values(costs).reduce((a, b) => a + b, 0).toFixed(1); out.bufferBytes = audio.bank.stats.bytes; out.recipes = Object.keys(RECIPES).length;
  // every event through the real graph, twice (second round reuses channels)
  const results: Record<string, boolean> = {};
  const dir = new PogoAudioDirector(audio, { rng: mulberry32(5) });
  for (let round = 0; round < 2; round++) {
    await new Promise(r => setTimeout(r, 300));
    dir.handle([sim('charge_start', 1, 0)]); await new Promise(r => setTimeout(r, 40));
    dir.handle([sim('launch', 2, 0.8), sim('boost', 2, 1)]);
    dir.handle([sim('wall_hit', 3, 0.9, { x: 2, y: 1 })]);
    results[`round${round}.break`] = audio.emit(AUDIO_EVENT.POGO_BREAK).played;
    results[`round${round}.time`] = audio.emit(AUDIO_EVENT.TIME_EFFECT).played;
    dir.update({ slideMode: true, grounded: true, sx: 20, sy: 0, groundId: 0, x: 0, y: 0 }, 10);
    await new Promise(r => setTimeout(r, 200));
    dir.update({ slideMode: false, grounded: true, sx: 0, sy: 0, groundId: 0, x: 0, y: 0 }, 10);
  }
  out.eventResults = results;
  out.hostStats = host.stats; out.poolStats = audio.pool.stats; out.bankStats = audio.bank.stats; out.managerStats = audio.stats;
  out.poolCountAfter = audio.pool.count;
  await new Promise(r => setTimeout(r, 1200));
  out.poolCountLater = audio.pool.count;                    // the 1.9 s boom and the truncated (3 s) sting may still be sounding
  await new Promise(r => setTimeout(r, 4200));
  out.poolCountDrained = audio.pool.count;                  // everything has ended and every voice has been freed
  return out;
}

async function offline(): Promise<Json & { left: number[]; right: number[] }> {
  const seconds = 9;
  const raw = new OfflineAudioContext(2, Math.round(SR * seconds), SR);
  const proxy = new Proxy(raw, {
    get(target, prop) {
      if (prop === 'state') return 'running';
      if (prop === 'resume') return () => Promise.resolve();
      const v = (target as unknown as Record<string | symbol, unknown>)[prop];
      return typeof v === 'function' ? (v as (...a: unknown[]) => unknown).bind(target) : v;
    },
    set(target, prop, value) { (target as unknown as Record<string | symbol, unknown>)[prop] = value; return true; },
  }) as unknown as AudioContext;
  const host = new WebAudioHost({ createContext: () => proxy });
  const audio = new AudioManager(host, { rng: mulberry32(21) });
  audio.unlock();
  await new Promise(r => setTimeout(r, 0));
  audio.bank.warmSome(1e9);
  audio.apply({ master: 0.8, music: 0.55, sfx: 0.9, ambient: 0.6 });
  const dir = new PogoAudioDirector(audio, { rng: mulberry32(8) });
  const log: { t: number; what: string }[] = [];
  const at = (t: number, what: string, fn: () => void): void => { void raw.suspend(t).then(() => { log.push({ t: +raw.currentTime.toFixed(3), what }); fn(); void raw.resume(); }); };

  // the scripted session
  at(0.20, 'charge_start', () => dir.handle([sim('charge_start', 1, 0)]));
  at(0.45, 'launch+boost (power jump)', () => dir.handle([sim('launch', 2, 0.9), sim('boost', 2, 1)]));
  at(0.90, 'wall_hit 0.8', () => dir.handle([sim('wall_hit', 3, 0.8)]));
  at(1.10, 'wall_hit 0.5', () => dir.handle([sim('wall_hit', 4, 0.5)]));
  at(1.25, 'wall_hit 0.95 (inside the cooldown/burst rules)', () => dir.handle([sim('wall_hit', 5, 0.95)]));
  at(1.30, 'wall_hit 0.3', () => dir.handle([sim('wall_hit', 6, 0.3)]));
  // ice slide: speed ramps 8 → 45 → decays, grounded on a slippery surface
  const sl = (speed: number, slideMode = true) => dir.update({ slideMode, grounded: true, sx: speed, sy: 0, groundId: 0, x: 0, y: 0 }, 10);
  for (let i = 0; i <= 30; i++) { const t = 1.8 + i * 0.05; const speed = i < 18 ? 8 + (i / 17) * 37 : 45 - (i - 18) * 3.5; at(t, i === 0 ? 'ice slide start (speed 8)' : `ice modulate ${speed.toFixed(1)}`, () => sl(Math.max(0, speed))); }
  at(3.35, 'ice slide ends', () => sl(0, false));
  at(3.90, 'break', () => audio.emit(AUDIO_EVENT.POGO_BREAK));
  at(4.25, 'break', () => audio.emit(AUDIO_EVENT.POGO_BREAK));
  at(4.70, 'time effect (split)', () => audio.emit(AUDIO_EVENT.TIME_EFFECT, { gain: 0.6 }));
  at(8.0, 'end', () => { /* nothing */ });

  const buf = await raw.startRendering();
  const left = Array.from(buf.getChannelData(0)), right = Array.from(buf.getChannelData(1));
  return { log, stats: audio.stats as unknown as Json, host: host.stats as unknown as Json, pool: audio.pool.stats as unknown as Json, left, right, sampleRate: SR };
}

(window as unknown as { runAudioCheck: () => Promise<unknown> }).runAudioCheck = async () => {
  const result: Json = {};
  try { result.live = await live(); } catch (e) { result.live = { error: String(e) }; }
  try { result.offline = await offline(); } catch (e) { result.offline = { error: String(e), left: [], right: [] }; }
  return result;
};
