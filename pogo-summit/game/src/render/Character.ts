import * as THREE from 'three';
import type { StyleMaterials } from './materials';
import { col, merge, mesh, solid, xf } from './geom';
import type { PogoMode } from '../sim/PogoState';

/**
 * "Pip" — original character (NOT the chef from the reference image). Big head, strong silhouette, inverted-hull outline.
 * Origin = centre of mass; +y = along the stick; the foot tip is at y = −comHeight. Faces the camera (+z), yawed toward travel.
 *
 * Animation states (reference: Idle · Charge · Jump · Boost · Landing, plus Fall) are procedural:
 *   Idle      breathing, blink, hat-pom bob
 *   Charge    stick spring compresses, rider crouches, face strains, energy ring grows
 *   Jump      stretch, legs tuck, arms up, mouth open
 *   Boost     spin aura + trail (VFX) while the sim says boost; arms back
 *   Landing   damped squash/rebound on impact
 *   Fall      arms flail, legs dangle, eyes wide, scarf streams up
 */
export interface CharacterAppearance { hat: string; stick: string; outfit: string; skin: string }
export const DEFAULT_APPEARANCE: CharacterAppearance = { hat: 'beanie', stick: 'copper', outfit: 'teal', skin: 'peach' };

const OUTFITS: Record<string, { parka: string; trim: string; scarf: string; pants: string }> = {
  teal: { parka: '#1fb0a8', trim: '#f4ead2', scarf: '#f7efe0', pants: '#35405a' },
  crimson: { parka: '#d9433a', trim: '#f4ead2', scarf: '#ffd24a', pants: '#3a2d4a' },
  forest: { parka: '#3f9a52', trim: '#f4ead2', scarf: '#ffb13a', pants: '#3d3a2f' },
  sunrise: { parka: '#f59a2c', trim: '#fff3d0', scarf: '#d9433a', pants: '#3a3350' },
  violet: { parka: '#7a52d0', trim: '#f4ead2', scarf: '#5fd6c8', pants: '#2c2f48' },
};
const SKINS: Record<string, string> = { peach: '#ffd2ad', tan: '#d9a070', deep: '#9a6440', rosy: '#f6b7a0', olive: '#c8a878' };
const STICKS: Record<string, { shaft: string; accent: string; stripe?: string }> = {
  copper: { shaft: '#e8923c', accent: '#b45f26' },
  candy: { shaft: '#ffffff', accent: '#e23b4a', stripe: '#e23b4a' },
  bamboo: { shaft: '#9bbf5a', accent: '#6f8f3a' },
  neon: { shaft: '#38e1ff', accent: '#9a52ff' },
  steel: { shaft: '#c7ccd8', accent: '#6e7688' },
};
export const OUTFIT_IDS = Object.keys(OUTFITS);
export const SKIN_IDS = Object.keys(SKINS);
export const STICK_IDS = Object.keys(STICKS);

export interface CharFrame {
  mode: PogoMode;
  charge01: number;
  vx: number;
  vy: number;
  omega: number;
  boosting: boolean;
  tilt: number;
  finished: boolean;
}

const mix = (a: number, b: number, t: number): number => a + (b - a) * t;
const damp = (cur: number, target: number, rate: number, dt: number): number => mix(cur, target, 1 - Math.exp(-rate * dt));

export class Character {
  readonly root = new THREE.Group();
  private readonly pivot = new THREE.Group();
  private readonly rider = new THREE.Group();
  private readonly crouchGroup = new THREE.Group();
  private readonly head = new THREE.Group();
  private readonly hatSlot = new THREE.Group();
  private readonly armL = new THREE.Group();
  private readonly armR = new THREE.Group();
  private readonly legL = new THREE.Group();
  private readonly legR = new THREE.Group();
  private readonly eyes: THREE.Mesh[] = [];
  private readonly mouth = new THREE.Mesh();
  private readonly springRings: THREE.Mesh[] = [];
  private readonly springGroup = new THREE.Group();
  private readonly stickGroup = new THREE.Group();
  private ribbon!: THREE.Mesh;
  private readonly pom = new THREE.Group();
  private readonly ring: THREE.Mesh;
  private readonly outlines: THREE.Mesh[] = [];
  private appearance: CharacterAppearance = { ...DEFAULT_APPEARANCE };

