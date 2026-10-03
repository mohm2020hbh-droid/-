import * as THREE from 'three';
import type { WorldTheme } from '../../data/worlds';
import type { StyleMaterials } from '../materials';
import { type Rng, fbm3, mulberry32, noise3, pick, range } from '../noise';
import { blobGeometry, col, lerpColor, merge, mesh, paint, solid, xf } from '../geom';
import { autumnTree, pineTree } from './trees';
import { bush, flower, mushroom } from './rock';

const C = (h: string): THREE.Color => col(h);

/** Floating island: lumpy top disc with trees/props over a tapering rock underside with spikes. */
export function buildIsland(rng: Rng, theme: WorldTheme, radius: number, thick: number, seed: number, opts: { trees?: number; detail?: number; props?: boolean } = {}): THREE.BufferGeometry {
  const parts: THREE.BufferGeometry[] = [];
  const body = new THREE.CylinderGeometry(radius, radius * 0.22, thick, 10, 3);
  const bp = body.getAttribute('position') as THREE.BufferAttribute;
  for (let i = 0; i < bp.count; i++) {
    const x = bp.getX(i), y = bp.getY(i), z = bp.getZ(i);
    const n = noise3(x * 0.3, y * 0.3, z * 0.3, seed), n2 = noise3(x * 0.3 + 4, y * 0.3, z * 0.3, seed + 2);
    const k = y > thick / 2 - 0.01 ? 0.06 : 0.22;
    bp.setXYZ(i, x * (1 + (n - 0.5) * k * 2), y - (y < -thick / 2 + 0.01 ? n2 * thick * 0.35 : 0) - thick / 2, z * (1 + (n2 - 0.5) * k * 2));
  }
  const rl = C(theme.terrain.rockLight), rm = C(theme.terrain.rockMid), rd = C(theme.terrain.rockDark);
  paint(body, (x, y, z, nx, ny) => {
    const t = Math.max(0, Math.min(1, (y + thick) / thick));
    const band = Math.sin(y * 1.3 + fbm3(x * 0.2, y * 0.3, z * 0.2, seed) * 5);
    const c = lerpColor(rd, rm, Math.pow(t, 0.8));
    if (band > 0.5) c.lerp(rl, 0.3);
    return c.multiplyScalar(0.95 + 0.1 * noise3(x, y, z, seed));
  });
  parts.push(body);
  const capT = 0.7;
  const cap = new THREE.CylinderGeometry(radius * 1.03, radius * 0.98, capT, 12, 1);
  const cp = cap.getAttribute('position') as THREE.BufferAttribute;
  for (let i = 0; i < cp.count; i++) {
    const x = cp.getX(i), y = cp.getY(i), z = cp.getZ(i);
    const n = noise3(x * 0.35, 0, z * 0.35, seed + 9);
    cp.setXYZ(i, x * (1 + (n - 0.5) * 0.08), y > 0 ? y - capT / 2 + 0.35 * (0.2 + n * 0.5) * (Math.hypot(x, z) < radius * 0.2 ? 1.6 : 0.4) : y - capT / 2 - n * 0.45, z * (1 + (n - 0.5) * 0.08));
  }
  const A = C(theme.terrain.capA), B = C(theme.terrain.capB), D = C(theme.terrain.capDark), S = C(theme.terrain.soil);
  paint(cap, (x, y, z, nx, ny) => (ny > 0.5 ? lerpColor(B, A, fbm3(x * 0.25, 0, z * 0.25, seed + 3, 2) * 1.3) : lerpColor(S, D, 0.5 + 0.5 * Math.max(0, Math.min(1, (y + capT) / capT)))));
  parts.push(cap);
  for (let i = 0; i < 3; i++) {
    const len = range(rng, 1.6, 4) * Math.min(1.6, radius / 6);
    const sp = new THREE.ConeGeometry(range(rng, 0.5, 1.2) * Math.min(1.5, radius / 7), len, 5, 1);
    xf(sp, 0, 0, 0, Math.PI, rng() * 6, 0);
    xf(sp, range(rng, -radius * 0.25, radius * 0.25), -thick - len * 0.3, range(rng, -radius * 0.2, radius * 0.2));
    paint(sp, () => rd.clone().lerp(rm, rng() * 0.3));
    parts.push(sp);
  }
  const n = opts.trees ?? Math.round(radius * 0.9);
  const detail = opts.detail ?? 0;
  const autumn = theme.scatter.autumn, pines = theme.scatter.pines;
  for (let i = 0; i < n; i++) {
    const a = rng() * 6.283, r = Math.sqrt(rng()) * radius * 0.82;
    const h = range(rng, 3.4, 6.8) * Math.min(1.6, 0.6 + radius / 10);
    const kind = rng() * (autumn + pines + 0.0001) < autumn ? 'autumn' : 'pine';
    const t = kind === 'autumn' ? autumnTree(rng, theme, h, detail) : pineTree(rng, theme, h, theme.capStyle === 'snow');
    const tm = new THREE.Matrix4().makeTranslation(Math.cos(a) * r, 0.35, Math.sin(a) * r * 0.8);
    t.forEach(g => { g.applyMatrix4(tm); parts.push(g); });
  }
  if (opts.props !== false) {
    const nb = Math.round(radius * 0.5);
    for (let i = 0; i < nb; i++) {
      const a = rng() * 6.283, r = Math.sqrt(rng()) * radius * 0.85;
      const px = Math.cos(a) * r, pz = Math.sin(a) * r * 0.8;
      const pickk = rng();
      const geoms = pickk < 0.45 ? bush(rng, theme, px, pz, range(rng, 0.6, 1.1), seed + i) : pickk < 0.8 ? flower(rng, px, pz, ['#ffffff', '#ffd1e0', '#fff0a0'], 1.4) : mushroom(rng, px, pz, 1.4);
      geoms.forEach(g => { xf(g, 0, 0.35, 0); parts.push(g); });
    }
  }
  return merge(parts);
}

