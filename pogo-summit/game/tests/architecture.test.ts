import { describe, expect, it } from 'vitest';
import { readdirSync, readFileSync } from 'node:fs';
import { join } from 'node:path';

const SIM = join(process.cwd(), 'src', 'sim');
const FORBIDDEN = /from\s+['"](?:three|\.\.\/(?:render|ui|audio|haptics|input|progression|game|lab)|\.\.\/\.\.\/(?:render|ui|audio|haptics|input))/;

describe('architecture: sim/ is pure (PROJECT_ARCHITECTURE §4)', () => {
  const files = readdirSync(SIM).filter(f => f.endsWith('.ts'));
  it('has simulation files', () => expect(files.length).toBeGreaterThan(8));
  for (const f of files) {
    it(`${f} has no DOM / Three.js / presentation imports`, () => {
      const src = readFileSync(join(SIM, f), 'utf8');
      expect(src).not.toMatch(FORBIDDEN);
      expect(src).not.toMatch(/\b(document|window|navigator|requestAnimationFrame|performance\.now|Date\.now|Math\.random)\b/);
    });
  }
});
