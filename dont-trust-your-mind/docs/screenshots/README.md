# Screenshots

Captured from the real game with `tests/capture.tscn` under a virtual display:

```bash
xvfb-run -a godot --resolution 720x1280 --rendering-driver opengl3 res://tests/capture.tscn
```

That harness writes 36 images (every screen and a sample of stages, in both
languages) to `tests/shots/`, which is gitignored. The eight kept here are the
ones worth reviewing in a diff.
