import * as THREE from 'three';
import { App } from './App';
import { createPlantedState, placeInAir } from './sim/PogoState';
import { Native } from './platform/Native';
import { ScriptInput } from './input/InputSource';
import { DT } from './sim/math';

/**
 * Entry point. Query flags (QA / development only, never shown to players):
 *   ?debug=1 (window.__pogo + debug keyboard)  ?lab=1 (Physics Lab)  ?autostart=1[&level=…]  ?world=world_2  ?quality=…
 */
const q = new URLSearchParams(location.search);
const canvas = document.getElementById('game') as HTMLCanvasElement;
const ui = document.getElementById('ui') as HTMLElement;
ui.style.pointerEvents = 'none';
// native safe-area fallback (display cutouts) when CSS env() reports 0 inside the WebView
const ins = Native.insets();
for (const [k, v] of [['--nt', ins.top], ['--nr', ins.right], ['--nb', ins.bottom], ['--nl', ins.left]] as const) document.documentElement.style.setProperty(k, `${v}px`);
const app = new App(canvas, ui, q);

if (q.get('debug') === '1') {
  const game = app.game;
  (window as unknown as { __pogo: unknown }).__pogo = {
    app, game,
    snapshot: () => app.snapshot(),
    /** QA: stand the player on platform `id` at fraction t along its top. */
    plant(id: string, t = 0.5) { const w = game.world; const i = w.colliders.findIndex(c => c.id === id); game.pogo.state = createPlantedState(w, game.cfg, i, t, game.pogo.state.tick); game.resetCamera(); },
    /** QA (Map V2): load + activate the chunks around entity `id`, then stand the player on it. */
    warp(id: string, dx = 0, dy = 2.5) {
      const rt = game.mapRuntime; const e = rt?.doc.entities.find(x => x.id === id);
      if (!rt || !e) return false;
      const s = game.pogo.state;
      placeInAir(game.cfg, s, e.position.x + dx, e.position.y + dy);        // free flight just above it: the pogo lands by itself
      rt.beforeStep(s);                                                      // streaming follows the player
      game.resetCamera();
      return true;
    },
    cam(x: number, y: number) { game.renderer.snapCamera(x, y); },
    light(o: { sun?: number; hemi?: number; exposure?: number; rim?: number; tone?: string }) { game.renderer.setLighting(o); },
    zoom(d: number) { game.renderer.rig.baseDistance = d; },
    hide(prefix: string, on = false) { game.renderer.scene.traverse(o => { if (o.name.startsWith(prefix)) o.visible = on; }); },
    /** QA: fast-forward a recorded input script ([tilt, held] per tick) through the REAL game loop (no rendering). */
    playScript(rows: [number, number, number?][]) {
      const frames = rows.map(([tilt, held, pull]) => ({ tilt, jumpHeld: !!held, pull: pull ?? 0, boostPressed: false, cancel: false }));
      const inp = new ScriptInput(frames);
      game.reset(); // tick 0: moving platforms are a function of the absolute tick, the recorded plan assumes a fresh start
      const prev = game.input; game.input = inp; game.renderEvery = 100000;
      let guard = 0;
      while (!inp.done && guard++ < 200000) game.tick(DT);
      game.renderEvery = 1; game.input = prev;
      return app.snapshot();
    },
    stepFrames(n: number, dt = 1 / 60) { for (let i = 0; i < n; i++) game.tick(dt); },
    /** QA: what is drawn at a world point (nearest hits of a ray from the camera through it). */
    probe(x: number, y: number, z = 0) {
      const r = game.renderer, cam = r.rig.camera; const v = new THREE.Vector3(x, y, z).project(cam);
      const ray = new THREE.Raycaster(); ray.setFromCamera(new THREE.Vector2(v.x, v.y), cam);
      return ray.intersectObjects(r.scene.children, true).slice(0, 6).map(h => `${h.object.name || h.object.type} d=${h.distance.toFixed(2)} z=${h.point.z.toFixed(2)}`);
    },
  };
}
app.boot();
(window as unknown as { __ready: boolean }).__ready = true;
