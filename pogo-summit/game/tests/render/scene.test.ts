import { describe, expect, it } from 'vitest';
import * as THREE from 'three';
import { parseMap } from '../../src/map/MapLoader';
import { MapRuntime } from '../../src/map/MapRuntime';
import { BUILTIN_THEMES } from '../../src/map/MapTheme';
import { MapScene } from '../../src/render/map/scene';
import { smallMap } from '../map/fixtures';
import type { MapDocument, MapEntity } from '../../src/map/schema';

function camera(x: number, y: number, dist = 19): THREE.PerspectiveCamera {
  const c = new THREE.PerspectiveCamera(30, 16 / 9, 0.5, 900);
  c.position.set(x, y + 2.3, dist); c.lookAt(x, y, 0); c.updateMatrixWorld(); c.updateProjectionMatrix();
  return c;
}

function mapWithDecor(): MapDocument {
  const raw = smallMap() as Record<string, unknown>;
  const doc = parseMap(raw).doc;
  doc.theme = { ref: 'world_meadow' };
  doc.entities.push(
    { id: 'forest', type: 'decor', prefab: 'tree_cluster', position: { x: 20, y: 0, z: -10 }, properties: { count: 60, spread: 40, seed: 3 } },
    { id: 'rocks', type: 'decor', prefab: 'rock_cluster', position: { x: 10, y: 0, z: -3 }, properties: { count: 20, spread: 30 } },
    { id: 'mtn', type: 'background', prefab: 'mountain', position: { x: 20, y: -10, z: -80 } },
  );
  return doc;
}

describe('MapScene probe', () => {
  it('builds and updates', () => {
    const doc = mapWithDecor();
    const rt = new MapRuntime(doc);
    const scene = new MapScene({ runtime: rt, theme: BUILTIN_THEMES.world_meadow });
    scene.beginFrame();
    scene.update({ dt: 1 / 60, time: 1, camera: camera(10, 3), focus: { x: 10, y: 3 }, tick: 100 });
    console.log(JSON.stringify(scene.stats));
    expect(scene.stats.chunksBuilt).toBeGreaterThan(0);
    scene.dispose();
  });
});
