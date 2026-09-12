"""A thin, failure-tolerant wrapper around one client WebSocket.

Send failures never propagate into the game logic: a socket that has gone away
is simply marked closed, and the disconnect handler cleans up the room when the
receive loop notices.
"""

from __future__ import annotations

import logging
from typing import Any

from fastapi import WebSocket

from ..models.messages import encode_audio_frame, envelope

logger = logging.getLogger(__name__)


class Connection:
    """One connected player socket."""

    def __init__(self, websocket: WebSocket, player_id: str) -> None:
        self.websocket = websocket
        self.player_id = player_id
        self.closed = False

    async def send_message(self, message_type: str, payload: dict[str, Any] | None = None) -> bool:
        """Send a JSON frame. Returns ``False`` if the socket is gone."""

        if self.closed:
            return False
        try:
            await self.websocket.send_json(envelope(message_type, payload))
            return True
        except Exception:  # noqa: BLE001 - any transport failure means "gone"
            logger.info("send_message(%s) failed for player %s", message_type, self.player_id)
            self.closed = True
            return False

    async def send_audio(self, header: dict[str, Any], audio: bytes) -> bool:
        """Send a framed recording. Returns ``False`` if the socket is gone."""

        if self.closed:
            return False
        try:
            await self.websocket.send_bytes(encode_audio_frame(header, audio))
            return True
        except Exception:  # noqa: BLE001
            logger.info("send_audio failed for player %s", self.player_id)
            self.closed = True
            return False

    def mark_closed(self) -> None:
        self.closed = True
