#!/usr/bin/env python3
"""
audio-analyze.py — measures WAV files (format, level, loudness, spectrum, envelope) with numpy only.

Usage:  python3 tools/audio-analyze.py <dir-with-wavs> [out.json]

Used by POGOSTUCK_AUDIO_ANALYSIS.md. It reads files, prints numbers, and never writes audio: the analysed recordings are
inputs for measurement, not assets of this project.

Definitions (every number in the report comes from exactly these):
  peak_dbfs        20·log10(max |x|)                           sample peak of the whole file
  true_peak_dbfs   same, after 4× FFT oversampling             (ITU-R BS.1770 style estimate)
  rms_dbfs         20·log10(sqrt(mean(x²)))                    all channels, whole file
  lufs_*           ITU-R BS.1770-4 K-weighting (two biquads, coefficients recomputed for the file's sample rate);
                   `lufs_integrated` uses 400 ms blocks / 75 % overlap with the −70 LUFS and −10 LU gates and exists only for files
                   ≥ 400 ms; `lufs_ungated` is the K-weighted mean-square energy of the whole file.
  env              RMS envelope, 5 ms window, 1 ms hop, mono mix, dB
  onset_ms         first time |x| exceeds 0.001 (−60 dBFS)
  attack_ms        onset → envelope maximum;  rise_10_90_ms  envelope from 10 % to 90 % of its maximum (linear amplitude)
  decay_*_ms       envelope maximum → first time the envelope falls 20 / 40 dB below the maximum and stays below
  rt60_ms          Schroeder backward integration, line fit between −5 and −25 dB, extrapolated to −60 dB
  centroid_hz      power-weighted mean frequency; rolloff_*  frequency below which 85 / 95 % of the power lies
"""
import json
import math
import struct
import sys
from pathlib import Path

import numpy as np


