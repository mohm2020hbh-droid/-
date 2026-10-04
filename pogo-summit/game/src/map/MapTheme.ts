/**
 * MapTheme — themes are independent of the map layout (SPEC §13).
 *
 * A theme carries palette, sky, fog, lighting, material slots ("@ground"), vegetation, VFX/audio ambience, background
 * props, weather and an optional day/night cycle. Swapping `doc.theme` changes the whole look without touching an entity.
 */
import { WORLDS, type WorldTheme, getTheme } from '../data/worlds';
import type { Json, MapDocument, MaterialDef, ThemeColors, ThemeDef, ThemeRef } from './schema';

const WEATHER: Record<string, NonNullable<ThemeDef['weather']>> = {
  autumn_hills: { type: 'leaves', intensity: 0.35, windX: 0.4 },
  snow_peaks: { type: 'snow', intensity: 0.6, windX: 0.8 },
  ancient_ruins: { type: 'none', intensity: 0 },
  volcanic_depths: { type: 'ash', intensity: 0.5, windX: 0.3 },
};

function fromWorld(w: WorldTheme): ThemeDef {
  return {
    id: w.id, name: w.name, base: w.id as ThemeDef['base'],
    palette: { ...w.palette },
    sky: { top: w.sky.top, mid: w.sky.mid, horizon: w.sky.horizon, sun: w.sky.sun, sunDir: [...w.sky.sunDir] as [number, number, number], sunIntensity: w.sky.sunIntensity },
    fog: { ...w.fog },
    lighting: { hemiSky: w.sky.hemiSky, hemiGround: w.sky.hemiGround, hemiIntensity: w.sky.hemiIntensity, exposure: w.sky.exposure },
    slots: { ground: `mat.${w.id}.ground`, secondary: `mat.${w.id}.secondary`, water: `mat.${w.id}.water`, lava: w.id === 'volcanic_depths' ? `mat.${w.id}.lava` : undefined },
    vegetation: { density: w.scatter.pines + w.scatter.autumn + w.scatter.bushes + w.scatter.flowers, kinds: Object.entries(w.scatter).filter(([, v]) => v > 0).map(([k]) => k) },
    vfx: { ambient: [w.capStyle === 'snow' ? 'snowfall' : w.capStyle === 'ash' ? 'embers' : 'leaves'], intensity: 0.6 },
    audio: { ambient: w.ambience.id, wind: w.ambience.wind, birds: w.ambience.birds, water: w.ambience.water },
    backgroundProps: ['mountain', 'cloud', ...(w.scatter.ruins > 0 ? ['ruin_arch'] : [])],
    weather: WEATHER[w.id],
  };
}

export const BUILTIN_THEMES: Record<string, ThemeDef> = Object.fromEntries(WORLDS.map(w => [w.id, fromWorld(w)]));

/** Material definitions behind the built-in theme slots (parameter sets of built-in shaders). */
export function themeMaterials(def: ThemeDef): Record<string, MaterialDef> {
  const w = getTheme(def.base ?? def.id);
  const t = w.terrain;
  const out: Record<string, MaterialDef> = {
    [def.slots.ground]: { id: def.slots.ground, shader: 'palette', color: t.capB },
    [def.slots.secondary]: { id: def.slots.secondary, shader: 'stylized-lit', color: t.wood },
    [def.slots.water]: { id: def.slots.water, shader: 'water', color: w.water },
  };
  if (def.slots.lava) out[def.slots.lava] = { id: def.slots.lava, shader: 'emissive', color: t.hazardCrystal, emissive: 0.8 };
  return out;
}

export function resolveTheme(ref: ThemeRef | undefined, fallbackId: string, registry: Record<string, ThemeDef> = BUILTIN_THEMES): { theme: ThemeDef; found: boolean } {
  if (ref && 'theme' in ref && ref.theme) return { theme: ref.theme, found: true };
  const id = ref && 'ref' in ref && ref.ref ? ref.ref : fallbackId;
  const t = registry[id];
  return t ? { theme: t, found: true } : { theme: registry.autumn_hills ?? Object.values(registry)[0], found: false };
}

const isObj = (v: unknown): v is Record<string, unknown> => typeof v === 'object' && v !== null && !Array.isArray(v);
function mergeInto(base: Record<string, unknown>, over: Record<string, Json>): void {
  for (const [k, v] of Object.entries(over)) {
    if (isObj(v) && isObj(base[k])) mergeInto(base[k] as Record<string, unknown>, v as Record<string, Json>);
    else base[k] = v;
  }
}

