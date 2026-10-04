import { h } from './dom';
import type { Game } from '../game/Game';
import { PARAM_DEFS, type ParamKey } from '../sim/PhysicsConfig';
import type { InputSource } from '../input/InputSource';
import { NEUTRAL_INPUT, type PogoInput, placeInAir, rotateAboutTip } from '../sim/PogoState';
import { TICK_RATE } from '../sim/math';
import { clamp } from '../sim/math';
import { dtTicks } from '../sim/units';

/**
 * Physics Lab (Phase 18) — inspect the LOCKED SPEC physics live: every locked constant (read-only, with its evidence
 * status), live readouts of the spec state (Q / T / deg / L), and scripted tests (Jump / Wall / Boost / Fall).
 * There are no tuning sliders any more: nothing in the physics is estimated.
 */
type Script = { frames: PogoInput[]; i: number; onEnd?: () => void; until?: (g: Game) => boolean };

export class Lab implements InputSource {
  readonly root: HTMLElement;
  private readout: HTMLElement;
  private script: Script | null = null;
  private lastText = 0;
  private lastNormal = '—';
  private manual: InputSource;
  private angleDeg = 35;
  private load = 95;
  private unsub: (() => void) | null = null;
  tilt = 0;

  constructor(parent: HTMLElement, private readonly game: Game, manual: InputSource, private readonly onExit: () => void) {
    this.manual = manual;
    this.readout = h('div.lab-readout.panel');
    let panel!: HTMLElement;
    const bar = h('div.lab-bar', null,
      this.b('Reset', () => this.game.reset()),
      this.b('Test Jump', () => this.testJump()),
      this.b('Test Wall', () => this.testWall()),
      this.b('Test Boost', () => this.testBoost()),
      this.b('Test Fall', () => this.testFall()),
      this.b('Constants', () => { panel.style.display = panel.style.display === 'none' ? '' : 'none'; }),
      this.b('Exit', () => this.onExit()),
    );
    panel = h('div.lab-panel.panel');
    panel.style.display = 'none';
    panel.append(h('h4', null, 'Test jump'),
      this.slider('Stick angle (° from the normal; + = lean left)', this.angleDeg, -70, 70, 1, v => { this.angleDeg = v; }, '°'),
      this.slider('Spring load L', this.load, 40, 300, 5, v => { this.load = v; }, ''));
    const groups: Record<string, ParamKey[]> = {};
    for (const k of Object.keys(PARAM_DEFS) as ParamKey[]) (groups[PARAM_DEFS[k].group] ??= []).push(k);
    for (const [g, keys] of Object.entries(groups)) {
      panel.append(h('h4', null, g));
      for (const k of keys) {
        const d = PARAM_DEFS[k];
        const tag = h(`span.tag${d.status === 'DESIGN' || d.status === 'SUPPLIED' ? '.des' : '.src'}`, null, `${d.ref} · ${d.status.replace('LOCKED_', '')}`);
        const row = h('div.p', null, h('span.n', null, d.label, tag), h('span', null, `${+d.value.toPrecision(7)} ${d.unit}`));
        row.title = d.note;
        panel.append(row);
      }
    }
    this.root = h('div', { style: { position: 'absolute', inset: '0', pointerEvents: 'none' } }, panel, this.readout, bar);
    parent.append(this.root);
    this.unsub = game.onEvents(ev => { for (const e of ev) { if (e.type === 'land' || e.type === 'wall_hit') this.lastNormal = `${e.type} n=(${e.nx.toFixed(2)}, ${e.ny.toFixed(2)}) ${e.surface ?? ''}`; } });
  }

  private b(label: string, fn: () => void): HTMLElement { return h('button.btn', { onclick: fn }, label); }

  private slider(label: string, value: number, min: number, max: number, step: number, set: (v: number) => void, unit: string): HTMLElement {
    const val = h('span', null, `${value}${unit}`);
    const inp = h('input', { type: 'range', min, max, step, value }) as HTMLInputElement;
    inp.addEventListener('input', () => { set(+inp.value); val.textContent = `${inp.value}${unit}`; });
    return h('div.p', null, h('span.n', null, label), val, h('div', { style: { gridColumn: '1 / -1' } }, inp));
  }

  // ── scripted tests (they drive the REAL input path, so they test what the player experiences) ──
  private run(frames: PogoInput[], opts: Partial<Script> = {}): void { this.script = { frames, i: 0, ...opts }; }
  private rep(n: number, p: Partial<PogoInput>): PogoInput[] { return Array.from({ length: n }, () => ({ ...NEUTRAL_INPUT, ...p })); }

