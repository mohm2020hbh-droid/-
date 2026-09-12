"""Shared fixtures. Timeouts are shrunk so the suite stays fast."""

from __future__ import annotations

import os
import sys

import pytest
from fastapi.testclient import TestClient

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

from app.config import Settings  # noqa: E402
from app.main import create_app  # noqa: E402

TEST_ROUNDS = 4


@pytest.fixture
def settings() -> Settings:
    return Settings(
        total_rounds=TEST_ROUNDS,
        countdown_seconds=1,
        performance_grace_seconds=60,
        rating_timeout_seconds=60,
        room_ttl_seconds=2,
        janitor_interval_seconds=1,
        max_audio_bytes=64 * 1024,
    )


@pytest.fixture
def client(settings: Settings):
    with TestClient(create_app(settings)) as test_client:
        yield test_client
