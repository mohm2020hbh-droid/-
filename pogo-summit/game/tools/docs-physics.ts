// Usage: npm run docs:physics — regenerates the parameter table in ../PHYSICS_MASTER.md from PhysicsConfig.ts (single source of truth).
import { readFileSync, writeFileSync } from 'node:fs';
import { PARAM_DEFS, type ParamDef } from '../src/sim/PhysicsConfig';

const file = new URL('../../PHYSICS_MASTER.md', import.meta.url);
const md = readFileSync(file, 'utf8');
const entries = Object.entries(PARAM_DEFS) as [string, ParamDef][];
const groups = [...new Set(entries.map(([, d]) => d.group))];
const count = (s: string) => entries.filter(([, d]) => d.status === s).length;

const fmtV = (n: number) => (Number.isInteger(n) ? String(n) : String(+n.toPrecision(17)));
let out = `> Generated (${entries.length} parameters): LOCKED_A=${count('LOCKED_A')} · LOCKED_AB=${count('LOCKED_AB')} · SUPPLIED=${count('SUPPLIED')} · DESIGN=${count('DESIGN')}. There is no TUNE_ME and no estimated (class C) value.\n`;
out += '> `LOCKED_*` values come from `Pogostuck_Physics_LOCKED_SPEC.md` exactly as the compiled game stores them. Grade = confidence of the *meaning* (A confirmed, B inferred with named engine semantics, D = supplied by this project, not an original value).\n';
for (const g of groups) {
  out += `\n### ${g}\n\n| Parameter | Value | Unit | Status | Grade | Spec row | Note |\n|---|---|---|---|---|---|---|\n`;
  for (const [k, d] of entries.filter(([, d]) => d.group === g)) {
    out += `| \`${k}\` | ${fmtV(d.value)} | ${d.unit} | ${d.status} | ${d.grade} | ${d.ref} | ${d.note.replace(/\|/g, '/')} |\n`;
  }
}
const next = md.replace(/<!-- PARAMS:BEGIN -->[\s\S]*<!-- PARAMS:END -->/, `<!-- PARAMS:BEGIN -->\n${out}<!-- PARAMS:END -->`);
if (next === md) throw new Error('PARAMS markers not found');
writeFileSync(file, next);
console.log(`PHYSICS_MASTER.md: ${entries.length} parameters written`);
