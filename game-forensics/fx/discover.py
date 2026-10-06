"""Stage 1: inventory the target -- magic bytes, hashes, entropy, archive listing.

Archives are listed, never extracted here, so zip-slip / zip-bomb / encrypted
members are reported as findings instead of being written to disk.
"""
import argparse
import hashlib
import json
import math
import os
import zipfile

from fx.ledger import Ledger

MAGIC = [  # (offset, bytes, type)
    (0, b"PK\x03\x04", "zip"),
    (0, b"\x7fELF", "elf"),
    (0, b"MZ", "pe"),
    (0, b"dex\n", "dex"),
    (0, b"\x00asm", "wasm"),
    (0, b"GDPC", "godot_pck"),
    (0, b"UnityFS", "unity_bundle"),
    (0, b"\xaf\x1b\xb1\xfa", "il2cpp_metadata"),
    (0, b"\x89PNG\r\n\x1a\n", "png"),
    (0, b"\xff\xd8\xff", "jpeg"),
    (0, b"GIF8", "gif"),
    (0, b"OggS", "ogg"),
    (0, b"RIFF", "riff"),
]
ZIP_FLAVOURS = {"AndroidManifest.xml": "apk", "BundleConfig.pb": "aab",
                "META-INF/MANIFEST.MF": "jar"}
MAX_RATIO = 200  # compressed -> uncompressed ratio treated as a bomb


def entropy(data):
    if not data:
        return 0.0
    counts = [0] * 256
    for b in data:
        counts[b] += 1
    n = len(data)
    return -sum(c / n * math.log2(c / n) for c in counts if c)


def iter_files(target):
    if os.path.isfile(target):
        yield target
        return
    for root, dirs, files in os.walk(target):
        dirs[:] = sorted(d for d in dirs if not d.startswith("."))
        for name in sorted(files):
            yield os.path.join(root, name)


def classify(path, ledger):
    with open(path, "rb") as f:
        head = f.read(16)
    for off, sig, kind in MAGIC:
        if head[off:off + len(sig)] == sig:
            mid = ledger.add(stage="discover", key="magic", value=kind,
                             status="EXTRACTED", confidence="HIGH", source=path,
                             location={"byte_offset": off}, evidence=sig.hex(),
                             method="magic-byte signature match")
            return kind, mid
    return "unknown", None


def list_zip(path, ledger, parent):
    findings = {"members": 0, "flavour": "zip", "encrypted": [], "unsafe_paths": [],
                "bomb_suspects": []}
    try:
        zf = zipfile.ZipFile(path)
    except zipfile.BadZipFile as e:
        ledger.add(stage="discover", key="zip.error", value=None, status="UNKNOWN",
                   confidence="NONE", source=path, method="zipfile open",
                   needed_to_resolve=f"archive unreadable ({e}); supply an intact copy")
        return findings
    with zf:
        for info in zf.infolist():
            findings["members"] += 1
            name = info.filename
            if name in ZIP_FLAVOURS:
                findings["flavour"] = ZIP_FLAVOURS[name]
            if info.flag_bits & 0x1:
                findings["encrypted"].append(name)
            if name.startswith(("/", "\\")) or ".." in name.replace("\\", "/").split("/"):
                findings["unsafe_paths"].append(name)
            if info.compress_size and info.file_size / info.compress_size > MAX_RATIO:
                findings["bomb_suspects"].append(name)
    ledger.add(stage="discover", key="archive.flavour", value=findings["flavour"],
               status="DERIVED", confidence="HIGH", source=path,
               method="zip central directory member names", parents=[parent])
    if findings["encrypted"]:
        ledger.add(stage="discover", key="protection.encrypted_members",
                   value=len(findings["encrypted"]), status="UNKNOWN",
                   confidence="NONE", source=path, method="zip flag bit 0",
                   needed_to_resolve="owner-supplied unprotected build or keys "
                                     "(encryption is not bypassed)")
    return findings


def discover(target, out_dir):
    ledger = Ledger(out_dir)
    ledger.replace_stage("discover")
    inventory = []
    for path in iter_files(target):
        size = os.path.getsize(path)
        sha = hashlib.sha256()
        with open(path, "rb") as f:
            sample = f.read(1 << 20)
            sha.update(sample)
            for chunk in iter(lambda: f.read(1 << 20), b""):
                sha.update(chunk)
        kind, magic_id = classify(path, ledger)
        ent = round(entropy(sample), 3)
        entry = {"path": path, "size": size, "sha256": sha.hexdigest(),
                 "type": kind, "entropy_first_mib": ent}
        ledger.add(stage="discover", key="sha256", value=entry["sha256"],
                   status="DERIVED", confidence="HIGH", source=path,
                   method="sha256 over full file")
        if ent > 7.9 and kind not in ("zip", "png", "jpeg", "gif", "ogg"):
            ledger.add(stage="discover", key="protection.high_entropy", value=ent,
                       status="ESTIMATED", confidence="MEDIUM", source=path,
                       method="Shannon entropy of first MiB > 7.9 bits/byte",
                       needed_to_resolve=None)
        if kind == "zip":
            entry["archive"] = list_zip(path, ledger, magic_id)
        inventory.append(entry)
    os.makedirs(os.path.join(out_dir, "work"), exist_ok=True)
    with open(os.path.join(out_dir, "work", "inventory.json"), "w") as f:
        json.dump(inventory, f, indent=2)
    return inventory


if __name__ == "__main__":
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("target")
    ap.add_argument("--out", required=True)
    args = ap.parse_args()
    for e in discover(args.target, args.out):
        print(f"{e['type']:16} {e['size']:>10}  {e['path']}")
