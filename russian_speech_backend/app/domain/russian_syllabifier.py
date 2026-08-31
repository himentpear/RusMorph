import re
import unicodedata
from dataclasses import dataclass

RUSSIAN_VOWELS = set("аеёиоуыэюяАЕЁИОУЫЭЮЯ")
COMBINING_ACUTE = "\u0301"
RUSSIAN_WORD = re.compile(r"[А-Яа-яЁё]+(?:\u0301)?[А-Яа-яЁё\u0301]*")


@dataclass(frozen=True)
class Syllable:
    text: str
    index: int
    vowel: str
    is_stressed: bool


def strip_stress(text: str) -> str:
    return unicodedata.normalize("NFC", text.replace(COMBINING_ACUTE, ""))


def syllabify(word: str) -> list[Syllable]:
    """Split around vowel nuclei; ь/ъ never create a syllable."""
    # NFC keeps ё and й as letters; only the acute stress mark remains combining.
    normalized = unicodedata.normalize("NFC", word)
    plain_chars: list[str] = []
    stressed_vowel_position: int | None = None
    last_plain = -1
    for char in normalized:
        if char == COMBINING_ACUTE:
            if last_plain >= 0:
                stressed_vowel_position = last_plain
            continue
        plain_chars.append(char)
        last_plain += 1
    plain = "".join(plain_chars)
    nuclei = [i for i, char in enumerate(plain) if char in RUSSIAN_VOWELS]
    if not nuclei:
        return []
    boundaries = [0]
    for left_vowel, right_vowel in zip(nuclei, nuclei[1:]):
        cluster_start = left_vowel + 1
        cluster_length = right_vowel - cluster_start
        if cluster_length <= 0:
            boundary = right_vowel
        elif cluster_length == 1:
            boundary = cluster_start
        elif plain[cluster_start] == plain[cluster_start + 1]:
            boundary = cluster_start + 1
        else:
            # Keep the last consonant with the following vowel (мо-ло-ко, рус-ский).
            boundary = right_vowel - 1
        boundaries.append(boundary)
    boundaries.append(len(plain))

    result: list[Syllable] = []
    for idx, nucleus in enumerate(nuclei):
        start, end = boundaries[idx], boundaries[idx + 1]
        is_stressed = stressed_vowel_position == nucleus or plain[nucleus].lower() == "ё"
        result.append(
            Syllable(
                text=plain[start:end],
                index=idx + 1,
                vowel=plain[nucleus],
                is_stressed=is_stressed,
            )
        )
    return result


def russian_words(text: str) -> list[str]:
    return RUSSIAN_WORD.findall(unicodedata.normalize("NFC", text))
