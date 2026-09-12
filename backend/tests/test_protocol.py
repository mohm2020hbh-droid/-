"""Unit tests for the wire protocol codec."""

from __future__ import annotations

import pytest

from app.models import messages as msg


def test_audio_frame_round_trip_preserves_bytes_exactly():
    audio = bytes(range(256)) * 40
    header = {"round_number": 3, "mime": "audio/mp4"}

    decoded_header, decoded_audio = msg.decode_audio_frame(
        msg.encode_audio_frame(header, audio)
    )

    assert decoded_header == header
    assert decoded_audio == audio


def test_audio_frame_supports_empty_payload():
    header, audio = msg.decode_audio_frame(msg.encode_audio_frame({"round_number": 1}, b""))
    assert header == {"round_number": 1}
    assert audio == b""


@pytest.mark.parametrize(
    "frame",
    [
        b"",
        b"\x00\x00",
        b"\x00\x00\x00\x00",          # zero-length header
        b"\x00\x00\x00\x20short",     # header longer than the frame
        b"\x00\x00\x00\x02[]",        # header is not an object
        b"\x00\x00\x00\x03abc",       # header is not JSON
        b"\xff\xff\xff\xffxx",        # absurd header length
    ],
)
def test_malformed_audio_frames_are_rejected(frame: bytes):
    with pytest.raises(msg.ProtocolError):
        msg.decode_audio_frame(frame)


def test_parse_message_accepts_a_well_formed_envelope():
    assert msg.parse_message('{"type":"join_room","payload":{"code":"1234"}}') == (
        "join_room",
        {"code": "1234"},
    )


def test_parse_message_defaults_a_missing_payload():
    assert msg.parse_message('{"type":"create_room"}') == ("create_room", {})


@pytest.mark.parametrize(
    "raw",
    ["", "not json", "[]", '"text"', "{}", '{"type":123}', '{"type":"x","payload":[]}'],
)
def test_parse_message_rejects_garbage(raw: str):
    with pytest.raises(msg.ProtocolError):
        msg.parse_message(raw)
