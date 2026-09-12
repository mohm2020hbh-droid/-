"""Application factory for the Voice Duel backend."""

from __future__ import annotations

import asyncio
import logging
from contextlib import asynccontextmanager

from fastapi import FastAPI

from . import __version__
from .config import Settings
from .routers import health, ws
from .services.game_service import GameService
from .services.room_manager import RoomManager

logging.basicConfig(
    level=logging.INFO, format="%(asctime)s %(levelname)-8s %(name)s: %(message)s"
)


def create_app(settings: Settings | None = None) -> FastAPI:
    resolved = settings or Settings.from_env()

    @asynccontextmanager
    async def lifespan(application: FastAPI):
        janitor = asyncio.create_task(application.state.rooms.run_janitor())
        try:
            yield
        finally:
            janitor.cancel()
            try:
                await janitor
            except asyncio.CancelledError:
                pass

    application = FastAPI(
        title="Voice Duel Online",
        version=__version__,
        description="Real-time 1v1 voice-imitation duel. Rooms live in memory only.",
        lifespan=lifespan,
    )

    rooms = RoomManager(resolved)
    application.state.settings = resolved
    application.state.rooms = rooms
    application.state.game_service = GameService(rooms, resolved)

    application.include_router(health.router)
    application.include_router(ws.router)
    return application


app = create_app()
