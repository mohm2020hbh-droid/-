"""End-to-end tests over real WebSocket connections.

These cover the verification steps of the specification: a full match (T1), a
bad room code (T2), a mid-match disconnect (T3), byte-exact audio relay (T4),
and score accumulation with alternating roles across many rounds (T5).
"""

from __future__ import annotations

import os

import pytest

from app.config import Settings
from app.main import create_app
from fastapi.testclient import TestClient

from .conftest import TEST_ROUNDS
from .helpers import expect, handshake, receive_audio, send, send_audio


def open_room(client) -> tuple:
    """Connect both players and play up to the first ``round_start``.

    Both sockets are entered defensively: if a later assertion in this
    function fails, the already-opened one is still closed, so a broken
    check here can never leak a live WebSocket test session into the
    ``client`` fixture's teardown (an open session there hangs the whole
    suite instead of failing one test).
    """

    host_socket = client.websocket_connect("/ws")
    host_socket.__enter__()
    try:
        host_id = handshake(host_socket)
        send(host_socket, "create_room")
        code = expect(host_socket, "room_created")["code"]

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
        assert ready_host["player_a_id"] == host_id
        assert ready_host["player_b_id"] == guest_id
        assert ready_host["mode"] == "duel"
    except BaseException:
        host_socket.__exit__(None, None, None)
        guest_socket.__exit__(None, None, None)
        raise

    return host_socket, host_id, guest_socket, guest_id, code


def play_round(sockets: dict, audio: bytes) -> tuple[dict, dict]:
    """Play one round from ``round_start`` to ``round_result``.

    Returns the round_start payload and the round_result payload.
    """

    starts = {pid: expect(sock, "round_start") for pid, sock in sockets.items()}
    assert len(set(map(str, starts.values()))) == 1, "both players must see the same round"
    start = next(iter(starts.values()))

    performer_id = start["performer_id"]
    rater_id = next(pid for pid in sockets if pid != performer_id)

    send_audio(sockets[performer_id], start["round_number"], audio)

    assert expect(sockets[rater_id], "audio_ready") == {
        "round_number": start["round_number"]
    }
    header, relayed = receive_audio(sockets[rater_id])
    assert relayed == audio, "the recording must arrive byte-for-byte intact"
    assert header["round_number"] == start["round_number"]
    assert header["performer_id"] == performer_id

    score = 10 * start["round_number"]
    send(sockets[rater_id], "rating_submitted", {"round_number": start["round_number"], "score": score})

    results = {pid: expect(sock, "round_result") for pid, sock in sockets.items()}
    assert len(set(map(str, results.values()))) == 1
    return start, next(iter(results.values()))


