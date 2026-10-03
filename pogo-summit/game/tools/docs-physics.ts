// Usage: npm run docs:physics — regenerates the parameter table in ../PHYSICS_MASTER.md from PhysicsConfig.ts (single source of truth).
import { readFileSync, writeFileSync } from 'node:fs';
import { PARAM_DEFS, type ParamDef } from '../src/sim/PhysicsConfig';

const file = new URL('../../PHYSICS_MASTER.md', import.meta.url);
const md = readFileSync(file, 'utf8');
const entries = Object.entries(PARAM_DEFS) as [string, ParamDef][];
const groups = [...new Set(entries.map(([, d]) => d.group))];
const fmt = (n: number) => (Number.isInteger(n) ? String(n) : String(+n.toPrecision(6)));
const count = (s: string) => entries.filter(([, d]) => d.status === s).length;

let out = `> مولَّد آليًا (${entries.length} معاملًا): TUNE_ME=${count('TUNE_ME')} · SOURCE_A=${count('SOURCE_A')} · DESIGN=${count('DESIGN')} · MEASURED_C=${count('MEASURED_C')}.\n`;
out += '> `القيمة` هي قيمتنا الابتدائية للعب، **وليست** قيمة Pogostuck الأصلية إلا حيث الحالة `SOURCE_A`. الدرجة = ثقة القيمة *الأصلية* (D = مجهولة).\n';
for (const g of groups) {
  out += `\n### ${g}\n\n| المعامل | القيمة | الوحدة | المدى | الحالة | الدرجة | مرجع XLSX | ملاحظة |\n|---|---|---|---|---|---|---|---|\n`;
  for (const [k, d] of entries.filter(([, d]) => d.group === g)) {
    const range = d.step === 0 ? '—' : `${fmt(d.min)} … ${fmt(d.max)}`;
    out += `| \`${k}\` | ${fmt(d.value)} | ${d.unit} | ${range} | ${d.status} | ${d.grade} | ${d.ref} | ${d.note.replace(/\|/g, '/')} |\n`;
  }
}
const next = md.replace(/<!-- PARAMS:BEGIN -->[\s\S]*<!-- PARAMS:END -->/, `<!-- PARAMS:BEGIN -->\n${out}<!-- PARAMS:END -->`);
if (next === md) throw new Error('PARAMS markers not found');
writeFileSync(file, next);
console.log(`PHYSICS_MASTER.md: ${entries.length} parameters written`);
