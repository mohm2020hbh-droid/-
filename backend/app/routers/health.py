"""Liveness endpoints — handy for checking a deployment from a phone browser."""

from __future__ import annotations

from pathlib import Path

from fastapi import APIRouter, Request
from fastapi.responses import FileResponse

from .. import __version__

router = APIRouter(tags=["health"])

STATIC_DIR = Path(__file__).resolve().parent.parent / "static"


@router.get("/")
async def root() -> dict:
    return {"service": "voice-duel", "version": __version__}


@router.get("/health")
async def health(request: Request) -> dict:
    settings = request.app.state.settings
    rooms = request.app.state.rooms
    return {
        "status": "ok",
        "version": __version__,
        "active_rooms": len(rooms),
        "total_rounds": settings.total_rounds,
        "countdown_seconds": settings.countdown_seconds,
    }


AUDIO_DIR = STATIC_DIR / "audio"
AUDIO_SUFFIXES = (".mp3", ".ogg", ".wav")


@router.get("/api/target-audio")
async def target_audio() -> dict:
    """Which target sounds have a real recording dropped in.

    Every target is synthesised on the device, so this list is normally empty
    and the game is fully playable offline. Dropping ``donkey.mp3`` into
    ``app/static/audio/`` makes it appear here, and the client then uses that
    file for both playback and scoring instead of the synthesised version.

    The client asks once, so a bank of 44 sounds costs one request rather than
    a burst of speculative 404s, and a file added while the server is running
    is picked up on the next page load with no restart.
    """

    files: dict[str, str] = {}
    if AUDIO_DIR.is_dir():
        for path in sorted(AUDIO_DIR.iterdir()):
            if path.is_file() and path.suffix.lower() in AUDIO_SUFFIXES:
                # First match wins, in the suffix order the client prefers.
                files.setdefault(path.stem, path.name)

    return {"available": files}


@router.get("/play", include_in_schema=False)
async def play() -> FileResponse:
    """The browser client — the same protocol the Android app speaks.

    The page changes as the game is developed, so it must never be cached:
    without this header a browser can keep showing an old menu or an old
    screen flow indefinitely after an update, with nothing on the server
    side to indicate anything is wrong.
    """

    return FileResponse(
        STATIC_DIR / "index.html",
        media_type="text/html",
        headers={"Cache-Control": "no-store, must-revalidate"},
    )
