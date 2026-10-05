import { describe, expect, it } from 'vitest';
import { CameraRig, type CameraTarget } from '../../src/render/CameraRig';
import { DEFAULT_CAMERA_PROFILE } from '../../src/map/MapCamera';

const tgt = (over: Partial<CameraTarget> = {}): CameraTarget => ({ x: 0, y: 0, vx: 0, vy: 0, grounded: true, charging: false, charge01: 0, boosting: false, ...over });
const settle = (rig: CameraRig, t: CameraTarget, secs = 6): void => { for (let i = 0; i < secs * 60; i++) rig.update(1 / 60, t, 16 / 9); };

describe('CameraRig profile (map CameraProfile drives the follow camera)', () => {
  it('the default profile is the camera the game always had (distance 19, fov 30)', () => {
    const rig = new CameraRig();
    rig.snap(0, 0); settle(rig, tgt());
    expect(rig.camera.fov).toBeCloseTo(30, 3);
    expect(rig.camera.position.z).toBeCloseTo(19, 1);
  });
  it('followDistance / zoom / fov change the framing', () => {
    const base = new CameraRig(), far = new CameraRig(), zoomed = new CameraRig(), wide = new CameraRig();
    for (const r of [base, far, zoomed, wide]) r.snap(0, 0);
    far.setProfile({ ...DEFAULT_CAMERA_PROFILE, followDistance: 34 });
    zoomed.setProfile({ ...DEFAULT_CAMERA_PROFILE, zoom: 1.5 });
    wide.setProfile({ ...DEFAULT_CAMERA_PROFILE, fov: 44 });
    for (const r of [base, far, zoomed, wide]) settle(r, tgt());
    expect(far.camera.position.z).toBeGreaterThan(base.camera.position.z + 10);
    expect(zoomed.camera.position.z).toBeGreaterThan(base.camera.position.z);
    expect(wide.camera.fov).toBeGreaterThan(base.camera.fov + 10);
    expect(far.halfH).toBeGreaterThan(base.halfH); expect(wide.halfH).toBeGreaterThan(base.halfH);
  });
  it('height raises the camera and lookAhead shifts the framing toward the motion', () => {
    const a = new CameraRig(), b = new CameraRig(), c = new CameraRig();
    for (const r of [a, b, c]) r.snap(0, 0);
    b.setProfile({ ...DEFAULT_CAMERA_PROFILE, height: DEFAULT_CAMERA_PROFILE.height + 3 });
    c.setProfile({ ...DEFAULT_CAMERA_PROFILE, lookAhead: 6, lookAheadGain: 1 });
    settle(a, tgt({ vx: 8 })); settle(b, tgt({ vx: 8 })); settle(c, tgt({ vx: 8 }));
    expect(b.camera.position.y).toBeGreaterThan(a.camera.position.y + 2.5);
    expect(c.focus.x).toBeGreaterThan(a.focus.x + 0.5);
  });
  it('stronger smoothing lags more behind a moving target', () => {
    const tight = new CameraRig(), loose = new CameraRig();
    tight.setProfile({ ...DEFAULT_CAMERA_PROFILE, smoothing: 0.05 }); loose.setProfile({ ...DEFAULT_CAMERA_PROFILE, smoothing: 0.6 });
    tight.snap(0, 0); loose.snap(0, 0);
    for (let i = 0; i < 20; i++) { tight.update(1 / 60, tgt({ x: i * 0.3, vx: 18 }), 16 / 9); loose.update(1 / 60, tgt({ x: i * 0.3, vx: 18 }), 16 / 9); }
    expect(loose.focus.x).toBeLessThan(tight.focus.x);
  });
  it('resetProfile restores the defaults (level change)', () => {
    const r = new CameraRig();
    r.setProfile({ ...DEFAULT_CAMERA_PROFILE, followDistance: 40, fov: 50 });
    r.resetProfile();
    expect(r.currentProfile).toEqual(DEFAULT_CAMERA_PROFILE);
    r.snap(0, 0); settle(r, tgt());
    expect(r.camera.position.z).toBeCloseTo(19, 1);
  });
  it('the player stays inside the safe frame whatever the profile', () => {
    const r = new CameraRig();
    r.setProfile({ ...DEFAULT_CAMERA_PROFILE, smoothing: 1, smoothingY: 1 });
    r.snap(0, 0);
    for (let i = 0; i < 120; i++) r.update(1 / 60, tgt({ x: i * 1.2, y: i * 0.8, vx: 60, vy: 40, grounded: false }), 16 / 9);
    expect(Math.abs(r.focus.x - 119 * 1.2)).toBeLessThan(r.halfW * 0.6 + 1);
    expect(Math.abs(r.focus.y - 119 * 0.8)).toBeLessThan(r.halfH * 0.8 + 3);
  });
});
