"""Voice Battle: both players imitate at once, self-report an acoustic score,
and the server compares them. No human rater, no relayed audio.
"""

from __future__ import annotations

import pytest
from fastapi.testclient import TestClient

from app.config import Settings
from app.main import create_app

from .helpers import expect, handshake, send

BATTLE_ROUNDS = 3


@pytest.fixture
def battle_settings() -> Settings:
    return Settings(
        battle_total_rounds=BATTLE_ROUNDS,
        battle_attempt_seconds=1,
        battle_attempt_grace_seconds=1,
        room_ttl_seconds=2,
        janitor_interval_seconds=1,
    )


@pytest.fixture
def client(battle_settings: Settings):
    with TestClient(create_app(battle_settings)) as test_client:
        yield test_client


def open_battle_room(client) -> tuple:
    """Create and join a Voice Battle room, up through the first round_start.

    Both sockets are entered defensively so that a failed assertion here
    cannot leak a live WebSocket test session into the ``client`` fixture's
    teardown, which would hang the whole suite instead of failing one test.
    """

    host_socket = client.websocket_connect("/ws")
    host_socket.__enter__()
    try:
        host_id = handshake(host_socket)
        send(host_socket, "create_room", {"mode": "battle"})
        created = expect(host_socket, "room_created")
        assert created["mode"] == "battle"
        code = created["code"]

        guest_socket = client.websocket_connect("/ws")
        guest_socket.__enter__()
    except BaseException:
        host_socket.__exit__(None, None, None)
        raise

    try:
        guest_id = handshake(guest_socket)
        send(guest_socket, "join_room", {"code": code})

        ready_host = expect(host_socket, "players_ready")
        ready_guest = expect(guest_socket, "players_ready")
        assert ready_host == ready_guest
        assert ready_host["mode"] == "battle"
    except BaseException:
        host_socket.__exit__(None, None, None)
        guest_socket.__exit__(None, None, None)
        raise

    return host_socket, host_id, guest_socket, guest_id, code


def close(*sockets) -> None:
    for socket in sockets:
        socket.__exit__(None, None, None)


# ---------------------------------------------------------------------------
# A full match
# ---------------------------------------------------------------------------
def test_full_battle_match_alternates_targets_and_tallies_wins(client):
    host, host_id, guest, guest_id, _ = open_battle_room(client)
    sockets = {host_id: host, guest_id: guest}
    expected_wins = {host_id: 0, guest_id: 0}

    try:
        for round_number in range(1, BATTLE_ROUNDS + 1):
            start_host = expect(host, "battle_round_start")
            start_guest = expect(guest, "battle_round_start")
            assert start_host == start_guest
            assert start_host["round_number"] == round_number
            assert start_host["total_rounds"] == BATTLE_ROUNDS
            assert start_host["sound_id"] and start_host["sound_name"] and start_host["sound_emoji"]
            assert start_host["attempt_seconds"] >= 1

            host_score = 40 + round_number
            guest_score = 50 + round_number
            send(host, "battle_attempt_submitted", {"round_number": round_number, "score": host_score})
            send(guest, "battle_attempt_submitted", {"round_number": round_number, "score": guest_score})

            result_host = expect(host, "battle_round_result")
            result_guest = expect(guest, "battle_round_result")
            assert result_host == result_guest
            assert result_host["scores"] == {host_id: host_score, guest_id: guest_score}
            assert result_host["round_winner_id"] == guest_id  # guest always scores higher here
            expected_wins[guest_id] += 1
            assert result_host["total_wins"] == expected_wins
            assert result_host["timed_out"] is False

        over_host = expect(host, "battle_over")
        over_guest = expect(guest, "battle_over")
        assert over_host == over_guest
        assert over_host["winner_id"] == guest_id
        assert over_host["total_wins"] == expected_wins
    finally:
        close(host, guest)


def test_each_battle_round_uses_a_different_sound(client):
    host, host_id, guest, guest_id, _ = open_battle_room(client)
    try:
        sound_ids = []
        for round_number in range(1, BATTLE_ROUNDS + 1):
            start = expect(host, "battle_round_start")
            expect(guest, "battle_round_start")
            sound_ids.append(start["sound_id"])
            send(host, "battle_attempt_submitted", {"round_number": round_number, "score": 50})
            send(guest, "battle_attempt_submitted", {"round_number": round_number, "score": 50})
            expect(host, "battle_round_result")
            expect(guest, "battle_round_result")
        assert len(set(sound_ids)) == BATTLE_ROUNDS
    finally:
        close(host, guest)


def test_a_tied_round_crowns_no_round_winner(client):
    host, host_id, guest, guest_id, _ = open_battle_room(client)
    try:
        for round_number in range(1, BATTLE_ROUNDS + 1):
            expect(host, "battle_round_start")
            expect(guest, "battle_round_start")
            send(host, "battle_attempt_submitted", {"round_number": round_number, "score": 60})
            send(guest, "battle_attempt_submitted", {"round_number": round_number, "score": 60})
            result = expect(host, "battle_round_result")
            expect(guest, "battle_round_result")
            assert result["round_winner_id"] is None

        over = expect(host, "battle_over")
        assert over["winner_id"] is None
        assert over["total_wins"] == {host_id: 0, guest_id: 0}
    finally:
        close(host, guest)