  /** Rotate the stick about the tip to the chosen angle, set the load inside the window, and let the pogo launch itself. */
  testJump(): void {
    const g = this.game, s = g.pogo.state;
    if (!s.grounded) g.reset();
    const st = g.pogo.state;
    rotateAboutTip(g.cfg, st, st.thetaN - g.cfg.normalAngleOffset + this.angleDeg);
    st.load = clamp(this.load, st.loadMin, st.loadMax);
    st.springBone = st.springBoneMax = st.load;
    this.run(this.rep(3, {}));
  }

  testFall(): void {
    const s = this.game.pogo.state;
    placeInAir(this.game.cfg, s, s.x < 20 ? 8 : s.x, 36);
    this.game.resetCamera();
    this.run([]);
  }

  /** Throw the pogo at the nearest wall to see the E14 bounce. */
  testWall(): void {
    const s = this.game.pogo.state;
    const k = this.game.cfg.qPerMetre;
    placeInAir(this.game.cfg, s, Math.max(this.game.level.bounds.minX + 6, s.x - 8), s.y + 6, -60, 10);
    void k;
    this.game.resetCamera();
    this.run([]);
  }

  /** Spin in the air until the 285° threshold arms the power jump (E13). */
  testBoost(): void {
    placeInAir(this.game.cfg, this.game.pogo.state, 8, 38, 0, 6);
    this.game.resetCamera();
    this.run(this.rep(600, { tilt: 1 }), { until: g => g.pogo.state.boostReady, onEnd: () => this.run(this.rep(60, { tilt: 0 })) });
  }

  // ── InputSource (script overrides manual touch) ──
  sample(): PogoInput {
    if (this.script) {
      const sc = this.script;
      if (sc.until?.(this.game) || sc.i >= sc.frames.length) {
        const end = sc.onEnd; this.script = null; end?.();
        return this.script ? this.sample() : this.manual.sample();
      }
      const f = sc.frames[sc.i++];
      this.tilt = f.tilt;
      return f;
    }
    const m = this.manual.sample();
    this.tilt = this.manual.tilt;
    return m;
  }
  dispose(): void { this.unsub?.(); this.root.remove(); }

  /** Call every display frame. */
  update(): void {
    const g = this.game, s = g.pogo.state, cfg = g.cfg;
    const now = performance.now();
    if (now - this.lastText < 90) return;
    this.lastText = now;
    const w = g.world.colliders[s.groundId];
    const ground = s.grounded ? `${w?.id ?? '?'} (${w?.surface}) n=(${s.nx.toFixed(2)}, ${s.ny.toFixed(2)}) θn=${s.thetaN.toFixed(1)}°` : 'none';
    this.readout.textContent = [
      `mode ${s.mode}   tick ${s.tick}  (${TICK_RATE} Hz · Δt = ${dtTicks(cfg).toFixed(6)} T)   hold ${s.held}`,
      `g ${s.grounded ? 1 : 0}   N ${s.noGround.toFixed(2)} T   J ${s.jumpTimer.toFixed(2)} T   p ${s.boost}   slide ${s.slideMode ? 'on' : 'off'} (${s.sx.toFixed(1)}, ${s.sy.toFixed(1)})`,
      `v (${s.qvx.toFixed(2)}, ${s.qvy.toFixed(2)}) Q/T = (${s.vx.toFixed(2)}, ${s.vy.toFixed(2)}) m/s   |v| ${Math.hypot(s.qvx, s.qvy).toFixed(1)} / ${cfg.maxSpeed}`,
      `θ ${s.theta.toFixed(1)}° (θj ${s.thetaJump.toFixed(1)}°, Δ ${Math.abs(s.theta - s.thetaJump).toFixed(0)}° / ${cfg.boostRotation}°)   ω ${s.omega.toFixed(2)} °/T`,
      `L ${s.load.toFixed(1)}  window [${s.loadMin.toFixed(1)}, ${s.loadMax.toFixed(1)}]  last launch ${s.loadLast.toFixed(1)}  impact I ${s.lastImpact.toFixed(1)}`,
      `hull z_min ${s.hullMinZ.toFixed(1)} Q (ext X ${s.springExt.toFixed(1)})   origin (${s.qx.toFixed(0)}, ${s.qy.toFixed(0)}) Q = (${s.x.toFixed(2)}, ${s.y.toFixed(2)}) m`,
      `ground contact ${ground}`,
      `last collision ${this.lastNormal}`,
      `height ${s.y.toFixed(2)} m   jumps ${s.jumps}  boosts ${s.boosts}  falls ${s.falls}`,
      `fps ${g.renderer.fps.toFixed(0)}   draw calls ${g.renderer.stats.calls}   tris ${g.renderer.stats.triangles}`,
    ].join('\n');
  }
}