  // animation state
  private facing = 0.4;
  private crouch = 0;
  private stretch = 0;
  private tuck = 0;
  private armUp = 0;
  private mouthOpen = 0;
  private eyeWide = 0;
  private blink = 0;
  private blinkT = 2;
  private landK = 0; private landV = 0;
  private hatY = 0; private hatV = 0;
  private lastVy = 0;
  private time = 0;
  private emote: { name: string; t: number } | null = null;
  private tailPos: THREE.Vector3[] = [];

  constructor(private readonly mats: StyleMaterials, private readonly comHeight = 1.2) {
    this.root.add(this.pivot);
    this.pivot.position.y = -comHeight;          // squash pivot = the foot
    const body = new THREE.Group();
    body.position.y = comHeight;                 // back to COM-centred coordinates
    this.pivot.add(body);
    body.add(this.stickGroup, this.crouchGroup);
    this.crouchGroup.add(this.rider);
    this.ring = new THREE.Mesh(new THREE.TorusGeometry(0.55, 0.035, 6, 24), new THREE.MeshBasicMaterial({ color: '#ffe27a', transparent: true, opacity: 0, depthWrite: false, blending: THREE.AdditiveBlending }));
    this.ring.rotation.x = Math.PI / 2;
    this.ring.position.y = -comHeight + 0.18;
    body.add(this.ring);
    this.build();
  }

  // ───────────────────────────────────────────────────────── build ──────
  private part(g: THREE.BufferGeometry, color: string | THREE.Color, parent: THREE.Object3D, outline = true): THREE.Mesh {
    solid(g, color, true);
    const m = mesh(g, this.mats.smooth, true, false);
    parent.add(m);
    if (outline) {
      const o = new THREE.Mesh(g, this.mats.outline);
      parent.add(o);
      this.outlines.push(o);
      o.userData.owner = m;
    }
    return m;
  }

  private clearGroup(gr: THREE.Group): void {
    for (const c of [...gr.children]) {
      gr.remove(c);
      c.traverse(o => { const m = o as THREE.Mesh; if (m.geometry) m.geometry.dispose(); });
    }
  }

  setAppearance(a: Partial<CharacterAppearance>): void {
    this.appearance = { ...this.appearance, ...a };
    this.build();
  }

