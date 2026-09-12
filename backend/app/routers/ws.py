"""The single game WebSocket endpoint.

One connection per client carries both the JSON control messages and the binary
audio frames; :mod:`app.services.game_service` owns all the game rules.
"""

from __future__ import annotations

import logging
from uuid import uuid4

from fastapi import APIRouter, WebSocket, WebSocketDisconnect

from ..models import messages as msg
from ..models.room import Player
from ..services.connection import Connection
from ..services.game_service import GameService

logger = logging.getLogger(__name__)

router = APIRouter()


async def _dispatch_text(game: GameService, player: Player, raw: str) -> None:
    try:
        message_type, payload = msg.parse_message(raw)
    except msg.ProtocolError as exc:
        await player.connection.send_message(msg.ERROR, {"reason": exc.reason})
        return

    if message_type == msg.CREATE_ROOM:
        await game.handle_create_room(player)
    elif message_type == msg.JOIN_ROOM:
        await game.handle_join_room(player, payload)
    elif message_type == msg.RATING_SUBMITTED:
        await game.handle_rating(player, payload)
    elif message_type == msg.LEAVE_ROOM:
        await game.handle_leave(player)
    elif message_type == msg.PING:
        await player.connection.send_message(msg.PONG)
    else:
        await player.connection.send_message(
            msg.ERROR, {"reason": msg.ERR_INVALID_MESSAGE}
        )


@router.websocket("/ws")
async def game_socket(websocket: WebSocket) -> None:
    game: GameService = websocket.app.state.game_service

    await websocket.accept()
    player_id = uuid4().hex[:8]
    player = Player(player_id=player_id, connection=Connection(websocket, player_id))
    logger.info("player %s connected", player_id)

    await player.connection.send_message(msg.CONNECTED, {"player_id": player_id})

    try:
        while True:
            event = await websocket.receive()
            event_type = event.get("type")

            if event_type == "websocket.disconnect":
                break

            if event.get("text") is not None:
                await _dispatch_text(game, player, event["text"])
            elif event.get("bytes") is not None:
                await game.handle_audio(player, event["bytes"])
    except WebSocketDisconnect:
        pass
    except RuntimeError:
        # Raised by Starlette when the socket is already closed.
        pass
    finally:
        logger.info("player %s disconnected", player_id)
        await game.handle_disconnect(player)
