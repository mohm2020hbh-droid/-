#!/usr/bin/env python3
"""Import a real recording as a Single Player target sound.

This is a one-time content-pipeline tool for whoever is sourcing recordings
(a maintainer, or an agent with real network access to Freesound/OpenGameArt)
— it is NOT part of the shipped game and run-server.bat never calls it.

What it does, matching the engineering spec's "معالجة صوتية موحدة" step
exactly:

    1. Standardises the file: a fixed sample rate and mono, so every
       imported sound is comparable and small.
    2. Trims silence from the start and end (a real recording almost always
       has some), so the target begins the instant the player presses play.
    3. Loudness-normalises with ffmpeg's loudnorm (EBU R128) to a moderate,
       consistent level — deliberately gentle (a single pass, a normal
       streaming-loudness target, true-peak limited) rather than aggressive
       compression, so the recording keeps its natural dynamics instead of
       being squashed into something that no longer sounds real.
    4. Writes the result as Ogg Vorbis into app/static/audio/<sound_id>.ogg
       — picked up automatically by targets.js / /api/target-audio with no
       other code change.
    5. Records the required attribution fields into sources.json.

It deliberately does NOT mark the sound reviewed: reviewed_by_ear is only
ever set by a human who actually listened to the finished file. Skipping
that step and shipping a sound anyway is exactly what the spec forbids.

Usage:
    python tools/import_audio.py \\
        --id lion --input /path/to/downloaded_lion_roar.wav \\
        --name-ar "زئير أسد" --name-en "Lion" \\
        --source freesound --creator "exact uploader name" \\
        --license "CC0 1.0" \\
        --source-url "https://freesound.org/people/.../sounds/12345/"

Requires ffmpeg + ffprobe on PATH (a content-pipeline dependency only; the
game itself needs neither).
"""

from __future__ import annotations

import argparse
import datetime
import json
import shutil
import subprocess
import sys
import tempfile
from pathlib import Path

AUDIO_DIR = Path(__file__).resolve().parent.parent / "app" / "static" / "audio"
SOUNDS_JS = Path(__file__).resolve().parent.parent / "app" / "static" / "js" / "sounds.js"
SOURCES_JSON = AUDIO_DIR / "sources.json"

TARGET_RATE = 44100          # standard rate; the browser resamples to the
                              # engine's analysis rate on load either way
LOUDNESS_TARGET_LUFS = -16   # a normal, moderate streaming/game loudness —
                              # not hot, not buried
TRUE_PEAK_DB = -1.5          # headroom so normalisation never clips


def sh(*args: str) -> str:
    result = subprocess.run(args, capture_output=True, text=True)
    if result.returncode != 0:
        raise RuntimeError(f"command failed: {' '.join(args)}\n{result.stderr}")
    return result.stdout


def known_sound_ids() -> set[str]:
    """The authoritative id list — sounds.js, not this script's own guess."""

    text = SOUNDS_JS.read_text(encoding="utf-8")
    import re

    return set(re.findall(r'\bid:\s*"([a-z0-9_]+)"', text))


def probe_duration(path: Path) -> float:
    out = sh(
        "ffprobe", "-v", "error", "-show_entries", "format=duration",
        "-of", "default=noprint_wrappers=1:nokey=1", str(path),
    )
    return float(out.strip())


def process(input_path: Path, output_path: Path) -> None:
    """Trim silence, normalise loudness, standardise format — one ffmpeg run."""

    # silenceremove trims leading AND trailing silence when applied twice
    # (once forward, once on the reversed stream); loudnorm follows so the
    # measurement isn't thrown off by dead air at the edges.
    filter_chain = (
        "silenceremove=start_periods=1:start_threshold=-45dB:start_silence=0.05,"
        "areverse,"
        "silenceremove=start_periods=1:start_threshold=-45dB:start_silence=0.05,"
        "areverse,"
        f"loudnorm=I={LOUDNESS_TARGET_LUFS}:TP={TRUE_PEAK_DB}:LRA=11"
    )
    sh(
        "ffmpeg", "-y", "-i", str(input_path),
        "-af", filter_chain,
        "-ac", "1", "-ar", str(TARGET_RATE),
        "-c:a", "libvorbis", "-q:a", "5",
        str(output_path),
    )


