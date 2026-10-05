import * as THREE from 'three';
import type { StyleMaterials } from './materials';
import { SURF, col, merge, mesh, paint, solid, xf } from './geom';
import { roundedBlock } from './shapes';
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
  teal: { parka: '#1fb0a8', trim: '#f4ead2', scarf: '#e8463a', pants: '#35405a' },
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
  private readonly eyes: THREE.Object3D[] = [];
  private readonly mouth = new THREE.Mesh();
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
  /** Parts are collected per animated group and merged at the end: ~20 draw calls for the whole character. */
  private pending = new Map<THREE.Object3D, { fill: THREE.BufferGeometry[]; line: THREE.BufferGeometry[] }>();

  /** Shaded part: vertical gradient (soft AO at the bottom of each part) + optional inverted-hull outline. */
  private part(g: THREE.BufferGeometry, color: string | THREE.Color, parent: THREE.Object3D, outline = true, shade = 0.18): void {
    const c = color instanceof THREE.Color ? color : new THREE.Color(color);
    g.computeBoundingBox();
    const bb = g.boundingBox!;
    const lo = c.clone().multiplyScalar(1 - shade), hi = c.clone().multiplyScalar(1 + shade * 0.35);
    paint(g, (_x, y) => lo.clone().lerp(hi, (y - bb.min.y) / Math.max(1e-3, bb.max.y - bb.min.y)), true, SURF.PLAIN);
    let e = this.pending.get(parent);
    if (!e) { e = { fill: [], line: [] }; this.pending.set(parent, e); }
    e.fill.push(g);
    if (outline) e.line.push(g);
  }

  private flush(): void {
    for (const [parent, e] of this.pending) {
      if (e.fill.length) parent.add(mesh(merge(e.fill), this.mats.char, true, false));
      if (e.line.length) { const o = new THREE.Mesh(merge(e.line), this.mats.outline); parent.add(o); this.outlines.push(o); }
    }
    this.pending.clear();
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
    for (const gr of [this.stickGroup, this.rider, this.hatSlot, this.head, this.armL, this.armR, this.legL, this.legR, this.springGroup, this.pom]) this.clearGroup(gr);
    this.eyes.length = 0; this.outlines.length = 0; this.pending.clear();
    const o = OUTFITS[this.appearance.outfit] ?? OUTFITS.teal;
    const skin = SKINS[this.appearance.skin] ?? SKINS.peach;
    const st = STICKS[this.appearance.stick] ?? STICKS.copper;
    const H = this.comHeight;

    // ── pogo stick: rubber foot · lower metal tube · helix spring · foot pegs · coloured upper tube · T-bar with grips ──
    const sg = this.stickGroup;
    const footY = -H + 0.1, pegY = -0.62, shaftTop = 0.36;
    this.part(xf(new THREE.SphereGeometry(0.105, 12, 8), 0, footY + 0.0, 0, 0, 0, 0, 1, 0.8, 1), '#262230', sg, true, 0.1);           // rubber foot
    this.part(xf(new THREE.CylinderGeometry(0.085, 0.1, 0.1, 12), 0, footY + 0.1, 0), '#3a3542', sg, false);                            // foot collar
    this.part(xf(new THREE.CylinderGeometry(0.042, 0.042, pegY - footY - 0.1, 10), 0, (pegY + footY + 0.1) / 2, 0), '#c9ced8', sg, false, 0.25); // lower tube
    this.part(xf(new THREE.CylinderGeometry(0.075, 0.075, 0.12, 12), 0, pegY + 0.02, 0), st.accent, sg, true);                       // peg block
    for (const sx of [-1, 1]) {                                                                                                      // foot pegs with treads
      this.part(xf(roundedBlock(0.26, 0.06, 0.15, 0.025, 0.3, 1).geometry, sx * 0.2, pegY, 0.03), '#3a3542', sg, true, 0.1);
    }
    this.part(xf(new THREE.CylinderGeometry(0.06, 0.066, shaftTop - pegY, 12), 0, (shaftTop + pegY) / 2, 0), st.shaft, sg, true, 0.22); // upper tube
    if (st.stripe) for (let i = 0; i < 5; i++) this.part(xf(new THREE.CylinderGeometry(0.068, 0.068, 0.07, 12), 0, pegY + 0.15 + i * 0.17, 0), st.stripe, sg, false);
    sg.add(this.springGroup);
    this.springGroup.position.y = footY + 0.15;
    {
      const turns = 8, r = 0.1, pts: THREE.Vector3[] = [];
      for (let i = 0; i <= turns * 14; i++) { const t = i / (turns * 14), a = t * turns * Math.PI * 2; pts.push(new THREE.Vector3(Math.cos(a) * r, t, Math.sin(a) * r)); }
      const sp = new THREE.TubeGeometry(new THREE.CatmullRomCurve3(pts), turns * 14, 0.026, 6, false);
      solid(sp, '#e4e8f0', true, SURF.PLAIN);
      this.springGroup.add(mesh(sp, this.mats.char, true, false));
    }
    // handlebar (rider frame so it follows the crouch)
    this.part(xf(new THREE.CylinderGeometry(0.038, 0.038, 0.8, 8), 0, shaftTop, 0, 0, 0, Math.PI / 2), st.accent, this.rider, true, 0.15);
    for (const s of [-1, 1]) {
      this.part(xf(new THREE.CylinderGeometry(0.058, 0.058, 0.17, 10), s * 0.36, shaftTop, 0, 0, 0, Math.PI / 2), '#2f2836', this.rider, true, 0.1);
      this.part(xf(new THREE.SphereGeometry(0.06, 8, 6), s * 0.455, shaftTop, 0), st.accent, this.rider, false);
    }

    // ── legs ──
    const pants = o.pants, boot = '#5a3527';
    for (const [grp, sx] of [[this.legL, -1], [this.legR, 1]] as [THREE.Group, number][]) {
      grp.position.set(sx * 0.15, -0.22, 0.02);
      this.part(xf(new THREE.CapsuleGeometry(0.105, 0.1, 4, 10), sx * 0.02, -0.09, 0), pants, grp);
      this.part(xf(new THREE.CapsuleGeometry(0.09, 0.08, 4, 10), sx * 0.06, -0.22, 0.02), pants, grp, false);
      this.part(xf(new THREE.SphereGeometry(0.13, 10, 8), sx * 0.08, -0.33, 0.07, 0, 0, 0, 1, 0.7, 1.38), boot, grp, true, 0.12);
      this.part(xf(new THREE.TorusGeometry(0.095, 0.03, 5, 12), sx * 0.07, -0.27, 0.04, Math.PI / 2, 0, 0), '#f4ead2', grp, false); // sock cuff
      this.rider.add(grp);
    }

    // ── torso: parka with fur-trimmed collar, zip, pocket, backpack + bedroll ──
    this.part(xf(new THREE.CapsuleGeometry(0.31, 0.32, 6, 14), 0, 0.02, 0, 0, 0, 0, 1, 1, 0.92), o.parka, this.rider, true, 0.2);
    this.part(xf(new THREE.TorusGeometry(0.27, 0.07, 6, 16), 0, -0.24, 0, Math.PI / 2, 0, 0, 1, 1, 0.88), o.trim, this.rider, false, 0.1); // hem
    this.part(xf(new THREE.BoxGeometry(0.05, 0.6, 0.05), 0, 0.02, 0.285), o.trim, this.rider, false);
    this.part(xf(roundedBlock(0.28, 0.18, 0.06, 0.03, 0.2, 1).geometry, -0.1, -0.1, 0.27, 0, 0, 0.08), o.trim, this.rider, false);
    this.part(xf(roundedBlock(0.48, 0.52, 0.22, 0.08, 0.3, 2).geometry, 0, 0.08, -0.33), '#9a6038', this.rider, true);
    this.part(xf(new THREE.CylinderGeometry(0.1, 0.1, 0.46, 10), 0, 0.38, -0.33, 0, 0, Math.PI / 2), '#e8c25a', this.rider, false);

    // ── arms (pivot at shoulder) + mittens ──
    for (const [grp, sx] of [[this.armL, -1], [this.armR, 1]] as [THREE.Group, number][]) {
      grp.position.set(sx * 0.33, 0.28, 0.02);
      this.part(xf(new THREE.CapsuleGeometry(0.088, 0.16, 4, 10), sx * 0.03, -0.1, 0.03, 0, 0, sx * 0.1), o.parka, grp);
      this.part(xf(new THREE.TorusGeometry(0.075, 0.03, 5, 10), sx * 0.045, -0.2, 0.05, Math.PI / 2, 0, 0), o.trim, grp, false);
      this.part(xf(new THREE.SphereGeometry(0.1, 10, 8), sx * 0.05, -0.27, 0.06), o.scarf, grp, true, 0.12);
      this.rider.add(grp);
    }

    // ── head: big chibi head, sclera + iris eyes (readable at phone size), brows, nose, blush ──
    this.head.position.set(0, 0.88, 0.02);
    this.rider.add(this.head);
    this.part(xf(new THREE.SphereGeometry(0.43, 20, 14), 0, 0, 0, 0, 0, 0, 1, 0.94, 0.96), skin, this.head, true, 0.14);
    for (const sx of [-1, 1]) this.part(xf(new THREE.SphereGeometry(0.085, 8, 6), sx * 0.42, -0.02, 0, 0, 0, 0, 0.6, 1, 0.9), skin, this.head, false);
    this.part(xf(new THREE.SphereGeometry(0.05, 8, 6), 0, -0.1, 0.42, 0, 0, 0, 1, 0.8, 0.8), '#eea283', this.head, false);
    for (const sx of [-1, 1]) {
      this.part(xf(new THREE.SphereGeometry(0.07, 8, 6), sx * 0.27, -0.16, 0.33, 0, 0, 0, 1, 0.6, 0.4), '#f39a8f', this.head, false, 0);    // blush
      this.part(xf(new THREE.CapsuleGeometry(0.022, 0.1, 3, 6), sx * 0.17, 0.15, 0.37, 0, 0, Math.PI / 2 + sx * 0.18), '#4a2a22', this.head, false, 0); // brow
      const eye = new THREE.Group();
      eye.position.set(sx * 0.165, -0.01, 0.35);
      const white = solid(xf(new THREE.SphereGeometry(0.1, 14, 10), 0, 0, 0, 0, 0, 0, 0.88, 1.18, 0.55), '#fbf6ee', true, SURF.PLAIN);
      const iris = solid(xf(new THREE.SphereGeometry(0.07, 12, 10), sx * -0.01, -0.012, 0.032, 0, 0, 0, 0.9, 1.1, 0.5), '#2a1a2c', true, SURF.PLAIN);
      const shine = solid(xf(new THREE.SphereGeometry(0.024, 6, 6), 0.024, 0.035, 0.066), '#ffffff', true, SURF.PLAIN);
      eye.add(mesh(merge([white, iris]), this.mats.char), mesh(shine, this.mats.basic));
      this.head.add(eye);
      this.eyes.push(eye);
    }
    this.mouth.geometry = solid(xf(new THREE.SphereGeometry(0.07, 10, 8), 0, -0.2, 0.385, 0, 0, 0, 1, 0.55, 0.5), '#6a2230', true, SURF.PLAIN);
    this.mouth.material = this.mats.char;
    this.head.add(this.mouth);
    // goggles resting on the beanie brim
    if (this.appearance.hat === 'beanie') {   // knitted badge on the beanie (a mountain on a teal disc)
      this.part(xf(new THREE.CylinderGeometry(0.1, 0.1, 0.03, 16), 0.19, 0.36, 0.38, Math.PI / 2 - 0.55, 0, -0.42), '#1fb0a8', this.hatSlot, false, 0);
      this.part(xf(new THREE.ConeGeometry(0.06, 0.08, 3), 0.19, 0.37, 0.4, -0.55, 0, -0.42), '#fbf6ee', this.hatSlot, false, 0);
    }

    // ── scarf (knot ring + streaming ribbon) ──
    this.part(xf(new THREE.TorusGeometry(0.29, 0.09, 8, 18), 0, 0.46, 0, Math.PI / 2, 0, 0, 1, 1, 0.85), o.scarf, this.rider, true, 0.15);
    const RS = 7;
    const rg = new THREE.BufferGeometry();
    rg.setAttribute('position', new THREE.BufferAttribute(new Float32Array(RS * 2 * 3), 3));
    const rc = new Float32Array(RS * 2 * 3), rn = new Float32Array(RS * 2 * 3);
    const c1 = col(o.scarf), c2 = col(o.trim);
    for (let i = 0; i < RS; i++) { const c = i % 3 === 2 ? c2 : c1; for (let k = 0; k < 2; k++) { rc.set([c.r, c.g, c.b], (i * 2 + k) * 3); rn.set([0, 0, 1], (i * 2 + k) * 3); } }
    rg.setAttribute('color', new THREE.BufferAttribute(rc, 3)); rg.setAttribute('normal', new THREE.BufferAttribute(rn, 3));
    const idx: number[] = [];
    for (let i = 0; i < RS - 1; i++) { const a = i * 2; idx.push(a, a + 1, a + 2, a + 1, a + 3, a + 2); }
    rg.setIndex(idx);
    this.ribbon = new THREE.Mesh(rg, new THREE.MeshStandardMaterial({ vertexColors: true, side: THREE.DoubleSide, roughness: 0.85, emissive: new THREE.Color(o.scarf).multiplyScalar(0.12) }));
    this.ribbon.castShadow = false; this.ribbon.frustumCulled = false; // a dynamic strip in the shadow map produced a long ghost shadow
    this.rider.add(this.ribbon);
    this.tailPos.length = 0;
    for (let i = 0; i < RS; i++) this.tailPos.push(new THREE.Vector3(-0.2 * (i + 1), 0.5, -0.1));

    // ── hat ──
    this.head.add(this.hatSlot);
    this.buildHat();
    this.flush();
    this.mouthOpen = 0;
  }

  private buildHat(): void {
    const hs = this.hatSlot;
    const hat = this.appearance.hat;
    const add = (g: THREE.BufferGeometry, c: string, outline = true) => this.part(g, c, hs, outline, 0.16);
    if (hat === 'propeller') {
      add(xf(new THREE.SphereGeometry(0.46, 16, 10, 0, Math.PI * 2, 0, Math.PI / 2), 0, 0.05, 0, 0, 0, 0, 1, 0.9, 1), '#e84a5f');
      add(xf(new THREE.SphereGeometry(0.465, 16, 4, 0, Math.PI * 2, Math.PI / 2 - 0.35, 0.35), 0, 0.05, 0, 0, 0, 0, 1.0, 0.9, 1), '#f5c542', false);
      add(xf(new THREE.CylinderGeometry(0.03, 0.03, 0.25, 6), 0, 0.5, 0), '#cfd3de', false);
      const blades = merge([xf(new THREE.BoxGeometry(0.8, 0.02, 0.14), 0, 0, 0), xf(new THREE.BoxGeometry(0.14, 0.02, 0.8), 0, 0, 0)].map(g => solid(g, '#4cc3ff', true, SURF.PLAIN)));
      const bm = mesh(blades, this.mats.char, true); bm.position.y = 0.66; bm.userData.spin = true;
      this.pom.add(bm); hs.add(this.pom);
    } else if (hat === 'tophat') {
      add(xf(new THREE.CylinderGeometry(0.6, 0.6, 0.06, 20), 0, 0.12, 0), '#2a2530');
      add(xf(new THREE.CylinderGeometry(0.34, 0.38, 0.6, 18), 0, 0.42, 0), '#2a2530');
      add(xf(new THREE.CylinderGeometry(0.385, 0.385, 0.12, 18), 0, 0.22, 0), '#d9433a', false);
    } else if (hat === 'bucket') {
      add(xf(new THREE.CylinderGeometry(0.42, 0.5, 0.36, 18), 0, 0.28, 0), '#7fb36a');
      add(xf(new THREE.CylinderGeometry(0.66, 0.66, 0.05, 20), 0, 0.12, 0), '#6b9a58');
    } else if (hat === 'party') {
      add(xf(new THREE.ConeGeometry(0.34, 0.9, 16), 0, 0.5, 0, 0, 0, 0.1), '#b362e8');
      for (let i = 0; i < 4; i++) add(xf(new THREE.TorusGeometry(0.12 + (3 - i) * 0.03, 0.025, 4, 12), 0, 0.2 + i * 0.18, 0, Math.PI / 2, 0, 0.1), i % 2 ? '#ffd24a' : '#ffffff', false);
      add(xf(new THREE.IcosahedronGeometry(0.12, 1), 0.04, 0.98, 0), '#ffd24a', false);
    } else { // beanie (default): knit body with ribs, folded cuff, pom-pom
      const knit = xf(new THREE.SphereGeometry(0.47, 22, 12, 0, Math.PI * 2, 0, Math.PI * 0.5), 0, 0.2, 0, 0, 0, 0, 1, 0.98, 1.02);
      const kp = knit.getAttribute('position') as THREE.BufferAttribute;
      for (let i = 0; i < kp.count; i++) { const a = Math.atan2(kp.getZ(i), kp.getX(i)); const k = 1 + Math.sin(a * 22) * 0.012; kp.setXYZ(i, kp.getX(i) * k, kp.getY(i), kp.getZ(i) * k); }
      add(knit, '#f5c542');
      add(xf(new THREE.TorusGeometry(0.44, 0.085, 10, 28), 0, 0.23, 0, Math.PI / 2, 0, 0, 1, 1.02, 1.0), '#e8a92a');
      const pom = xf(new THREE.IcosahedronGeometry(0.17, 2), 0, 0.68, 0);
      this.part(pom, '#e0463a', this.pom, true, 0.2);
      hs.add(this.pom);
    }
  }

  // ───────────────────────────────────────────────────── triggers ───────
  triggerLand(k: number): void { this.landV += 7 * Math.min(1, k + 0.15); }
  triggerLaunch(power: number): void { this.landK = -0.12 * power; this.landV += 4 * power; }
  triggerEmote(name: string): void { this.emote = { name, t: 0 }; }

  // ─────────────────────────────────────────────────────── update ───────
  update(dt: number, f: CharFrame): void {
    dt = Math.max(0, Math.min(0.1, dt));
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

    // landing / launch spring — sub-stepped: explicit integration of a stiff spring diverges when a frame takes ≥ ~0.09 s
    // (slow devices, software GL), which made the whole character fly off to infinity
    const nSub = Math.max(1, Math.ceil(dt / (1 / 60))), hSub = dt / nSub;
    for (let i = 0; i < nSub; i++) { const a = -260 * this.landK - 20 * this.landV; this.landV += a * hSub; this.landK += this.landV * hSub; }
    this.landK = Math.max(-1, Math.min(1, this.landK)); this.landV = Math.max(-40, Math.min(40, this.landV));

    // pose
    const sy = 1 + this.stretch * 0.1 - this.crouch * 0.05 - this.landK * 0.2;
    const sx = 1 - this.stretch * 0.05 + this.crouch * 0.07 + this.landK * 0.2;
    this.pivot.scale.set(sx, sy, sx);
    this.crouchGroup.position.y = -this.crouch * 0.3 + this.landK * 0.1;
    this.head.position.y = 0.88 + Math.sin(this.time * 2.1) * 0.012 * (1 - this.crouch) - this.crouch * 0.05;
    this.head.rotation.z = -f.tilt * 0.08 + Math.sin(this.time * 1.3) * 0.015;
    const compress = Math.min(1, this.crouch * 0.95 + Math.max(0, this.landK) * 1.6);
    // spring: fixed between the foot collar and the peg block; it bulges as the rider loads it
    const L0 = (-0.62 - 0.06) - (-this.comHeight + 0.1 + 0.15);
    this.springGroup.scale.set(1 + compress * 0.35, L0 * (1 - compress * 0.12), 1 + compress * 0.35);
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
    for (const e of this.eyes) { e.scale.y = Math.max(0.1, (1 + this.eyeWide * 0.3) * (1 - lid * 0.88)); e.scale.x = 1 + this.eyeWide * 0.15; }
    this.mouth.scale.set(1 + this.mouthOpen * 0.4, 0.25 + this.mouthOpen * 1.5, 1);
    this.mouth.position.y = -this.mouthOpen * 0.02;
    // hat pom spring (reacts to vertical acceleration)
    const acc = (f.vy - this.lastVy) / Math.max(dt, 1e-3);
    this.lastVy = f.vy;
    for (let i = 0; i < nSub; i++) {
      this.hatV += (-acc * 0.004 - 120 * this.hatY - 9 * this.hatV) * hSub;
      this.hatY = Math.max(-0.2, Math.min(0.2, this.hatY + this.hatV * hSub));
    }
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
      const dl = p.distanceTo(prev);
      if (dl > 0.24) p.sub(prev).multiplyScalar(0.24 / dl).add(prev); // hard length constraint: the scarf can never stretch
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
