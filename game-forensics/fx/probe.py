"""Stage 0: detect which external tools are installed. Never pretends."""
import argparse
import json
import os
import shutil

from fx.ledger import Ledger

TOOLS = {
    "jadx": ("APK/DEX -> Java source", "https://github.com/skylot/jadx/releases"),
    "apktool": ("APK resources + smali", "https://apktool.org/docs/install"),
    "ilspycmd": ("Unity Mono .NET assemblies -> C#", "dotnet tool install -g ilspycmd"),
    "Il2CppDumper": ("Unity IL2CPP metadata", "https://github.com/Perfare/Il2CppDumper"),
    "gdre_tools": ("Godot PCK -> project", "https://github.com/GDRETools/gdsdecomp"),
    "wasm2wat": ("WASM -> text", "apt install wabt"),
    "analyzeHeadless": ("Ghidra headless (native code)", "https://ghidra-sre.org"),
    "file": ("libmagic file typing", "apt install file"),
    "unzip": ("archive extraction", "apt install unzip"),
}


def probe(out_dir):
    ledger = Ledger(out_dir)
    ledger.replace_stage("probe")
    found = {}
    for name, (purpose, hint) in TOOLS.items():
        path = shutil.which(name)
        found[name] = {"installed": bool(path), "path": path, "purpose": purpose,
                       "install_hint": None if path else hint}
        if path:
            ledger.add(stage="probe", key=f"tool.{name}", value=path,
                       status="DERIVED", confidence="HIGH",
                       method="shutil.which on PATH")
        else:
            ledger.add(stage="probe", key=f"tool.{name}", value=None,
                       status="UNKNOWN", confidence="NONE",
                       method="shutil.which on PATH",
                       needed_to_resolve=f"install {name}: {hint}")
    os.makedirs(os.path.join(out_dir, "work"), exist_ok=True)
    with open(os.path.join(out_dir, "work", "tools.json"), "w") as f:
        json.dump(found, f, indent=2)
    return found


if __name__ == "__main__":
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--out", required=True)
    for name, info in probe(ap.parse_args().out).items():
        print(f"{'OK ' if info['installed'] else '-- '} {name:16} {info['purpose']}")