def load_sources() -> dict:
    data = json.loads(SOURCES_JSON.read_text(encoding="utf-8"))
    data.setdefault("entries", {})
    return data


def save_sources(data: dict) -> None:
    SOURCES_JSON.write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--id", required=True, help="sound_id exactly as in sounds.js")
    parser.add_argument("--input", required=True, type=Path, help="the downloaded source file")
    parser.add_argument("--name-ar", required=True)
    parser.add_argument("--name-en", required=True)
    parser.add_argument("--source", required=True, choices=["freesound", "opengameart", "other"])
    parser.add_argument("--creator", required=True, help="exact uploader/recordist name at the source")
    parser.add_argument("--license", required=True, help="exact license, e.g. 'CC0 1.0' or 'CC-BY 4.0'")
    parser.add_argument("--source-url", required=True, help="the exact page the file came from")
    parser.add_argument("--force", action="store_true", help="overwrite an existing import for this id")
    args = parser.parse_args()

    if shutil.which("ffmpeg") is None or shutil.which("ffprobe") is None:
        print("ffmpeg/ffprobe not found on PATH — this tool needs them; the game itself does not.")
        return 1

    valid_ids = known_sound_ids()
    if args.id not in valid_ids:
        print(f"'{args.id}' is not a known sound_id in sounds.js. Known ids: {', '.join(sorted(valid_ids))}")
        return 1

    if not args.input.is_file():
        print(f"input file not found: {args.input}")
        return 1

    if args.source == "other":
        print(
            "Source 'other' is only allowed when you have PERSONALLY verified, at the\n"
            "source itself, that the license explicitly permits redistribution inside an\n"
            "application. This tool cannot verify that for you — re-run once you have."
        )

    output_path = AUDIO_DIR / f"{args.id}.ogg"
    if output_path.exists() and not args.force:
        print(f"{output_path} already exists — pass --force to replace it.")
        return 1

    with tempfile.TemporaryDirectory() as tmp:
        tmp_out = Path(tmp) / f"{args.id}.ogg"
        try:
            process(args.input, tmp_out)
        except RuntimeError as exc:
            print(f"processing failed: {exc}")
            return 1

        duration = probe_duration(tmp_out)
        if duration < 0.15:
            print(
                f"the processed clip is only {duration:.2f}s — silence trimming likely ate the "
                "whole recording (or the source was silent/too quiet). Not importing."
            )
            return 1
        if duration > 6.0:
            print(
                f"warning: the processed clip is {duration:.1f}s, long for a single target sound "
                "in this game (existing targets run well under 3s). Trim the source and re-run "
                "if this was not intentional."
            )

        AUDIO_DIR.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(tmp_out, output_path)

    data = load_sources()
    data["entries"][args.id] = {
        "display_name_ar": args.name_ar,
        "display_name_en": args.name_en,
        "source": args.source,
        "creator": args.creator,
        "license": args.license,
        "source_url": args.source_url,
        "original_filename": args.input.name,
        "file": output_path.name,
        "downloaded_at": datetime.date.today().isoformat(),
        "processing": {
            "sample_rate": TARGET_RATE,
            "channels": 1,
            "loudness_target_lufs": LOUDNESS_TARGET_LUFS,
            "true_peak_db": TRUE_PEAK_DB,
            "silence_trimmed": True,
        },
        "reviewed_by_ear": False,
    }
    save_sources(data)

    print(f"imported: {output_path}  ({duration:.2f}s)")
    print(
        "NOT yet reviewed. Listen to it now — it must be instantly recognizable as "
        f"'{args.name_en}' / '{args.name_ar}' to a person who has never seen this task, "
        "not a generic tone. Only then set reviewed_by_ear: true in sources.json."
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
