import { App } from './App';
import { createPlantedState } from './sim/PogoState';

/**
 * Entry point. Query flags (QA / development only, never shown to players):
 *   ?debug=1 (window.__pogo + debug keyboard)  ?lab=1 (Physics Lab)  ?autostart=1[&level=…]  ?world=world_2  ?quality=…
 */
const q = new URLSearchParams(location.search);
const canvas = document.getElementById('game') as HTMLCanvasElement;
const ui = document.getElementById('ui') as HTMLElement;
ui.style.pointerEvents = 'none';
const app = new App(canvas, ui, q);

if (q.get('debug') === '1') {
  const game = app.game;
  (window as unknown as { __pogo: unknown }).__pogo = {
    app, game,
    snapshot: () => app.snapshot(),
    /** QA: stand the player on platform `id` at fraction t along its top. */
    plant(id: string, t = 0.5) { const w = game.world; const i = w.colliders.findIndex(c => c.id === id); game.pogo.state = createPlantedState(w, game.cfg, i, t, game.pogo.state.tick); game.resetCamera(); },
    cam(x: number, y: number) { game.renderer.snapCamera(x, y); },
    light(o: { sun?: number; hemi?: number; exposure?: number; rim?: number; tone?: string }) { game.renderer.setLighting(o); },
    zoom(d: number) { game.renderer.rig.baseDistance = d; },
    hide(prefix: string, on = false) { game.renderer.scene.traverse(o => { if (o.name.startsWith(prefix)) o.visible = on; }); },
    stepFrames(n: number, dt = 1 / 60) { for (let i = 0; i < n; i++) game.tick(dt); },
  };
}
app.boot();
(window as unknown as { __ready: boolean }).__ready = true;
