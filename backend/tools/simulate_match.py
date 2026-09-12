#!/usr/bin/env python3
"""Play a full match against a running server, as two separate clients.

This is an *independent* client: it builds frames the same way the Kotlin app
does (a 4-byte big-endian header length, a JSON header, then the raw audio)
rather than importing the server's own codec, so it cross-checks the wire
format instead of trusting it.

Usage::

    python tools/simulate_match.py --url ws://127.0.0.1:8000/ws
"""

from __future__ import annotations

import argparse
import asyncio
import json
import os
import struct
import sys

import websockets

LENGTH_PREFIX = struct.Struct(">I")


def encode_audio_frame(round_number: int, audio: bytes, mime: str = "audio/mp4") -> bytes:
    header = json.dumps({"round_number": round_number, "mime": mime}).encode("utf-8")
    return LENGTH_PREFIX.pack(len(header)) + header + audio


def decode_audio_frame(frame: bytes) -> tuple[dict, bytes]:
    (header_length,) = LENGTH_PREFIX.unpack_from(frame, 0)
    start = LENGTH_PREFIX.size
    header = json.loads(frame[start : start + header_length].decode("utf-8"))
    return header, frame[start + header_length :]


class Device:
    """One phone."""

    def __init__(self, name: str, socket) -> None:
        self.name = name
        self.socket = socket
        self.player_id = ""

    async def send(self, message_type: str, payload: dict | None = None) -> None:
        await self.socket.send(json.dumps({"type": message_type, "payload": payload or {}}))

    async def expect(self, message_type: str) -> dict:
        raw = await asyncio.wait_for(self.socket.recv(), timeout=15)
        if isinstance(raw, bytes):
            raise AssertionError(f"{self.name}: expected {message_type}, got a binary frame")
        message = json.loads(raw)
        if message["type"] != message_type:
            raise AssertionError(
                f"{self.name}: expected {message_type}, got {message['type']} {message['payload']}"
            )
        print(f"  {self.name} <- {message['type']} {message['payload']}")
        return message["payload"]

    async def recv_audio(self) -> tuple[dict, bytes]:
        frame = await asyncio.wait_for(self.socket.recv(), timeout=15)
        assert isinstance(frame, bytes), f"{self.name}: expected a binary frame"
        header, audio = decode_audio_frame(frame)
        print(f"  {self.name} <- audio frame {header} ({len(audio)} bytes)")
        return header, audio


async def play(url: str) -> int:
    failures: list[str] = []

    async with websockets.connect(url) as socket_a, websockets.connect(url) as socket_b:
        host = Device("HOST ", socket_a)
        guest = Device("GUEST", socket_b)

        host.player_id = (await host.expect("connected"))["player_id"]
        guest.player_id = (await guest.expect("connected"))["player_id"]

        print("\n== creating a room ==")
        await host.send("create_room")
        code = (await host.expect("room_created"))["code"]
        assert len(code) == 4 and code.isdigit(), f"bad room code: {code!r}"

        print("\n== a wrong code must be refused (T2) ==")
        async with websockets.connect(url) as stray_socket:
            stray = Device("STRAY", stray_socket)
            await stray.expect("connected")
            await stray.send("join_room", {"code": "0000"})
            reason = (await stray.expect("error"))["reason"]
            if reason != "invalid_code":
                failures.append(f"expected invalid_code, got {reason}")

        print(f"\n== joining room {code} ==")
        await guest.send("join_room", {"code": code})
        await host.expect("players_ready")
        await guest.expect("players_ready")

        devices = {host.player_id: host, guest.player_id: guest}
        expected_totals = {host.player_id: 0, guest.player_id: 0}
        performers: list[str] = []
        round_number = 0

        while True:
            start_host = await host.expect("round_start")
            start_guest = await guest.expect("round_start")
            if start_host != start_guest:
                failures.append("players saw different round_start payloads")

            round_number = start_host["round_number"]
            performer = devices[start_host["performer_id"]]
            rater = next(d for d in devices.values() if d is not performer)
            performers.append(performer.name.strip())
            print(f"\n== round {round_number}: {performer.name.strip()} performs "
                  f"'{start_host['sound_name']}' {start_host['sound_emoji']} ==")

            # A recording containing every byte value, to prove the relay is
            # binary-clean (T4).
            audio = os.urandom(8_000) + bytes(range(256))
            await performer.socket.send(encode_audio_frame(round_number, audio))

            ready = await rater.expect("audio_ready")
            if ready["round_number"] != round_number:
                failures.append("audio_ready named the wrong round")

            header, relayed = await rater.recv_audio()
            if relayed != audio:
                failures.append(f"round {round_number}: relayed audio differs from the original")
            else:
                print(f"  ✓ {len(relayed)} bytes relayed intact")
            if header["round_number"] != round_number:
                failures.append("audio header named the wrong round")

            score = 10 * round_number
            await rater.send("rating_submitted", {"round_number": round_number, "score": score})
            expected_totals[performer.player_id] += score

            result_host = await host.expect("round_result")
            await guest.expect("round_result")
            if result_host["total_scores"] != expected_totals:
                failures.append(
                    f"round {round_number}: totals {result_host['total_scores']} "
                    f"!= expected {expected_totals}"
                )

            if round_number >= start_host["total_rounds"]:
                break

        print("\n== final ==")
        over_host = await host.expect("game_over")
        over_guest = await guest.expect("game_over")
        if over_host != over_guest:
            failures.append("players saw different game_over payloads")
        if over_host["final_scores"] != expected_totals:
            failures.append("final scores do not match the accumulated totals")

        expected_winner = max(expected_totals, key=expected_totals.get)
        if over_host["winner_id"] != expected_winner:
            failures.append(f"winner {over_host['winner_id']} != expected {expected_winner}")

        alternating = all(
            performers[i] != performers[i + 1] for i in range(len(performers) - 1)
        )
        if not alternating:
            failures.append(f"roles did not alternate: {performers}")

        print(f"\nrounds played : {round_number}")
        print(f"performers    : {' -> '.join(performers)}")
        print(f"final scores  : {over_host['final_scores']}")
        print(f"winner        : {over_host['winner_id']}")

    print("\n== a mid-match disconnect must notify the opponent (T3) ==")
    async with websockets.connect(url) as socket_a, websockets.connect(url) as socket_b:
        host = Device("HOST ", socket_a)
        guest = Device("GUEST", socket_b)
        host.player_id = (await host.expect("connected"))["player_id"]
        guest.player_id = (await guest.expect("connected"))["player_id"]

        await host.send("create_room")
        code = (await host.expect("room_created"))["code"]
        await guest.send("join_room", {"code": code})
        await host.expect("players_ready")
        await guest.expect("players_ready")
        await host.expect("round_start")
        await guest.expect("round_start")

        await socket_b.close()
        payload = await host.expect("opponent_disconnected")
        if payload.get("reason") != "disconnected":
            failures.append(f"unexpected disconnect reason: {payload}")

    print()
    if failures:
        print("FAILURES:")
        for failure in failures:
            print(f"  ✗ {failure}")
        return 1

    print("✓ full match, audio integrity, bad code, and disconnect all behaved correctly")
    return 0


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--url", default="ws://127.0.0.1:8000/ws", help="server WebSocket URL")
    args = parser.parse_args()
    return asyncio.run(play(args.url))


if __name__ == "__main__":
    sys.exit(main())
