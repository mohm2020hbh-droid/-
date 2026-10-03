# Project: 2D pogo-stick climbing game (working title: POGO-CLIMB)

## Goal
A precision-platformer where the player balances on a pogo stick, charges a jump, and climbs a tall hand-built level. Targets: Windows and Android from ONE codebase. The game must have its own original identity: no names, art, audio, text, level layouts or code copied from any other game.

## Stack (verify, do not assume)
- Godot 4.x. Detect the installed version at the start of every session (`godot --version`) and record it in docs/ENGINE_VERSION.md. Never use an API you have not verified exists in THAT version (check the editor class reference or write a tiny headless script that calls it).
- Language: GDScript with static typing everywhere (typed vars, typed function signatures). No plugins or addons unless I approve.
- Renderer: Compatibility (widest Android support) unless a measurement says otherwise.

## Architecture rules
1. Simulation is separate from presentation. The simulation (movement, collisions response, checkpoints, timer) runs ONLY in `_physics_process` at a fixed 120 ticks/second. Sprites, camera, particles, audio, UI live outside the simulation and never change simulation state.
2. The simulation is deterministic: no randomness, no wall-clock time, no frame-dependent values. Use the constant TICK_DT = 1.0 / 120.0.
3. Input reaches the simulation ONLY as a per-tick bitmask int (TILT_LEFT=1, TILT_RIGHT=2, JUMP=4) from the GameInput autoload. Keyboard, gamepad and touch all feed the same input actions.
4. Every gameplay number lives in a Resource file (res://data/*.tres), never as a magic number in code. Placeholder values must be marked TUNE-ME and must never be described as measured or taken from another game.
5. Folders: scripts/sim, scripts/presentation, scripts/ui, scenes, data, tests, docs.

## Working rules
- Inspect the actual files before planning or changing anything. The code on disk is the source of truth, not memory of earlier sessions.
- State assumptions explicitly. If something is unknown, check it or ask; do not guess and continue.
- Plan first (short, ordered), then implement in small commits with clear messages.
- Never claim a task is done without running it: run the headless tests (`godot --headless --script res://tests/run_tests.gd`) and report real output. If you could not run something, say so.
- Do not refactor or change anything outside the scope of the current task.
- Keep each task's report short: what changed, files touched, test output, anything that failed.
