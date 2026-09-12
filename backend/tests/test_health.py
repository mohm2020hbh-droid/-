"""The plain HTTP endpoints: liveness, the page itself, and the audio list."""

from __future__ import annotations


def test_play_is_served_and_never_cached(client):
    """A cached page is how an update silently fails to reach the player."""

    response = client.get("/play")
    assert response.status_code == 200
    assert "text/html" in response.headers["content-type"]

    cache_control = response.headers["cache-control"]
    assert "no-store" in cache_control
    assert "must-revalidate" in cache_control


def test_play_serves_the_current_start_screen(client):
    """The first screen must be the mode chooser, not the room form."""

    body = client.get("/play").text
    assert 'id="screen-menu"' in body
    assert 'id="btn-mode-single"' in body
    assert 'id="btn-mode-online"' in body
    # The language switch is part of that screen.
    assert 'data-lang="en"' in body


def test_target_audio_lists_nothing_when_no_recordings_are_dropped_in(client):
    """The synthesised targets are the normal case, so the list is empty."""

    response = client.get("/api/target-audio")
    assert response.status_code == 200
    assert response.json() == {"available": {}}


def test_target_audio_lists_a_dropped_in_recording(client, tmp_path, monkeypatch):
    """Dropping a file in is the whole configuration step — no list to edit."""

    from app.routers import health

    audio_dir = tmp_path / "audio"
    audio_dir.mkdir()
    (audio_dir / "donkey.mp3").write_bytes(b"not really an mp3")
    (audio_dir / "lion.wav").write_bytes(b"not really a wav")
    (audio_dir / "README.md").write_text("ignored")
    monkeypatch.setattr(health, "AUDIO_DIR", audio_dir)

    available = client.get("/api/target-audio").json()["available"]
    assert available == {"donkey": "donkey.mp3", "lion": "lion.wav"}


def test_target_audio_prefers_mp3_when_several_formats_are_present(
    client, tmp_path, monkeypatch
):
    """The client asks for one file per sound, so the server has to choose."""

    from app.routers import health

    audio_dir = tmp_path / "audio"
    audio_dir.mkdir()
    for suffix in ("wav", "ogg", "mp3"):
        (audio_dir / f"cat.{suffix}").write_bytes(b"x")
    monkeypatch.setattr(health, "AUDIO_DIR", audio_dir)

    assert client.get("/api/target-audio").json()["available"] == {"cat": "cat.mp3"}
