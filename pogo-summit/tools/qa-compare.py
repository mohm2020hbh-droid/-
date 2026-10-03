#!/usr/bin/env python3
"""Visual-QA metrics: reference image vs game screenshots (Phase 20).

Usage: python3 tools/qa-compare.py <reference.webp> <shot.png> [<shot2.png> ...]

The reference is cropped to the gameplay viewport (top ~65 % of the mock-up: the lower strip is the world/platform
catalogue). Metrics are deliberately simple and reproducible; they are a *guard-rail* for the art direction, not a
similarity score:
  * mean luminance / saturation          (flat, washed-out or murky frames show up immediately)
  * sky fraction in the top half         (blue-dominant, bright pixels: the reference reads as an open vista)
  * warm fraction (autumn orange / red)  (the reference's identity colour)
  * dominant colours (k-means, k=6)
  * edge density                         (detail richness: Sobel magnitude mean)
"""
import sys
import numpy as np
from PIL import Image


def load(path, crop_top_frac=None):
    im = Image.open(path).convert('RGB')
    if crop_top_frac:
        im = im.crop((0, 0, im.width, int(im.height * crop_top_frac)))
    # normalise the size so edge density is comparable
    h = 360
    im = im.resize((int(im.width * h / im.height), h), Image.LANCZOS)
    return np.asarray(im).astype(np.float32) / 255.0


def hsv(a):
    mx, mn = a.max(2), a.min(2)
    d = mx - mn
    s = np.where(mx > 1e-6, d / np.maximum(mx, 1e-6), 0)
    r, g, b = a[..., 0], a[..., 1], a[..., 2]
    h = np.zeros_like(mx)
    m = d > 1e-6
    rc = (mx == r) & m; gc = (mx == g) & m & ~rc; bc = (mx == b) & m & ~rc & ~gc
    h[rc] = ((g - b)[rc] / d[rc]) % 6
    h[gc] = (b - r)[gc] / d[gc] + 2
    h[bc] = (r - g)[bc] / d[bc] + 4
    return h * 60.0, s, mx


def sobel_mean(lum):
    gx = lum[:, 2:] - lum[:, :-2]
    gy = lum[2:, :] - lum[:-2, :]
    return float(np.hypot(gx[1:-1, :], gy[:, 1:-1]).mean())


def kmeans(px, k=6, iters=14, seed=1):
    rng = np.random.default_rng(seed)
    px = px[rng.choice(len(px), min(len(px), 20000), replace=False)]
    c = px[rng.choice(len(px), k, replace=False)]
    for _ in range(iters):
        d = ((px[:, None, :] - c[None]) ** 2).sum(2)
        a = d.argmin(1)
        for i in range(k):
            if (a == i).any():
                c[i] = px[a == i].mean(0)
    share = np.bincount(a, minlength=k) / len(a)
    order = np.argsort(-share)
    return [(tuple(int(v * 255) for v in c[i]), float(share[i])) for i in order]


def metrics(a):
    h, s, v = hsv(a)
    lum = 0.2126 * a[..., 0] + 0.7152 * a[..., 1] + 0.0722 * a[..., 2]
    top = slice(0, a.shape[0] // 2)
    sky = ((h[top] > 185) & (h[top] < 250) & (s[top] > 0.18) & (v[top] > 0.55)).mean()
    cloud = ((s[top] < 0.18) & (v[top] > 0.85)).mean()
    warm = (((h < 45) | (h > 335)) & (s > 0.45) & (v > 0.35)).mean()
    green = ((h > 70) & (h < 160) & (s > 0.3) & (v > 0.25)).mean()
    return {
        'lum': float(lum.mean()), 'lum_std': float(lum.std()), 'sat': float(s.mean()),
        'sky_top': float(sky), 'cloud_top': float(cloud), 'warm': float(warm), 'green': float(green),
        'edge': sobel_mean(lum),
    }


def fmt(name, m, pal):
    row = ' '.join(f'{k}={v:.3f}' for k, v in m.items())
    cols = ' '.join('#%02x%02x%02x(%.0f%%)' % (*c, s * 100) for c, s in pal)
    return f'{name:28s} {row}\n{"":28s} palette: {cols}'


if __name__ == '__main__':
    ref, *shots = sys.argv[1:]
    a = load(ref, 0.655)
    print(fmt('REFERENCE (gameplay crop)', metrics(a), kmeans(a.reshape(-1, 3))))
    for sh in shots:
        b = load(sh)
        print(fmt(sh.split('/')[-1], metrics(b), kmeans(b.reshape(-1, 3))))
