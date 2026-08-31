from pathlib import Path

from tools.xlsx_builder.declension_parser import parse_declension_sheets
from tools.xlsx_builder.workbook_auditor import SheetData


def test_masculine_consonant_singular_genitive_is_parsed_as_a() -> None:
    rows = [
        ["性", "结尾", "数", "主格", "属格", "与格", "宾格", "工具格", "前置格"],
        ["阳性", "辅音", "单数", "零词尾", "а", "у", "同主格", "ом", "е"],
    ]
    sheet = SheetData(Path("rules.xlsx"), "变格表", rows, 2, 9)
    rules = parse_declension_sheets([sheet]).rules
    genitive = next(item for item in rules if item.case_name == "GENITIVE")
    assert genitive.gender == "阳性"
    assert genitive.ending_type == "辅音"
    assert genitive.number == "SINGULAR"
    assert genitive.result_ending == "а"
