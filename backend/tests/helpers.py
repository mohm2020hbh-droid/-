"""Small helpers that drive a match the way the Android client does."""

from __future__ import annotations

from typing import Any

from app.models.messages import decode_audio_frame, encode_audio_frame


def expect(socket, message_type: str) -> dict[str, Any]:
    """Receive the next JSON frame and assert its type."""

    message = socket.receive_json()
    assert message["type"] == message_type, f"expected {message_type}, got {message}"
    return message["payload"]


def connect(client, path: str = "/ws"):
    return client.websocket_connect(path)


def handshake(socket) -> str:
    """Read the ``connected`` frame and return this socket's player id."""

    return expect(socket, "connected")["player_id"]


def send(socket, message_type: str, payload: dict[str, Any] | None = None) -> None:
    socket.send_json({"type": message_type, "payload": payload or {}})


def send_audio(socket, round_number: int, audio: bytes, mime: str = "audio/mp4") -> None:
    socket.send_bytes(
        encode_audio_frame({"round_number": round_number, "mime": mime}, audio)
    )


def receive_audio(socket) -> tuple[dict[str, Any], bytes]:
    return decode_audio_frame(socket.receive_bytes())
