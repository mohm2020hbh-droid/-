import * as THREE from 'three';
import { type Rng, mulberry32, range } from './noise';

/**
 * Vfx — pooled billboard particles (Phase 13): one InstancedMesh, one draw call, zero allocation in the hot path.
 * Types: 0 soft puff · 1 ring · 2 star · 3 confetti square · 4 streak · 5 leaf/diamond · 6 snow flake
 */
export type FxKind = 'dust' | 'ring' | 'sparkle' | 'ice' | 'goo' | 'boost' | 'confetti' | 'hazard' | 'speed' | 'debris' | 'charge';

const vert = `
attribute vec3 iPos; attribute vec4 iCol; attribute vec3 iData; // size, rot, type
varying vec4 vCol; varying vec2 vUv; varying float vType; varying float vFog;
void main(){
  vec4 mv = viewMatrix * vec4(iPos, 1.0);
  float s = iData.x; float c = cos(iData.y), sn = sin(iData.y);
  vec2 p = vec2(position.x*c - position.y*sn, position.x*sn + position.y*c) * s;
  if (iData.z > 3.5 && iData.z < 4.5) p = vec2(position.x * s * 0.16, position.y * s * 1.6);
  mv.xy += p;
  vFog = -mv.z;
  gl_Position = projectionMatrix * mv;
  vCol = iCol; vUv = position.xy + 0.5; vType = iData.z;
}`;
const frag = `
varying vec4 vCol; varying vec2 vUv; varying float vType; varying float vFog;
uniform vec3 uFogColor; uniform float uFogDensity;
void main(){
  vec2 q = vUv - 0.5; float r = length(q); float a = 0.0;
  if (vType < 0.5) a = smoothstep(0.5, 0.05, r);
  else if (vType < 1.5) a = smoothstep(0.5, 0.42, r) * smoothstep(0.24, 0.40, r);
  else if (vType < 2.5) { float k = min(abs(q.x), abs(q.y)); a = smoothstep(0.5, 0.0, r) * smoothstep(0.1, 0.0, k) + smoothstep(0.18, 0.0, r); }
  else if (vType < 3.5) a = 1.0 - step(0.5, max(abs(q.x), abs(q.y)) * 1.05);
  else if (vType < 4.5) a = smoothstep(0.5, 0.2, abs(q.y)) * smoothstep(0.5, 0.0, abs(q.x) * 4.0);
  else if (vType < 5.5) a = 1.0 - smoothstep(0.38, 0.5, abs(q.x) * 1.3 + abs(q.y));
  else a = smoothstep(0.5, 0.2, r);
  a *= vCol.a;
  if (a < 0.01) discard;
  vec3 col = vCol.rgb;
  float f = 1.0 - exp(-pow(uFogDensity * vFog, 2.0));
  gl_FragColor = vec4(mix(col, uFogColor, f * 0.6), a);
}`;

interface P {
  active: boolean; x: number; y: number; z: number; vx: number; vy: number; vz: number; life: number; max: number;
  s0: number; s1: number; rot: number; spin: number; type: number; grav: number; drag: number;
  r0: number; g0: number; b0: number; r1: number; g1: number; b1: number; a0: number; a1: number;
}

export class Vfx {
  readonly mesh: THREE.InstancedMesh;
  private readonly ps: P[] = [];
  private readonly pos: THREE.InstancedBufferAttribute;
  private readonly colA: THREE.InstancedBufferAttribute;
  private readonly data: THREE.InstancedBufferAttribute;
  private cursor = 0;
  private rng: Rng = mulberry32(1234);
  private density = 1;
  readonly material: THREE.ShaderMaterial;

