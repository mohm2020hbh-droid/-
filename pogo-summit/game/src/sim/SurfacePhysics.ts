/**
 * SurfacePhysics — surface kinds.
 *
 * The LOCKED SPEC defines exactly ONE surface-dependent rule: landing on a *slippery* surface enters slide mode (E15).
 * Every other surface kind behaves as an ordinary solid. In particular the earlier project-invented surface physics
 * (bounce pad restitution, sticky/boost multipliers, friction coefficients) is gone: the original has no such numbers
 * in the locked spec. Kinds are kept as level data so existing levels keep their visuals.
 */
export type SurfaceId = 'normal' | 'bounce' | 'slippery' | 'sticky' | 'hazard' | 'boost' | 'goal';

/** Only slippery surfaces change the physics (E15 slide entry). */
export const isSlippery = (s: SurfaceId): boolean => s === 'slippery';
