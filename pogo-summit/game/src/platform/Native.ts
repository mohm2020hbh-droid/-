/**
 * Native bridge (Android WebView). The Java host exposes `window.PogoNative` (see android/…/NativeBridge.java):
 *   vibrate(ms, amplitude 1..255) · saveString(key,value) · loadString(key) · getInsets() · exitApp()
 * Every call is guarded: on a normal browser (QA / desktop preview) everything falls back to web APIs.
 */
interface Bridge {
  vibrate?(ms: number, amp: number): void;
  vibratePattern?(pattern: string, amp: number): void;
  saveString?(k: string, v: string): void;
  loadString?(k: string): string | null;
  getInsets?(): string;
  exitApp?(): void;
}
const b = (): Bridge | undefined => (window as unknown as { PogoNative?: Bridge }).PogoNative;

export const Native = {
  get available(): boolean { return !!b(); },
  vibrate(ms: number, amp01: number): void {
    try {
      const n = b();
      if (n?.vibrate) { n.vibrate(Math.round(ms), Math.max(1, Math.min(255, Math.round(amp01 * 255)))); return; }
      navigator.vibrate?.(Math.round(ms));
    } catch { /* haptics are best-effort */ }
  },
  vibratePattern(pattern: number[], amp01: number): void {
    try {
      const n = b();
      if (n?.vibratePattern) { n.vibratePattern(pattern.join(','), Math.max(1, Math.min(255, Math.round(amp01 * 255)))); return; }
      navigator.vibrate?.(pattern);
    } catch { /* ignore */ }
  },
  save(key: string, value: string): void {
    try { const n = b(); if (n?.saveString) n.saveString(key, value); } catch { /* ignore */ }
    try { localStorage.setItem(key, value); } catch { /* private mode / blocked */ }
  },
  load(key: string): string | null {
    try { const n = b(); const v = n?.loadString?.(key); if (typeof v === 'string' && v.length) return v; } catch { /* ignore */ }
    try { return localStorage.getItem(key); } catch { return null; }
  },
  insets(): { top: number; right: number; bottom: number; left: number } {
    try { const s = b()?.getInsets?.(); if (s) { const o = JSON.parse(s); return { top: +o.top || 0, right: +o.right || 0, bottom: +o.bottom || 0, left: +o.left || 0 }; } } catch { /* ignore */ }
    return { top: 0, right: 0, bottom: 0, left: 0 };
  },
  exit(): void { try { b()?.exitApp?.(); } catch { /* ignore */ } },
};
