/** Issues produced by the loader (structure) and the validator (quality). One shape for both (SPEC §14). */
export type Severity = 'ERROR' | 'WARNING' | 'INFO';

export interface MapIssue {
  severity: Severity;
  code: string;
  message: string;
  /** JSON-pointer-like path into the document, e.g. `/entities/3/collision/shape`. */
  path: string;
  entityId?: string;
  hint?: string;
}

export const mkIssue = (severity: Severity, code: string, path: string, message: string, extra: Partial<MapIssue> = {}): MapIssue =>
  ({ severity, code, message, path, ...extra });

export class MapLoadError extends Error {
  constructor(readonly issues: MapIssue[]) {
    super(`map load failed: ${issues.filter(i => i.severity === 'ERROR').length} error(s): ${issues.filter(i => i.severity === 'ERROR').slice(0, 3).map(i => `${i.path} ${i.message}`).join('; ')}`);
  }
}

export interface ValidationReport {
  ok: boolean;
  counts: { error: number; warning: number; info: number };
  issues: MapIssue[];
}

export function makeReport(issues: MapIssue[]): ValidationReport {
  const counts = { error: 0, warning: 0, info: 0 };
  for (const i of issues) { if (i.severity === 'ERROR') counts.error++; else if (i.severity === 'WARNING') counts.warning++; else counts.info++; }
  return { ok: counts.error === 0, counts, issues };
}