  private build(): void {
    this.clearGroup(this.stickGroup); this.clearGroup(this.rider); this.clearGroup(this.hatSlot);
    this.springRings.length = 0; this.eyes.length = 0; this.outlines.length = 0;
    this.head.clear(); this.armL.clear(); this.armR.clear(); this.legL.clear(); this.legR.clear(); this.springGroup.clear(); this.pom.clear();
    const o = OUTFITS[this.appearance.outfit] ?? OUTFITS.teal;
    const skin = SKINS[this.appearance.skin] ?? SKINS.peach;
    const st = STICKS[this.appearance.stick] ?? STICKS.copper;
    const H = this.comHeight;

    // ── stick ──
    const sg = this.stickGroup;
    const shaftTop = 0.36, shaftBot = -H + 0.14;
    const shaft = xf(new THREE.CylinderGeometry(0.05, 0.05, shaftTop - (shaftBot + 0.5), 8), 0, (shaftTop + shaftBot + 0.5) / 2, 0);
    this.part(shaft, st.shaft, sg);
    if (st.stripe) for (let i = 0; i < 5; i++) this.part(xf(new THREE.CylinderGeometry(0.056, 0.056, 0.09, 8), 0, shaftBot + 0.62 + i * 0.17, 0), st.stripe, sg, false);
    this.part(xf(new THREE.SphereGeometry(0.14, 10, 8), 0, shaftBot, 0), '#2b2530', sg);              // rubber tip
    this.part(xf(new THREE.CylinderGeometry(0.075, 0.1, 0.16, 8), 0, shaftBot + 0.2, 0), st.accent, sg);  // foot piece
    this.part(xf(new THREE.BoxGeometry(0.62, 0.05, 0.1), 0, -0.62, 0), st.accent, sg);                   // pegs
    sg.add(this.springGroup);
    const ringGeo = new THREE.TorusGeometry(0.115, 0.032, 5, 12);
    solid(ringGeo, '#d5dae4', true);
    for (let i = 0; i < 8; i++) {
      const m = mesh(ringGeo, this.mats.smooth, true, false);
      m.rotation.x = Math.PI / 2;
      this.springGroup.add(m); this.springRings.push(m);
    }
    const bar = xf(new THREE.CylinderGeometry(0.04, 0.04, 0.78, 6), 0, shaftTop, 0, 0, 0, Math.PI / 2); // handlebar (rider frame)
    const grips: THREE.BufferGeometry[] = [-1, 1].map(s => solid(xf(new THREE.CylinderGeometry(0.06, 0.06, 0.16, 6), s * 0.36, shaftTop, 0, 0, 0, Math.PI / 2), '#3a2f3a', true));
    solid(bar, st.accent, true);
    this.rider.add(mesh(merge([bar, ...grips]), this.mats.smooth, true, false));

    // ── legs ──
    const pants = o.pants, boot = '#4b2f27';
    for (const [grp, sx] of [[this.legL, -1], [this.legR, 1]] as [THREE.Group, number][]) {
      grp.position.set(sx * 0.14, -0.3, 0.02);
      const thigh = xf(new THREE.CapsuleGeometry(0.1, 0.2, 4, 8), sx * 0.02, -0.14, 0);
      this.part(thigh, pants, grp);
      const shin = xf(new THREE.CapsuleGeometry(0.085, 0.14, 4, 8), sx * 0.05, -0.34, 0.02);
      this.part(shin, pants, grp, false);
      const bootM = xf(new THREE.SphereGeometry(0.13, 8, 6), sx * 0.07, -0.48, 0.06, 0, 0, 0, 1, 0.7, 1.35);
      this.part(bootM, boot, grp);
      this.rider.add(grp);
    }

    // ── torso ──
    const torso = xf(new THREE.CapsuleGeometry(0.3, 0.32, 6, 12), 0, 0.02, 0, 0, 0, 0, 1, 1, 0.92);
    this.part(torso, o.parka, this.rider);
    this.part(xf(new THREE.BoxGeometry(0.06, 0.62, 0.05), 0, 0.02, 0.28), o.trim, this.rider, false);       // zip
    this.part(xf(new THREE.BoxGeometry(0.3, 0.2, 0.06), -0.1, -0.1, 0.27, 0, 0, 0.08), o.trim, this.rider, false); // pocket
    this.part(xf(new THREE.BoxGeometry(0.46, 0.5, 0.2), 0, 0.08, -0.32), '#8b5a38', this.rider);             // backpack
    this.part(xf(new THREE.CylinderGeometry(0.1, 0.1, 0.44, 8), 0, 0.37, -0.32, 0, 0, Math.PI / 2), '#e8c25a', this.rider, false); // bedroll

    // ── arms (pivot at shoulder) ──
    for (const [grp, sx] of [[this.armL, -1], [this.armR, 1]] as [THREE.Group, number][]) {
      grp.position.set(sx * 0.33, 0.28, 0.02);
      const upper = xf(new THREE.CapsuleGeometry(0.085, 0.16, 4, 8), sx * 0.03, -0.1, 0.03, 0, 0, sx * 0.1);
      this.part(upper, o.parka, grp);
      const hand = xf(new THREE.SphereGeometry(0.1, 8, 6), sx * 0.05, -0.27, 0.06);
      this.part(hand, '#f1d8c0', grp, false);
      this.rider.add(grp);
    }

    // ── head ──
    this.head.position.set(0, 0.88, 0.02);
    this.rider.add(this.head);
    this.part(xf(new THREE.SphereGeometry(0.43, 16, 12), 0, 0, 0, 0, 0, 0, 1, 0.94, 0.96), skin, this.head);
    for (const sx of [-1, 1]) this.part(xf(new THREE.SphereGeometry(0.085, 8, 6), sx * 0.42, -0.02, 0, 0, 0, 0, 0.6, 1, 0.9), skin, this.head, false);
    // eyes
    for (const sx of [-1, 1]) {
      const eye = mesh(solid(xf(new THREE.SphereGeometry(0.095, 10, 10), sx * 0.18, -0.07, 0.37, 0, 0, 0, 0.95, 1.2, 0.6), '#231826', true), this.mats.smooth);
      const shine = mesh(solid(xf(new THREE.SphereGeometry(0.032, 6, 6), sx * 0.18 + 0.03, -0.01, 0.43), '#ffffff', true), this.mats.basic);
      this.head.add(eye, shine);
      this.eyes.push(eye);
      this.part(xf(new THREE.SphereGeometry(0.075, 6, 5), sx * 0.28, -0.17, 0.3, 0, 0, 0, 1, 0.6, 0.4), '#f08a8a', this.head, false); // blush
    }
    this.mouth.geometry = solid(xf(new THREE.SphereGeometry(0.07, 8, 6), 0, -0.19, 0.39, 0, 0, 0, 1, 0.55, 0.5), '#6a2230', true);
    this.mouth.material = this.mats.smooth;
    this.head.add(this.mouth);
    this.part(xf(new THREE.SphereGeometry(0.045, 6, 5), 0, -0.11, 0.42, 0, 0, 0, 1, 0.8, 0.8), '#e9a98a', this.head, false); // nose
    // goggles on the forehead
    this.part(xf(new THREE.TorusGeometry(0.44, 0.03, 5, 20, Math.PI * 1.0), 0, 0.12, 0, Math.PI / 2 * 0, 0, Math.PI * 0.5 - 0.0), '#2b2530', this.head, false);

    // ── scarf ──
    this.part(xf(new THREE.TorusGeometry(0.29, 0.085, 8, 16), 0, 0.46, 0, Math.PI / 2, 0, 0, 1, 1, 0.85), o.scarf, this.rider);
    const RS = 7;
    const rg = new THREE.BufferGeometry();
    rg.setAttribute('position', new THREE.BufferAttribute(new Float32Array(RS * 2 * 3), 3));
    const rc = new Float32Array(RS * 2 * 3), rn = new Float32Array(RS * 2 * 3);
    const c1 = col(o.scarf), c2 = col(o.trim);
    for (let i = 0; i < RS; i++) { const c = i >= RS - 2 ? col('#e8a92a') : i % 2 ? c2 : c1; for (let k = 0; k < 2; k++) { rc.set([c.r, c.g, c.b], (i * 2 + k) * 3); rn.set([0, 0, 1], (i * 2 + k) * 3); } }
    rg.setAttribute('color', new THREE.BufferAttribute(rc, 3)); rg.setAttribute('normal', new THREE.BufferAttribute(rn, 3));
    const idx: number[] = [];
    for (let i = 0; i < RS - 1; i++) { const a = i * 2; idx.push(a, a + 1, a + 2, a + 1, a + 3, a + 2); }
    rg.setIndex(idx);
    this.ribbon = new THREE.Mesh(rg, new THREE.MeshStandardMaterial({ vertexColors: true, side: THREE.DoubleSide, roughness: 0.9 }));
    this.ribbon.castShadow = false; this.ribbon.frustumCulled = false; // a dynamic strip in the shadow map produced a long ghost shadow
    this.rider.add(this.ribbon);
    this.tailPos.length = 0;
    for (let i = 0; i < RS; i++) this.tailPos.push(new THREE.Vector3(-0.2 * (i + 1), 0.5, -0.1));

    // ── hat ──
    this.head.add(this.hatSlot);
    this.buildHat(o);
    this.mouthOpen = 0;
  }

