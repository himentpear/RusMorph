from dataclasses import dataclass
from pathlib import Path

import numpy as np

from app.utils.exceptions import DependencyUnavailable, SpeechError


@dataclass(frozen=True)
class AcousticFeatures:
    start_ms: int
    end_ms: int
    duration_ms: int
    mean_intensity_db: float
    peak_intensity_db: float
    mean_f0_hz: float | None
    f0_range_hz: float | None
    f1_hz: float | None
    f2_hz: float | None


class AcousticService:
    def analyze_intervals(
        self, audio_path: Path, intervals: list[tuple[int, int]]
    ) -> list[AcousticFeatures]:
        try:
            import parselmouth
        except ImportError as exc:
            raise DependencyUnavailable("Parselmouth/Praat", "请安装 praat-parselmouth") from exc
        try:
            sound = parselmouth.Sound(str(audio_path))
            intensity = sound.to_intensity()
            pitch = sound.to_pitch()
            formant = sound.to_formant_burg()
        except Exception as exc:
            raise SpeechError("acoustic_analysis_failed", "无法提取声学特征") from exc

        result: list[AcousticFeatures] = []
        for start_ms, end_ms in intervals:
            start, end = start_ms / 1000, end_ms / 1000
            times = np.linspace(start, max(start + 0.001, end), 12)
            intensity_values = [
                float(intensity.get_value(t)) for t in times
                if np.isfinite(intensity.get_value(t))
            ]
            f0_values = [
                float(pitch.get_value_at_time(t)) for t in times
                if np.isfinite(pitch.get_value_at_time(t)) and pitch.get_value_at_time(t) > 0
            ]
            midpoint = (start + end) / 2
            f1 = formant.get_value_at_time(1, midpoint)
            f2 = formant.get_value_at_time(2, midpoint)
            result.append(AcousticFeatures(
                start_ms=start_ms,
                end_ms=end_ms,
                duration_ms=max(1, end_ms - start_ms),
                mean_intensity_db=round(float(np.mean(intensity_values)), 3) if intensity_values else -120.0,
                peak_intensity_db=round(float(np.max(intensity_values)), 3) if intensity_values else -120.0,
                mean_f0_hz=round(float(np.mean(f0_values)), 3) if f0_values else None,
                f0_range_hz=round(float(np.ptp(f0_values)), 3) if len(f0_values) > 1 else None,
                f1_hz=round(float(f1), 3) if np.isfinite(f1) else None,
                f2_hz=round(float(f2), 3) if np.isfinite(f2) else None,
            ))
        return result


def stress_probabilities(features: list[AcousticFeatures]) -> tuple[list[float], float]:
    if len(features) <= 1:
        return [1.0] if features else [], 1.0
    durations = np.array([f.duration_ms for f in features], dtype=float)
    intensities = np.array([f.mean_intensity_db for f in features], dtype=float)
    pitches = np.array([f.mean_f0_hz or np.nan for f in features], dtype=float)

    def z(values: np.ndarray) -> np.ndarray:
        finite = np.isfinite(values)
        if finite.sum() < 2 or float(np.nanstd(values)) < 1e-6:
            return np.zeros_like(values)
        filled = np.where(finite, values, np.nanmean(values))
        return (filled - filled.mean()) / filled.std()

    # Duration, intensity and F0 are combined; loudness alone can never decide stress.
    logits = z(durations) * 0.45 + z(intensities) * 0.30 + z(pitches) * 0.25
    exp = np.exp(logits - np.max(logits))
    probabilities = exp / exp.sum()
    ordered = np.sort(probabilities)
    margin = float(ordered[-1] - ordered[-2])
    confidence = min(0.95, max(0.35, 0.50 + margin))
    return [round(float(p), 4) for p in probabilities], round(confidence, 4)
