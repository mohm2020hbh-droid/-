import { describe, expect, it } from 'vitest';
import { readdirSync, readFileSync } from 'node:fs';
import { join, relative } from 'node:path';

const SIM = join(process.cwd(), 'src', 'sim');
const FORBIDDEN = /from\s+['"](?:three|(?:\.\.\/)+(?:render|ui|audio|haptics|input|progression|game|lab))/;

function list(dir: string): string[] {
  return readdirSync(dir, { withFileTypes: true }).flatMap(e => e.isDirectory() ? list(join(dir, e.name)) : e.name.endsWith('.ts') ? [join(dir, e.name)] : []);
}
const stripComments = (s: string) => s.replace(/\/\*[\s\S]*?\*\//g, '').replace(/(^|[^:])\/\/.*$/gm, '$1');

describe('architecture: sim/ (Physics Core) is pure (PROJECT_ARCHITECTURE §4)', () => {
  const files = list(SIM);
  it('has simulation files, including the core/ modules', () => {
    expect(files.length).toBeGreaterThan(14);
    expect(files.some(f => relative(SIM, f).startsWith('core'))).toBe(true);
  });
  for (const f of files) {
    it(`${relative(SIM, f)} has no DOM / Three.js / presentation imports and no clock or randomness`, () => {
      const src = stripComments(readFileSync(f, 'utf8'));
      expect(src).not.toMatch(FORBIDDEN);
      expect(src).not.toMatch(/\b(document|window|navigator|requestAnimationFrame|performance\.now|Date\.now|Math\.random)\b/);
    });
  }
});
