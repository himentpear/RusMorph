import pytest

from app.domain.score_rules import overall_score, pronunciation_accuracy, status_for
from app.services.pronunciation_score_service import PronunciationScoreService


def test_score_ranges_and_weights():
    assert pronunciation_accuracy(100, 50, 0) == 70
    assert overall_score(80, 90, 70, 100) == 83.5
    with pytest.raises(ValueError):
        overall_score(80, 80, 80, 80, (1, 1, 1, 1))


def test_word_score_status_uses_ui_color_thresholds():
    assert status_for(90, 0.8) == "excellent"
    assert status_for(75, 0.8) == "good"
    assert status_for(60, 0.8) == "acceptable"
    assert status_for(59.9, 0.8) == "needs_work"


def test_omission_lowers_completeness():
    result = PronunciationScoreService.compare_text(
        ["Я", "изучаю", "русский", "язык"],
        ["Я", "изучаю", "язык"],
    )
    assert result.score == 75
    assert result.omitted == ["русский"]


def test_substitution_and_extra_are_separate():
    result = PronunciationScoreService.compare_text(["я", "читаю"], ["мы", "читаю", "книгу"])
    assert result.substitutions == [("я", "мы")]
    assert result.extra == ["книгу"]
