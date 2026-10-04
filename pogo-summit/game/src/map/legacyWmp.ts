/**
 * legacyWmp — developer tool: legacy map text export (`.$$M`, "wmpio" format) → MapDocument (SPEC §19.2).
 *
 * Reads a user's OWN file and emits geometry / behaviour / zone DATA only. Models, textures, shaders and materials of the
 * legacy kit are never converted or copied; model-bearing entities get placeholder collision boxes (sized by the optional
 * `modelSizes` table) and are tagged so they can be replaced by new visuals.
 *
 * Coordinates: legacy (x, y = depth, z = up) in units; V2 metres: x' = x / unitsPerMetre, y' = z / unitsPerMetre.
 * unitsPerMetre = 52 (INFER, medium confidence — see MAP_SYSTEM_V2_ANALYSIS.md §8 U3).
 *
 * Behaviour mapping (names are only used to *recognise* the legacy behaviour; the V2 data is our own schema):
 *   moveSine_act → move/sine · map3Wheel_act → rotate · toggleBlock_act → toggle · toggleSine_act → timed
 *   collision entity with FLAG7 → slippery · boostjuice_act → boostZone (declared) · bgObject_act → background
 *   regions: kill → kill zone · CP_<n> → checkpoint n · reg_finish → finish · path_progress → main route
 */
import type { CheckpointDef, MapDocument, MapEntity, RegionDef, RouteDef, Vec2, BehaviorDef, FinishZone } from './schema';
import { newMapDocument } from './MapLoader';
import { type MapIssue, mkIssue } from './MapIssue';
import { convexHull } from './MapCollision';

const T_PER_SEC = 15.2;                 // 1 s = 16 · 0.95 game-time units (locked-spec time base)

interface Node { tag: string; lines: string[]; kids: Node[] }