  constructor(readonly capacity = 320, fogColor = '#b9bde2', fogDensity = 0.004) {
    const g = new THREE.InstancedBufferGeometry();
    const base = new THREE.PlaneGeometry(1, 1);
    g.index = base.index; g.setAttribute('position', base.getAttribute('position'));
    this.pos = new THREE.InstancedBufferAttribute(new Float32Array(capacity * 3), 3).setUsage(THREE.DynamicDrawUsage);
    this.colA = new THREE.InstancedBufferAttribute(new Float32Array(capacity * 4), 4).setUsage(THREE.DynamicDrawUsage);
    this.data = new THREE.InstancedBufferAttribute(new Float32Array(capacity * 3), 3).setUsage(THREE.DynamicDrawUsage);
    g.setAttribute('iPos', this.pos); g.setAttribute('iCol', this.colA); g.setAttribute('iData', this.data);
    g.instanceCount = capacity;
    this.material = new THREE.ShaderMaterial({
      vertexShader: vert, fragmentShader: frag, transparent: true, depthWrite: false,
      uniforms: { uFogColor: { value: new THREE.Color(fogColor) }, uFogDensity: { value: fogDensity } },
    });
    this.mesh = new THREE.InstancedMesh(g, this.material, capacity);
    (this.mesh as unknown as { geometry: THREE.BufferGeometry }).geometry = g;
    this.mesh.frustumCulled = false;
    this.mesh.renderOrder = 10;
    for (let i = 0; i < capacity; i++) this.ps.push({ active: false, x: 0, y: 0, z: 0, vx: 0, vy: 0, vz: 0, life: 0, max: 1, s0: 0, s1: 0, rot: 0, spin: 0, type: 0, grav: 0, drag: 0, r0: 1, g0: 1, b0: 1, r1: 1, g1: 1, b1: 1, a0: 1, a1: 0 });
  }

  setDensity(d: number): void { this.density = d; }
  setFog(color: THREE.Color, density: number): void { this.material.uniforms.uFogColor.value.copy(color); this.material.uniforms.uFogDensity.value = density; }

  private spawn(): P {
    for (let n = 0; n < this.capacity; n++) {
      const p = this.ps[this.cursor];
      this.cursor = (this.cursor + 1) % this.capacity;
      if (!p.active) return p;
    }
    return this.ps[this.cursor++ % this.capacity]; // recycle
  }

  private emit(x: number, y: number, z: number, vx: number, vy: number, vz: number, life: number, s0: number, s1: number, type: number, c0: THREE.Color, c1: THREE.Color, a0 = 0.9, a1 = 0, grav = 0, drag = 1, spin = 0): void {
    const p = this.spawn();
    p.active = true; p.x = x; p.y = y; p.z = z; p.vx = vx; p.vy = vy; p.vz = vz; p.life = 0; p.max = life;
    p.s0 = s0; p.s1 = s1; p.rot = this.rng() * 6.283; p.spin = spin; p.type = type; p.grav = grav; p.drag = drag;
    p.r0 = c0.r; p.g0 = c0.g; p.b0 = c0.b; p.r1 = c1.r; p.g1 = c1.g; p.b1 = c1.b; p.a0 = a0; p.a1 = a1;
  }

  private static C = new THREE.Color();
  private static D = new THREE.Color();