  private buildHat(o: { parka: string; scarf: string }): void {
    const hs = this.hatSlot;
    this.pom.clear();
    const hat = this.appearance.hat;
    const add = (g: THREE.BufferGeometry, c: string, outline = true) => this.part(g, c, hs, outline);
    if (hat === 'propeller') {
      add(xf(new THREE.SphereGeometry(0.46, 14, 8, 0, Math.PI * 2, 0, Math.PI / 2), 0, 0.05, 0, 0, 0, 0, 1, 0.9, 1), '#e84a5f');
      add(xf(new THREE.SphereGeometry(0.46, 14, 4, 0, Math.PI * 2, Math.PI / 2 - 0.35, 0.35), 0, 0.05, 0, 0, 0, 0, 1.0, 0.9, 1), '#f5c542', false);
      add(xf(new THREE.CylinderGeometry(0.03, 0.03, 0.25, 5), 0, 0.5, 0), '#cfd3de', false);
      const blades = merge([xf(new THREE.BoxGeometry(0.8, 0.02, 0.14), 0, 0, 0), xf(new THREE.BoxGeometry(0.14, 0.02, 0.8), 0, 0, 0)].map(g => solid(g, '#4cc3ff', true)));
      const bm = mesh(blades, this.mats.smooth, true); bm.position.y = 0.66; bm.userData.spin = true;
      this.pom.add(bm); hs.add(this.pom);
    } else if (hat === 'tophat') {
      add(xf(new THREE.CylinderGeometry(0.6, 0.6, 0.06, 16), 0, 0.12, 0), '#2a2530');
      add(xf(new THREE.CylinderGeometry(0.34, 0.38, 0.6, 14), 0, 0.42, 0), '#2a2530');
      add(xf(new THREE.CylinderGeometry(0.385, 0.385, 0.12, 14), 0, 0.22, 0), '#d9433a', false);
    } else if (hat === 'bucket') {
      add(xf(new THREE.CylinderGeometry(0.42, 0.5, 0.36, 14), 0, 0.28, 0), '#7fb36a');
      add(xf(new THREE.CylinderGeometry(0.66, 0.66, 0.05, 16), 0, 0.12, 0), '#6b9a58');
    } else if (hat === 'party') {
      add(xf(new THREE.ConeGeometry(0.34, 0.9, 12), 0, 0.5, 0, 0, 0, 0.1), '#b362e8');
      for (let i = 0; i < 4; i++) add(xf(new THREE.TorusGeometry(0.12 + (3 - i) * 0.03, 0.025, 4, 10), 0, 0.2 + i * 0.18, 0, Math.PI / 2, 0, 0.1), i % 2 ? '#ffd24a' : '#ffffff', false);
      const p = xf(new THREE.IcosahedronGeometry(0.12, 1), 0.04, 0.98, 0); add(p, '#ffd24a', false);
    } else { // beanie (default)
      add(xf(new THREE.SphereGeometry(0.47, 16, 10, 0, Math.PI * 2, 0, Math.PI * 0.5), 0, 0.12, 0, 0, 0, 0, 1, 0.95, 1.02), '#f5c542');
      add(xf(new THREE.TorusGeometry(0.44, 0.095, 8, 20), 0, 0.15, 0, Math.PI / 2, 0, 0, 1, 1.02, 1.0), '#e8a92a');
      const pom = xf(new THREE.IcosahedronGeometry(0.17, 1), 0, 0.6, 0); solid(pom, '#e0463a', true);
      const pm = mesh(pom, this.mats.smooth, true); const po = new THREE.Mesh(pom, this.mats.outline);
      this.pom.add(pm, po); this.outlines.push(po); hs.add(this.pom);
    }
  }

