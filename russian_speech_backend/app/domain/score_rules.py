from dataclasses import dataclass


def clamp(value: float) -> float:
    return round(max(0.0, min(100.0, value)), 2)


def pronunciation_accuracy(phoneme: float, vowel: float, consonant: float) -> float:
    return clamp(phoneme * 0.60 + vowel * 0.20 + consonant * 0.20)


def fluency_score(rate: float, pause: float, continuity: float, rhythm: float) -> float:
    return clamp(rate * 0.30 + pause * 0.30 + continuity * 0.25 + rhythm * 0.15)


def overall_score(
    pronunciation: float,
    stress: float,
    fluency: float,
    completeness: float,
    weights: tuple[float, float, float, float] = (0.40, 0.25, 0.20, 0.15),
) -> float:
    if abs(sum(weights) - 1.0) > 1e-6:
        raise ValueError("overall score weights must sum to 1")
    return clamp(sum(score * weight for score, weight in zip(
        (pronunciation, stress, fluency, completeness), weights
    )))


def proficiency(score: float) -> str:
    if score >= 90:
        return "熟练"
    if score >= 80:
        return "较熟练"
    if score >= 70:
        return "基本熟练"
    if score >= 60:
        return "基本可懂"
    if score >= 40:
        return "需要加强"
    return "建议重新练习"


def status_for(score: float | None, confidence: float) -> str:
    if score is None or confidence < 0.55:
        return "unreliable"
    if score >= 90:
        return "excellent"
    if score >= 75:
        return "good"
    if score >= 60:
        return "acceptable"
    return "needs_work"


@dataclass(frozen=True)
class Completeness:
    score: float
    omitted: list[str]
    extra: list[str]
    substitutions: list[tuple[str, str]]