  /** `nx,ny` = surface normal (puffs spray along the tangent), `power` 0..1, `tint` base colour. */
  burst(kind: FxKind, x: number, y: number, nx: number, ny: number, power: number, tint = '#e8d6b8', tint2 = '#ffffff'): void {
    const r = this.rng, d = this.density;
    const t0 = Vfx.C.set(tint), t1 = Vfx.D.set(tint2);
    const z = 0.9;
    const tx = ny, ty = -nx;
    switch (kind) {
      case 'dust': {
        const n = Math.round((5 + power * 9) * d);
        for (let i = 0; i < n; i++) {
          const side = i % 2 ? 1 : -1, sp = range(r, 1.2, 3.6 + power * 4);
          this.emit(x + side * range(r, 0, 0.3), y + 0.1, z + range(r, -0.5, 0.5), tx * side * sp + nx * range(r, 0, 1.2), ty * side * sp * 0.2 + ny * range(r, 0.4, 1.8) + 0.3, range(r, -0.3, 0.6), range(r, 0.45, 0.85), range(r, 0.25, 0.45), range(r, 0.8, 1.5) + power * 0.7, 0, t0.clone().lerp(t1, 0.15), t1, 0.62, 0, -0.8, 2.2, range(r, -1, 1));
        }
        break;
      }
      case 'debris': {
        const n = Math.round((3 + power * 6) * d);
        for (let i = 0; i < n; i++) this.emit(x, y + 0.1, z, range(r, -5, 5), range(r, 2, 7), range(r, -1, 1), range(r, 0.5, 0.9), range(r, 0.12, 0.22), 0.05, 5, t0, t0, 1, 0.2, -22, 0.2, range(r, -8, 8));
        break;
      }
      case 'ring':
        this.emit(x, y + 0.15, z, 0, 0, 0, 0.42, 0.6, 3.2 + power * 1.6, 1, t0, t1, 0.85, 0, 0, 1);
        break;
      case 'sparkle': {
        const n = Math.round((7 + power * 8) * d);
        for (let i = 0; i < n; i++) { const a = r() * 6.283, sp = range(r, 2, 6 + power * 4); this.emit(x, y + 0.3, z, Math.cos(a) * sp, Math.sin(a) * sp + 2, 0, range(r, 0.5, 0.9), range(r, 0.22, 0.4), 0.05, 2, t0, t1, 1, 0, -7, 1.2, range(r, -5, 5)); }
        break;
      }
      case 'ice': {
        const n = Math.round(9 * d);
        for (let i = 0; i < n; i++) { const a = r() * Math.PI; this.emit(x, y + 0.1, z, Math.cos(a) * range(r, -4, 4), range(r, 1.5, 5), 0, range(r, 0.45, 0.8), range(r, 0.14, 0.26), 0.05, 2, Vfx.C.set('#bfeaff'), Vfx.D.set('#ffffff'), 1, 0, -14, 0.6, range(r, -9, 9)); }
        break;
      }
      case 'goo': {
        const n = Math.round(8 * d);
        for (let i = 0; i < n; i++) this.emit(x, y + 0.1, z, range(r, -3, 3), range(r, 1.5, 5), 0, range(r, 0.5, 0.8), range(r, 0.18, 0.3), 0.08, 0, Vfx.C.set('#88d94a'), Vfx.D.set('#5fa832'), 0.9, 0, -16, 0.8);
        break;
      }
      case 'hazard': {
        const n = Math.round(14 * d);
        for (let i = 0; i < n; i++) { const a = r() * 6.283, sp = range(r, 2, 8); this.emit(x, y + 0.5, z, Math.cos(a) * sp, Math.sin(a) * sp, 0, range(r, 0.4, 0.8), range(r, 0.2, 0.4), 0.05, 2, Vfx.C.set('#ff5a4a'), Vfx.D.set('#ffd0a0'), 1, 0, -6, 1.5, range(r, -6, 6)); }
        this.emit(x, y + 0.4, z, 0, 0, 0, 0.35, 0.8, 4, 1, Vfx.C.set('#ff6a5a'), Vfx.D.set('#ffffff'), 0.9, 0);
        break;
      }
      case 'confetti': {
        const cols = ['#ff5a4a', '#ffd24a', '#4cc3ff', '#7be07a', '#c77bff', '#ff9a3a'];
        const n = Math.round(60 * d);
        for (let i = 0; i < n; i++) { const a = -0.2 + r() * Math.PI * 1.4, sp = range(r, 4, 13); const c = Vfx.C.set(cols[i % cols.length]); this.emit(x + range(r, -1, 1), y + 1.5, z + range(r, -1, 1), Math.cos(a) * sp * 0.7, Math.sin(a) * sp, range(r, -2, 2), range(r, 1.4, 2.4), 0.2, 0.2, 3, c, c, 1, 0.6, -9, 0.7, range(r, -12, 12)); }
        break;
      }
      case 'charge': {
        const a = r() * 6.283, rad = range(r, 0.8, 1.6);
        this.emit(x + Math.cos(a) * rad, y + 0.2 + Math.sin(a) * rad * 0.5, z, -Math.cos(a) * 3.6, -Math.sin(a) * 1.6 + 1.2, 0, 0.32, 0.22, 0.03, 2, Vfx.C.set('#ffe27a'), Vfx.D.set('#ffffff'), 0.9, 0, 0, 0.6, 6);
        break;
      }
      default: break;
    }
  }

