"""The round watchdog: a silent client must never freeze the match."""

from __future__ import annotations

import pytest
from fastapi.testclient import TestClient

from app.config import Settings
from app.main import create_app

from .helpers import expect, handshake, receive_audio, send, send_audio


@pytest.fixture
def impatient_client():
    settings = Settings(
        total_rounds=2,
        countdown_seconds=1,
        performance_grace_seconds=1,
        rating_timeout_seconds=1,
        room_ttl_seconds=1,
        janitor_interval_seconds=1,
    )
    with TestClient(create_app(settings)) as client:
        yield client


def seat_two(client):
    host = client.websocket_connect("/ws")
    host.__enter__()
    host_id = handshake(host)
    send(host, "create_room")
    code = expect(host, "room_created")["code"]

    guest = client.websocket_connect("/ws")
    guest.__enter__()
    guest_id = handshake(guest)
    send(guest, "join_room", {"code": code})
    expect(host, "players_ready")
    expect(guest, "players_ready")
    return host, host_id, guest, guest_id


def test_a_performer_who_never_records_scores_zero_and_the_match_moves_on(
    impatient_client,
):
    host, host_id, guest, guest_id = seat_two(impatient_client)
    try:
        start = expect(host, "round_start")
        expect(guest, "round_start")

        # Nobody sends audio; the watchdog closes the round.
        result = expect(host, "round_result")
        expect(guest, "round_result")

        assert result["round_number"] == start["round_number"]
        assert result["score"] == 0
        assert result["timed_out"] is True
        assert result["total_scores"][start["performer_id"]] == 0

        # The next round still starts normally.
        assert expect(host, "round_start")["round_number"] == 2
    finally:
        host.__exit__(None, None, None)
        guest.__exit__(None, None, None)


def test_a_rater_who_never_scores_does_not_stall_the_match(impatient_client):
    host, host_id, guest, guest_id = seat_two(impatient_client)
    sockets = {host_id: host, guest_id: guest}
    try:
        start = expect(host, "round_start")
        expect(guest, "round_start")
        performer_id = start["performer_id"]
        rater_id = next(pid for pid in sockets if pid != performer_id)

        send_audio(sockets[performer_id], start["round_number"], b"audio")
        expect(sockets[rater_id], "audio_ready")
        receive_audio(sockets[rater_id])

        # The rater never moves the slider; the watchdog closes the round.
        result = expect(host, "round_result")
        expect(guest, "round_result")
        assert result["score"] == 0
        assert result["timed_out"] is True
    finally:
        host.__exit__(None, None, None)
        guest.__exit__(None, None, None)
