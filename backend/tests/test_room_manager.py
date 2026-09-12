"""Unit tests for room allocation, seating, and expiry."""

from __future__ import annotations

import random

import pytest

from app.config import Settings
from app.models.messages import ERR_INVALID_CODE, ERR_ROOM_FULL
from app.models.room import Player, RoomState
from app.services.room_manager import RoomError, RoomManager


class FakeConnection:
    def __init__(self, player_id: str) -> None:
        self.player_id = player_id
        self.closed = False
        self.sent: list[tuple[str, dict]] = []

    async def send_message(self, message_type, payload=None):
        self.sent.append((message_type, payload or {}))
        return True

    async def send_audio(self, header, audio):
        self.sent.append(("audio", header))
        return True

    def mark_closed(self):
        self.closed = True


def make_player(player_id: str) -> Player:
    return Player(player_id=player_id, connection=FakeConnection(player_id))


@pytest.fixture
def manager() -> RoomManager:
    return RoomManager(Settings(room_ttl_seconds=600))


def test_codes_are_four_digits_and_unique(manager: RoomManager):
    codes = {manager.create_room(make_player(f"p{i}")).code for i in range(300)}

    assert len(codes) == 300
    assert all(len(code) == 4 and code.isdigit() for code in codes)


def test_code_generation_skips_codes_already_taken():
    # An RNG that always returns the same value forces a collision.
    class StubbornRandom(random.Random):
        def randint(self, a, b):  # noqa: D102
            return 1234

    manager = RoomManager(Settings(), rng=StubbornRandom())
    first = manager.create_room(make_player("a"))
    second = manager.create_room(make_player("b"))

    assert first.code == "1234"
    assert second.code != first.code


def test_join_seats_the_second_player(manager: RoomManager):
    host = make_player("host")
    room = manager.create_room(host)

    guest = make_player("guest")
    joined = manager.join_room(room.code, guest)

    assert joined is room
    assert joined.player_ids == ["host", "guest"]
    assert joined.is_full
    assert manager.room_of_player("guest") is room


def test_join_with_an_unknown_code_is_rejected(manager: RoomManager):
    with pytest.raises(RoomError) as exc:
        manager.join_room("0000", make_player("guest"))

    assert exc.value.reason == ERR_INVALID_CODE


def test_join_a_full_room_is_rejected(manager: RoomManager):
    room = manager.create_room(make_player("host"))
    manager.join_room(room.code, make_player("guest"))

    with pytest.raises(RoomError) as exc:
        manager.join_room(room.code, make_player("third"))

    assert exc.value.reason == ERR_ROOM_FULL


def test_join_ignores_surrounding_whitespace(manager: RoomManager):
    room = manager.create_room(make_player("host"))

    assert manager.join_room(f"  {room.code} ", make_player("guest")) is room


def test_removing_a_room_forgets_its_players(manager: RoomManager):
    room = manager.create_room(make_player("host"))
    manager.join_room(room.code, make_player("guest"))

    manager.remove_room(room.code)

    assert manager.get(room.code) is None
    assert manager.room_of_player("host") is None
    assert manager.room_of_player("guest") is None
    assert len(manager) == 0


def test_janitor_purges_a_room_nobody_joined(manager: RoomManager):
    room = manager.create_room(make_player("host"))

    assert manager.purge_expired(now=room.last_activity + 10) == []
    purged = manager.purge_expired(now=room.last_activity + 601)

    assert [r.code for r in purged] == [room.code]
    assert manager.get(room.code) is None


def test_janitor_leaves_a_room_that_has_two_players(manager: RoomManager):
    room = manager.create_room(make_player("host"))
    manager.join_room(room.code, make_player("guest"))

    assert manager.purge_expired(now=room.last_activity + 99_999) == []
    assert manager.get(room.code) is room


def test_performer_alternates_every_round():
    room = RoomState(code="1234")
    room.add_player(make_player("host"))
    room.add_player(make_player("guest"))

    assert [room.performer_for_round(n) for n in range(1, 6)] == [
        "host",
        "guest",
        "host",
        "guest",
        "host",
    ]


def test_winner_is_the_highest_total_and_none_on_a_draw():
    room = RoomState(code="1234")
    room.add_player(make_player("host"))
    room.add_player(make_player("guest"))

    room.add_score("host", 70)
    room.add_score("guest", 70)
    assert room.winner_id() is None

    room.add_score("guest", 1)
    assert room.winner_id() == "guest"
