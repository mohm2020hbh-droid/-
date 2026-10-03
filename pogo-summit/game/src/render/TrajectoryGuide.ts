import * as THREE from 'three';
import { glowTexture } from './builders/special';

/**
 * TrajectoryGuide — aim assist. Shows the predicted arc (computed with the REAL physics via simulateJump) as fading dots
 * while the player is charging. Off / Short / Full is a user setting; the original game has no such aid, so it is a
 * mobile-friendliness feature (DESIGN, not TUNE_ME).
 */
export class TrajectoryGuide {
  readonly group = new THREE.Group();
  private sprites: THREE.Sprite[] = [];
  private readonly max = 48;

  constructor() {
    const tex = glowTexture('#ffffff');
    for (let i = 0; i < this.max; i++) {
      const s = new THREE.Sprite(new THREE.SpriteMaterial({ map: tex, color: '#fff3c0', transparent: true, depthWrite: false, depthTest: false, opacity: 0 }));
      s.renderOrder = 20; s.visible = false;
      this.sprites.push(s); this.group.add(s);
    }
  }

  /** `points` = x,y pairs. */
  set(points: number[] | null, landX?: number, landY?: number): void {
    const n = points ? Math.min(this.max, points.length / 2) : 0;
    for (let i = 0; i < this.max; i++) {
      const s = this.sprites[i];
      if (i < n) {
        const t = i / Math.max(1, n - 1);
        s.visible = true;
        s.position.set(points![i * 2], points![i * 2 + 1], 0.8);
        const sc = 0.5 - 0.22 * t;
        s.scale.set(sc, sc, 1);
        (s.material as THREE.SpriteMaterial).opacity = 0.9 * (1 - t * 0.75);
      } else s.visible = false;
    }
    void landX; void landY;
  }
}
