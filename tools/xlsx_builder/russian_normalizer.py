"""Unicode-safe Russian normalization and source-form parsing."""

from __future__ import annotations

import re
import unicodedata
from collections.abc import Iterable

ACCENT = "\u0301"
SPACE_RE = re.compile(r"\s+")


def normalize_russian_for_storage(text: str) -> str:
    """Preserve Russian spelling and accents while normalizing whitespace/NFC."""
    return SPACE_RE.sub(" ", unicodedata.normalize("NFC", text).strip())


def normalize_russian_for_search(text: str) -> str:
    """Lowercase Russian and remove combining acute accents, apostrophes, and stress marks."""
    decomposed = unicodedata.normalize("NFD", text).lower()
    without_accents = "".join(character for character in decomposed if character != ACCENT and character not in "'`’")
    return SPACE_RE.sub(" ", unicodedata.normalize("NFC", without_accents)).strip()


def generate_search_variants(text: str) -> list[str]:
    """Generate stable accentless search forms, including ё/е aliases."""
    normalized = normalize_russian_for_search(text)
    if not normalized:
        return []
    variants = [normalized]
    if "ё" in normalized:
        variants.append(normalized.replace("ё", "е"))
    return list(dict.fromkeys(variants))


def split_outer_forms(text: str) -> tuple[list[str], bool]:
    """Split only top-level comma/semicolon separators and ignore leading grammar notes."""
    source = normalize_russian_for_storage(text)
    forms: list[str] = []
    buffer: list[str] = []
    depth = 0
    for character in source:
        if character == "(":
            depth += 1
        elif character == ")" and depth:
            depth -= 1
        if depth == 0 and character in ",，;；":
            candidate = "".join(buffer).strip()
            if candidate:
                forms.append(candidate)
            buffer.clear()
        else:
            buffer.append(character)
    candidate = "".join(buffer).strip()
    if candidate:
        forms.append(candidate)

    cleaned: list[str] = []
    is_lemma_explicit = True
    for form in forms:
        without_note = re.sub(r"^\([^)]*\)\s*", "", form).strip()
        if without_note != form:
            is_lemma_explicit = False
        if without_note and re.search(r"[А-Яа-яЁё]", without_note):
            cleaned.append(without_note)
    return list(dict.fromkeys(cleaned)), is_lemma_explicit


def ordered_unique(values: Iterable[str]) -> list[str]:
    return list(dict.fromkeys(value for value in values if value))
