from typing import Literal

from app.schemas.common import ApiModel


class WordAnalysis(ApiModel):
    text: str
    start_ms: int
    end_ms: int
    score: float | None
    confidence: float
    status: str
    feedback_zh: str


class TextSubstitution(ApiModel):
    target: str
    recognized: str


class Issue(ApiModel):
    type: str
    word: str | None = None
    syllable: str | None = None
    severity: Literal["low", "medium", "high"]
    feedback_zh: str


class PracticeItem(ApiModel):
    type: str
    content: str
    repetitions: int


class PronunciationResponse(ApiModel):
    success: bool
    target_text: str
    target_stressed_text: str
    recognized_text: str
    text_match_score: float
    score_available: bool
    reason: str | None = None
    overall_score: float | None = None
    pronunciation_accuracy: float | None = None
    stress_accuracy: float | None = None
    fluency_score: float | None = None
    completeness_score: float | None = None
    proficiency_level: str | None = None
    asr_confidence: float
    text_match_confidence: float
    alignment_confidence: float
    stress_confidence: float
    phoneme_score_confidence: float
    analysis_confidence: float
    duration_ms: int
    speech_rate_words_per_minute: float
    words: list[WordAnalysis]
    omitted_words: list[str]
    extra_words: list[str]
    substituted_words: list[TextSubstitution]
    top_issues: list[Issue]
    summary_feedback_zh: str
    next_practice: list[PracticeItem]
    warnings: list[str]
