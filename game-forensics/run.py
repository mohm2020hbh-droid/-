"""Run the forensics pipeline on one target.

    python3 run.py targets/my_game.apk --out reports/my_game
"""
import argparse
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from fx.discover import discover  # noqa: E402
from fx.probe import probe  # noqa: E402
from fx.validate import validate  # noqa: E402


def main():
    ap = argparse.ArgumentParser(description=__doc__,
                                 formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("target", help="file or folder you are authorised to analyse")
    ap.add_argument("--out", required=True, help="output folder (e.g. reports/<name>)")
    args = ap.parse_args()
    if not os.path.exists(args.target):
        ap.error(f"target not found: {args.target}")

    tools = probe(args.out)
    missing = [n for n, t in tools.items() if not t["installed"]]
    print(f"[probe]    tools installed: {len(tools) - len(missing)}/{len(tools)}"
          + (f"  (missing: {', '.join(missing)})" if missing else ""))

    inv = discover(args.target, args.out)
    kinds = {}
    for e in inv:
        kinds[e["type"]] = kinds.get(e["type"], 0) + 1
    print(f"[discover] {len(inv)} files: "
          + ", ".join(f"{k}={v}" for k, v in sorted(kinds.items())))

    rep = validate(args.out)
    print(f"[validate] {rep['records']} records, {len(rep['errors'])} errors")
    for e in rep["errors"]:
        print("  FAIL", e)
    return 0 if rep["ok"] else 1


if __name__ == "__main__":
    sys.exit(main())