/** Tall rock pillar rising from the cloud sea, grass top with trees. */
export function buildPillar(rng: Rng, theme: WorldTheme, radius: number, height: number, seed: number): THREE.BufferGeometry {
  const parts: THREE.BufferGeometry[] = [];
  const g = new THREE.CylinderGeometry(radius * 0.92, radius * 1.35, height, 9, Math.max(4, Math.round(height / 5)));
  const p = g.getAttribute('position') as THREE.BufferAttribute;
  for (let i = 0; i < p.count; i++) {
    const x = p.getX(i), y = p.getY(i), z = p.getZ(i);
    const n = noise3(x * 0.2, y * 0.15, z * 0.2, seed), n2 = noise3(x * 0.2 + 5, y * 0.15, z * 0.2, seed + 1);
    p.setXYZ(i, x * (1 + (n - 0.5) * 0.35), y - height / 2, z * (1 + (n2 - 0.5) * 0.35));
  }
  const rl = C(theme.terrain.rockLight), rm = C(theme.terrain.rockMid), rd = C(theme.terrain.rockDark), moss = C(theme.terrain.capB);
  paint(g, (x, y, z, nx, ny) => {
    const t = Math.max(0, Math.min(1, (y + height) / height));
    const band = Math.sin(y * 0.8 + fbm3(x * 0.2, y * 0.2, z * 0.2, seed) * 6);
    const c = lerpColor(rd, rm, 0.2 + 0.7 * Math.pow(t, 0.6));
    if (band > 0.5) c.lerp(rl, 0.25);
    if (ny > 0.5) return lerpColor(C(theme.terrain.capDark), moss, fbm3(x * 0.3, 0, z * 0.3, seed));
    if (noise3(x * 0.4, y * 0.4, z * 0.4, seed + 6) > 0.78 && t > 0.5) c.lerp(moss, 0.6);
    return c;
  });
  parts.push(g);
  const n = Math.round(radius * 0.8);
  for (let i = 0; i < n; i++) {
    const a = rng() * 6.283, r = Math.sqrt(rng()) * radius * 0.75, h = range(rng, 3.5, 7);
    const kind = rng() * (theme.scatter.autumn + theme.scatter.pines + 0.001) < theme.scatter.autumn;
    const t = kind ? autumnTree(rng, theme, h, 0) : pineTree(rng, theme, h, theme.capStyle === 'snow');
    const tm = new THREE.Matrix4().makeTranslation(Math.cos(a) * r, 0.1, Math.sin(a) * r);
    t.forEach(gg => { gg.applyMatrix4(tm); parts.push(gg); });
  }
  return merge(parts);
}