  /** Continuous boost trail: call every frame while boosting. */
  trail(x: number, y: number, vx: number, vy: number, tint = '#ffb347'): void {
    const r = this.rng;
    const n = Math.max(1, Math.round(2 * this.density));
    for (let i = 0; i < n; i++) {
      this.emit(x + range(r, -0.2, 0.2), y + range(r, -0.2, 0.2), 0.3 + range(r, -0.3, 0.3), -vx * 0.08 + range(r, -0.6, 0.6), -vy * 0.08 + range(r, -0.6, 0.6), 0, range(r, 0.35, 0.6), range(r, 0.5, 0.8), 0.05, i % 2 ? 2 : 0, Vfx.C.set(tint), Vfx.D.set('#ff6a3a'), 0.85, 0, 0, 2, range(r, -6, 6));
    }
  }

  speedLines(camX: number, camY: number, halfW: number, halfH: number, vy: number): void {
    const r = this.rng;
    if (r() > 0.55 * this.density) return;
    const side = r() < 0.5 ? -1 : 1;
    const c = Vfx.C.set('#ffffff');
    this.emit(camX + side * range(r, halfW * 0.55, halfW * 0.95), camY + range(r, -halfH, halfH), 5, 0, -vy * 0.1 - 6, 0, 0.35, 1.2, 1.2, 4, c, c, 0.35, 0, 0, 1);
  }

  /** Ambient drifting leaves / snow / embers around the camera. */
  ambient(camX: number, camY: number, halfW: number, halfH: number, kind: 'leaf' | 'snow' | 'ember', colors: string[]): void {
    const r = this.rng;
    if (r() > 0.12 * this.density) return;
    const c = Vfx.C.set(colors[Math.floor(r() * colors.length)]);
    const x = camX + range(r, -halfW, halfW), y = camY + halfH * 1.05;
    if (kind === 'leaf') this.emit(x, y, range(r, -3, 8), range(r, 0.2, 1.4), range(r, -1.6, -0.8), 0, range(r, 5, 8), 0.28, 0.28, 5, c, c, 0.95, 0.95, -0.2, 0.3, range(r, -3, 3));
    else if (kind === 'snow') this.emit(x, y, range(r, -4, 8), range(r, -0.6, 0.6), range(r, -1.8, -0.9), 0, range(r, 5, 8), 0.12, 0.12, 6, c, c, 0.9, 0.9, 0, 0.1, 0);
    else this.emit(x, camY - halfH, range(r, -2, 6), range(r, -0.6, 0.6), range(r, 1.2, 3), 0, range(r, 3, 6), 0.12, 0.02, 6, Vfx.C.set('#ff8a3a'), Vfx.D.set('#ff3a1a'), 0.95, 0, 0, 0.2, 0);
  }

  update(dt: number): void {
    const P = this.pos.array as Float32Array, Cc = this.colA.array as Float32Array, Dd = this.data.array as Float32Array;
    for (let i = 0; i < this.capacity; i++) {
      const p = this.ps[i];
      if (p.active) {
        p.life += dt;
        if (p.life >= p.max) p.active = false;
        else {
          const k = Math.exp(-p.drag * dt);
          p.vx *= k; p.vz *= k; p.vy = p.vy * k + p.grav * dt;
          p.x += p.vx * dt; p.y += p.vy * dt; p.z += p.vz * dt; p.rot += p.spin * dt;
        }
      }
      if (p.active) {
        const t = p.life / p.max;
        P[i * 3] = p.x; P[i * 3 + 1] = p.y; P[i * 3 + 2] = p.z;
        Cc[i * 4] = p.r0 + (p.r1 - p.r0) * t; Cc[i * 4 + 1] = p.g0 + (p.g1 - p.g0) * t; Cc[i * 4 + 2] = p.b0 + (p.b1 - p.b0) * t;
        Cc[i * 4 + 3] = (p.a0 + (p.a1 - p.a0) * t) * Math.min(1, (1 - t) * 3);
        Dd[i * 3] = p.s0 + (p.s1 - p.s0) * t; Dd[i * 3 + 1] = p.rot; Dd[i * 3 + 2] = p.type;
      } else { Dd[i * 3] = 0; Cc[i * 4 + 3] = 0; }
    }
    this.pos.needsUpdate = true; this.colA.needsUpdate = true; this.data.needsUpdate = true;
  }

  clear(): void { for (const p of this.ps) p.active = false; }
  activeCount(): number { let n = 0; for (const p of this.ps) if (p.active) n++; return n; }
}
