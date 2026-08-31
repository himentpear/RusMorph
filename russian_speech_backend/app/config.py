from functools import lru_cache
from pathlib import Path

from pydantic import Field
from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_file=".env", env_prefix="SPEECH_", extra="ignore")

    app_name: str = "RusMorph Speech"
    temp_dir: Path = Path("temp")
    max_upload_bytes: int = 20 * 1024 * 1024
    max_duration_seconds: float = 60.0
    min_speech_seconds: float = 0.30
    ffmpeg_binary: str = "ffmpeg"
    ffprobe_binary: str = "ffprobe"

    asr_backend: str = "faster_whisper"
    asr_model: str = "small"
    asr_device: str = "cpu"
    asr_compute_type: str = "int8"
    asr_language: str = "ru"
    asr_beam_size: int = 1
    asr_warmup: bool = True
    pronunciation_alignment_backend: str = "asr_timestamps"

    stress_backend: str = "ruaccent"
    stress_model_path: str | None = None
    mfa_binary: str = "mfa"
    mfa_acoustic_model: str = "russian_mfa"
    mfa_dictionary: str = "russian_mfa"
    mfa_g2p_model: str = "russian_mfa"
    mfa_beam: int = 100
    mfa_retry_beam: int = 400
    acoustic_model_name: str = "configured-acoustic-model"

    auto_search_threshold: float = 0.85
    confirm_search_threshold: float = 0.65
    minimum_analysis_confidence: float = 0.55
    websocket_timeout_seconds: float = 30.0
    allowed_extensions: set[str] = Field(
        default_factory=lambda: {".wav", ".mp3", ".m4a", ".ogg", ".webm", ".flac", ".aac"}
    )

    overall_pronunciation_weight: float = 0.40
    overall_stress_weight: float = 0.25
    overall_fluency_weight: float = 0.20
    overall_completeness_weight: float = 0.15


@lru_cache
def get_settings() -> Settings:
    settings = Settings()
    settings.temp_dir.mkdir(parents=True, exist_ok=True)
    return settings
