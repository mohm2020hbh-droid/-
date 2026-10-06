"""Append-only evidence ledger (evidence.jsonl).

Every datum the pipeline reports is a record here. Status and confidence
caps are enforced in code so a claim can never be stronger than its evidence.
"""
import hashlib
import json
import os
import time

STATUSES = ("EXTRACTED", "DERIVED", "INFERRED", "ESTIMATED", "UNKNOWN")
CONFIDENCE = ("NONE", "LOW", "MEDIUM", "HIGH")
_RANK = {c: i for i, c in enumerate(CONFIDENCE)}


def cap_confidence(status, confidence, parents=None):
    """Return the highest confidence the status/parents allow."""
    if status not in STATUSES:
        raise ValueError(f"unknown status {status!r}")
    if confidence not in CONFIDENCE:
        raise ValueError(f"unknown confidence {confidence!r}")
    limit = "HIGH"
    if status == "UNKNOWN":
        limit = "NONE"
    elif status == "ESTIMATED":
        limit = "MEDIUM"
    elif status == "INFERRED" and len(parents or []) < 3:
        limit = "MEDIUM"
    if status in ("DERIVED", "INFERRED") and parents:
        weakest = min(_RANK[p["confidence"]] for p in parents)
        limit = CONFIDENCE[min(_RANK[limit], weakest)]
    return CONFIDENCE[min(_RANK[confidence], _RANK[limit])]


class Ledger:
    def __init__(self, out_dir):
        os.makedirs(out_dir, exist_ok=True)
        self.path = os.path.join(out_dir, "evidence.jsonl")

    def records(self):
        if not os.path.exists(self.path):
            return []
        with open(self.path, encoding="utf-8") as f:
            return [json.loads(line) for line in f if line.strip()]

    def add(self, *, stage, key, value, status, confidence, source=None,
            location=None, evidence=None, method, tool="python-stdlib",
            unit=None, parents=None, needed_to_resolve=None):
        if status == "EXTRACTED" and not (source and evidence is not None):
            raise ValueError("EXTRACTED needs a source file and evidence")
        if status == "UNKNOWN" and not needed_to_resolve:
            raise ValueError("UNKNOWN needs needed_to_resolve")
        parent_recs = []
        if parents:
            by_id = {r["id"]: r for r in self.records()}
            parent_recs = [by_id[p] for p in parents]
        rec = {
            "stage": stage, "key": key, "value": value, "unit": unit,
            "status": status,
            "confidence": cap_confidence(status, confidence, parent_recs),
            "source": source, "location": location, "evidence": evidence,
            "method": method, "tool": tool, "parents": parents or [],
            "needed_to_resolve": needed_to_resolve, "ts": time.time(),
        }
        digest = json.dumps([stage, key, source, location], sort_keys=True)
        rec["id"] = "ev_" + hashlib.sha1(digest.encode()).hexdigest()[:12]
        with open(self.path, "a", encoding="utf-8") as f:
            f.write(json.dumps(rec, ensure_ascii=False) + "\n")
        return rec["id"]

    def replace_stage(self, stage):
        """Drop a stage's records (and anything derived from them) before a re-run."""
        recs = self.records()
        dropped = {r["id"] for r in recs if r["stage"] == stage}
        changed = True
        while changed:
            changed = False
            for r in recs:
                if r["id"] not in dropped and dropped.intersection(r["parents"]):
                    dropped.add(r["id"])
                    changed = True
        with open(self.path, "w", encoding="utf-8") as f:
            for r in recs:
                if r["id"] not in dropped:
                    f.write(json.dumps(r, ensure_ascii=False) + "\n")