function parseTree(text: string): Node {
  const root: Node = { tag: 'root', lines: [], kids: [] };
  const stack = [root];
  for (const raw of text.replace(/\r/g, '').split('\n')) {
    const s = raw.trim();
    const m = /^\{\s*(?:\/\/\s*(.*))?$/.exec(s);
    if (m) { const n: Node = { tag: (m[1] ?? '').trim(), lines: [], kids: [] }; stack[stack.length - 1].kids.push(n); stack.push(n); continue; }
    if (s === '}') { if (stack.length > 1) stack.pop(); continue; }
    if (s) stack[stack.length - 1].lines.push(s);
  }
  return root;
}

const nums = (s: string): number[] => s.split(/\s+/).filter(Boolean).map(Number);
const flagBits = (s: string): number => { const v = parseInt(s, 2); return Number.isFinite(v) ? v : 0; };
const slug = (s: string): string => s.replace(/[^A-Za-z0-9_.\-]/g, '_').slice(0, 56) || 'x';

export interface ImportOptions {
  unitsPerMetre?: number;
  /** Placeholder collision size (metres) per legacy model file name (lower case), e.g. { 'moveblock1.mdl': [7.3, 2.5] }. */
  modelSizes?: Record<string, [number, number]>;
  /** Lines of the map's `splitSetup.txt` (split names in CP order). */
  splitNames?: string[];
  id?: string;
  name?: string;
  author?: string;
}
export interface ImportReport { issues: MapIssue[]; stats: { blocks: number; models: number; regions: number; paths: number; mapped: Record<string, number>; unmapped: Record<string, number> } }

export function importLegacyWmp(text: string, o: ImportOptions = {}): { doc: MapDocument; report: ImportReport } {
  const U = o.unitsPerMetre ?? 52;
  const issues: MapIssue[] = [];
  const stats: ImportReport['stats'] = { blocks: 0, models: 0, regions: 0, paths: 0, mapped: {}, unmapped: {} };
  const count = (bucket: Record<string, number>, k: string) => { bucket[k] = (bucket[k] ?? 0) + 1; };
  const root = parseTree(text);
  if (!/wmpio/i.test(text.slice(0, 80))) issues.push(mkIssue('WARNING', 'IMPORT_FORMAT', '', 'file does not start with a "wmpio" header; trying anyway'));
  const level = root.kids.find(k => k.tag === 'level');
  const doc = newMapDocument(slug(o.id ?? 'imported'));
  doc.manifest.name = o.name ?? doc.manifest.id; doc.manifest.author = o.author ?? ''; doc.manifest.tags = ['imported', 'legacy'];
  const ents: MapEntity[] = [];
  const regions: RegionDef[] = [];
  const checkpoints: CheckpointDef[] = [];
  const finish: FinishZone[] = [];
  const routes: RouteDef[] = [];
  const paths: MapDocument['paths'] = [];
  const m = (v: number): number => Math.round((v / U) * 1e4) / 1e4;
  const pt = (x: number, z: number): Vec2 => ({ x: m(x), y: m(z) });

  // ── blocks → convex collision (XZ hull) ──────────────────────────────────────────────────────────────────────
  level?.kids.forEach((b, bi) => {
    const nv = Number((b.lines.find(l => l.startsWith('vertices')) ?? '').split(' ')[1]);
    if (!(nv > 0)) return;
    const li = b.lines.findIndex(l => l.startsWith('faces'));
    const flat: number[] = [];
    let k = li + 1;
    while (flat.length < nv * 3 && k < b.lines.length) { flat.push(...nums(b.lines[k])); k++; }
    if (flat.length < nv * 3) { issues.push(mkIssue('WARNING', 'IMPORT_BLOCK', `/blocks/${bi}`, `block ${bi}: vertex list is truncated`)); return; }
    const pts: Vec2[] = [];
    for (let i = 0; i < nv; i++) pts.push(pt(flat[i * 3], flat[i * 3 + 2]));
    const hull = convexHull(pts);
    if (hull.length < 3) { count(stats.unmapped, 'degenerate-block'); return; }
    stats.blocks++;
    ents.push({ id: `block_${bi}`, type: 'wall', position: { x: 0, y: 0 }, visual: { kind: 'procedural', style: 'cliff', material: '@ground' }, collision: { shape: { kind: 'convex', points: hull }, surface: 'normal', material: 'stone', safe: true }, tags: ['legacy:block'] });
  });

  // ── top-level records ───────────────────────────────────────────────────────────────────────────────────────────
  for (const n of root.kids) {
    if (n.tag === 'start') {
      const p = nums(n.lines[1] ?? '');
      doc.spawn = { id: 'spawn', position: pt(p[0] ?? 0, p[2] ?? 0) };
    } else if (n.tag === 'region') {
      stats.regions++;
      const a = nums(n.lines[1] ?? ''), b = nums(n.lines[2] ?? ''), name = n.lines[3] ?? `region_${stats.regions}`;
      const x0 = Math.min(a[0], b[0]), x1 = Math.max(a[0], b[0]), z0 = Math.min(a[2], b[2]), z1 = Math.max(a[2], b[2]);
      const cx = m((x0 + x1) / 2), cy = m((z0 + z1) / 2), w = m(x1 - x0), h = m(z1 - z0);
      const cp = /^CP_(\d+)$/i.exec(name);
      if (cp) { const ord = Number(cp[1]); checkpoints.push({ id: `cp${ord}`, order: ord, name: o.splitNames?.[ord], region: { kind: 'box', x: cx, y: cy, w, h }, respawn: { x: cx, y: cy } }); count(stats.mapped, 'region CP_n'); }
      else if (name === 'reg_finish') { finish.push({ id: `finish${finish.length || ''}`, position: { x: cx, y: cy }, shape: { kind: 'box', w, h } }); count(stats.mapped, 'region reg_finish'); }
      else if (name === 'kill') { regions.push({ id: `kill_${regions.length}`, type: 'kill', shape: { kind: 'box', x: cx, y: cy, w, h }, enter: [{ op: 'kill' }] }); count(stats.mapped, 'region kill'); }
      else { count(stats.unmapped, `region ${name}`); issues.push(mkIssue('INFO', 'IMPORT_UNMAPPED', '/regions', `region "${name}" has no V2 meaning (name-coded roles: kill, CP_n, reg_finish)`)); }
    } else if (n.tag === 'path') {
      stats.paths++;
      const name = n.lines[1] ?? `path_${stats.paths}`;
      const cnt = Number(n.lines[2] ?? 0);
      const pts = n.lines.slice(4, 4 + cnt).map(l => { const v = nums(l); return pt(v[0], v[2]); });
      if (name === 'path_progress') { routes.push({ id: 'main', kind: 'main', points: pts }); count(stats.mapped, 'path path_progress'); }
      else { paths.push({ id: slug(name), kind: 'polyline', points: pts }); count(stats.mapped, 'path (motion)'); }
    } else if (n.tag === 'model') {
      stats.models++;
      const L = n.lines;
      const code = Number(L[0]);
      const pos = nums(L[1] ?? ''), ang = nums(L[2] ?? ''), sc = nums(L[3] ?? '');
      const file = (L[4] ?? '').toLowerCase(), name = L[5] ?? `model_${stats.models}`, action = L[6] ?? 'ndef';
      const skills = nums(L[7] ?? '');
      const bits = flagBits(L[8] ?? '');
      const flag = (n1: number): boolean => ((bits >>> (n1 - 1)) & 1) === 1;
      const invisible = (bits & 256) !== 0, passable = (bits & 512) !== 0;
      const sk = (i: number, d = 0): number => (skills[i - 1] ? skills[i - 1] : d);
      const position = { x: m(pos[0] ?? 0), y: m(pos[2] ?? 0), z: m(pos[1] ?? 0) };
      const size = o.modelSizes?.[file] ?? [2, 2];
      const w = size[0] * (sc[0] ?? 1), h = size[1] * (sc[2] ?? sc[0] ?? 1);
      const id = slug(`${name}_${stats.models}`);
      const box = { shape: { kind: 'box' as const, w, h }, surface: flag(7) ? ('slippery' as const) : ('normal' as const), material: 'stone', safe: true };
      const base: MapEntity = { id, type: 'decor', position, rotation: ang[2] ? (ang[2] % 360) : 0, tags: [`legacy:${action}`, `legacy:model:${file}`] };
      const addMapped = (e: MapEntity, key: string) => { ents.push(e); count(stats.mapped, key); };
      if (action === 'spawn_act') { doc.spawn = { id: 'spawn', position: { x: position.x, y: position.y } }; count(stats.mapped, 'spawn_act'); continue; }
      if (name === 'startLine' || name === 'finishLine') { addMapped({ ...base, type: 'marker', visual: { kind: 'procedural', style: name === 'startLine' ? 'start_line' : 'finish_line' } }, 'start/finish marker'); continue; }
      switch (action) {
        case 'moveSine_act': {
          const speed = (i: number): number => sk(i, 5);
          const per = (i: number): number => 360 / (speed(i) * T_PER_SEC);
          const beh: BehaviorDef = { type: 'move', mode: 'sine' };
          if (sk(1)) beh.x = { amplitude: m(sk(1)), period: per(3), phase: sk(5) / 360 };
          if (sk(2)) beh.y = { amplitude: m(sk(2)), period: per(4), phase: sk(6) / 360 };
          if (!beh.x && !beh.y) issues.push(mkIssue('INFO', 'IMPORT_UNMAPPED', `/entities/${id}`, `moving block "${name}" has zero amplitude`));
          addMapped({ ...base, type: 'moving', visual: { kind: 'procedural', style: 'wood', material: '@secondary' }, collision: { ...box, safe: false }, behavior: beh }, 'moveSine_act'); break;
        }
        case 'map3Wheel_act': {
          const speed = sk(1, 1.5);
          const beh: BehaviorDef = flag(3) ? { type: 'rotate', mode: 'continuous', speed: speed * T_PER_SEC }
            : flag(4) ? { type: 'rotate', mode: 'sine', base: sk(2), amplitude: sk(3, 45), period: 360 / (speed * T_PER_SEC) }
            : { type: 'rotate', mode: 'free', initialSpeed: 0, damping: 0.9 };
          addMapped({ ...base, type: 'hazard', visual: { kind: 'procedural', style: 'blade', material: '@secondary' }, collision: { ...box, hazard: true, safe: false }, behavior: beh }, 'map3Wheel_act'); break;
        }
        case 'toggleBlock_act': {
          const beh: BehaviorDef = { type: 'toggle', channel: flag(3) ? 'boosts' : 'jumps', modulus: 2, active: [flag(4) ? 1 : 0], inactive: { collision: false, visual: flag(5) ? 'hidden' : 'ghost' } };
          addMapped({ ...base, type: 'interactive', visual: { kind: 'procedural', style: 'toggle', material: '@secondary' }, collision: { ...box, safe: false }, behavior: beh }, 'toggleBlock_act'); break;
        }
        case 'toggleSine_act': {
          const beh: BehaviorDef = { type: 'timed', period: 360 / (sk(1, 5) * T_PER_SEC), phase: sk(2) / 360, duty: 0.5, inactive: { collision: false, visual: flag(5) ? 'hidden' : 'ghost' } };
          addMapped({ ...base, type: 'interactive', visual: { kind: 'procedural', style: 'timed', material: '@secondary' }, collision: { ...box, safe: false }, behavior: beh }, 'toggleSine_act'); break;
        }
        case 'boostjuice_act': addMapped({ ...base, type: 'interactive', collision: { shape: box.shape, trigger: true }, behavior: { type: 'boostZone', kind: 'powerJump' } }, 'boostjuice_act'); break;
        case 'monolithThorn_act': addMapped({ ...base, type: 'hazard', visual: { kind: 'procedural', style: 'thorns' }, collision: { ...box, hazard: true, safe: false } }, 'monolithThorn_act'); break;
        case 'bgObject_act': addMapped({ ...base, type: 'background', visual: { kind: 'procedural', style: 'mountain', layer: 'far', parallax: { x: sk(1, 1), y: sk(2, 1) }, instancing: true } }, 'bgObject_act'); break;
        default: {
          if (invisible && !passable) { addMapped({ ...base, type: 'platform', collision: box }, 'collision entity'); break; }       // INVISIBLE + solid = collision proxy
          if (code === 3 || passable) { count(stats.unmapped, `decor ${action}`); ents.push({ ...base, visual: { kind: 'procedural', style: 'flowers', layer: 'mid' } }); break; }
          count(stats.unmapped, action); ents.push({ ...base, collision: box });
          issues.push(mkIssue('INFO', 'IMPORT_UNMAPPED', `/entities/${id}`, `action "${action}" has no V2 equivalent; imported as a tagged placeholder`));
        }
      }
    }
  }
  doc.entities = ents;
  doc.regions = regions;
  doc.paths = paths;
  doc.checkpoints = checkpoints.sort((a, b) => a.order - b.order);
  doc.finish = { zones: finish };
  doc.progress = { routes };
  doc.manifest.checkpointCount = checkpoints.length;
  if (o.splitNames) doc.splits = { splits: o.splitNames.slice(0, checkpoints.length).map((n, i) => ({ id: `s${i}`, name: n, checkpoint: checkpoints[i]?.id ?? `cp${i}` })).filter(s => checkpoints.some(c => c.id === s.checkpoint)), targets: { gold: 0, silver: 0, bronze: 0 } };
  // bounds from everything imported (+ margin); kill plane below
  let minX = Infinity, maxX = -Infinity, minY = Infinity, maxY = -Infinity;
  const grow = (p: Vec2) => { minX = Math.min(minX, p.x); maxX = Math.max(maxX, p.x); minY = Math.min(minY, p.y); maxY = Math.max(maxY, p.y); };
  for (const e of ents) { grow(e.position); const c = e.collision && !Array.isArray(e.collision) ? e.collision : null; if (c?.shape.kind === 'convex') c.shape.points.forEach(grow); }
  for (const r of routes) r.points.forEach(grow);
  if (doc.spawn) grow(doc.spawn.position);
  if (Number.isFinite(minX)) { doc.world.bounds = { minX: minX - 20, maxX: maxX + 20, minY: minY - 20, maxY: maxY + 20 }; doc.world.killY = minY - 40; doc.manifest.mapSize = { width: maxX - minX, height: maxY - minY }; }
  if (!doc.spawn) issues.push(mkIssue('WARNING', 'IMPORT_SPAWN', '/spawn', 'no start/spawn found: set the spawn manually'));
  issues.push(mkIssue('INFO', 'IMPORT_NOTE', '', 'models, textures, shaders and materials are not converted; model entities carry placeholder collision boxes (see tags legacy:model:*)'));
  return { doc, report: { issues, stats } };
}
