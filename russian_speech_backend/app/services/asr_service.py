import re
import threading
from dataclasses import dataclass
from pathlib import Path
from typing import Protocol

from app.config import Settings
from app.schemas.asr import WordTimestamp
from app.utils.exceptions import DependencyUnavailable, SpeechError


@dataclass(frozen=True)
class AsrResult:
    text: str
    language: str
    language_confidence: float
    confidence: float
    words: list[WordTimestamp]
    alternatives: list[str]


class AsrEngine(Protocol):
    def transcribe(
        self,
        audio_path: Path,
        language: str = "ru",
        prompt: str | None = None,
    ) -> AsrResult: ...


class FasterWhisperEngine:
    def __init__(self, settings: Settings):
        self.settings = settings
        self._model = None
        self._lock = threading.Lock()

    def _load(self):
        if self._model is not None:
            return self._model
        try:
            from faster_whisper import WhisperModel
        except ImportError as exc:
            raise DependencyUnavailable("faster-whisper", "请安装模型依赖") from exc
        with self._lock:
            if self._model is None:
                self._model = WhisperModel(
                    self.settings.asr_model,
                    device=self.settings.asr_device,
                    compute_type=self.settings.asr_compute_type,
                )
        return self._model

    def warm_up(self) -> None:
        self._load()

    def transcribe(
        self,
        audio_path: Path,
        language: str = "ru",
        prompt: str | None = None,
    ) -> AsrResult:
        model = self._load()
        segments, info = model.transcribe(
            str(audio_path),
            language=language,
            beam_size=self.settings.asr_beam_size,
            best_of=1,
            temperature=0.0,
            word_timestamps=True,
            vad_filter=False,
            condition_on_previous_text=False,
            hotwords=prompt,
        )
        words: list[WordTimestamp] = []
        texts: list[str] = []
        probabilities: list[float] = []
        for segment in segments:
            texts.append(segment.text.strip())
            for word in segment.words or []:
                probability = float(word.probability or 0.0)
                probabilities.append(probability)
                words.append(WordTimestamp(
                    text=word.word.strip(),
                    start_ms=max(0, round(word.start * 1000)),
                    end_ms=max(0, round(word.end * 1000)),
                    confidence=round(probability, 4),
                ))
        text = normalize_transcript(" ".join(filter(None, texts)))
        if not text:
            raise SpeechError("unreliable_transcription", "说话内容无法可靠识别")
        confidence = sum(probabilities) / len(probabilities) if probabilities else 0.0
        return AsrResult(
            text=text,
            language=info.language or language,
            language_confidence=round(float(info.language_probability or 0.0), 4),
            confidence=round(confidence, 4),
            words=words,
            alternatives=[],
        )


def normalize_transcript(text: str) -> str:
    text = re.sub(r"\s+([,.!?;:])", r"\1", text.strip())
    return re.sub(r"\s+", " ", text)


def build_asr_engine(settings: Settings) -> AsrEngine:
    if settings.asr_backend == "faster_whisper":
        return FasterWhisperEngine(settings)
    raise DependencyUnavailable(settings.asr_backend, "当前构建未启用该 ASR 适配器")
