"""The wire protocol shared by the server and the Android client.

Two kinds of frames travel over the single WebSocket connection:

* **Text frames** — JSON objects shaped ``{"type": ..., "payload": {...}}``.
* **Binary frames** — one recording, framed as::

      [4 bytes big-endian header length][UTF-8 JSON header][raw audio bytes]

  The header carries at least ``round_number`` so a late recording from a
  previous round can be recognised and dropped instead of being relayed.
"""

from __future__ import annotations

import json
import struct
from typing import Any, Final

# ---------------------------------------------------------------------------
# Client -> Server
# ---------------------------------------------------------------------------
CREATE_ROOM: Final = "create_room"
JOIN_ROOM: Final = "join_room"
RATING_SUBMITTED: Final = "rating_submitted"
LEAVE_ROOM: Final = "leave_room"
PING: Final = "ping"

# ---------------------------------------------------------------------------
# Server -> Client
# ---------------------------------------------------------------------------
CONNECTED: Final = "connected"
ROOM_CREATED: Final = "room_created"
PLAYERS_READY: Final = "players_ready"
ROUND_START: Final = "round_start"
AUDIO_READY: Final = "audio_ready"
ROUND_RESULT: Final = "round_result"
GAME_OVER: Final = "game_over"
OPPONENT_DISCONNECTED: Final = "opponent_disconnected"
ERROR: Final = "error"
PONG: Final = "pong"

# ---------------------------------------------------------------------------
# Error reasons
# ---------------------------------------------------------------------------
ERR_INVALID_CODE: Final = "invalid_code"
ERR_ROOM_FULL: Final = "room_full"
ERR_ALREADY_IN_ROOM: Final = "already_in_room"
ERR_NOT_IN_ROOM: Final = "not_in_room"
ERR_NOT_YOUR_TURN: Final = "not_your_turn"
ERR_WRONG_PHASE: Final = "wrong_phase"
ERR_STALE_ROUND: Final = "stale_round"
ERR_INVALID_SCORE: Final = "invalid_score"
ERR_INVALID_MESSAGE: Final = "invalid_message"
ERR_AUDIO_TOO_LARGE: Final = "audio_too_large"

MAX_HEADER_BYTES: Final = 4096
_LENGTH_PREFIX: Final = struct.Struct(">I")


class ProtocolError(ValueError):
    """A frame that does not follow the protocol."""

    def __init__(self, reason: str = ERR_INVALID_MESSAGE) -> None:
        super().__init__(reason)
        self.reason = reason


def envelope(message_type: str, payload: dict[str, Any] | None = None) -> dict[str, Any]:
    """Build a ``{type, payload}`` message."""

    return {"type": message_type, "payload": payload or {}}


def parse_message(raw: str) -> tuple[str, dict[str, Any]]:
    """Parse an incoming text frame into ``(type, payload)``."""

    try:
        data = json.loads(raw)
    except (json.JSONDecodeError, TypeError) as exc:
        raise ProtocolError() from exc

    if not isinstance(data, dict):
        raise ProtocolError()

    message_type = data.get("type")
    if not isinstance(message_type, str) or not message_type:
        raise ProtocolError()

    payload = data.get("payload", {})
    if payload is None:
        payload = {}
    if not isinstance(payload, dict):
        raise ProtocolError()

    return message_type, payload


def encode_audio_frame(header: dict[str, Any], audio: bytes) -> bytes:
    """Frame a recording for the wire."""

    encoded_header = json.dumps(header, ensure_ascii=False).encode("utf-8")
    if len(encoded_header) > MAX_HEADER_BYTES:
        raise ProtocolError()
    return _LENGTH_PREFIX.pack(len(encoded_header)) + encoded_header + audio


def decode_audio_frame(frame: bytes) -> tuple[dict[str, Any], bytes]:
    """Split a binary frame back into ``(header, audio)``."""

    if len(frame) < _LENGTH_PREFIX.size:
        raise ProtocolError()

    (header_length,) = _LENGTH_PREFIX.unpack_from(frame, 0)
    if header_length == 0 or header_length > MAX_HEADER_BYTES:
        raise ProtocolError()

    header_end = _LENGTH_PREFIX.size + header_length
    if len(frame) < header_end:
        raise ProtocolError()

    try:
        header = json.loads(frame[_LENGTH_PREFIX.size : header_end].decode("utf-8"))
    except (json.JSONDecodeError, UnicodeDecodeError) as exc:
        raise ProtocolError() from exc

    if not isinstance(header, dict):
        raise ProtocolError()

    return header, frame[header_end:]
