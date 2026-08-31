from pathlib import Path

from tools.xlsx_builder.lexicon_parser import clean_value, parse_lexicon
from tools.xlsx_builder.workbook_auditor import SheetData, audit_sources


HEADERS = ["课号", "序号", "俄语", "中文", "词类", "词类2", "词类3", "词类4", "性", "变格法", "结尾字母", "复数重音转移", "", "Column1", "动词的体", "变位法", "语音交替"]


def sheet(name: str, rows: list[list[object]]) -> SheetData:
    return SheetData(Path("test.xlsx"), name, rows, len(rows), max(map(len, rows)))


def test_empty_columns_and_blank_tail_do_not_generate_entries() -> None:
    source = sheet("大学俄语（一）", [HEADERS, [1, 1, "друг", "朋友", "名词"], [None] * 17, [None] * 17])
    result = parse_lexicon([source])
    assert len(result.entries) == 1


def test_near_duplicate_main_sheets_are_imported_once() -> None:
    rows = [HEADERS, [1, 1, "друг", "朋友", "名词"]]
    placeholder_rows = [HEADERS, [1, 1, "друг", "朋友", "名词", 8, 8, 8, 8]]
    result = parse_lexicon([sheet("大学俄语（一）", rows), sheet("大学俄语（一） (2)", placeholder_rows)])
    assert len(result.entries) == 1
    assert len(result.skipped_duplicate_sheets) == 1


def test_xlsx_is_authoritative_over_legacy_csv() -> None:
    xlsx = sheet("第一册", [HEADERS, [1, 1, "друг", "朋友", "名词"]])
    csv = SheetData(Path("legacy.csv"), "legacy", [HEADERS, [1, 1, "друг", "旧释义", "名词"]], 2, len(HEADERS))
    result = parse_lexicon([xlsx, csv])
    assert len(result.entries) == 1
    assert result.entries[0].chinese_meaning == "朋友"
    assert result.skipped_duplicate_sheets == [{"file": "legacy.csv", "sheet": "legacy"}]


def test_all_meaningful_annotations_are_preserved_and_normalized() -> None:
    headers = [*HEADERS, "形容词变"]
    row = [1, 1, "друг", "朋友", "名词", None, None, None, "阳性", 2, "辅音", 8, None, None, None, None, "г-ж", "特殊注释"]
    entry = parse_lexicon([sheet("第一册", [headers, row])]).entries[0]
    values = {(item.field_name, item.value, item.normalized_value) for item in entry.annotations}
    assert ("性", "阳性", "阳性") in values
    assert ("语音交替", "г-ж", "г-ж") in values
    assert ("形容词变", "特殊注释", "特殊注释") in values


def test_same_word_in_different_lesson_or_meaning_is_legal() -> None:
    source = sheet("词表", [HEADERS, [1, 1, "мир", "和平", "名词"], [2, 1, "мир", "世界", "名词"]])
    result = parse_lexicon([source])
    assert len(result.entries) == 2
    assert result.legal_duplicate_words == 1


def test_parts_of_speech_are_ordered_and_deduplicated() -> None:
    source = sheet("词表", [HEADERS, [1, 1, "мой", "我的", "代词", "形容词", "代词", None]])
    assert parse_lexicon([source]).entries[0].parts_of_speech == ["代词", "形容词"]


def test_field_specific_eight_cleaning() -> None:
    assert clean_value("lesson", 8) == 8
    assert clean_value("sequence", "8") == 8
    assert clean_value("gender", 8) is None
    source = sheet("词表", [HEADERS, [8, 8, "восемь", "八", "数词", None, None, None, 8]])
    entry = parse_lexicon([source]).entries[0]
    assert entry.lesson == 8 and entry.sequence == 8 and entry.gender is None


def test_ids_are_stable_between_runs() -> None:
    source = sheet("词表", [HEADERS, [1, 1, "писа́ть", "写", "动词"]])
    assert parse_lexicon([source]).entries[0].id == parse_lexicon([source]).entries[0].id


def test_multi_form_cell_generates_four_ordered_search_forms() -> None:
    source = sheet("词表", [HEADERS, [1, 1, "писа́ть, пишу́, пи́шешь, пи́шут", "写", "动词"]])
    forms = parse_lexicon([source]).entries[0].search_forms
    assert [item.normalized for item in forms] == ["писать", "пишу", "пишешь", "пишут"]


def test_pure_word_validation_detects_mismatch() -> None:
    main = sheet("词表", [HEADERS, [1, 1, "друг", "朋友", "名词"]])
    pure = sheet("纯单词", [[1, 1, "дру́г"]])
    validation = parse_lexicon([main, pure]).pure_word_validation
    assert validation["available"]
    assert len(validation["russianMismatches"]) == 1


def test_audit_ignores_empty_and_column_placeholders(tmp_path: Path) -> None:
    path = tmp_path / "source.csv"
    path.write_text("课号,序号,俄语,中文,词类,,Column1\n1,1,друг,朋友,名词,,\n", encoding="utf-8-sig")
    report = audit_sources(tmp_path).report
    assert report["sheets"][0]["ignoredColumns"] == [6, 7]


def test_part_of_speech_alias_keeps_raw_value() -> None:
    source = sheet("词表", [HEADERS, [9, 4, "впро́чем", "不过", "连接词", "副词"]])
    result = parse_lexicon([source], {"连接词": "连词"})
    entry = result.entries[0]
    assert entry.parts_of_speech == ["连词", "副词"]
    assert entry.raw_parts_of_speech == ["连接词", "副词"]
    assert result.applied_aliases == {"连接词": "连词"}
    assert result.unknown_part_of_speech_values == []
