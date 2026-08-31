from tools.xlsx_builder.russian_normalizer import (
    generate_search_variants,
    normalize_russian_for_search,
    split_outer_forms,
)


def test_accent_normalization() -> None:
    assert normalize_russian_for_search("автомоби́ль") == "автомобиль"


def test_yo_alias() -> None:
    assert generate_search_variants("всё") == ["всё", "все"]


def test_hard_soft_and_short_i_are_preserved() -> None:
    assert normalize_russian_for_search("объект") == "объект"
    assert normalize_russian_for_search("семья") == "семья"
    assert normalize_russian_for_search("май") == "май"


def test_multiple_forms_remain_ordered() -> None:
    forms, explicit = split_outer_forms("писа́ть, пишу́, пи́шешь, пи́шут")
    assert forms == ["писа́ть", "пишу́", "пи́шешь", "пи́шут"]
    assert explicit


def test_parenthetical_grammar_note_is_not_search_form() -> None:
    forms, explicit = split_outer_forms("(он, она́, оно́) стои́т")
    assert forms == ["стои́т"]
    assert not explicit
