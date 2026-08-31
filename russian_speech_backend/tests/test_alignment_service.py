from types import SimpleNamespace

import pytest

from app.config import Settings
from app.services.alignment_service import AlignmentService
from app.utils.exceptions import AlignmentFailed


def test_alignment_uses_fast_single_file_mode(monkeypatch, tmp_path):
    audio = tmp_path / "recording.wav"
    audio.write_bytes(b"fixture")
    work = tmp_path / "work"
    work.mkdir()
    captured = {}

    def fake_run(command, **kwargs):
        captured["command"] = command
        captured["kwargs"] = kwargs
        return SimpleNamespace(returncode=1, stderr="fixture failure", stdout="")

    monkeypatch.setattr("app.services.alignment_service.subprocess.run", fake_run)

    service = AlignmentService(Settings(temp_dir=tmp_path, mfa_binary="mfa"))
    with pytest.raises(AlignmentFailed):
        service.align(audio, "привет", work)

    command = captured["command"]
    assert command[1] == "align_one"
    assert "--num_jobs" in command
    assert command[command.index("--num_jobs") + 1] == "1"
    assert "--clean" in command
    assert captured["kwargs"]["timeout"] == 300