/** A storybook castle: keep, towers with red cone roofs, curtain walls, lit windows. */
export function buildCastle(rng: Rng, theme: WorldTheme): { body: THREE.BufferGeometry; glow: THREE.BufferGeometry } {
  const parts: THREE.BufferGeometry[] = [];
  const wins: THREE.BufferGeometry[] = [];
  const stone = C('#d9ccb8'), stoneD = C('#a99a8c'), roof = C('#b9432f'), roofD = C('#7c2d28');
  const hill = blobGeometry(18, 1, 0.22, (x, y, z) => noise3(x, y, z, 4));
  xf(hill, 0, -9, 0, 0, 0, 0, 1.5, 0.8, 1.1);
  paint(hill, (_x, y) => lerpColor(C(theme.terrain.rockDark), C(theme.terrain.capB), Math.max(0, Math.min(1, (y + 14) / 12))), true);
  parts.push(hill);
  const keep = new THREE.BoxGeometry(7, 12, 6); xf(keep, 0, 6, 0);
  paint(keep, (_x, y, _z, nx, ny, nz) => lerpColor(stoneD, stone, 0.5 + 0.5 * Math.abs(nz)));
  parts.push(keep);
  const keepRoof = new THREE.ConeGeometry(5.6, 6, 4, 1); xf(keepRoof, 0, 15, 0, 0, Math.PI / 4, 0);
  paint(keepRoof, (_x, y) => lerpColor(roofD, roof, Math.min(1, (y - 12) / 6)));
  parts.push(keepRoof);
  const towers: [number, number, number, number][] = [[-9, 0, 2.1, 14], [9, 0, 2.1, 14], [-5, 5, 1.7, 10], [5, 5, 1.7, 10], [0, -6, 1.9, 12]];
  for (const [x, z, r, h] of towers) {
    const t = new THREE.CylinderGeometry(r, r * 1.08, h, 8, 1); xf(t, x, h / 2, z);
    paint(t, (_x, _y, _z, nx) => lerpColor(stoneD, stone, 0.5 + 0.5 * nx));
    parts.push(t);
    const rf = new THREE.ConeGeometry(r * 1.45, h * 0.5, 8, 1); xf(rf, x, h + h * 0.25, z);
    paint(rf, (_x, y) => lerpColor(roofD, roof, Math.min(1, (y - h) / (h * 0.5))));
    parts.push(rf);
    const flag = new THREE.BoxGeometry(1.6, 0.8, 0.1); xf(flag, x + 0.9, h + h * 0.5 + 0.8, z); solid(flag, '#f4b63a'); parts.push(flag);
    const pole = new THREE.CylinderGeometry(0.06, 0.06, 2, 4); xf(pole, x, h + h * 0.5 + 0.6, z); solid(pole, '#4a3a30'); parts.push(pole);
    for (let k = 0; k < 2; k++) { const w = new THREE.BoxGeometry(0.5, 0.9, 0.2); xf(w, x + (k ? 0.5 : -0.5) * 0, h * (0.4 + k * 0.25), z + r + 0.02); solid(w, '#ffd37a'); wins.push(w); }
  }
  for (const [x1, x2, z] of [[-9, 9, 0], [-5, 5, 5]] as [number, number, number][]) {
    const wall = new THREE.BoxGeometry(x2 - x1, 5, 1.2); xf(wall, (x1 + x2) / 2, 2.5, z);
    paint(wall, (_x, _y, _z, nx, ny, nz) => lerpColor(stoneD, stone, 0.4 + 0.5 * Math.abs(nz)));
    parts.push(wall);
    for (let i = 0; i <= (x2 - x1) / 1.4; i++) { const m = new THREE.BoxGeometry(0.7, 0.8, 1.3); xf(m, x1 + i * 1.4, 5.4, z); solid(m, stone); parts.push(m); }
  }
  for (let k = 0; k < 6; k++) { const w = new THREE.BoxGeometry(0.7, 1.2, 0.2); xf(w, -2.4 + (k % 3) * 2.4, 5 + Math.floor(k / 3) * 4, 3.02); solid(w, '#ffd37a'); wins.push(w); }
  return { body: merge(parts), glow: merge(wins) };
}

