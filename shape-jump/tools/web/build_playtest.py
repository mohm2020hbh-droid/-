"""Builds the playable web page of Shape Jump (dev tool).

    python3 tools/web/build_playtest.py [--godot=PATH] [--skip-export]

1. Exports the "Web" preset (single-threaded, so no special server headers
   are needed) to export/web/.
2. Assembles export/playtest/:
   - play.html: tools/web/playtest_page.html, a small page that paints at
     once, shows any error on screen, and offers normal progression or a
     test mode with every level open (`-- --unlock-all`). `#autoplay` and
     `#autoplay-deaths` on its URL start the QA autoplay (src/game/autoplay.gd);
   - godot.js: Godot's loader (the page runs it as an inline script);
   - engine.gz.wasm: the engine, gzip-compressed (39 MB -> ~10 MB) under a
     .wasm name so static hosts serve it; the page decompresses it;
   - game-data.txt: the game data (index.pck) as base64 text;
   - the two audio worklet scripts.
Serve export/playtest/ with any static server and open play.html, or
publish the folder as-is.
"""
import base64
import gzip
import json
import os
import shutil
import subprocess
import sys

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
WEB = os.path.join(ROOT, "export", "web")
OUT = os.path.join(ROOT, "export", "playtest")
TEMPLATE = os.path.join(ROOT, "tools", "web", "playtest_page.html")
COPIED = ["index.audio.worklet.js", "index.audio.position.worklet.js"]
ENGINE = "engine.gz.wasm"
DATA = "game-data.txt"


def main():
    args = sys.argv[1:]
    godot = next((a.split("=", 1)[1] for a in args if a.startswith("--godot=")), os.environ.get("GODOT", "godot"))
    # Godot must not import the exported files back into the project.
    os.makedirs(os.path.join(ROOT, "export"), exist_ok=True)
    open(os.path.join(ROOT, "export", ".gdignore"), "a").close()
    if "--skip-export" not in args:
        os.makedirs(WEB, exist_ok=True)
        subprocess.run([godot, "--headless", "--path", ROOT, "--export-release", "Web",
                        os.path.join(WEB, "index.html")], check=True)
    shutil.rmtree(OUT, ignore_errors=True)
    os.makedirs(OUT)
    for name in COPIED:
        shutil.copy(os.path.join(WEB, name), os.path.join(OUT, name))
    with open(os.path.join(WEB, "index.wasm"), "rb") as fh:
        wasm = fh.read()
    with gzip.open(os.path.join(OUT, ENGINE), "wb", compresslevel=9) as fh:
        fh.write(wasm)
    with open(os.path.join(WEB, "index.pck"), "rb") as fh:
        pck = fh.read()
    with open(os.path.join(OUT, DATA), "w", encoding="ascii") as fh:
        fh.write(base64.b64encode(pck).decode("ascii"))
    shutil.copy(os.path.join(WEB, "index.js"), os.path.join(OUT, "godot.js"))
    sizes = {"index.wasm": len(wasm), ENGINE: os.path.getsize(os.path.join(OUT, ENGINE)), "index.pck": len(pck),
             DATA: os.path.getsize(os.path.join(OUT, DATA))}
    with open(TEMPLATE, encoding="utf-8") as fh:
        page = fh.read()
    page = page.replace("/*__FILE_SIZES__*/", json.dumps(sizes))
    with open(os.path.join(OUT, "play.html"), "w", encoding="utf-8") as fh:
        fh.write(page)
    for name in sorted(os.listdir(OUT)):
        print(f"{os.path.getsize(os.path.join(OUT, name)):>10}  export/playtest/{name}")


if __name__ == "__main__":
    main()
