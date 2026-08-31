from difflib import SequenceMatcher

import numpy as np

from app.config import Settings
from app.domain.russian_syllabifier import strip_stress
from app.domain.score_rules import Completeness, clamp, fluency_score, overall_score
from app.services.acoustic_service import AcousticFeatures


class PronunciationScoreService:
    def __init__(self, settings: Settings):
        self.settings = settings

    @staticmethod
    def compare_text(target_words: list[str], recognized_words: list[str]) -> Completeness:
        target = [strip_stress(w).lower() for w in target_words]
        actual = [strip_stress(w).lower() for w in recognized_words]
        matcher = SequenceMatcher(a=target, b=actual, autojunk=False)
        omitted: list[str] = []
        extra: list[str] = []
        substitutions: list[tuple[str, str]] = []
        correct = 0
        for tag, i1, i2, j1, j2 in matcher.get_opcodes():
            if tag == "equal":
                correct += i2 - i1
            elif tag == "delete":
                omitted.extend(target_words[i1:i2])
            elif tag == "insert":
                extra.extend(recognized_words[j1:j2])
            else:
                target_chunk = target_words[i1:i2]
                actual_chunk = recognized_words[j1:j2]
                paired = min(len(target_chunk), len(actual_chunk))
                substitutions.extend(zip(target_chunk[:paired], actual_chunk[:paired]))
                omitted.extend(target_chunk[paired:])
                extra.extend(actual_chunk[paired:])
        score = correct / len(target_words) * 100 if target_words else 0
        return Completeness(clamp(score), omitted, extra, substitutions)

    @staticmethod
    def acoustic_proxy_score(
        feature: AcousticFeatures,
        word_features: list[AcousticFeatures],
        alignment_confidence: float,
    ) -> tuple[float, float, float, float]:
        """Tier-3 score: alignment + duration/formant/voicing plausibility, never called GOP."""
        durations = np.array([item.duration_ms for item in word_features], dtype=float)
        median = float(np.median(durations)) if durations.size else feature.duration_ms
        ratio = feature.duration_ms / max(1.0, median)
        duration_score = clamp(100 - abs(np.log(max(0.15, ratio))) * 26)
        intensity_score = clamp(100 - max(0, -42 - feature.mean_intensity_db) * 3)
        voiced = feature.mean_f0_hz is not None
        spectral_available = feature.f1_hz is not None and feature.f2_hz is not None
        evidence_score = 80 if spectral_available else 66
        if not voiced:
            evidence_score -= 8
        phoneme_proxy = clamp(
            alignment_confidence * 70 + duration_score * 0.15 +
            intensity_score * 0.10 + evidence_score * 0.05
        )
        vowel = clamp(phoneme_proxy + (4 if spectral_available else -5))
        consonant = clamp(phoneme_proxy - 2)
        return phoneme_proxy, vowel, consonant, duration_score

    @staticmethod
    def fluency(
        duration_ms: int,
        word_intervals: list[tuple[int, int]],
        target_word_count: int,
        difficulty: str,
    ) -> tuple[float, float]:
        minutes = max(duration_ms / 60_000, 1 / 600)
        wpm = target_word_count / minutes
        ideal_low, ideal_high = {
            "beginner": (55, 125),
            "intermediate": (70, 150),
            "advanced": (85, 175),
        }[difficulty]
        if ideal_low <= wpm <= ideal_high:
            rate_score = 100.0
        elif wpm < ideal_low:
            rate_score = max(45.0, 100 - (ideal_low - wpm) * 1.0)
        else:
            rate_score = max(40.0, 100 - (wpm - ideal_high) * 0.8)
        gaps = [
            max(0, word_intervals[i + 1][0] - word_intervals[i][1])
            for i in range(len(word_intervals) - 1)
        ]
        long_gaps = sum(gap > 650 for gap in gaps)
        pause_score = clamp(100 - long_gaps * 18)
        continuity = clamp(100 - sum(gaps) / max(1, duration_ms) * 100)
        lengths = [end - start for start, end in word_intervals]
        rhythm = clamp(100 - (float(np.std(lengths)) / max(1.0, float(np.mean(lengths))) * 35)) if lengths else 0
        return fluency_score(rate_score, pause_score, continuity, rhythm), round(wpm, 2)

    def overall(
        self, pronunciation: float, stress: float, fluency: float, completeness: float
    ) -> float:
        return overall_score(
            pronunciation, stress, fluency, completeness,
            (
                self.settings.overall_pronunciation_weight,
                self.settings.overall_stress_weight,
                self.settings.overall_fluency_weight,
                self.settings.overall_completeness_weight,
            ),
        )