/** Stone viaduct with N arches (extruded shape with arch holes). */
export function buildArchBridge(theme: WorldTheme, arches = 5): THREE.BufferGeometry {
  const L = arches * 10, H = 12, deck = 3;
  const s = new THREE.Shape();
  s.moveTo(0, 0); s.lineTo(0, H); s.lineTo(L, H); s.lineTo(L, 0);
  for (let i = arches - 1; i >= 0; i--) {
    const x0 = i * 10 + 1.2, x1 = (i + 1) * 10 - 1.2;
    s.lineTo(x1, 0);
    const r = (x1 - x0) / 2;
    s.absarc((x0 + x1) / 2, 0, r, 0, Math.PI, false);
    s.lineTo(x0, 0);
  }
  s.closePath();
  const g = new THREE.ExtrudeGeometry(s, { depth: 3, bevelEnabled: false, curveSegments: 8 });
  xf(g, -L / 2, 0, -1.5);
  const stone = C(theme.terrain.stone), dark = C(theme.terrain.rockMid), moss = C(theme.terrain.capB);
  paint(g, (x, y, z, nx, ny, nz) => {
    const c = lerpColor(dark, stone, 0.45 + 0.5 * Math.abs(nz) + (noise3(x * 0.8, y * 0.8, z, 2) - 0.5) * 0.4);
    if (ny > 0.5) c.lerp(moss, 0.55);
    return c;
  });
  const parts: THREE.BufferGeometry[] = [g];
  for (let i = 0; i <= arches; i++) { const p = new THREE.BoxGeometry(1.6, H - deck + 1, 3.4); xf(p, -L / 2 + i * 10, (H - deck) / 2 + 0.5, 0); paint(p, () => dark.clone().lerp(stone, 0.4)); parts.push(p); }
  for (let i = 0; i < arches * 5; i++) { const b = new THREE.BoxGeometry(1.4, 0.8, 0.5); xf(b, -L / 2 + 1 + i * 2, H + 0.4, 1.5); solid(b, stone); parts.push(b); }
  return merge(parts);
}

/** Rope-and-plank bridge between two points (visible in the mid-ground, like the reference). */
export function buildWoodBridge(theme: WorldTheme, length = 14, sag = 0.9, depth = 2.4): THREE.BufferGeometry {
  const parts: THREE.BufferGeometry[] = [];
  const wood = C(theme.terrain.wood), dark = C(theme.terrain.woodDark);
  const n = Math.round(length / 0.7);
  const path = (t: number): [number, number] => [(t - 0.5) * length, -Math.sin(t * Math.PI) * sag];
  for (let i = 0; i <= n; i++) {
    const [x, y] = path(i / n), [x2, y2] = path(Math.min(1, (i + 0.5) / n));
    const g = new THREE.BoxGeometry(0.6, 0.12, depth); xf(g, x, y, 0, 0, 0, Math.atan2(y2 - y, x2 - x)); solid(g, lerpColor(wood, dark, ((i * 37) % 10) / 24)); parts.push(g);
  }
  for (const sz of [-1, 1]) {
    for (let i = 0; i < n; i++) {
      const [x, y] = path(i / n), [x2, y2] = path((i + 1) / n);
      const len = Math.hypot(x2 - x, y2 - y);
      const r = new THREE.CylinderGeometry(0.035, 0.035, len + 0.05, 4); xf(r, (x + x2) / 2, (y + y2) / 2 + 0.95, sz * depth / 2, 0, 0, Math.PI / 2 + Math.atan2(y2 - y, x2 - x)); solid(r, '#cdb58a'); parts.push(r);
      if (i % 3 === 0) { const p = new THREE.CylinderGeometry(0.06, 0.07, 1.0, 5); xf(p, x, y + 0.48, sz * depth / 2); solid(p, dark); parts.push(p); }
    }
  }
  for (const sx of [-1, 1]) { const p = new THREE.BoxGeometry(0.4, 2.2, 0.4); xf(p, sx * length / 2, 0.5, 0); solid(p, dark); parts.push(p); }
  return merge(parts);
}

export interface WaterfallView { group: THREE.Group; update(t: number): void }

