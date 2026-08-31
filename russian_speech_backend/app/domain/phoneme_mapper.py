VOWEL_PHONES = {
    "a", "e", "i", "o", "u", "y", "ɐ", "ə", "ɪ", "ʊ",
    "A", "E", "I", "O", "U", "Y",
}
SILENCE_PHONES = {"sil", "sp", "spn", "<eps>", ""}


def is_vowel_phone(phone: str) -> bool:
    base = phone.replace("ˈ", "").replace("ˌ", "").rstrip("0123456789")
    return base in VOWEL_PHONES


def phones_for_syllables(phones: list[str], syllable_count: int) -> list[list[str]]:
    if syllable_count <= 0:
        return []
    result: list[list[str]] = [[] for _ in range(syllable_count)]
    current = 0
    seen_vowel = False
    for phone in phones:
        if phone in SILENCE_PHONES:
            continue
        if is_vowel_phone(phone):
            if seen_vowel and current < syllable_count - 1:
                current += 1
            seen_vowel = True
        result[current].append(phone)
    return result