# ───────────────────────────────────────────────────────────── RIFF / WAV ──
def read_wav(path: Path):
    raw = path.read_bytes()
    if raw[:4] != b'RIFF' or raw[8:12] != b'WAVE':
        raise ValueError('not a RIFF/WAVE file')
    pos, chunks, fmt, pcm = 12, [], None, None
    while pos + 8 <= len(raw):
        cid = raw[pos:pos + 4].decode('latin1')
        size = struct.unpack('<I', raw[pos + 4:pos + 8])[0]
        body = raw[pos + 8:pos + 8 + size]
        chunks.append({'id': cid, 'size': size})
        if cid == 'fmt ':
            tag, ch, rate, byte_rate, align, bits = struct.unpack('<HHIIHH', body[:16])
            sub = tag
            if tag == 0xFFFE and len(body) >= 26:
                sub = struct.unpack('<H', body[24:26])[0]
            fmt = {'tag': tag, 'subformat': sub, 'channels': ch, 'rate': rate, 'byte_rate': byte_rate, 'block_align': align, 'bits': bits}
        elif cid == 'data':
            pcm = body
        pos += 8 + size + (size & 1)
    if fmt is None or pcm is None:
        raise ValueError('missing fmt or data chunk')
    ch, bits = fmt['channels'], fmt['bits']
    if fmt['subformat'] == 3:
        x = np.frombuffer(pcm[: len(pcm) // 4 * 4], dtype='<f4').astype(np.float64)
    elif bits == 16:
        x = np.frombuffer(pcm[: len(pcm) // 2 * 2], dtype='<i2').astype(np.float64) / 32768.0
    elif bits == 8:
        x = (np.frombuffer(pcm, dtype=np.uint8).astype(np.float64) - 128.0) / 128.0
    elif bits == 24:
        n = len(pcm) // 3
        b = np.frombuffer(pcm[: n * 3], dtype=np.uint8).reshape(n, 3).astype(np.int32)
        v = b[:, 0] | (b[:, 1] << 8) | (b[:, 2] << 16)
        v = np.where(v & 0x800000, v - 0x1000000, v)
        x = v.astype(np.float64) / 8388608.0
    elif bits == 32:
        x = np.frombuffer(pcm[: len(pcm) // 4 * 4], dtype='<i4').astype(np.float64) / 2147483648.0
    else:
        raise ValueError(f'unsupported bit depth {bits}')
    frames = len(x) // ch
    x = x[: frames * ch].reshape(frames, ch)
    return fmt, chunks, x, len(raw)


# ─────────────────────────────────────────────────────────────── helpers ──
EPS = 1e-12
db = lambda v: 20.0 * math.log10(max(v, EPS))
db_pow = lambda v: 10.0 * math.log10(max(v, EPS))


def true_peak(x: np.ndarray, factor: int = 4) -> float:
    best = 0.0
    for c in range(x.shape[1]):
        s = x[:, c]
        n = len(s)
        S = np.fft.rfft(s)
        up = np.zeros(n * factor // 2 + 1, dtype=complex)
        up[: len(S)] = S
        y = np.fft.irfft(up, n * factor) * factor
        best = max(best, float(np.max(np.abs(y))))
    return best


def biquad(x, b, a):
    """Direct form I, vectorised over nothing (files are short)."""
    y = np.zeros_like(x)
    x1 = x2 = y1 = y2 = 0.0
    b0, b1, b2 = b
    _, a1, a2 = a
    for i in range(len(x)):
        xi = x[i]
        yi = b0 * xi + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2
        y[i] = yi
        x2, x1 = x1, xi
        y2, y1 = y1, yi
    return y


def k_weight(x: np.ndarray, fs: int) -> np.ndarray:
    # stage 1: high shelf (+4 dB, ~1.68 kHz) — coefficients recomputed for fs
    G, Q, f0 = 3.999843853973347, 0.7071752369554196, 1681.974450955533
    A = 10 ** (G / 40.0)
    w0 = 2 * math.pi * f0 / fs
    alpha = math.sin(w0) / (2 * Q)
    cw = math.cos(w0)
    sa = 2 * math.sqrt(A) * alpha
    b = np.array([A * ((A + 1) + (A - 1) * cw + sa), -2 * A * ((A - 1) + (A + 1) * cw), A * ((A + 1) + (A - 1) * cw - sa)])
    a = np.array([(A + 1) - (A - 1) * cw + sa, 2 * ((A - 1) - (A + 1) * cw), (A + 1) - (A - 1) * cw - sa])
    b, a = b / a[0], a / a[0]
    # stage 2: RLB high-pass (~38 Hz)
    Q2, f2 = 0.5003270373238773, 38.13547087602444
    K = math.tan(math.pi * f2 / fs)
    d = 1 + K / Q2 + K * K
    a2 = np.array([1.0, 2 * (K * K - 1) / d, (1 - K / Q2 + K * K) / d])
    b2 = np.array([1.0, -2.0, 1.0])
    out = np.zeros_like(x)
    for c in range(x.shape[1]):
        out[:, c] = biquad(biquad(x[:, c], b, a), b2, a2)
    return out


def lufs(x: np.ndarray, fs: int):
    k = k_weight(x, fs)
    gains = np.ones(x.shape[1])           # L, R (and mono) are weighted 1.0
    ungated = -0.691 + db_pow(float(np.sum(gains * np.mean(k * k, axis=0))))
    n = len(x)
    blk, hop = int(0.4 * fs), int(0.1 * fs)
    res = {'lufs_ungated': ungated, 'lufs_integrated': None, 'lufs_momentary_max': None}
    if n >= blk:
        ms = []
        for s in range(0, n - blk + 1, hop):
            seg = k[s:s + blk]
            ms.append(float(np.sum(gains * np.mean(seg * seg, axis=0))))
        ms = np.array(ms)
        l = -0.691 + 10 * np.log10(np.maximum(ms, EPS))
        res['lufs_momentary_max'] = float(np.max(l))
        keep = l > -70
        if np.any(keep):
            rel = -0.691 + db_pow(float(np.mean(ms[keep]))) - 10
            keep2 = keep & (l > rel)
            if np.any(keep2):
                res['lufs_integrated'] = -0.691 + db_pow(float(np.mean(ms[keep2])))
    return res


def envelope(mono: np.ndarray, fs: int, win_ms=5.0, hop_ms=1.0):
    w = max(2, int(fs * win_ms / 1000))
    h = max(1, int(fs * hop_ms / 1000))
    sq = mono * mono
    cs = np.concatenate([[0.0], np.cumsum(sq)])
    idx = np.arange(0, max(1, len(mono) - w + 1), h)
    ms = (cs[idx + w] - cs[idx]) / w
    t = (idx + w / 2) / fs
    return t, np.sqrt(ms)


def first_crossing_below(env, start, level):
    """first index ≥ start after which the envelope stays below `level`"""
    above = np.where(env[start:] > level)[0]
    return start + (above[-1] + 1 if len(above) else 0)


def stft(mono, fs, n=2048, hop=512):
    win = np.hanning(n)
    frames = []
    for s in range(0, max(1, len(mono) - n + 1), hop):
        frames.append(np.abs(np.fft.rfft(mono[s:s + n] * win)) ** 2)
    if not frames:
        seg = np.zeros(n)
        seg[: len(mono)] = mono[:n] * np.hanning(len(mono[:n]))
        frames.append(np.abs(np.fft.rfft(seg)) ** 2)
    f = np.fft.rfftfreq(n, 1.0 / fs)
    t = (np.arange(len(frames)) * hop + n / 2) / fs
    return f, t, np.array(frames)


def spectrum_stats(mono, fs):
    n = len(mono)
    nfft = 1 << (max(n, 1024) - 1).bit_length()
    nfft = min(nfft, 1 << 20)
    seg = mono[:nfft] * np.hanning(min(n, nfft))
    P = np.abs(np.fft.rfft(seg, nfft)) ** 2
    f = np.fft.rfftfreq(nfft, 1.0 / fs)
    tot = float(np.sum(P)) + EPS
    cen = float(np.sum(f * P) / tot)
    cum = np.cumsum(P) / tot
    r85 = float(f[min(len(f) - 1, np.searchsorted(cum, 0.85))])
    r95 = float(f[min(len(f) - 1, np.searchsorted(cum, 0.95))])
    bw = float(math.sqrt(np.sum(((f - cen) ** 2) * P) / tot))
    sel = P > 1e-14 * tot
    flat = float(np.exp(np.mean(np.log(P[sel] + 1e-30))) / (np.mean(P[sel]) + 1e-30)) if np.any(sel) else 0.0
    bands = [('infra <20', 0, 20), ('sub 20-60', 20, 60), ('bass 60-250', 60, 250), ('low-mid 250-500', 250, 500), ('mid 500-2k', 500, 2000),
             ('high-mid 2-4k', 2000, 4000), ('presence 4-8k', 4000, 8000), ('air >8k', 8000, 1e9)]
    be = {name: float(np.sum(P[(f >= lo) & (f < hi)]) / tot) for name, lo, hi in bands}
    # dominant peaks (local maxima, ≥ 12 dB above the median of ±40 bins, spaced ≥ 40 Hz)
    Pd = 10 * np.log10(P + 1e-30)
    ref = float(Pd[f >= 20].max()) if np.any(f >= 20) else float(Pd.max())     # dB re the strongest line at ≥ 20 Hz (DC / infrasound are not "lines")
    peaks = []
    order = np.argsort(P)[::-1]
    for i in order[:20000]:
        if f[i] < 20:
            continue
        if all(abs(f[i] - p['hz']) > 40 for p in peaks):
            lo, hi = max(0, i - 40), min(len(P), i + 41)
            if Pd[i] - np.median(Pd[lo:hi]) > 12:
                peaks.append({'hz': round(float(f[i]), 1), 'db_rel': round(float(Pd[i] - ref), 1)})
        if len(peaks) >= 5:
            break
    return {'centroid_hz': cen, 'rolloff85_hz': r85, 'rolloff95_hz': r95, 'bandwidth_hz': bw, 'flatness': flat, 'band_energy': be, 'peaks': peaks}


def sweep(mono, fs, env_t, env_db):
    """dominant-frequency trajectory (frames within 30 dB of the loudest) → slope in octaves/second"""
    f, t, S = stft(mono, fs)
    pk = np.max(S)
    fr = []
    for i in range(len(t)):
        if np.max(S[i]) < pk * 1e-3:
            continue
        j = int(np.argmax(S[i][2:])) + 2
        if f[j] < 30:
            continue
        # parabolic refinement
        if 1 <= j < len(f) - 1:
            a, b, c = np.log(S[i][j - 1] + 1e-30), np.log(S[i][j] + 1e-30), np.log(S[i][j + 1] + 1e-30)
            d = 0.5 * (a - c) / (a - 2 * b + c + 1e-30)
            fj = f[j] + d * (f[1] - f[0])
        else:
            fj = f[j]
        if not (30.0 <= fj <= fs / 2):      # parabolic refinement of a bin next to DC can leave the valid range: drop the frame
            continue
        cen = float(np.sum(f * S[i]) / (np.sum(S[i]) + EPS))
        fr.append((float(t[i]), float(fj), cen))
    if len(fr) < 5:                          # too few valid frames for a trajectory (e.g. energy below the STFT's usable range)
        return {'frames': len(fr)}
    ts = np.array([a for a, _, _ in fr])
    fs_ = np.array([b for _, b, _ in fr])
    cs = np.array([c for _, _, c in fr])
    oct_ = np.log2(fs_)
    slope = float(np.polyfit(ts, oct_, 1)[0]) if ts[-1] > ts[0] else 0.0
    cslope = float(np.polyfit(ts, np.log2(np.maximum(cs, 20)), 1)[0]) if ts[-1] > ts[0] else 0.0
    return {'frames': len(fr), 'dominant_start_hz': round(float(fs_[0]), 1), 'dominant_end_hz': round(float(fs_[-1]), 1),
            'dominant_min_hz': round(float(fs_.min()), 1), 'dominant_max_hz': round(float(fs_.max()), 1),
            'dominant_slope_oct_per_s': round(slope, 3), 'centroid_start_hz': round(float(cs[0]), 1), 'centroid_end_hz': round(float(cs[-1]), 1),
            'centroid_slope_oct_per_s': round(cslope, 3)}


def onsets(env_t, env_db, min_gap=0.04):
    """local maxima where the envelope rises ≥ 6 dB in 10 ms and is within 30 dB of the loudest"""
    ev = []
    peak = env_db.max()
    last = -1e9
    for i in range(10, len(env_db) - 1):
        if env_db[i] >= env_db[i - 1] and env_db[i] > env_db[i + 1] and env_db[i] > peak - 30:
            if env_db[i] - env_db[i - 10] >= 6 and env_t[i] - last >= min_gap:
                ev.append({'t_ms': round(float(env_t[i] * 1000), 1), 'db_rel': round(float(env_db[i] - peak), 1)})
                last = env_t[i]
    return ev


def analyze(path: Path):
    fmt, chunks, x, size = read_wav(path)
    fs, ch = fmt['rate'], fmt['channels']
    n = len(x)
    mono = x.mean(axis=1)
    out = {
        'filename': path.name, 'bytes': size, 'chunks': chunks,
        'format': {'tag': fmt['tag'], 'subformat': fmt['subformat'], 'sample_rate_hz': fs, 'channels': ch, 'bit_depth': fmt['bits'], 'frames': n},
        'duration_s': n / fs,
    }
    pk = float(np.max(np.abs(x)))
    out['peak_dbfs'] = db(pk)
    out['peak_per_channel_dbfs'] = [db(float(np.max(np.abs(x[:, c])))) for c in range(ch)]
    out['true_peak_dbfs'] = db(true_peak(x))
    rms = float(math.sqrt(np.mean(x * x)))
    out['rms_dbfs'] = db(rms)
    out['crest_factor_db'] = out['peak_dbfs'] - out['rms_dbfs']
    out['dc_offset'] = [float(np.mean(x[:, c])) for c in range(ch)]
    out['clipped_samples'] = int(np.sum(np.abs(x) >= 0.999))
    out['zero_crossing_rate_hz'] = float(np.sum(np.abs(np.diff(np.sign(mono))) > 0) / (n / fs))
    out.update(lufs(x, fs))
    if ch == 2:
        l, r = x[:, 0], x[:, 1]
        den = math.sqrt(float(np.sum(l * l) * np.sum(r * r))) + EPS
        mid, side = (l + r) / 2, (l - r) / 2
        out['stereo'] = {'lr_correlation': float(np.sum(l * r) / den), 'side_to_mid_db': db_pow(float(np.mean(side ** 2)) + EPS) - db_pow(float(np.mean(mid ** 2)) + EPS),
                         'identical_channels': bool(np.array_equal(l, r))}
    # envelope
    et, ee = envelope(mono, fs)
    edb = 20 * np.log10(np.maximum(ee, 1e-9))
    imax = int(np.argmax(ee))
    emax = float(ee[imax])
    out['envelope_peak_dbfs'] = db(emax)
    thr = 0.001
    on = np.where(np.abs(mono) > thr)[0]
    onset = float(on[0] / fs) if len(on) else None
    out['onset_ms'] = None if onset is None else onset * 1000
    out['time_to_envelope_peak_ms'] = float(et[imax] * 1000)
    out['attack_ms'] = None if onset is None else max(0.0, float(et[imax] - onset) * 1000)
    lo, hi = 0.1 * emax, 0.9 * emax
    i10 = int(np.argmax(ee >= lo)) if np.any(ee >= lo) else 0
    i90 = int(np.argmax(ee >= hi)) if np.any(ee >= hi) else imax
    out['rise_10_90_ms'] = float((et[i90] - et[i10]) * 1000)
    for dbdrop in (20, 40):
        lvl = emax * 10 ** (-dbdrop / 20)
        j = first_crossing_below(ee, imax, lvl)
        out[f'decay_{dbdrop}db_ms'] = float((et[min(j, len(et) - 1)] - et[imax]) * 1000)
    # effective length: last moment above −40 dB re peak
    j40 = first_crossing_below(ee, 0, emax * 0.01)
    out['effective_length_ms'] = float(et[min(j40, len(et) - 1)] * 1000)
    # leading / trailing silence at −60 dBFS
    last = np.where(np.abs(mono) > thr)[0]
    out['trailing_silence_ms'] = float((n - 1 - last[-1]) / fs * 1000) if len(last) else None
    # tail level: RMS of the last 50 ms relative to the peak envelope
    tail = mono[-int(0.05 * fs):]
    out['tail_50ms_db_re_peak'] = db(float(math.sqrt(np.mean(tail * tail)))) - out['envelope_peak_dbfs']
    head = mono[: int(0.02 * fs)]
    out['head_20ms_db_re_peak'] = db(float(math.sqrt(np.mean(head * head)))) - out['envelope_peak_dbfs']
    out['wrap_jump'] = float(abs(mono[0] - mono[-1]))
    # Schroeder RT
    seg = mono[int(imax * 0.001 * fs):] if imax else mono
    e = np.cumsum((seg ** 2)[::-1])[::-1]
    e = e / (e[0] + EPS)
    ed = 10 * np.log10(e + 1e-30)
    t_ = np.arange(len(ed)) / fs
    sel = (ed <= -5) & (ed >= -25)
    if np.sum(sel) > 8:
        slope = np.polyfit(t_[sel], ed[sel], 1)[0]
        out['rt60_ms'] = float(-60.0 / slope * 1000) if slope < 0 else None
    else:
        out['rt60_ms'] = None
    # modulation of the envelope (AM) — useful for slides / loops
    if len(ee) > 64:
        d = edb - np.mean(edb)
        sp = np.abs(np.fft.rfft(d * np.hanning(len(d)))) ** 2
        fm = np.fft.rfftfreq(len(d), 1.0 / 1000.0)
        m = (fm >= 0.5) & (fm <= 60)
        if np.any(m):
            k = int(np.argmax(sp[m]))
            out['envelope_mod_peak_hz'] = float(fm[m][k])
            out['envelope_mod_depth_db_std'] = float(np.std(edb[edb > edb.max() - 40]))
    out['spectrum'] = spectrum_stats(mono, fs)
    out['sweep'] = sweep(mono, fs, et, edb)
    out['onsets'] = onsets(et, edb)
    # first 50 ms after the onset vs the rest: where is the energy
    if onset is not None:
        a = mono[int(onset * fs): int(onset * fs) + int(0.05 * fs)]
        out['spectrum_first_50ms'] = {k: v for k, v in spectrum_stats(a, fs).items() if k in ('centroid_hz', 'rolloff85_hz', 'peaks')} if len(a) > 64 else None
    # coarse envelope shape for documentation: level every 10 % of the length
    pts = np.linspace(0, len(ee) - 1, 11).astype(int)
    out['envelope_10pt_db_re_peak'] = [round(float(edb[i] - edb.max()), 1) for i in pts]
    return out


def main():
    d = Path(sys.argv[1])
    res = [analyze(p) for p in sorted(d.glob('*.wav'))]
    if len(sys.argv) > 2:
        Path(sys.argv[2]).write_text(json.dumps(res, indent=1))
    for r in res:
        f = r['format']
        print(f"{r['filename']:<16} {r['duration_s']:6.3f}s  {f['sample_rate_hz']}Hz {f['channels']}ch {f['bit_depth']}bit  peak {r['peak_dbfs']:6.1f}  tp {r['true_peak_dbfs']:6.1f}  rms {r['rms_dbfs']:6.1f}  "
              f"LUFS {r['lufs_integrated'] if r['lufs_integrated'] is not None else float('nan'):6.1f}/{r['lufs_ungated']:6.1f}  centroid {r['spectrum']['centroid_hz']:7.0f}Hz  "
              f"attack {r['attack_ms'] if r['attack_ms'] is not None else float('nan'):6.1f}ms  dec20 {r['decay_20db_ms']:7.1f}ms  rt60 {r['rt60_ms'] if r['rt60_ms'] else float('nan'):7.1f}")


if __name__ == '__main__':
    main()
