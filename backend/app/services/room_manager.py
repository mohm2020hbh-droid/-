"""In-memory room registry: code allocation, seating, and expiry (C1)."""

from __future__ import annotations

import asyncio
import logging
import random
import time
from typing import Iterator, Optional

from ..config import Settings
from ..models.messages import ERR_INVALID_CODE, ERR_ROOM_FULL
from ..models.room import Player, RoomState

logger = logging.getLogger(__name__)

CODE_LENGTH = 4
_CODE_MIN = 10 ** (CODE_LENGTH - 1)
_CODE_MAX = 10**CODE_LENGTH - 1
_CODE_ATTEMPTS = 200

ERR_NO_CODES_AVAILABLE = "no_codes_available"


class RoomError(Exception):
    """A room operation the client asked for cannot be satisfied."""

    def __init__(self, reason: str) -> None:
        super().__init__(reason)
        self.reason = reason


class RoomManager:
    """Holds every live room. Rooms exist only in this process's memory."""

    def __init__(self, settings: Settings, rng: random.Random | None = None) -> None:
        self._settings = settings
        self._rng = rng or random.Random()
        self._rooms: dict[str, RoomState] = {}
        self._room_of_player: dict[str, str] = {}

    # ------------------------------------------------------------------
    # Lookups
    # ------------------------------------------------------------------
    def get(self, code: str) -> Optional[RoomState]:
        return self._rooms.get(code)

    def room_of_player(self, player_id: str) -> Optional[RoomState]:
        code = self._room_of_player.get(player_id)
        return self._rooms.get(code) if code else None

    def __len__(self) -> int:
        return len(self._rooms)

    def __iter__(self) -> Iterator[RoomState]:
        return iter(list(self._rooms.values()))

    # ------------------------------------------------------------------
    # Mutations
    # ------------------------------------------------------------------
    def generate_code(self) -> str:
        """Allocate a 4-digit code that no live room is already using."""

        for _ in range(_CODE_ATTEMPTS):
            code = str(self._rng.randint(_CODE_MIN, _CODE_MAX))
            if code not in self._rooms:
                return code

        # Extremely unlikely: fall back to a full scan before giving up.
        for candidate in range(_CODE_MIN, _CODE_MAX + 1):
            code = str(candidate)
            if code not in self._rooms:
                return code
        raise RoomError(ERR_NO_CODES_AVAILABLE)

    def create_room(self, player: Player) -> RoomState:
        room = RoomState(code=self.generate_code())
        room.add_player(player)
        self._rooms[room.code] = room
        self._room_of_player[player.player_id] = room.code
        logger.info("room %s created by player %s", room.code, player.player_id)
        return room

    def join_room(self, code: str, player: Player) -> RoomState:
        room = self._rooms.get(str(code).strip())
        if room is None:
            raise RoomError(ERR_INVALID_CODE)
        if room.is_full:
            raise RoomError(ERR_ROOM_FULL)

        room.add_player(player)
        self._room_of_player[player.player_id] = room.code
        logger.info("player %s joined room %s", player.player_id, room.code)
        return room

    def leave_room(self, player_id: str) -> Optional[RoomState]:
        """Detach a player from their room. Returns the room they left."""

        room = self.room_of_player(player_id)
        self._room_of_player.pop(player_id, None)
        if room is not None:
            room.remove_player(player_id)
        return room

    def remove_room(self, code: str) -> Optional[RoomState]:
        room = self._rooms.pop(code, None)
        if room is None:
            return None
        room.cancel_watchdog()
        for player_id in list(room.player_ids):
            self._room_of_player.pop(player_id, None)
        logger.info("room %s removed", code)
        return room

    def purge_expired(self, now: Optional[float] = None) -> list[RoomState]:
        """Drop rooms nobody joined within the TTL."""

        current = now if now is not None else time.monotonic()
        expired = [
            room
            for room in list(self._rooms.values())
            if room.is_expired(self._settings.room_ttl_seconds, current)
        ]
        for room in expired:
            self.remove_room(room.code)
        return expired

    async def run_janitor(self) -> None:
        """Background sweep; cancelled on application shutdown."""

        interval = max(1, self._settings.janitor_interval_seconds)
        while True:
            try:
                await asyncio.sleep(interval)
                purged = self.purge_expired()
                if purged:
                    logger.info("janitor purged %d idle room(s)", len(purged))
            except asyncio.CancelledError:
                raise
            except Exception:  # noqa: BLE001 - the janitor must never die
                logger.exception("janitor sweep failed")
