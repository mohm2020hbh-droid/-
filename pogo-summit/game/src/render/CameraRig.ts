import * as THREE from 'three';
import { noise3 } from './noise';

/**
 * CameraRig — landscape camera tuned for a phone (Phase 9 / "CAMERA").
 * Follow · Smooth Follow · Vertical Tracking · Look Ahead · Dynamic Framing · Landing Feedback · Impact Shake ·
 * Boost Feedback · Fall Feedback.
 *
 * A true perspective camera with a narrow vertical FOV: the vertical framing stays constant on every aspect ratio,
 * so a 16:9 phone and a 21:9 phone see the same amount of climb (wider screens simply see further sideways).
 */
export interface CameraTarget {
  x: number; y: number; vx: number; vy: number;
  grounded: boolean; charging: boolean; charge01: number; boosting: boolean;
}

export interface CameraBounds { minX: number; maxX: number; minY: number; maxY: number }

export class CameraRig {
  readonly camera = new THREE.PerspectiveCamera(30, 16 / 9, 0.5, 900);
  baseFov = 30;
  baseDistance = 19;
  private x = 0; private y = 0;
  private vxs = 0; private vys = 0;
  private lookX = 0; private lookY = 0;
  private dist = 19;
  private fovKick = 0;
  private trauma = 0;
  private dip = 0; private dipV = 0;
  private bounds: CameraBounds = { minX: -1e9, maxX: 1e9, minY: -1e9, maxY: 1e9 };
  private time = 0;
  private fallTilt = 0;
  halfW = 12; halfH = 5.2;
  /** Menu framing: shifts the view sideways (metres) so the character sits beside the menu panel. */
  shiftX = 0;
  shiftTarget = 0;
  distScale = 1;
  distScaleTarget = 1;

  setBounds(b: CameraBounds): void { this.bounds = b; }

  snap(x: number, y: number): void {
    this.x = x; this.y = y + 1.4; this.vxs = this.vys = 0; this.lookX = 0; this.lookY = 0; this.dip = this.dipV = 0;
    this.apply(0);
  }

  /** Landing feedback: kick the camera down proportional to the impact. */
  landKick(k: number): void { this.dipV -= 5 * k; this.addTrauma(0.12 + 0.5 * k * k); }
  addTrauma(t: number): void { this.trauma = Math.min(1, this.trauma + t); }
  boostKick(): void { this.fovKick = 6; this.addTrauma(0.3); }

  private smoothDamp(cur: number, target: number, vel: number, smoothTime: number, dt: number, maxSpeed = 1e9): [number, number] {
    const omega = 2 / Math.max(0.0001, smoothTime);
    const x = omega * dt;
    const exp = 1 / (1 + x + 0.48 * x * x + 0.235 * x * x * x);
    let change = cur - target;
    const maxChange = maxSpeed * smoothTime;
    change = Math.max(-maxChange, Math.min(maxChange, change));
    const tgt = cur - change;
    const temp = (vel + omega * change) * dt;
    vel = (vel - omega * temp) * exp;
    let out = tgt + (change + temp) * exp;
    if ((target - cur > 0) === (out > target)) { out = target; vel = (out - target) / dt; }
    return [out, vel];
  }

  update(dt: number, t: CameraTarget, aspect: number): void {
    this.time += dt;
    const speed = Math.hypot(t.vx, t.vy);
    // dynamic framing: widen when fast / falling, tighten slightly while charging a big jump
    const falling = t.vy < -14;
    this.shiftX += (this.shiftTarget - this.shiftX) * (1 - Math.exp(-4 * dt));
    this.distScale += (this.distScaleTarget - this.distScale) * (1 - Math.exp(-4 * dt));
    const targetDist = this.baseDistance * this.distScale * (1 + Math.min(0.14, speed / 220) + (falling ? 0.08 : 0) - (t.charging ? 0.03 * t.charge01 : 0));
    this.dist += (targetDist - this.dist) * (1 - Math.exp(-3 * dt));
    this.fovKick *= Math.exp(-5 * dt);
    this.trauma = Math.max(0, this.trauma - dt * 1.7);
    // spring for landing dip
    this.dipV += (-90 * this.dip - 12 * this.dipV) * dt;
    this.dip += this.dipV * dt;

    this.camera.fov = this.baseFov + this.fovKick + (t.boosting ? 2 : 0);
    const vh = 2 * this.dist * Math.tan((this.camera.fov * Math.PI) / 360);
    this.halfH = vh / 2;
    this.halfW = this.halfH * aspect;

    // look-ahead: see where the momentum is taking us (more when moving fast)
    const la = Math.max(-5.5, Math.min(5.5, t.vx * 0.3));
    this.lookX += (la - this.lookX) * (1 - Math.exp(-2.6 * dt));
    // vertical bias: keep the player in the lower third so upcoming platforms are visible; look further down when falling
    const lyT = t.vy < -6 ? Math.max(-3.5, t.vy * 0.12) : (t.grounded ? 1.55 : 1.15) + Math.min(1.2, Math.max(0, t.vy) * 0.06);
    this.lookY += (lyT - this.lookY) * (1 - Math.exp(-2.4 * dt));

    const tx = t.x + this.lookX - this.shiftX, ty = t.y + this.lookY;
    const fast = falling ? 0.16 : 0.3;
    [this.x, this.vxs] = this.smoothDamp(this.x, tx, this.vxs, 0.26, dt);
    [this.y, this.vys] = this.smoothDamp(this.y, ty, this.vys, fast, dt);
    // never let the player leave the safe frame (hard clamp, soft elsewhere)
    const maxOffY = this.halfH * 0.7, maxOffX = this.halfW * 0.55 + Math.abs(this.shiftX);
    this.y = Math.max(t.y + this.lookY - maxOffY - 0.0, Math.min(t.y + this.lookY + maxOffY, this.y));
    this.x = Math.max(t.x - maxOffX, Math.min(t.x + maxOffX, this.x));
    // level bounds (leave a little extra margin to see the cliff walls)
    const b = this.bounds;
    const loX = b.minX + this.halfW - 5, hiX = b.maxX - this.halfW + 5;
    if (loX < hiX) this.x = Math.max(loX, Math.min(hiX, this.x)); else this.x = (b.minX + b.maxX) / 2;
    this.y = Math.max(b.minY + this.halfH * 0.55, Math.min(b.maxY - this.halfH * 0.4, this.y));
    this.fallTilt += ((falling ? -0.02 : 0) - this.fallTilt) * (1 - Math.exp(-3 * dt));
    this.apply(dt);
  }

  private apply(_dt: number): void {
    const s = this.trauma * this.trauma * 0.45;
    const t = this.time * 38;
    const sx = (noise3(t, 0.5, 0, 3) - 0.5) * 2 * s, sy = (noise3(0.5, t, 0, 7) - 0.5) * 2 * s;
    const cy = this.y + this.dip;
    this.camera.position.set(this.x + sx, cy + 2.3 + sy, this.dist);
    this.camera.lookAt(this.x + sx * 0.5, cy + sy * 0.5, 0);
    this.camera.rotation.z += (noise3(t * 0.7, 9, 0, 5) - 0.5) * s * 0.06 + this.fallTilt;
    this.camera.updateProjectionMatrix();
    this.camera.updateMatrixWorld();
  }

  get focus(): { x: number; y: number } { return { x: this.x, y: this.y }; }
}
