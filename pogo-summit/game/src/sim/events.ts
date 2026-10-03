/** Events emitted by the simulation. They are the ONLY contract with audio / haptics / VFX / UI. */
export type SimEventType =
  | 'charge_start' | 'launch' | 'land' | 'hard_impact' | 'bounce' | 'wall_hit' | 'slide'
  | 'boost_armed' | 'boost' | 'boost_pad' | 'hazard' | 'fall' | 'respawn' | 'goal';

export interface SimEvent {
  type: SimEventType;
  tick: number;
  /** Event position (metres). */
  x: number;
  y: number;
  /** Normalised 0..1 strength (impact speed, charge power…). */
  intensity: number;
  /** Contact normal where relevant. */
  nx: number;
  ny: number;
  surface?: string;
  material?: string;
  /** Index of the collider involved (when applicable). */
  collider?: number;
}

export const makeEvent = (
  type: SimEventType, tick: number, x: number, y: number, intensity: number,
  nx = 0, ny = 1, surface?: string, material?: string, collider?: number,
): SimEvent => ({ type, tick, x, y, intensity, nx, ny, surface, material, collider });
