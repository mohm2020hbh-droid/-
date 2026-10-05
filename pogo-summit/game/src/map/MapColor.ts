/** Small colour helpers shared by the map modules (hex strings only; no DOM, no Three.js). */

export function parseHex(c: string): [number, number, number] {
  const m = /^#([0-9a-f]{3}|[0-9a-f]{6})$/i.exec(c.trim());
  if (!m) return [0, 0, 0];
  let h = m[1];
  if (h.length === 3) h = h.split('').map(x => x + x).join('');
  return [parseInt(h.slice(0, 2), 16), parseInt(h.slice(2, 4), 16), parseInt(h.slice(4, 6), 16)];
}
export const isHexColor = (c: string): boolean => /^#([0-9a-f]{3}|[0-9a-f]{6})$/i.test(c.trim());
export function toHex(rgb: [number, number, number]): string { return '#' + rgb.map(v => Math.max(0, Math.min(255, Math.round(v))).toString(16).padStart(2, '0')).join(''); }
export function lerpColor(a: string, b: string, t: number): string {
  const x = parseHex(a), y = parseHex(b);
  return toHex([x[0] + (y[0] - x[0]) * t, x[1] + (y[1] - x[1]) * t, x[2] + (y[2] - x[2]) * t]);
}
/** Multiply a colour's channels (k < 1 darkens, k > 1 brightens, clamped). */
export function scaleColor(c: string, k: number): string { const [r, g, b] = parseHex(c); return toHex([r * k, g * k, b * k]); }
/** Relative luminance 0..1 (sRGB weights, no gamma — good enough for contrast heuristics). */
export function luminance(c: string): number { const [r, g, b] = parseHex(c); return (0.2126 * r + 0.7152 * g + 0.0722 * b) / 255; }
