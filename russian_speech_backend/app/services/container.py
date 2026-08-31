from dataclasses import dataclass

from app.config import Settings, get_settings
from app.services.acoustic_service import AcousticService
from app.services.alignment_service import AlignmentService
from app.services.asr_service import AsrEngine, build_asr_engine
from app.services.audio_preprocessor import AudioPreprocessor
from app.services.feedback_service import FeedbackService
from app.services.pronunciation_pipeline import PronunciationPipeline
from app.services.pronunciation_score_service import PronunciationScoreService
from app.services.stress_service import StressService
from app.services.vad_service import VadService


@dataclass
class ServiceContainer:
    settings: Settings
    audio: AudioPreprocessor
    vad: VadService
    asr: AsrEngine
    stress: StressService
    alignment: AlignmentService
    acoustic: AcousticService
    scoring: PronunciationScoreService
    feedback: FeedbackService
    pronunciation: PronunciationPipeline


def build_container(settings: Settings | None = None, asr: AsrEngine | None = None) -> ServiceContainer:
    settings = settings or get_settings()
    settings.temp_dir.mkdir(parents=True, exist_ok=True)
    asr = asr or build_asr_engine(settings)
    audio = AudioPreprocessor(settings)
    vad = VadService()
    stress = StressService(settings)
    alignment = AlignmentService(settings)
    acoustic = AcousticService()
    scoring = PronunciationScoreService(settings)
    feedback = FeedbackService()
    pronunciation = PronunciationPipeline(
        settings, asr, stress, alignment, acoustic, scoring, feedback
    )
    return ServiceContainer(
        settings, audio, vad, asr, stress, alignment, acoustic, scoring, feedback, pronunciation
    )