  // ───────────────────────────────────────────────────── triggers ───────
  triggerLand(k: number): void { this.landV += 7 * Math.min(1, k + 0.15); }
  triggerLaunch(power: number): void { this.landK = -0.12 * power; this.landV += 4 * power; }
  triggerEmote(name: string): void { this.emote = { name, t: 0 }; }

  // ─────────────────────────────────────────────────────── update ───────
  update(dt: number, f: CharFrame): void {
    this.time += dt;
    const air = f.mode === 'AIR' || f.mode === 'SLIDING';
    const charging = f.mode === 'CHARGING';
    // facing: toward the direction of motion / lean
    const dir = Math.abs(f.vx) > 0.8 ? Math.sign(f.vx) : f.tilt !== 0 ? Math.sign(f.tilt) : Math.sign(this.facing || 1);
    this.facing = damp(this.facing, dir * 0.9, 7, dt);
    this.rider.rotation.y = this.facing * 0.55;

    // targets
    const crouchT = charging ? 0.25 + 0.75 * f.charge01 : 0;
    this.crouch = damp(this.crouch, crouchT, charging ? 18 : 22, dt);
    const rising = air && f.vy > 1;
    const falling = air && f.vy < -3;
    this.stretch = damp(this.stretch, rising ? Math.min(1, f.vy / 16) : falling ? -Math.min(1, -f.vy / 28) * 0.6 : 0, 12, dt);
    this.tuck = damp(this.tuck, air ? (falling ? 0.25 : 1) : charging ? 0.4 * this.crouch : 0, 14, dt);
    this.armUp = damp(this.armUp, f.finished ? 1.4 : falling ? 1.2 + Math.sin(this.time * 18) * 0.35 : rising ? 0.85 : f.boosting ? -0.4 : charging ? -0.2 : 0, 12, dt);
    this.mouthOpen = damp(this.mouthOpen, falling ? 1 : rising || f.finished ? 0.8 : charging ? 0.45 * this.crouch : 0.0, 14, dt);
    this.eyeWide = damp(this.eyeWide, falling ? 1 : 0, 14, dt);
    this.blinkT -= dt;
    if (this.blinkT < 0) { this.blink = 1; this.blinkT = 2 + ((this.time * 7919) % 3); }
    this.blink = Math.max(0, this.blink - dt * 9);

    // landing / launch spring
    const a = -260 * this.landK - 20 * this.landV;
    this.landV += a * dt; this.landK += this.landV * dt;

    // pose
    const sy = 1 + this.stretch * 0.1 - this.crouch * 0.05 - this.landK * 0.2;
    const sx = 1 - this.stretch * 0.05 + this.crouch * 0.07 + this.landK * 0.2;
    this.pivot.scale.set(sx, sy, sx);
    this.crouchGroup.position.y = -this.crouch * 0.3 + this.landK * 0.1;
    this.head.position.y = 0.88 + Math.sin(this.time * 2.1) * 0.012 * (1 - this.crouch) - this.crouch * 0.05;
    this.head.rotation.z = -f.tilt * 0.08 + Math.sin(this.time * 1.3) * 0.015;
    const compress = Math.min(1, this.crouch * 0.95 + Math.max(0, this.landK) * 1.6);
    const springTop = -0.62 - 0.0, springBot = -this.comHeight + 0.3;
    for (let i = 0; i < this.springRings.length; i++) {
      const t = (i + 0.5) / this.springRings.length;
      const span = (springTop - springBot) * (1 - compress * 0.55);
      this.springRings[i].position.y = springBot + span * t;
      this.springRings[i].scale.set(1 + compress * 0.1, 1 + compress * 0.1, 1 + compress * 0.1);
    }
    // legs
    const splay = this.tuck * 0.6 + this.crouch * 0.3;
    this.legL.scale.y = 1 - this.tuck * 0.38 - this.crouch * 0.2; this.legR.scale.y = this.legL.scale.y;
    this.legL.rotation.z = splay * 0.55; this.legR.rotation.z = -splay * 0.55;
    if (f.mode === 'AIR' && falling) { this.legL.rotation.z += Math.sin(this.time * 14) * 0.2; this.legR.rotation.z -= Math.sin(this.time * 14 + 1) * 0.2; }
    // arms
    const hold = 0.12 + this.crouch * 0.25;
    let aL = hold + this.armUp * 1.6, aR = -hold - this.armUp * 1.6;
    // emotes override
    if (this.emote) {
      this.emote.t += dt;
      const t = this.emote.t;
      if (this.emote.name === 'wave') { aR = -2.7 + Math.sin(t * 12) * 0.35; }
      else if (this.emote.name === 'cheer') { aL = 2.6 + Math.sin(t * 10) * 0.2; aR = -2.6 - Math.sin(t * 10) * 0.2; this.rider.rotation.y += Math.sin(t * 6) * 0.2; }
      else if (this.emote.name === 'dance') { aL = 1.2 + Math.sin(t * 9) * 0.9; aR = -1.2 + Math.sin(t * 9 + 3) * 0.9; this.head.rotation.z = Math.sin(t * 9) * 0.18; }
      if (t > 2.2) this.emote = null;
    }
    this.armL.rotation.z = aL; this.armR.rotation.z = aR;
    // face
    const lid = Math.max(this.blink, this.crouch * 0.55);
    for (const e of this.eyes) { e.scale.y = Math.max(0.12, (1.25 + this.eyeWide * 0.45) * (1 - lid * 0.85)); }
    this.mouth.scale.set(1 + this.mouthOpen * 0.4, 0.25 + this.mouthOpen * 1.5, 1);
    this.mouth.position.y = -this.mouthOpen * 0.02;
    // hat pom spring (reacts to vertical acceleration)
    const acc = (f.vy - this.lastVy) / Math.max(dt, 1e-3);
    this.lastVy = f.vy;
    this.hatV += (-acc * 0.004 - 120 * this.hatY - 9 * this.hatV) * dt;
    this.hatY = Math.max(-0.2, Math.min(0.2, this.hatY + this.hatV * dt));
    this.hatSlot.position.y = this.hatY * 0.4;
    this.pom.position.y = this.hatY * 0.9;
    this.pom.rotation.z = Math.sin(this.time * 3.1) * 0.05 - f.vx * 0.012;
    const spin = this.pom.children.find(c => c.userData.spin);
    if (spin) spin.rotation.y += dt * (14 + Math.abs(f.vy));
    // scarf ribbon: chain follows the neck anchor with lag; streams opposite to the velocity
    const sgn = Math.sign(this.facing || 1);
    const wind = new THREE.Vector3(-f.vx * 0.045, -f.vy * 0.03 + (air ? 0.05 : 0), 0);
    const anchor = new THREE.Vector3(-0.22 * sgn, 0.46, -0.12);
    this.tailPos[0].copy(anchor);
    const pa = (this.ribbon.geometry.getAttribute('position') as THREE.BufferAttribute);
    for (let i = 1; i < this.tailPos.length; i++) {
      const prev = this.tailPos[i - 1], p = this.tailPos[i];
      const rest = new THREE.Vector3(prev.x - 0.17 * sgn + wind.x, prev.y - 0.05 + wind.y + 0.015 * i, prev.z - 0.015);
      p.lerp(rest, 1 - Math.exp(-13 * dt));
    }
    for (let i = 0; i < this.tailPos.length; i++) {
      const p = this.tailPos[i];
      const w = 0.075 * (1 - (i / this.tailPos.length) * 0.15) * (i === 0 ? 0.6 : 1);
      pa.setXYZ(i * 2, p.x, p.y + w, p.z); pa.setXYZ(i * 2 + 1, p.x, p.y - w, p.z);
    }
    pa.needsUpdate = true;
    // charge ring
    const rm = this.ring.material as THREE.MeshBasicMaterial;
    rm.opacity = charging ? 0.25 + 0.7 * f.charge01 * (f.charge01 >= 0.999 ? 0.6 + 0.4 * Math.sin(this.time * 24) : 1) : 0;
    this.ring.scale.setScalar(0.6 + 1.2 * (charging ? f.charge01 : 0));
    this.ring.visible = charging;
  }
}
