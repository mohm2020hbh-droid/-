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


@router.get("/play", include_in_schema=False)
async def play() -> FileResponse:
    """The browser client — the same protocol the Android app speaks."""

    return FileResponse(STATIC_DIR / "index.html", media_type="text/html")
