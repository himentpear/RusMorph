import unicodedata
from dataclasses import dataclass

from app.config import Settings
from app.domain.russian_syllabifier import COMBINING_ACUTE, russian_words, strip_stress, syllabify


@dataclass(frozen=True)
class StressedWord:
    text: str
    stressed_text: str
    stress_syllable_index: int | None


class StressService:
    """Context-aware RUAccent adapter with a conservative built-in fallback."""

    _fallback = {
        "я": "я",
        "молоко": "молоко́",
        "изучаю": "изуча́ю",
        "русский": "ру́сский",
        "язык": "язы́к",
        "семья": "семья́",
        "объект": "объе́кт",
        "подъезд": "подъе́зд",
        "ещё": "ещё",
        "здравствуйте": "здра́вствуйте",
        "хорошо": "хорошо́",
        "читаю": "чита́ю",
        "книгу": "кни́гу",
    }

    def __init__(self, settings: Settings):
        self.settings = settings
        self._accentizer = None
        self._attempted = False

    def _load(self):
        if self._attempted:
            return self._accentizer
        self._attempted = True
        if self.settings.stress_backend != "ruaccent":
            return None
        try:
            from ruaccent import RUAccent
            accentizer = RUAccent()
            kwargs = {}
            if self.settings.stress_model_path:
                kwargs["model_path"] = self.settings.stress_model_path
            accentizer.load(**kwargs)
            self._accentizer = accentizer
        # RUAccent may also raise ValueError when an otherwise valid model path
        # cannot be opened (notably some Windows/non-ASCII environments).
        # Stress annotation is optional evidence: degrade with an explicit
        # warning instead of failing or stalling the complete speech request.
        except Exception:
            self._accentizer = None
        return self._accentizer

    def accent_text(self, text: str) -> tuple[str, list[str]]:
        accentizer = self._load()
        if accentizer is not None:
            try:
                return unicodedata.normalize("NFC", accentizer.process_all(text)), []
            except Exception:
                pass
        warnings = ["重音模型不可用；仅对内置已校验词提供重音，其他多音节词保持未判定"]
        result = text
        # Replace from right to left to preserve offsets and punctuation.
        for word in reversed(russian_words(text)):
            plain = strip_stress(word)
            replacement = self._fallback.get(plain.lower())
            if replacement:
                if plain[:1].isupper():
                    replacement = replacement[:1].upper() + replacement[1:]
                position = result.rfind(word)
                if position >= 0:
                    result = result[:position] + replacement + result[position + len(word):]
        return unicodedata.normalize("NFC", result), warnings

    def words(self, text: str) -> tuple[list[StressedWord], list[str]]:
        stressed_text, warnings = self.accent_text(text)
        original_words = russian_words(text)
        accented_words = russian_words(stressed_text)
        result: list[StressedWord] = []
        for original, accented in zip(original_words, accented_words):
            stress_index = next((s.index for s in syllabify(accented) if s.is_stressed), None)
            result.append(StressedWord(strip_stress(original), accented, stress_index))
        return result, warnings
