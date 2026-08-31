from pathlib import Path

from fastapi.testclient import TestClient

from app.config import Settings
from app.main import create_app
from app.services.audio_preprocessor import AudioInfo
from app.services.container import build_container
from app.utils.exceptions import AlignmentFailed
from tests.test_asr import FakeAsr, FakeAudio


class FailingAlignmentPipeline:
    def analyze(self, audio: AudioInfo, target_text: str, difficulty: str):
        raise AlignmentFailed("fixture")


def test_alignment_failure_never_returns_syllable_scores(tmp_path):
    container = build_container(Settings(temp_dir=tmp_path), FakeAsr())
    container.audio = FakeAudio()
    container.pronunciation = FailingAlignmentPipeline()
    with TestClient(create_app(container)) as client:
        response = client.post(
            "/api/pronunciation/analyze",
            data={"target_text": "Я изучаю русский язык.", "difficulty": "beginner"},
            files={"audio": ("sample.wav", b"fixture", "audio/wav")},
        )
    assert response.status_code == 422
    assert response.json()["error_code"] == "alignment_failed"
    assert "words" not in response.json()


def test_invalid_difficulty_is_rejected(tmp_path):
    container = build_container(Settings(temp_dir=tmp_path), FakeAsr())
    container.audio = FakeAudio()
    with TestClient(create_app(container)) as client:
        response = client.post(
            "/api/pronunciation/analyze",
            data={"target_text": "молоко", "difficulty": "expert"},
            files={"audio": ("sample.wav", b"fixture", "audio/wav")},
        )
    assert response.status_code == 422
    assert response.json()["error_code"] == "invalid_difficulty"
