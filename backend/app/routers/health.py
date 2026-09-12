"""Liveness endpoints — handy for checking a deployment from a phone browser."""

from __future__ import annotations

from fastapi import APIRouter, Request

from .. import __version__

router = APIRouter(tags=["health"])


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
