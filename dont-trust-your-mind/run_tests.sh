#!/usr/bin/env bash
# Runs the whole verification suite. Set GODOT to your Godot 4.3+ binary.
set -euo pipefail

GODOT="${GODOT:-godot}"
cd "$(dirname "$0")"

echo "==> importing resources"
"$GODOT" --headless --import >/dev/null 2>&1 || true

echo "==> unit, content and integration suites"
"$GODOT" --headless res://tests/test_runner.tscn

# The smoke test drives real screens, so it needs a display. Under CI or a
# headless box, xvfb-run supplies one.
if command -v xvfb-run >/dev/null 2>&1; then
  echo "==> end-to-end smoke test"
  xvfb-run -a "$GODOT" --resolution 720x1280 --rendering-driver opengl3 \
      res://tests/smoke.tscn
else
  echo "==> skipping smoke test (no xvfb-run; run it on a machine with a display)"
fi

echo "==> all checks passed"
