"""Runtime configuration for the Voice Duel backend.

All values are plain integers so a deployment can tune the game without code
changes, and so tests can shrink the timeouts to keep the suite fast.
"""

from __future__ import annotations

import os
from dataclasses import dataclass


def _env_int(name: str, default: int) -> int:
    raw = os.getenv(name)
    if raw is None or raw.strip() == "":
        return default
    try:
        return int(raw)
    except ValueError:
        return default


@dataclass(frozen=True)
class Settings:
    """Immutable game/server settings."""

    # Number of rounds in one match. Roles swap every round, so an even number
    # gives both players the same amount of turns as a performer.
    total_rounds: int = 6

    # How long the performer is given to record, as announced in `round_start`.
    countdown_seconds: int = 8

    # Extra time on top of the countdown before the server gives up waiting for
    # the performer's audio and scores the round as 0.
    performance_grace_seconds: int = 25

    # How long the rater may listen and move the slider before the round is
    # auto-scored as 0.
    rating_timeout_seconds: int = 120

    # A room nobody joins is dropped from memory after this long.
    room_ttl_seconds: int = 600

    # How often the janitor sweeps for expired rooms.
    janitor_interval_seconds: int = 30

    # Hard cap on a single recording (relayed straight through, never stored).
    max_audio_bytes: int = 5 * 1024 * 1024

    # --- Voice Battle (both players imitate at once, scored acoustically) ---

    # Rounds in one Voice Battle match. Odd is fine: there is no role to swap.
    battle_total_rounds: int = 5

    # How long both players get to imitate, announced in `battle_round_start`.
    battle_attempt_seconds: int = 6

    # Extra time on top of the attempt window before a silent player is scored
    # zero for the round, so one dead client can never stall the other.
    battle_attempt_grace_seconds: int = 15

    @classmethod
    def from_env(cls) -> "Settings":
        return cls(
            total_rounds=_env_int("VD_TOTAL_ROUNDS", cls.total_rounds),
            countdown_seconds=_env_int("VD_COUNTDOWN_SECONDS", cls.countdown_seconds),
            performance_grace_seconds=_env_int(
                "VD_PERFORMANCE_GRACE_SECONDS", cls.performance_grace_seconds
            ),
            rating_timeout_seconds=_env_int(
                "VD_RATING_TIMEOUT_SECONDS", cls.rating_timeout_seconds
            ),
            room_ttl_seconds=_env_int("VD_ROOM_TTL_SECONDS", cls.room_ttl_seconds),
            janitor_interval_seconds=_env_int(
                "VD_JANITOR_INTERVAL_SECONDS", cls.janitor_interval_seconds
            ),
            max_audio_bytes=_env_int("VD_MAX_AUDIO_BYTES", cls.max_audio_bytes),
            battle_total_rounds=_env_int("VD_BATTLE_TOTAL_ROUNDS", cls.battle_total_rounds),
            battle_attempt_seconds=_env_int(
                "VD_BATTLE_ATTEMPT_SECONDS", cls.battle_attempt_seconds
            ),
            battle_attempt_grace_seconds=_env_int(
                "VD_BATTLE_ATTEMPT_GRACE_SECONDS", cls.battle_attempt_grace_seconds
            ),
        )
