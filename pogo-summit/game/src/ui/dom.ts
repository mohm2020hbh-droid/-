/** Tiny DOM helper (no framework): h('div.cls#id', {attrs}, children…) */
type Child = Node | string | number | null | undefined | false;
export function h<K extends keyof HTMLElementTagNameMap>(tag: string, attrs?: Record<string, unknown> | null, ...children: Child[]): HTMLElementTagNameMap[K] {
  const m = /^([a-z0-9]+)?((?:[.#][\w-]+)*)$/i.exec(tag)!;
  const el = document.createElement(m[1] || 'div') as HTMLElementTagNameMap[K];
  for (const part of m[2].match(/[.#][\w-]+/g) ?? []) part[0] === '.' ? el.classList.add(part.slice(1)) : (el.id = part.slice(1));
  if (attrs) for (const [k, v] of Object.entries(attrs)) {
    if (v === undefined || v === null || v === false) continue;
    if (k.startsWith('on') && typeof v === 'function') el.addEventListener(k.slice(2).toLowerCase(), v as EventListener);
    else if (k === 'style' && typeof v === 'object') Object.assign(el.style, v);
    else if (k === 'html') el.innerHTML = String(v);
    else el.setAttribute(k, v === true ? '' : String(v));
  }
  for (const c of children) if (c !== null && c !== undefined && c !== false) el.append(c instanceof Node ? c : String(c));
  return el;
}
export const svg = (html: string, cls = ''): HTMLElement => { const d = document.createElement('span'); d.className = `ico ${cls}`; d.innerHTML = html; return d; };
