import pytest

from app.domain.russian_syllabifier import syllabify


@pytest.mark.parametrize(
    ("word", "count", "vowels", "stress"),
    [
        ("молоко́", 3, "ооо", 3),
        ("изуча́ю", 4, "иуаю", 3),
        ("ру́сский", 2, "уи", 1),
        ("семья́", 2, "ея", 2),
        ("объе́кт", 2, "ое", 2),
        ("подъе́зд", 2, "ое", 2),
        ("ещё", 2, "её", 2),
        ("язы́к", 2, "яы", 2),
        ("здра́вствуйте", 3, "ауе", 1),
    ],
)
def test_syllabification(word, count, vowels, stress):
    result = syllabify(word)
    assert len(result) == count
    assert "".join(item.vowel.lower() for item in result) == vowels
    assert next(item.index for item in result if item.is_stressed) == stress
    assert all("ь" != item.vowel and "ъ" != item.vowel for item in result)
