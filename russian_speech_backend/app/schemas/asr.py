from app.schemas.common import ApiModel


class WordTimestamp(ApiModel):
    text: str
    start_ms: int
    end_ms: int
    confidence: float


class TranscriptionResponse(ApiModel):
    success: bool = True
    language: str
    language_confidence: float
    transcript: str
    normalized_transcript: str
    confidence: float
    should_auto_search: bool
    duration_ms: int
    speech_duration_ms: int
    words: list[WordTimestamp]
    alternatives: list[str] = []
    warnings: list[str] = []