const waterVert = `varying vec2 vUv; varying float vFogDepth;
void main(){ vUv = uv; vec4 mv = modelViewMatrix * vec4(position,1.0); vFogDepth = -mv.z; gl_Position = projectionMatrix * mv; }`;
const waterFrag = `uniform float uTime; uniform vec3 uColor; uniform vec3 uFoam; uniform vec3 uFogColor; uniform float uFogDensity;
varying vec2 vUv; varying float vFogDepth;
float h(vec2 p){ return fract(sin(dot(p, vec2(127.1,311.7))) * 43758.5453); }
void main(){
  float x = vUv.x; float y = vUv.y;
  float streak = 0.0;
  for(int i=0;i<4;i++){ float fi=float(i); float cx = x*(6.0+fi*5.0)+fi*3.1; float id=floor(cx); float f=fract(cx);
    float sp = 0.9+h(vec2(id,fi))*1.3; float v = fract(y*(1.5+fi*0.7) + uTime*sp + h(vec2(id,fi+9.0)));
    streak += smoothstep(0.55,0.9,v) * smoothstep(0.0,0.35,f) * smoothstep(1.0,0.65,f) * (0.35 - fi*0.05); }
  float edge = smoothstep(0.0,0.14,x)*smoothstep(1.0,0.86,x);
  float top = smoothstep(1.0,0.9,y); float foam = smoothstep(0.12,0.0,y) + smoothstep(0.9,1.0,y)*0.6;
  vec3 c = mix(uColor, uFoam, clamp(streak + foam*0.9, 0.0, 1.0));
  float a = (0.78 + streak*0.2) * edge * (0.35 + 0.65*top);
  float fogF = 1.0 - exp(-pow(uFogDensity*vFogDepth, 2.0));
  gl_FragColor = vec4(mix(c, uFogColor, fogF), a);
}`;

/**
 * Cliff with a waterfall. `y` of the placed group = TOP of the cliff; it extends 70 m downward into the cloud sea
 * (so it always stands on something), the water ribbon runs down its front face and ends in mist.
 */
export function buildWaterfall(rng: Rng, theme: WorldTheme, mats: StyleMaterials, scale: number, seed: number): WaterfallView {
  const g = new THREE.Group();
  const W = 14 * scale, H = 70, D = 7 * scale;
  const cliff = new THREE.BoxGeometry(W, H, D, 5, 14, 3);
  const p = cliff.getAttribute('position') as THREE.BufferAttribute;
  for (let i = 0; i < p.count; i++) {
    const x = p.getX(i), y = p.getY(i) - H / 2, z = p.getZ(i);
    const n = noise3(x * 0.3, y * 0.2, z * 0.3, seed), n2 = noise3(x * 0.3 + 9, y * 0.2, z * 0.3, seed + 2);
    p.setXYZ(i, x + (n - 0.5) * 2.2, y + (y > -0.01 ? 0 : (n2 - 0.5) * 1.2), z + (Math.abs(z) > D / 2 - 0.01 ? (n - 0.5) * 2 : 0));
  }
  const rl = C(theme.terrain.rockLight), rm = C(theme.terrain.rockMid), rd = C(theme.terrain.rockDark), moss = C(theme.terrain.capB);
  paint(cliff, (x, y, z, nx, ny, nz) => {
    const band = Math.sin(y * 0.8 + fbm3(x * 0.2, y * 0.2, z * 0.2, seed) * 5);
    const c = lerpColor(rd, rm, 0.4 + 0.45 * band * 0.5 + 0.12);
    if (band > 0.45) c.lerp(rl, 0.45);
    if (nz > 0.5) c.lerp(rl, 0.2);
    if (ny > 0.5) return lerpColor(C(theme.terrain.capDark), moss, fbm3(x * 0.4, 0, z * 0.4, seed));
    if (noise3(x * 0.5, y * 0.3, z * 0.5, seed + 4) > 0.66) c.lerp(moss, 0.65); // moss streaks
    return c.multiplyScalar(0.8 + 0.4 * noise3(x * 1.3, y * 1.3, z * 1.3, seed + 7));
  });
  const parts: THREE.BufferGeometry[] = [cliff];
  for (let i = 0; i < 6; i++) {
    const t = i % 2 ? autumnTree(rng, theme, range(rng, 4, 8) * scale, 0) : pineTree(rng, theme, range(rng, 4, 8) * scale);
    const tm = new THREE.Matrix4().makeTranslation(range(rng, -W * 0.4, W * 0.4), 0.05, range(rng, -D * 0.3, D * 0.3));
    t.forEach(gg => { gg.applyMatrix4(tm); parts.push(gg); });
  }
  const body = mesh(merge(parts), mats.smooth, false, false);
  body.position.y = 0;
  g.add(body);
  const uniforms = {
    uTime: { value: 0 }, uColor: { value: C(theme.water) }, uFoam: { value: C(theme.foam) },
    uFogColor: { value: C(theme.fog.color) }, uFogDensity: { value: theme.fog.density },
  };
  const wh = 46;
  const water = new THREE.Mesh(new THREE.PlaneGeometry(W * 0.22, wh, 1, 1), new THREE.ShaderMaterial({ uniforms, vertexShader: waterVert, fragmentShader: waterFrag, transparent: true, depthWrite: false }));
  water.position.set(W * (rng() - 0.5) * 0.3, -wh / 2 + 0.3, D / 2 + 0.35);
  g.add(water);
  const mist = new THREE.Sprite(new THREE.SpriteMaterial({ map: softPuffTexture(), color: '#ffffff', transparent: true, opacity: 0.55, depthWrite: false }));
  mist.scale.set(W * 0.9, W * 0.5, 1); mist.position.set(water.position.x, -wh + 2, D / 2 + 1);
  g.add(mist);
  return { group: g, update(t) { uniforms.uTime.value = t; mist.material.opacity = 0.5 + Math.sin(t * 1.3) * 0.06; } };
}

