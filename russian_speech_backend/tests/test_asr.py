from pathlib import Path

from fastapi.testclient import TestClient

from app.config import Settings
from app.main import create_app
from app.schemas.asr import WordTimestamp
from app.services.asr_service import AsrResult
from app.services.audio_preprocessor import AudioInfo
from app.services.container import build_container


class FakeAsr:
    def __init__(self, confidence=0.9, language="ru"):
        self.confidence = confidence
        self.language = language

    def transcribe(self, audio_path: Path, language="ru"):
        return AsrResult(
            "молоко", self.language, 0.98, self.confidence,
            [WordTimestamp(text="молоко", start_ms=0, end_ms=600, confidence=self.confidence)],
            [],
        )


class FakeAudio:
    def normalize(self, source, output):
        output.write_bytes(b"fake")
        return AudioInfo(output, 1000, 800, -20, [])


def client_for(confidence):
    container = build_container(Settings(temp_dir=Path("temp-test")), FakeAsr(confidence))
    container.audio = FakeAudio()
    app = create_app(container)
    return TestClient(app)


def test_high_confidence_auto_searches():
    with client_for(0.90) as client:
        response = client.post(
            "/api/asr/transcribe",
            files={"audio": ("sample.wav", b"not-real-audio", "audio/wav")},
        )
    assert response.status_code == 200
    assert response.json()["normalized_transcript"] == "молоко"
    assert response.json()["should_auto_search"] is True


def test_mid_confidence_requires_confirmation():
    with client_for(0.75) as client:
        response = client.post(
            "/api/asr/transcribe",
            files={"audio": ("sample.wav", b"not-real-audio", "audio/wav")},
        )
    assert response.status_code == 200
    assert response.json()["should_auto_search"] is False


def test_low_confidence_is_not_presented_as_certain():
    with client_for(0.40) as client:
        response = client.post(
            "/api/asr/transcribe",
            files={"audio": ("sample.wav", b"not-real-audio", "audio/wav")},
        )
    body = response.json()
    assert body["should_auto_search"] is False
    assert any("置信度" in warning for warning in body["warnings"])
