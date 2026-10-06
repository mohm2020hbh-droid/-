"""Re-verify the ledger. Exit code != 0 means a claim is unsupported."""
import argparse
import json
import os
import sys

from fx.ledger import CONFIDENCE, STATUSES, Ledger, cap_confidence


def validate(out_dir):
    recs = Ledger(out_dir).records()
    by_id = {r["id"]: r for r in recs}
    errors = []
    for r in recs:
        rid = r["id"]
        if r["status"] not in STATUSES or r["confidence"] not in CONFIDENCE:
            errors.append(f"{rid}: bad status/confidence")
            continue
        missing = [p for p in r["parents"] if p not in by_id]
        if missing:
            errors.append(f"{rid}: orphan parents {missing}")
            continue
        parents = [by_id[p] for p in r["parents"]]
        if cap_confidence(r["status"], r["confidence"], parents) != r["confidence"]:
            errors.append(f"{rid}: confidence above what its evidence allows")
        if r["status"] == "UNKNOWN" and not r.get("needed_to_resolve"):
            errors.append(f"{rid}: UNKNOWN without needed_to_resolve")
        if r["status"] == "EXTRACTED":
            errors.extend(_recheck_extracted(r))
    report = {"records": len(recs), "errors": errors, "ok": not errors}
    with open(os.path.join(out_dir, "validation_report.json"), "w") as f:
        json.dump(report, f, indent=2)
    return report


def _recheck_extracted(r):
    src, loc = r.get("source"), r.get("location") or {}
    if not src or not os.path.isfile(src):
        return [f"{r['id']}: source file missing ({src})"]
    if "byte_offset" in loc:
        want = bytes.fromhex(r["evidence"])
        with open(src, "rb") as f:
            f.seek(loc["byte_offset"])
            if f.read(len(want)) != want:
                return [f"{r['id']}: bytes at offset no longer match evidence"]
    elif "line" in loc:
        with open(src, encoding="utf-8", errors="replace") as f:
            lines = f.read().splitlines()
        n = loc["line"]
        if not (1 <= n <= len(lines) and r["evidence"] in lines[n - 1]):
            return [f"{r['id']}: evidence text not found on line {n}"]
    else:
        return [f"{r['id']}: EXTRACTED record without a location"]
    return []


if __name__ == "__main__":
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--out", required=True)
    rep = validate(ap.parse_args().out)
    for e in rep["errors"]:
        print("FAIL", e)
    print(f"{rep['records']} records, {len(rep['errors'])} errors")
    sys.exit(0 if rep["ok"] else 1)
