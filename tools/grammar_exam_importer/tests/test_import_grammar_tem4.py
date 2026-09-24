from pathlib import Path

from openpyxl import Workbook

from tools.grammar_exam_importer.import_grammar_tem4 import (
    GRAMMAR_HEADERS,
    QUESTION_HEADERS,
    parse_workbook,
    split_point_ids,
)


ROOT = Path(__file__).resolve().parents[3]
SOURCE = ROOT / "data-source/grammar-tem4/grammar_tem4_source.xlsx"


def test_real_workbook_counts_and_relationships() -> None:
    result = parse_workbook(SOURCE)
    assert len(result.grammar_points) == 16
    assert len(result.questions) == 48
    assert len(result.links) == 51
    assert result.audit["multi_link_question_count"] == 3
    assert result.audit["grammar_points_with_questions"] == 8
    assert not result.audit["unknown_point_ids"]
    assert all(link["pointId"] in {point["pointId"] for point in result.grammar_points} for link in result.links)


def test_multi_point_id_is_normalized() -> None:
    assert split_point_ids("SYN_0006\nSYN_0008") == ["SYN_0006", "SYN_0008"]


def test_parser_ignores_phantom_columns_and_blank_rows(tmp_path: Path) -> None:
    workbook = Workbook()
    grammar = workbook.active
    grammar.title = "01_语法点"
    grammar.append(GRAMMAR_HEADERS)
    for index in range(16):
        grammar.append((f"SYN_{index + 1:04d}", f"语法{index}", f"Grammar {index}", "Explanation", None, None))
    grammar["XFD955"] = "phantom"

    questions = workbook.create_sheet("02_专四题目")
    questions.append(QUESTION_HEADERS)
    for index in range(48):
        point = f"SYN_{index % 8 + 1:04d}"
        if index < 3:
            point += f"\nSYN_{(index + 1) % 8 + 1:04d}"
        questions.append((index + 1, point, 2018, f"Stem {index}", "A", "B", "C", "D", "A", "Analysis"))
    questions["Q981"] = "phantom"
    path = tmp_path / "phantom.xlsx"
    workbook.save(path)

    result = parse_workbook(path)
    assert len(result.grammar_points) == 16
    assert len(result.questions) == 48
    assert len(result.links) == 51
