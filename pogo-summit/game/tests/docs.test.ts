import { describe, it, expect } from 'vitest';
import { readFileSync } from 'node:fs';
import { join } from 'node:path';
import { PARAM_DEFS } from '../src/sim/PhysicsConfig';

describe('PHYSICS_MASTER.md stays in sync with PhysicsConfig', () => {
  const md = readFileSync(join(process.cwd(), '..', 'PHYSICS_MASTER.md'), 'utf8');
  it('lists every parameter with its value and status (run `npm run docs:physics` after editing PARAM_DEFS)', () => {
    for (const [k, d] of Object.entries(PARAM_DEFS)) {
      const row = md.split('\n').find(l => l.startsWith(`| \`${k}\` |`));
      expect(row, `missing row for ${k}`).toBeTruthy();
      expect(row).toContain(`| ${d.status} | ${d.grade} |`);
    }
  });
  it('has no TUNE_ME value and no estimated (class C) value; every non-locked value is grade D', () => {
    for (const [k, d] of Object.entries(PARAM_DEFS)) {
      expect(['LOCKED_A', 'LOCKED_AB', 'SUPPLIED', 'DESIGN'], k).toContain(d.status);
      if (d.status === 'SUPPLIED' || d.status === 'DESIGN') expect(d.grade, k).toBe('D');
    }
  });
});
