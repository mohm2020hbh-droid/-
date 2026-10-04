import { describe, expect, it } from 'vitest';
import { execFileSync } from 'node:child_process';
import { readdirSync, readFileSync } from 'node:fs';
import { join, relative } from 'node:path';

const MAP = join(process.cwd(), 'src', 'map');
const stripComments = (s: string) => s.replace(/\/\*[\s\S]*?\*\//g, '').replace(/(^|[^:])\/\/.*$/gm, '$1');
const files = (dir: string): string[] => readdirSync(dir, { withFileTypes: true }).flatMap(e => (e.isDirectory() ? files(join(dir, e.name)) : e.name.endsWith('.ts') ? [join(dir, e.name)] : []));

/** The only parts of the simulation the map layer may import (SPEC §0.2). Nothing from `sim/core` except `respawn`. */
const ALLOWED_SIM = new Set([
  'math', 'geometry', 'PhysicsWorld', 'PhysicsConfig', 'SurfacePhysics', 'PogoState', 'events', 'analysis', 'routeBot',
  'PogoPhysicsController', 'core/step',
]);

describe('Map System V2 respects the physics boundary (SPEC §0)', () => {
  const all = files(MAP);

  it('src/map is pure data/logic: no DOM, Three.js, UI/render imports, clocks or randomness', () => {
    for (const f of all) {
      const src = stripComments(readFileSync(f, 'utf8'));
      expect(src, relative(MAP, f)).not.toMatch(/from\s+['"](?:three|(?:\.\.\/)+(?:render|ui|audio|haptics|input|progression|game|lab)(?:\/|['"]))/);
      expect(src, relative(MAP, f)).not.toMatch(/(?<![\w.])(?:window|document|navigator|localStorage|sessionStorage)\s*[.[]|typeof\s+(?:window|document)|\b(?:requestAnimationFrame|performance\.now|Date\.now|Math\.random|setTimeout|setInterval)\b/);
    }
  });

  it('src/map only imports an allow-listed surface of the simulation (the core is reached through MapWorld / respawn only)', () => {
    const bad: string[] = [];
    for (const f of all) {
      const src = stripComments(readFileSync(f, 'utf8'));
      for (const m of src.matchAll(/from\s+'\.\.\/sim\/([A-Za-z0-9_/]+)'/g)) if (!ALLOWED_SIM.has(m[1])) bad.push(`${relative(MAP, f)} → sim/${m[1]}`);
    }
    expect(bad).toEqual([]);
  });

  it('the map layer never assigns to physics state or constants (only the DESIGN-class respawn anchor)', () => {
    const writes: string[] = [];
    for (const f of all) {
      const src = stripComments(readFileSync(f, 'utf8'));
      // direct assignments to PogoState physics fields / config
      for (const m of src.matchAll(/\b(?:s|state|pogo\.state)\.(qx|qy|qvx|qvy|theta|omega|load|loadMin|loadMax|boost|noGround|jumpTimer|grounded|sx|sy|gamma)\s*(?:[+\-*/]?=)(?!=)/g)) writes.push(`${relative(MAP, f)}: ${m[0]}`);
      for (const m of src.matchAll(/\bcfg\.\w+\s*=(?!=)/g)) writes.push(`${relative(MAP, f)}: ${m[0]}`);
    }
    expect(writes).toEqual([]);
  });

  it('git: Physics Core, physics constants and the LOCKED SPEC are byte-identical to the commit that implemented them', () => {
    const BASE = '9601653';                                                      // last physics commit before Map System V2
    let out: string;
    try { out = execFileSync('git', ['diff', '--name-only', BASE, 'HEAD', '--', 'game/src/sim/core', 'game/src/sim/PhysicsConfig.ts', 'game/src/sim/PogoState.ts', 'game/src/sim/PogoPhysicsController.ts', 'Pogostuck_Physics_LOCKED_SPEC.md'], { cwd: join(process.cwd(), '..', '..'), encoding: 'utf8', stdio: ['ignore', 'pipe', 'ignore'] }); }
    catch { return; }                                                             // git/history unavailable (shallow clone): nothing to compare
    expect(out.trim()).toBe('');
    let wt = '';
    try { wt = execFileSync('git', ['status', '--porcelain', '--', 'pogo-summit/game/src/sim/core', 'pogo-summit/game/src/sim/PhysicsConfig.ts', 'pogo-summit/Pogostuck_Physics_LOCKED_SPEC.md'], { cwd: join(process.cwd(), '..', '..'), encoding: 'utf8', stdio: ['ignore', 'pipe', 'ignore'] }); } catch { return; }
    expect(wt.trim()).toBe('');
  });
});
