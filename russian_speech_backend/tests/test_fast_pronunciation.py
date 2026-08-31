from pathlib import Path

from app.config import Settings
from app.schemas.asr import WordTimestamp
from app.services.acoustic_service import AcousticFeatures
from app.services.asr_service import AsrResult
from app.services.audio_preprocessor import AudioInfo
from app.services.feedback_service import FeedbackService
from app.services.pronunciation_pipeline import PronunciationPipeline
from app.services.pronunciation_score_service import PronunciationScoreService


class TimestampAsr:
    def transcribe(self, audio_path, language="ru", prompt=None):
        assert prompt == "Я читаю."
        return AsrResult(
            text="Я читаю.",
            language="ru",
            language_confidence=1.0,
            confidence=0.92,
            words=[
                WordTimestamp(text="Я", start_ms=100, end_ms=300, confidence=0.91),
                WordTimestamp(text="читаю", start_ms=320, end_ms=900, confidence=0.93),
            ],
            alternatives=[],
        )


class ForbiddenMfa:
    def align(self, *_args, **_kwargs):
        raise AssertionError("fast pronunciation path must not invoke MFA")


class ForbiddenStress:
    def accent_text(self, _text):
        raise AssertionError("word-only fast path must not invoke stress analysis")


class FixedAcoustic:
    def analyze_intervals(self, _audio_path, intervals):
        return [
            AcousticFeatures(
                start_ms=start,
                end_ms=end,
                duration_ms=end - start,
                mean_intensity_db=-24,
                peak_intensity_db=-18,
                mean_f0_hz=180,
                f0_range_hz=20,
                f1_hz=500,
                f2_hz=1500,
            )
            for start, end in intervals
        ]


def test_fast_word_scoring_uses_asr_timestamps_without_mfa(tmp_path):
    settings = Settings(
        temp_dir=tmp_path,
        pronunciation_alignment_backend="asr_timestamps",
    )
    pipeline = PronunciationPipeline(
        settings=settings,
        asr=TimestampAsr(),
        stress=ForbiddenStress(),
        alignment=ForbiddenMfa(),
        acoustic=FixedAcoustic(),
        scoring=PronunciationScoreService(settings),
        feedback=FeedbackService(),
    )
    audio = AudioInfo(
        path=Path("recording.wav"),
        duration_ms=1200,
        speech_duration_ms=1000,
        rms_db=-20,
        warnings=[],
        trim_start_ms=80,
    )

    result = pipeline.analyze(audio, "Я читаю.", "beginner")

    assert result.score_available is True
    assert [word.text for word in result.words] == ["Я", "читаю"]
    assert result.words[0].start_ms == 180
    assert result.words[1].end_ms == 980
    assert "syllables" not in result.words[0].model_dump()
