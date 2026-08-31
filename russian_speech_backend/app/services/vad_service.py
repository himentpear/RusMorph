from pathlib import Path

from app.services.audio_preprocessor import AudioInfo


class VadService:
    """VAD boundary kept explicit; normalization currently performs energy-based trim."""

    def validate(self, audio: AudioInfo) -> list[str]:
        return list(audio.warnings)