# ---------------------------------------------------------------------------
# Timeout: a silent client can never stall the other
# ---------------------------------------------------------------------------
def test_a_player_who_never_submits_loses_the_round_by_default(client):
    host, host_id, guest, guest_id, _ = open_battle_room(client)
    try:
        expect(host, "battle_round_start")
        expect(guest, "battle_round_start")

        # Only the guest submits; the host's silence must not freeze the match.
        send(guest, "battle_attempt_submitted", {"round_number": 1, "score": 70})

        result_host = expect(host, "battle_round_result")
        result_guest = expect(guest, "battle_round_result")
        assert result_host == result_guest
        assert result_host["scores"][host_id] == 0
        assert result_host["scores"][guest_id] == 70
        assert result_host["round_winner_id"] == guest_id
        assert result_host["timed_out"] is True

        # The match still moves on to the next round afterwards.
        assert expect(host, "battle_round_start")["round_number"] == 2
    finally:
        close(host, guest)


# ---------------------------------------------------------------------------
# Validation and phase enforcement
# ---------------------------------------------------------------------------
def test_an_invalid_mode_is_rejected(client):
    with client.websocket_connect("/ws") as socket:
        handshake(socket)
        send(socket, "create_room", {"mode": "tournament"})
        assert expect(socket, "error") == {"reason": "invalid_mode"}


def test_omitting_mode_still_defaults_to_duel(client):
    """Regression: plain create_room (no payload) must keep working exactly
    as it always has for the original room game.
    """

    with client.websocket_connect("/ws") as socket:
        handshake(socket)
        send(socket, "create_room")
        created = expect(socket, "room_created")
        assert created["mode"] == "duel"


def test_a_battle_score_out_of_range_is_refused(client):
    host, host_id, guest, guest_id, _ = open_battle_room(client)
    try:
        expect(host, "battle_round_start")
        expect(guest, "battle_round_start")
        send(host, "battle_attempt_submitted", {"round_number": 1, "score": 101})
        assert expect(host, "error") == {"reason": "invalid_score"}
    finally:
        close(host, guest)


def test_a_battle_score_for_the_wrong_round_is_refused(client):
    host, host_id, guest, guest_id, _ = open_battle_room(client)
    try:
        expect(host, "battle_round_start")
        expect(guest, "battle_round_start")
        send(host, "battle_attempt_submitted", {"round_number": 99, "score": 50})
        assert expect(host, "error") == {"reason": "stale_round"}
    finally:
        close(host, guest)


def test_rating_submitted_is_refused_in_a_battle_room(client):
    """The human-slider message from duel mode must not work in battle mode."""

    host, host_id, guest, guest_id, _ = open_battle_room(client)
    try:
        expect(host, "battle_round_start")
        expect(guest, "battle_round_start")
        send(host, "rating_submitted", {"round_number": 1, "score": 50})
        assert expect(host, "error") == {"reason": "wrong_phase"}
    finally:
        close(host, guest)


def test_a_duplicate_submission_does_not_double_count(client):
    host, host_id, guest, guest_id, _ = open_battle_room(client)
    try:
        expect(host, "battle_round_start")
        expect(guest, "battle_round_start")
        send(host, "battle_attempt_submitted", {"round_number": 1, "score": 10})
        send(host, "battle_attempt_submitted", {"round_number": 1, "score": 99})  # ignored
        send(guest, "battle_attempt_submitted", {"round_number": 1, "score": 20})
        result = expect(host, "battle_round_result")
        expect(guest, "battle_round_result")
        assert result["scores"][host_id] == 10
    finally:
        close(host, guest)


# ---------------------------------------------------------------------------
# Disconnect
# ---------------------------------------------------------------------------
def test_disconnecting_mid_battle_notifies_the_opponent(client):
    host, host_id, guest, guest_id, code = open_battle_room(client)
    try:
        expect(host, "battle_round_start")
        expect(guest, "battle_round_start")

        guest.__exit__(None, None, None)

        assert expect(host, "opponent_disconnected")["reason"] == "disconnected"
    finally:
        host.__exit__(None, None, None)

    with client.websocket_connect("/ws") as socket:
        handshake(socket)
        send(socket, "join_room", {"code": code})
        assert expect(socket, "error") == {"reason": "invalid_code"}


def test_joining_a_full_battle_room_is_rejected(client):
    host, host_id, guest, guest_id, code = open_battle_room(client)
    try:
        expect(host, "battle_round_start")
        expect(guest, "battle_round_start")
        with client.websocket_connect("/ws") as third:
            handshake(third)
            send(third, "join_room", {"code": code})
            assert expect(third, "error") == {"reason": "room_full"}
    finally:
        close(host, guest)