let _puff: THREE.Texture | null = null;
export function softPuffTexture(): THREE.Texture {
  if (_puff) return _puff;
  const c = document.createElement('canvas'); c.width = c.height = 256;
  const x = c.getContext('2d')!;
  const rng = mulberry32(99);
  const blobs = 9;
  for (let i = 0; i < blobs; i++) {
    const a = (i / blobs) * 6.283, d = i === 0 ? 0 : 55 + rng() * 25;
    const cx = 128 + Math.cos(a) * d * 0.95, cy = 140 + Math.sin(a) * d * 0.5 - (i === 0 ? 10 : 0), r = i === 0 ? 78 : 42 + rng() * 22;
    const gr = x.createRadialGradient(cx, cy, 0, cx, cy, r);
    gr.addColorStop(0, 'rgba(255,255,255,0.95)'); gr.addColorStop(0.55, 'rgba(255,255,255,0.55)'); gr.addColorStop(1, 'rgba(255,255,255,0)');
    x.fillStyle = gr; x.beginPath(); x.arc(cx, cy, r, 0, 6.283); x.fill();
  }
  // soft shaded belly
  const bg = x.createLinearGradient(0, 100, 0, 220);
  bg.addColorStop(0, 'rgba(120,100,160,0)'); bg.addColorStop(1, 'rgba(120,100,160,0.28)');
  x.globalCompositeOperation = 'source-atop'; x.fillStyle = bg; x.fillRect(0, 0, 256, 256);
  _puff = new THREE.CanvasTexture(c);
  _puff.colorSpace = THREE.SRGBColorSpace;
  return _puff;
}

let _cumulus: THREE.Texture | null = null;
/** Crisp storybook cumulus (defined silhouette, flat belly, lilac underside) — the reference's clouds have outlines, not veils. */
export function cumulusTexture(): THREE.Texture {
  if (_cumulus) return _cumulus;
  const c = document.createElement('canvas'); c.width = 256; c.height = 128;
  const x = c.getContext('2d')!;
  const rng = mulberry32(2024);
  const base = 104;
  const puffs: [number, number, number][] = [];
  for (let i = 0; i < 11; i++) {
    const t = i / 10, cx = 28 + t * 200, mound = Math.sin(t * Math.PI);
    const r = 20 + mound * 30 * (0.7 + rng() * 0.5);
    puffs.push([cx, base - r * 0.62 - mound * 10 * rng(), r]);
  }
  puffs.push([128, base - 52, 44]);
  x.fillStyle = '#ffffff';
  for (const [cx, cy, r] of puffs) { x.beginPath(); x.arc(cx, cy, r, 0, 6.283); x.fill(); }
  // belly shade + sun-lit top, clipped to the silhouette
  x.globalCompositeOperation = 'source-atop';
  const g = x.createLinearGradient(0, 20, 0, base + 6);
  g.addColorStop(0, 'rgba(255,248,240,0)'); g.addColorStop(0.55, 'rgba(200,196,230,0.35)'); g.addColorStop(1, 'rgba(140,132,196,0.75)');
  x.fillStyle = g; x.fillRect(0, 0, 256, 128);
  // flat bottom: cut everything below the base line with a short fade
  x.globalCompositeOperation = 'destination-out';
  const cut = x.createLinearGradient(0, base - 4, 0, base + 10);
  cut.addColorStop(0, 'rgba(0,0,0,0)'); cut.addColorStop(1, 'rgba(0,0,0,1)');
  x.fillStyle = cut; x.fillRect(0, base - 4, 256, 128);
  _cumulus = new THREE.CanvasTexture(c);
  _cumulus.colorSpace = THREE.SRGBColorSpace;
  return _cumulus;
}

export { pick };
