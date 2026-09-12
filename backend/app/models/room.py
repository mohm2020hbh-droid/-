"""In-memory room state (C1 — nothing is persisted).

A room lives only as long as the match: it is created when a player asks for a
code, and dropped when the match ends, a player disconnects, or the room sits
unjoined past its TTL.
"""

from __future__ import annotations

import asyncio
import time
from dataclasses import dataclass, field
from enum import Enum
from typing import TYPE_CHECKING, Optional

from .battle_sounds import BattleSound
from .sounds import Sound

if TYPE_CHECKING:  # pragma: no cover - typing only
    from ..services.connection import Connection


class RoomMode(str, Enum):
    """Which game this room is playing.

    Both modes share the same room/code/lock/disconnect machinery; only the
    round logic in :mod:`app.services.game_service` differs.
    """

    DUEL = "duel"      # the original room game: one performs, one rates by ear
    BATTLE = "battle"  # Voice Battle: both imitate at once, scored acoustically


class RoomPhase(str, Enum):
    """Where a room is in the match lifecycle."""

    WAITING = "waiting"            # created, waiting for the second player
    RECORDING = "recording"        # duel: performer is recording, rater is waiting
    RATING = "rating"              # duel: rater is listening and scoring
    BATTLE_ATTEMPT = "battle_attempt"  # battle: both are recording/scoring at once
    FINISHED = "finished"          # match over


@dataclass
class Player:
    """One connected player seated in a room."""

    player_id: str
    connection: "Connection"


@dataclass
class RoomState:
    """Everything the server knows about one match."""

    code: str
    mode: RoomMode = RoomMode.DUEL
    players: list[Player] = field(default_factory=list)
    phase: RoomPhase = RoomPhase.WAITING
    round_number: int = 0
    scores: dict[str, int] = field(default_factory=dict)
    performer_id: Optional[str] = None
    current_sound: Optional[Sound] = None
    used_sound_ids: set[str] = field(default_factory=set)
    created_at: float = field(default_factory=time.monotonic)
    last_activity: float = field(default_factory=time.monotonic)

    # --- Voice Battle only ---
    current_battle_sound: Optional[BattleSound] = None
    # This round's self-reported scores, keyed by player id; cleared each round.
    battle_round_scores: dict[str, int] = field(default_factory=dict)
    # Rounds won, cumulative across the match.
    battle_wins: dict[str, int] = field(default_factory=dict)

    # Serialises the two sockets that can touch this room concurrently.
    lock: asyncio.Lock = field(default_factory=asyncio.Lock, repr=False)
    # Cancels the round watchdog when the round advances normally.
    watchdog: Optional[asyncio.Task] = field(default=None, repr=False)

    @property
    def is_full(self) -> bool:
        return len(self.players) >= 2

    @property
    def player_ids(self) -> list[str]:
        return [player.player_id for player in self.players]

    def touch(self) -> None:
        self.last_activity = time.monotonic()

    def add_player(self, player: Player) -> None:
        self.players.append(player)
        self.scores.setdefault(player.player_id, 0)
        self.battle_wins.setdefault(player.player_id, 0)
        self.touch()

    def remove_player(self, player_id: str) -> None:
        self.players = [p for p in self.players if p.player_id != player_id]
        self.touch()

    def find_player(self, player_id: str) -> Optional[Player]:
        return next((p for p in self.players if p.player_id == player_id), None)

    def opponents_of(self, player_id: str) -> list[Player]:
        return [p for p in self.players if p.player_id != player_id]

    @property
    def rater_id(self) -> Optional[str]:
        """The player who is *not* performing this round."""

        if self.performer_id is None:
            return None
        opponents = self.opponents_of(self.performer_id)
        return opponents[0].player_id if opponents else None

    def performer_for_round(self, round_number: int) -> str:
        """Roles alternate every round; the room creator performs round 1."""

        return self.player_ids[(round_number - 1) % len(self.players)]

    def add_score(self, player_id: str, score: int) -> None:
        self.scores[player_id] = self.scores.get(player_id, 0) + score

    def winner_id(self) -> Optional[str]:
        """Highest total, or ``None`` on a draw (duel mode)."""

        return self._highest(self.scores)

    def add_battle_win(self, player_id: str) -> None:
        self.battle_wins[player_id] = self.battle_wins.get(player_id, 0) + 1

    def battle_winner_id(self) -> Optional[str]:
        """The player with more round wins, or ``None`` on a tie (battle mode)."""

        return self._highest(self.battle_wins)

    @staticmethod
    def _highest(totals: dict[str, int]) -> Optional[str]:
        if not totals:
            return None
        ranked = sorted(totals.items(), key=lambda item: item[1], reverse=True)
        if len(ranked) > 1 and ranked[0][1] == ranked[1][1]:
            return None
        return ranked[0][0]

    def is_expired(self, ttl_seconds: int, now: Optional[float] = None) -> bool:
        """A room with fewer than two players that has gone quiet for too long."""

        current = now if now is not None else time.monotonic()
        return not self.is_full and (current - self.last_activity) >= ttl_seconds

    def cancel_watchdog(self) -> None:
        if self.watchdog is not None and not self.watchdog.done():
            self.watchdog.cancel()
        self.watchdog = None
