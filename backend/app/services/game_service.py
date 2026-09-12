"""Match orchestration: rounds, role swapping, audio relay, and scoring.

The whole protocol of section 3 of the specification lives here. Every mutation
of a room happens under that room's lock, because two sockets (and a watchdog
task) can act on the same room concurrently.
"""

from __future__ import annotations

import asyncio
import logging
import random
from typing import Any, Optional

from ..config import Settings
from ..models import messages as msg
from ..models.battle_sounds import pick_battle_sound
from ..models.room import Player, RoomMode, RoomPhase, RoomState
from ..models.sounds import pick_sound
from .room_manager import RoomError, RoomManager

_VALID_MODES = {mode.value for mode in RoomMode}

logger = logging.getLogger(__name__)

MIN_SCORE = 0
MAX_SCORE = 100


class GameService:
    """Drives every room through the match lifecycle."""

    def __init__(
        self,
        rooms: RoomManager,
        settings: Settings,
        rng: random.Random | None = None,
    ) -> None:
        self._rooms = rooms
        self._settings = settings
        self._rng = rng or random.Random()

    # ------------------------------------------------------------------
    # Client commands
    # ------------------------------------------------------------------
    async def handle_create_room(self, player: Player, payload: dict[str, Any]) -> None:
        if self._rooms.room_of_player(player.player_id) is not None:
            await player.connection.send_message(
                msg.ERROR, {"reason": msg.ERR_ALREADY_IN_ROOM}
            )
            return

        raw_mode = payload.get("mode", RoomMode.DUEL.value)
        if raw_mode not in _VALID_MODES:
            await player.connection.send_message(msg.ERROR, {"reason": msg.ERR_INVALID_MODE})
            return
        mode = RoomMode(raw_mode)

        try:
            room = self._rooms.create_room(player, mode=mode)
        except RoomError as exc:
            await player.connection.send_message(msg.ERROR, {"reason": exc.reason})
            return

        await player.connection.send_message(
            msg.ROOM_CREATED, {"code": room.code, "mode": room.mode.value}
        )

    async def handle_join_room(self, player: Player, payload: dict[str, Any]) -> None:
        if self._rooms.room_of_player(player.player_id) is not None:
            await player.connection.send_message(
                msg.ERROR, {"reason": msg.ERR_ALREADY_IN_ROOM}
            )
            return

        code = str(payload.get("code", "")).strip()
        try:
            room = self._rooms.join_room(code, player)
        except RoomError as exc:
            await player.connection.send_message(msg.ERROR, {"reason": exc.reason})
            return

        async with room.lock:
            await self._broadcast(
                room,
                msg.PLAYERS_READY,
                {
                    "player_a_id": room.player_ids[0],
                    "player_b_id": room.player_ids[1],
                    "mode": room.mode.value,
                },
            )
            if room.mode is RoomMode.BATTLE:
                await self._start_battle_round(room, round_number=1)
            else:
                await self._start_round(room, round_number=1)

    async def handle_audio(self, player: Player, frame: bytes) -> None:
        """Relay one recording from the performer to the rater."""

        room = self._rooms.room_of_player(player.player_id)
        if room is None:
            await player.connection.send_message(msg.ERROR, {"reason": msg.ERR_NOT_IN_ROOM})
            return

        if len(frame) > self._settings.max_audio_bytes:
            await player.connection.send_message(
                msg.ERROR, {"reason": msg.ERR_AUDIO_TOO_LARGE}
            )
            return

        try:
            header, audio = msg.decode_audio_frame(frame)
        except msg.ProtocolError as exc:
            await player.connection.send_message(msg.ERROR, {"reason": exc.reason})
            return

        async with room.lock:
            if room.phase is not RoomPhase.RECORDING:
                await player.connection.send_message(
                    msg.ERROR, {"reason": msg.ERR_WRONG_PHASE}
                )
                return
            if player.player_id != room.performer_id:
                await player.connection.send_message(
                    msg.ERROR, {"reason": msg.ERR_NOT_YOUR_TURN}
                )
                return
            if self._as_int(header.get("round_number")) != room.round_number:
                await player.connection.send_message(
                    msg.ERROR, {"reason": msg.ERR_STALE_ROUND}
                )
                return

            rater = room.find_player(room.rater_id) if room.rater_id else None
            if rater is None:
                # The opponent vanished between frames; the receive loop will
                # tear the room down, so there is nothing to relay to.
                return

            room.phase = RoomPhase.RATING
            room.touch()
            self._schedule_watchdog(room, self._settings.rating_timeout_seconds)

            relay_header = {
                "round_number": room.round_number,
                "mime": str(header.get("mime") or "audio/mp4"),
                "performer_id": room.performer_id,
            }
            await rater.connection.send_message(
                msg.AUDIO_READY, {"round_number": room.round_number}
            )
            await rater.connection.send_audio(relay_header, audio)

    async def handle_rating(self, player: Player, payload: dict[str, Any]) -> None:
        room = self._rooms.room_of_player(player.player_id)
        if room is None:
            await player.connection.send_message(msg.ERROR, {"reason": msg.ERR_NOT_IN_ROOM})
            return

        score = self._as_int(payload.get("score"))
        if score is None or not MIN_SCORE <= score <= MAX_SCORE:
            await player.connection.send_message(
                msg.ERROR, {"reason": msg.ERR_INVALID_SCORE}
            )
            return

        async with room.lock:
            if room.phase is not RoomPhase.RATING:
                await player.connection.send_message(
                    msg.ERROR, {"reason": msg.ERR_WRONG_PHASE}
                )
                return
            if player.player_id != room.rater_id:
                await player.connection.send_message(
                    msg.ERROR, {"reason": msg.ERR_NOT_YOUR_TURN}
                )
                return
            if self._as_int(payload.get("round_number")) != room.round_number:
                await player.connection.send_message(
                    msg.ERROR, {"reason": msg.ERR_STALE_ROUND}
                )
                return

            await self._finish_round(room, score)

    async def handle_battle_attempt(self, player: Player, payload: dict[str, Any]) -> None:
        """A Voice Battle player reports its own acoustically-computed score.

        The client, not the server, runs the comparison: both players render
        the identical deterministic target locally from the ``sound_id`` this
        room announced and score their own recording against it with the same
        engine single player uses (see ``app/static/js/dsp.js``). This mirrors
        the trust model duel mode already has — a human-submitted
        ``rating_submitted`` score is likewise taken at the client's word —
        rather than adding a server-side audio pipeline for an ephemeral,
        no-account party game.
        """

        room = self._rooms.room_of_player(player.player_id)
        if room is None:
            await player.connection.send_message(msg.ERROR, {"reason": msg.ERR_NOT_IN_ROOM})
            return

        score = self._as_int(payload.get("score"))
        if score is None or not MIN_SCORE <= score <= MAX_SCORE:
            await player.connection.send_message(
                msg.ERROR, {"reason": msg.ERR_INVALID_SCORE}
            )
            return

        async with room.lock:
            if room.mode is not RoomMode.BATTLE or room.phase is not RoomPhase.BATTLE_ATTEMPT:
                await player.connection.send_message(
                    msg.ERROR, {"reason": msg.ERR_WRONG_PHASE}
                )
                return
            if self._as_int(payload.get("round_number")) != room.round_number:
                await player.connection.send_message(
                    msg.ERROR, {"reason": msg.ERR_STALE_ROUND}
                )
                return
            if player.player_id in room.battle_round_scores:
                # A duplicate submission (e.g. a retried request) is harmless;
                # the first score for this round stands.
                return

            room.battle_round_scores[player.player_id] = score
            room.touch()

            if len(room.battle_round_scores) >= len(room.players):
                await self._finish_battle_round(room)

    async def handle_leave(self, player: Player) -> Optional[RoomState]:
        """A player deliberately left the room but keeps their socket open."""

        return await self._tear_down_room(player, notify_reason="left")

    async def handle_disconnect(self, player: Player) -> Optional[RoomState]:
        """A player's socket dropped (R7 / T3)."""

        player.connection.mark_closed()
        return await self._tear_down_room(player, notify_reason="disconnected")

    # ------------------------------------------------------------------
    # Round lifecycle
    # ------------------------------------------------------------------
    async def _start_round(self, room: RoomState, round_number: int) -> None:
        """Announce a round. Caller must hold ``room.lock``."""

        sound = pick_sound(room.used_sound_ids, rng=self._rng)
        room.used_sound_ids.add(sound.id)
        room.round_number = round_number
        room.current_sound = sound
        room.performer_id = room.performer_for_round(round_number)
        room.phase = RoomPhase.RECORDING
        room.touch()

        self._schedule_watchdog(
            room, self._settings.countdown_seconds + self._settings.performance_grace_seconds
        )

        await self._broadcast(
            room,
            msg.ROUND_START,
            {
                "round_number": round_number,
                "total_rounds": self._settings.total_rounds,
                "sound_id": sound.id,
                # Both names travel with the round so either client can show
                # the sound in its own language without another round trip.
                "sound_name": sound.name,
                "sound_name_en": sound.name_en,
                "sound_emoji": sound.emoji,
                "performer_id": room.performer_id,
                "countdown_seconds": self._settings.countdown_seconds,
            },
        )

    async def _finish_round(
        self, room: RoomState, score: int, timed_out: bool = False
    ) -> None:
        """Score the round and either start the next one or end the match.

        Caller must hold ``room.lock``.
        """

        room.cancel_watchdog()
        performer_id = room.performer_id or ""
        room.add_score(performer_id, score)
        room.touch()

        await self._broadcast(
            room,
            msg.ROUND_RESULT,
            {
                "round_number": room.round_number,
                "performer_id": performer_id,
                "score": score,
                "total_scores": dict(room.scores),
                "timed_out": timed_out,
            },
        )

        if room.round_number >= self._settings.total_rounds:
            room.phase = RoomPhase.FINISHED
            await self._broadcast(
                room,
                msg.GAME_OVER,
                {"winner_id": room.winner_id(), "final_scores": dict(room.scores)},
            )
            self._rooms.remove_room(room.code)
            return

        await self._start_round(room, room.round_number + 1)

    async def _start_battle_round(self, room: RoomState, round_number: int) -> None:
        """Announce a Voice Battle round. Caller must hold ``room.lock``."""

        sound = pick_battle_sound(room.used_sound_ids, rng=self._rng)
        room.used_sound_ids.add(sound.id)
        room.round_number = round_number
        room.current_battle_sound = sound
        room.battle_round_scores = {}
        room.phase = RoomPhase.BATTLE_ATTEMPT
        room.touch()

        self._schedule_watchdog(
            room,
            self._settings.battle_attempt_seconds + self._settings.battle_attempt_grace_seconds,
        )

        await self._broadcast(
            room,
            msg.BATTLE_ROUND_START,
            {
                "round_number": round_number,
                "total_rounds": self._settings.battle_total_rounds,
                "sound_id": sound.id,
                # Both names travel with the round so either client can show
                # the sound in its own language without another round trip.
                "sound_name": sound.name,
                "sound_name_en": sound.name_en,
                "sound_emoji": sound.emoji,
                "attempt_seconds": self._settings.battle_attempt_seconds,
            },
        )

    async def _finish_battle_round(self, room: RoomState, timed_out: bool = False) -> None:
        """Compare both self-reported scores and either continue or end the
        match. Caller must hold ``room.lock``.
        """

        room.cancel_watchdog()
        room.touch()

        # A player who never submitted (denied microphone, dead connection)
        # is scored zero for the round rather than stalling their opponent.
        scores = {p.player_id: room.battle_round_scores.get(p.player_id, 0) for p in room.players}
        round_winner_id = RoomState._highest(scores)
        if round_winner_id is not None:
            room.add_battle_win(round_winner_id)

        await self._broadcast(
            room,
            msg.BATTLE_ROUND_RESULT,
            {
                "round_number": room.round_number,
                "scores": scores,
                "round_winner_id": round_winner_id,
                "total_wins": dict(room.battle_wins),
                "timed_out": timed_out,
            },
        )

        if room.round_number >= self._settings.battle_total_rounds:
            room.phase = RoomPhase.FINISHED
            await self._broadcast(
                room,
                msg.BATTLE_OVER,
                {"winner_id": room.battle_winner_id(), "total_wins": dict(room.battle_wins)},
            )
            self._rooms.remove_room(room.code)
            return

        await self._start_battle_round(room, room.round_number + 1)

    def _schedule_watchdog(self, room: RoomState, delay_seconds: int) -> None:
        """Guard a phase so a silent client can never freeze the match.

        Caller must hold ``room.lock``.
        """

        room.cancel_watchdog()
        expected_round = room.round_number
        expected_phase = room.phase
        room.watchdog = asyncio.create_task(
            self._watch_phase(room, expected_round, expected_phase, max(1, delay_seconds))
        )

    async def _watch_phase(
        self,
        room: RoomState,
        expected_round: int,
        expected_phase: RoomPhase,
        delay_seconds: int,
    ) -> None:
        try:
            await asyncio.sleep(delay_seconds)
            async with room.lock:
                if room.round_number != expected_round or room.phase is not expected_phase:
                    return
                if self._rooms.get(room.code) is None:
                    return
                # Clear the handle first: the finisher below cancels the
                # room's watchdog, and this task must not cancel itself.
                room.watchdog = None
                logger.info(
                    "room %s round %s timed out in phase %s",
                    room.code,
                    expected_round,
                    expected_phase.value,
                )
                if room.mode is RoomMode.BATTLE:
                    await self._finish_battle_round(room, timed_out=True)
                else:
                    await self._finish_round(room, MIN_SCORE, timed_out=True)
        except asyncio.CancelledError:
            raise
        except Exception:  # noqa: BLE001 - a watchdog failure must not kill the room
            logger.exception("watchdog failed for room %s", room.code)

    # ------------------------------------------------------------------
    # Helpers
    # ------------------------------------------------------------------
    async def _tear_down_room(
        self, player: Player, notify_reason: str
    ) -> Optional[RoomState]:
        room = self._rooms.room_of_player(player.player_id)
        if room is None:
            return None

        async with room.lock:
            room.cancel_watchdog()
            opponents = room.opponents_of(player.player_id)
            self._rooms.leave_room(player.player_id)
            was_finished = room.phase is RoomPhase.FINISHED

            for opponent in opponents:
                if not was_finished:
                    await opponent.connection.send_message(
                        msg.OPPONENT_DISCONNECTED, {"reason": notify_reason}
                    )
                self._rooms.leave_room(opponent.player_id)

            self._rooms.remove_room(room.code)

        return room

    async def _broadcast(
        self, room: RoomState, message_type: str, payload: dict[str, Any]
    ) -> None:
        for player in list(room.players):
            await player.connection.send_message(message_type, payload)

    @staticmethod
    def _as_int(value: Any) -> Optional[int]:
        if isinstance(value, bool) or value is None:
            return None
        if isinstance(value, int):
            return value
        if isinstance(value, str):
            try:
                return int(value.strip())
            except ValueError:
                return None
        if isinstance(value, float) and value.is_integer():
            return int(value)
        return None
