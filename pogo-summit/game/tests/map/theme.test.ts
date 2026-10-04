import { describe, expect, it } from 'vitest';
import { WORLD_1, WORLDS } from '../../src/data/worlds';
import { BUILTIN_THEMES, lerpColor, parseHex, resolveMaterialRef, resolveTheme, sampleTheme, themeMaterials, toWorldTheme } from '../../src/map/MapTheme';
import type { ThemeDef } from '../../src/map/schema';

describe('MapTheme', () => {
  it('four built-in themes mirror the four existing worlds', () => {
    expect(Object.keys(BUILTIN_THEMES)).toEqual(WORLDS.map(w => w.id));
    for (const w of WORLDS) expect(BUILTIN_THEMES[w.id].palette).toEqual(w.palette);
  });

  it('a theme without overrides converts back to an identical WorldTheme (renderer compatibility)', () => {
    const w = toWorldTheme(BUILTIN_THEMES.autumn_hills);
    expect(w).toEqual(WORLD_1);
  });

  it('theme swap: overrides and palette change the renderer theme without any map edit', () => {
    const night: ThemeDef = { ...BUILTIN_THEMES.autumn_hills, id: 'autumn_night', name: 'Autumn night',
      sky: { ...BUILTIN_THEMES.autumn_hills.sky, top: '#0a1030', sunIntensity: 0.3 }, fog: { color: '#101830', density: 0.01 },
      overrides: { capStyle: 'ash', cloud: { amount: 0.2 } } };
    const w = toWorldTheme(night);
    expect(w.sky.top).toBe('#0a1030'); expect(w.sky.sunIntensity).toBe(0.3); expect(w.fog.density).toBe(0.01);
    expect(w.capStyle).toBe('ash'); expect(w.cloud.amount).toBe(0.2); expect(w.cloud.light).toBe(WORLD_1.cloud.light);   // deep merge keeps siblings
  });

  it('resolveTheme: built-in by id, inline theme, and an unknown id falls back with found=false', () => {
    expect(resolveTheme({ ref: 'snow_peaks' }, 'x').theme.id).toBe('snow_peaks');
    expect(resolveTheme({ theme: BUILTIN_THEMES.ancient_ruins }, 'x').theme.id).toBe('ancient_ruins');
    const miss = resolveTheme({ ref: 'nope' }, 'nope');
    expect(miss.found).toBe(false); expect(miss.theme.id).toBe('autumn_hills');
  });

  it('@slot material references resolve through the theme; slots have material definitions', () => {
    const t = BUILTIN_THEMES.volcanic_depths;
    expect(resolveMaterialRef('@ground', t)).toBe(t.slots.ground);
    expect(resolveMaterialRef('@lava', t)).toBe(t.slots.lava);
    expect(resolveMaterialRef('custom_mat', t)).toBe('custom_mat');
    const mats = themeMaterials(t);
    expect(mats[t.slots.ground].shader).toBe('palette');
    expect(mats[t.slots.lava!].shader).toBe('emissive');
  });

  it('day/night: keyframes interpolate sky, fog and light and loop', () => {
    const t: ThemeDef = { ...BUILTIN_THEMES.autumn_hills, dayNight: { cycleSec: 100, keyframes: [
      { t: 0, sky: { top: '#000000' }, sunIntensity: 0, hemiIntensity: 0.2, fog: { density: 0.01 } },
      { t: 0.5, sky: { top: '#ffffff' }, sunIntensity: 2, hemiIntensity: 1, fog: { density: 0.002 } },
    ] } };
    expect(sampleTheme(t, 0).sky.top).toBe('#000000');
    expect(sampleTheme(t, 25).sky.top).toBe('#808080');
    expect(sampleTheme(t, 50).sky.top).toBe('#ffffff');
    expect(sampleTheme(t, 75).sky.top).toBe('#808080');                                   // second half interpolates back to the first key
    expect(sampleTheme(t, 100).sky.top).toBe('#000000');                                  // loops
    expect(sampleTheme(t, 25).sky.sunIntensity).toBeCloseTo(1, 9);
    expect(sampleTheme(t, 25).fog.density).toBeCloseTo(0.006, 9);
    expect(sampleTheme(BUILTIN_THEMES.snow_peaks, 33)).toBe(BUILTIN_THEMES.snow_peaks);   // no cycle ⇒ unchanged
  });

  it('colour helpers', () => {
    expect(parseHex('#fff')).toEqual([255, 255, 255]);
    expect(parseHex('#102030')).toEqual([16, 32, 48]);
    expect(lerpColor('#000000', '#ff0000', 0.5)).toBe('#800000');
  });
});
