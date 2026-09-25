"""Builds the playable web page of Shape Jump (dev tool).

    python3 tools/web/build_playtest.py [--godot=PATH] [--skip-export]

1. Exports the "Web" preset (single-threaded, so no special server headers
   are needed) to export/web/.
2. Assembles export/playtest/:
   - play.html: tools/web/playtest_page.html with Godot's loader and the
     game data (index.pck, base64) inlined; the page offers normal
     progression or a test mode with every level open (it passes
     `-- --unlock-all`, see src/game/progression.gd);
   - engine.gz.wasm: the engine, gzip-compressed (39 MB -> ~10 MB) under a
     .wasm name so static hosts serve it; the page decompresses it;
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
    sizes = {"index.wasm": len(wasm), ENGINE: os.path.getsize(os.path.join(OUT, ENGINE)), "index.pck": len(pck)}
    with open(os.path.join(WEB, "index.js"), encoding="utf-8") as fh:
        engine_js = fh.read()
    if "</script" in engine_js:
        engine_js = engine_js.replace("</script", "<\\/script")
    with open(TEMPLATE, encoding="utf-8") as fh:
        page = fh.read()
    page = (page.replace("/*__GODOT_ENGINE_JS__*/", engine_js)
            .replace("/*__FILE_SIZES__*/", json.dumps(sizes))
            .replace("/*__PCK_BASE64__*/", base64.b64encode(pck).decode("ascii")))
    with open(os.path.join(OUT, "play.html"), "w", encoding="utf-8") as fh:
        fh.write(page)
    for name in sorted(os.listdir(OUT)):
        print(f"{os.path.getsize(os.path.join(OUT, name)):>10}  export/playtest/{name}")


if __name__ == "__main__":
    main()