# ---------------------------------------------------------------------------
# T1 / T5 — a full match
# ---------------------------------------------------------------------------
def test_full_match_alternates_roles_and_accumulates_scores(client):
    host_socket, host_id, guest_socket, guest_id, _ = open_room(client)
    sockets = {host_id: host_socket, guest_id: guest_socket}

    expected_totals = {host_id: 0, guest_id: 0}
    performers = []

    try:
        for round_number in range(1, TEST_ROUNDS + 1):
            start, result = play_round(sockets, audio=f"round-{round_number}".encode())

            assert start["round_number"] == round_number
            assert start["total_rounds"] == TEST_ROUNDS
            assert start["countdown_seconds"] >= 1
            assert start["sound_id"] and start["sound_name"] and start["sound_emoji"]

            performers.append(start["performer_id"])
            expected_totals[start["performer_id"]] += 10 * round_number

            assert result["round_number"] == round_number
            assert result["performer_id"] == start["performer_id"]
            assert result["score"] == 10 * round_number
            assert result["total_scores"] == expected_totals
            assert result["timed_out"] is False

        over_host = expect(host_socket, "game_over")
        over_guest = expect(guest_socket, "game_over")

        assert over_host == over_guest
        assert over_host["final_scores"] == expected_totals
        assert over_host["winner_id"] == max(expected_totals, key=expected_totals.get)

        # Roles alternate, starting with the room creator.
        assert performers == [host_id, guest_id] * (TEST_ROUNDS // 2)
    finally:
        host_socket.__exit__(None, None, None)
        guest_socket.__exit__(None, None, None)


def test_each_round_uses_a_different_sound(client):
    host_socket, host_id, guest_socket, guest_id, _ = open_room(client)
    sockets = {host_id: host_socket, guest_id: guest_socket}
    try:
        sound_ids = []
        for round_number in range(1, TEST_ROUNDS + 1):
            start, _ = play_round(sockets, audio=b"x")
            sound_ids.append(start["sound_id"])
        assert len(set(sound_ids)) == TEST_ROUNDS
    finally:
        host_socket.__exit__(None, None, None)
        guest_socket.__exit__(None, None, None)


def test_a_draw_reports_no_winner(settings):
    """Both players score the same, so ``winner_id`` is null."""

    with TestClient(create_app(settings)) as client:
        host_socket, host_id, guest_socket, guest_id, _ = open_room(client)
        sockets = {host_id: host_socket, guest_id: guest_socket}
        try:
            for _ in range(TEST_ROUNDS):
                start = {
                    pid: expect(sock, "round_start") for pid, sock in sockets.items()
                }[host_id]
                performer_id = start["performer_id"]
                rater_id = next(pid for pid in sockets if pid != performer_id)

                send_audio(sockets[performer_id], start["round_number"], b"audio")
                expect(sockets[rater_id], "audio_ready")
                receive_audio(sockets[rater_id])
                send(
                    sockets[rater_id],
                    "rating_submitted",
                    {"round_number": start["round_number"], "score": 50},
                )
                for sock in sockets.values():
                    expect(sock, "round_result")

            game_over = expect(host_socket, "game_over")
            assert game_over["winner_id"] is None
            assert set(game_over["final_scores"].values()) == {50 * (TEST_ROUNDS // 2)}
        finally:
            host_socket.__exit__(None, None, None)
            guest_socket.__exit__(None, None, None)


# ---------------------------------------------------------------------------
# T4 — audio integrity
# ---------------------------------------------------------------------------
def test_a_large_binary_recording_survives_the_relay_unchanged(client):
    host_socket, host_id, guest_socket, guest_id, _ = open_room(client)
    sockets = {host_id: host_socket, guest_id: guest_socket}
    try:
        # Every byte value, repeated — catches any text/UTF-8 mangling.
        audio = os.urandom(32_000) + bytes(range(256))
        start, result = play_round(sockets, audio=audio)
        assert result["round_number"] == start["round_number"]
    finally:
        host_socket.__exit__(None, None, None)
        guest_socket.__exit__(None, None, None)


def test_oversized_audio_is_refused_without_dropping_the_match(client):
    host_socket, host_id, guest_socket, guest_id, _ = open_room(client)
    sockets = {host_id: host_socket, guest_id: guest_socket}
    try:
        start = expect(host_socket, "round_start")
        expect(guest_socket, "round_start")
        performer_id = start["performer_id"]

        send_audio(sockets[performer_id], start["round_number"], b"\x00" * 70_000)
        assert expect(sockets[performer_id], "error")["reason"] == "audio_too_large"

        # The round is untouched: a normal recording still works.
        send_audio(sockets[performer_id], start["round_number"], b"ok")
        rater_id = next(pid for pid in sockets if pid != performer_id)
        expect(sockets[rater_id], "audio_ready")
        assert receive_audio(sockets[rater_id])[1] == b"ok"
    finally:
        host_socket.__exit__(None, None, None)
        guest_socket.__exit__(None, None, None)


# ---------------------------------------------------------------------------
# T2 — bad codes
# ---------------------------------------------------------------------------
def test_joining_an_unknown_code_returns_a_clear_error(client):
    with client.websocket_connect("/ws") as socket:
        handshake(socket)
        send(socket, "join_room", {"code": "0000"})

        assert expect(socket, "error") == {"reason": "invalid_code"}

        # The socket stays usable — the client can retry with a good code.
        send(socket, "create_room")
        assert expect(socket, "room_created")["code"].isdigit()


def test_joining_a_full_room_returns_room_full(client):
    host_socket, _, guest_socket, _, code = open_room(client)
    try:
        expect(host_socket, "round_start")
        expect(guest_socket, "round_start")

        with client.websocket_connect("/ws") as third:
            handshake(third)
            send(third, "join_room", {"code": code})
            assert expect(third, "error") == {"reason": "room_full"}
    finally:
        host_socket.__exit__(None, None, None)
        guest_socket.__exit__(None, None, None)


@pytest.mark.parametrize("code", ["", "abc", "12", "99999", "  "])
def test_malformed_codes_are_rejected_as_invalid(client, code):
    with client.websocket_connect("/ws") as socket:
        handshake(socket)
        send(socket, "join_room", {"code": code})
        assert expect(socket, "error") == {"reason": "invalid_code"}


def test_unknown_message_types_do_not_kill_the_connection(client):
    with client.websocket_connect("/ws") as socket:
        handshake(socket)
        socket.send_text("this is not json")
        assert expect(socket, "error") == {"reason": "invalid_message"}

        send(socket, "no_such_command")
        assert expect(socket, "error") == {"reason": "invalid_message"}

        send(socket, "ping")
        expect(socket, "pong")


# ---------------------------------------------------------------------------
# T3 — disconnect
# ---------------------------------------------------------------------------
def test_disconnecting_mid_match_notifies_the_opponent(client):
    host_socket, host_id, guest_socket, guest_id, code = open_room(client)
    try:
        expect(host_socket, "round_start")
        expect(guest_socket, "round_start")

        guest_socket.__exit__(None, None, None)

        assert expect(host_socket, "opponent_disconnected")["reason"] == "disconnected"
    finally:
        host_socket.__exit__(None, None, None)

    # The room is gone, so its code can no longer be joined.
    with client.websocket_connect("/ws") as socket:
        handshake(socket)
        send(socket, "join_room", {"code": code})
        assert expect(socket, "error") == {"reason": "invalid_code"}


def test_leaving_deliberately_notifies_the_opponent_and_frees_the_player(client):
    host_socket, host_id, guest_socket, guest_id, _ = open_room(client)
    try:
        expect(host_socket, "round_start")
        expect(guest_socket, "round_start")

        send(guest_socket, "leave_room")
        assert expect(host_socket, "opponent_disconnected")["reason"] == "left"

        # The leaver can immediately host a new room on the same socket.
        send(guest_socket, "create_room")
        assert expect(guest_socket, "room_created")["code"].isdigit()
    finally:
        host_socket.__exit__(None, None, None)
        guest_socket.__exit__(None, None, None)


def test_host_disconnecting_in_the_waiting_room_frees_the_code(client):
    with client.websocket_connect("/ws") as socket:
        handshake(socket)
        send(socket, "create_room")
        code = expect(socket, "room_created")["code"]

    with client.websocket_connect("/ws") as socket:
        handshake(socket)
        send(socket, "join_room", {"code": code})
        assert expect(socket, "error") == {"reason": "invalid_code"}


# ---------------------------------------------------------------------------
# Turn and phase enforcement
# ---------------------------------------------------------------------------
def test_the_rater_cannot_send_audio_and_the_performer_cannot_rate(client):
    host_socket, host_id, guest_socket, guest_id, _ = open_room(client)
    sockets = {host_id: host_socket, guest_id: guest_socket}
    try:
        start = expect(host_socket, "round_start")
        expect(guest_socket, "round_start")
        performer_id = start["performer_id"]
        rater_id = next(pid for pid in sockets if pid != performer_id)

        send_audio(sockets[rater_id], start["round_number"], b"nope")
        assert expect(sockets[rater_id], "error") == {"reason": "not_your_turn"}

        send(
            sockets[performer_id],
            "rating_submitted",
            {"round_number": start["round_number"], "score": 100},
        )
        assert expect(sockets[performer_id], "error") == {"reason": "wrong_phase"}
    finally:
        host_socket.__exit__(None, None, None)
        guest_socket.__exit__(None, None, None)


def test_audio_for_the_wrong_round_is_refused(client):
    host_socket, host_id, guest_socket, guest_id, _ = open_room(client)
    sockets = {host_id: host_socket, guest_id: guest_socket}
    try:
        start = expect(host_socket, "round_start")
        expect(guest_socket, "round_start")
        performer_id = start["performer_id"]

        send_audio(sockets[performer_id], start["round_number"] + 7, b"late")
        assert expect(sockets[performer_id], "error") == {"reason": "stale_round"}
    finally:
        host_socket.__exit__(None, None, None)
        guest_socket.__exit__(None, None, None)


@pytest.mark.parametrize("score", [-1, 101, "abc", None, 5.5, True])
def test_out_of_range_scores_are_refused(client, score):
    host_socket, host_id, guest_socket, guest_id, _ = open_room(client)
    sockets = {host_id: host_socket, guest_id: guest_socket}
    try:
        start = expect(host_socket, "round_start")
        expect(guest_socket, "round_start")
        performer_id = start["performer_id"]
        rater_id = next(pid for pid in sockets if pid != performer_id)

        send_audio(sockets[performer_id], start["round_number"], b"audio")
        expect(sockets[rater_id], "audio_ready")
        receive_audio(sockets[rater_id])

        send(
            sockets[rater_id],
            "rating_submitted",
            {"round_number": start["round_number"], "score": score},
        )
        assert expect(sockets[rater_id], "error") == {"reason": "invalid_score"}
    finally:
        host_socket.__exit__(None, None, None)
        guest_socket.__exit__(None, None, None)


def test_creating_a_second_room_while_seated_is_refused(client):
    host_socket, host_id, guest_socket, guest_id, _ = open_room(client)
    try:
        expect(host_socket, "round_start")
        expect(guest_socket, "round_start")

        send(host_socket, "create_room")
        assert expect(host_socket, "error") == {"reason": "already_in_room"}
    finally:
        host_socket.__exit__(None, None, None)
        guest_socket.__exit__(None, None, None)


def test_rating_without_a_room_is_refused(client):
    with client.websocket_connect("/ws") as socket:
        handshake(socket)
        send(socket, "rating_submitted", {"round_number": 1, "score": 50})
        assert expect(socket, "error") == {"reason": "not_in_room"}


def test_health_endpoint_reports_live_room_count(client):
    with client.websocket_connect("/ws") as socket:
        handshake(socket)
        send(socket, "create_room")
        expect(socket, "room_created")

        body = client.get("/health").json()
        assert body["status"] == "ok"
        assert body["active_rooms"] == 1
        assert body["total_rounds"] == TEST_ROUNDS