/** Theme definition → the renderer's WorldTheme (the existing renderer accepts V2 themes unchanged). */
export function toWorldTheme(def: ThemeDef): WorldTheme {
  const base = getTheme(def.base ?? def.id);
  const w: WorldTheme = JSON.parse(JSON.stringify(base));
  w.id = def.id; w.name = def.name;
  w.palette = { ...def.palette };
  w.sky = { ...w.sky, top: def.sky.top, mid: def.sky.mid, horizon: def.sky.horizon, sun: def.sky.sun, sunDir: [...def.sky.sunDir] as [number, number, number], sunIntensity: def.sky.sunIntensity, hemiSky: def.lighting.hemiSky, hemiGround: def.lighting.hemiGround, hemiIntensity: def.lighting.hemiIntensity, exposure: def.lighting.exposure };
  w.fog = { ...def.fog };
  w.ambience = { ...w.ambience, wind: def.audio.wind, birds: def.audio.birds, water: def.audio.water };
  if (def.overrides) mergeInto(w as unknown as Record<string, unknown>, def.overrides);
  return w;
}

// ── colours ─────────────────────────────────────────────────────────────────────────────────────────────────────
export function parseHex(c: string): [number, number, number] {
  const m = /^#([0-9a-f]{3}|[0-9a-f]{6})$/i.exec(c.trim());
  if (!m) return [0, 0, 0];
  let h = m[1];
  if (h.length === 3) h = h.split('').map(x => x + x).join('');
  return [parseInt(h.slice(0, 2), 16), parseInt(h.slice(2, 4), 16), parseInt(h.slice(4, 6), 16)];
}
export const isHexColor = (c: string): boolean => /^#([0-9a-f]{3}|[0-9a-f]{6})$/i.test(c.trim());
export function toHex(rgb: [number, number, number]): string { return '#' + rgb.map(v => Math.max(0, Math.min(255, Math.round(v))).toString(16).padStart(2, '0')).join(''); }
export function lerpColor(a: string, b: string, t: number): string {
  const x = parseHex(a), y = parseHex(b);
  return toHex([x[0] + (y[0] - x[0]) * t, x[1] + (y[1] - x[1]) * t, x[2] + (y[2] - x[2]) * t]);
}
const lerp = (a: number, b: number, t: number): number => a + (b - a) * t;

/** Interpolated theme at `tSec` (day/night cycle, closed loop). Themes without `dayNight` are returned unchanged. */
export function sampleTheme(def: ThemeDef, tSec: number): ThemeDef {
  const dn = def.dayNight;
  if (!dn || dn.keyframes.length === 0 || dn.cycleSec <= 0) return def;
  const kf = [...dn.keyframes].sort((a, b) => a.t - b.t);
  const u = ((tSec / dn.cycleSec) % 1 + 1) % 1;
  let i = kf.length - 1;
  for (let k = 0; k < kf.length; k++) if (kf[k].t <= u) i = k;
  const a = kf[i], b = kf[(i + 1) % kf.length];
  const span = ((b.t - a.t) + 1) % 1 || 1;
  const f = Math.max(0, Math.min(1, (((u - a.t) + 1) % 1) / span));
  const sky: ThemeColors = { ...def.sky };
  for (const k of ['top', 'mid', 'horizon', 'sun'] as const) {
    const ca = a.sky?.[k] ?? def.sky[k], cb = b.sky?.[k] ?? def.sky[k];
    sky[k] = lerpColor(ca, cb, f);
  }
  const fogA = { color: a.fog?.color ?? def.fog.color, density: a.fog?.density ?? def.fog.density };
  const fogB = { color: b.fog?.color ?? def.fog.color, density: b.fog?.density ?? def.fog.density };
  return {
    ...def,
    sky: { ...def.sky, ...sky, sunIntensity: lerp(a.sunIntensity ?? def.sky.sunIntensity, b.sunIntensity ?? def.sky.sunIntensity, f) },
    fog: { color: lerpColor(fogA.color, fogB.color, f), density: lerp(fogA.density, fogB.density, f) },
    lighting: { ...def.lighting, hemiIntensity: lerp(a.hemiIntensity ?? def.lighting.hemiIntensity, b.hemiIntensity ?? def.lighting.hemiIntensity, f) },
  };
}

/** Material id for a visual material reference: "@slot" → the theme's slot, anything else is returned as is. */
export function resolveMaterialRef(ref: string | undefined, theme: ThemeDef): string | undefined {
  if (!ref || !ref.startsWith('@')) return ref;
  const slot = ref.slice(1) as keyof ThemeDef['slots'];
  return theme.slots[slot];
}

/** Every material id usable in `doc` (map-local + theme slots). */
export function availableMaterials(doc: Pick<MapDocument, 'materials'>, theme: ThemeDef): Set<string> {
  return new Set([...Object.keys(doc.materials), ...Object.keys(themeMaterials(theme))]);
}
