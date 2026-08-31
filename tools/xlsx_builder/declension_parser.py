"""Conservative parser for semantic declension matrices."""

from __future__ import annotations

import hashlib
import re
from dataclasses import dataclass, field
from typing import Any

from openpyxl.utils import get_column_letter

from .models import BuildIssue, DeclensionRule
from .workbook_auditor import SheetData, classify_sheet

CASE_MAP = {
    "主格": "NOMINATIVE", "属格": "GENITIVE", "与格": "DATIVE",
    "宾格": "ACCUSATIVE", "工具格": "INSTRUMENTAL", "前置格": "PREPOSITIONAL",
    "介词格": "PREPOSITIONAL",
}
NUMBER_MAP = {"单数": "SINGULAR", "复数": "PLURAL"}
GENDERS = ("阳性", "阴性", "中性")
ENDING_MARKERS = ("辅音", "й", "ь", "а", "я", "о", "е", "硬音", "软音")
REFERENCE_RE = re.compile(r"^同\s*(.+)$")


@dataclass
class DeclensionParseResult:
    rules: list[DeclensionRule]
    warnings: list[dict[str, Any]] = field(default_factory=list)
    issues: list[BuildIssue] = field(default_factory=list)


def _stable_rule_id(sheet: SheetData, cell: str, raw: str) -> str:
    token = f"{sheet.source.name}\x1f{sheet.name}\x1f{cell}\x1f{raw}"
    return "rule_" + hashlib.sha256(token.encode("utf-8")).hexdigest()[:20]


def _context(sheet: SheetData, row_index: int, column_index: int) -> list[str]:
    fragments: list[str] = []
    for row in sheet.rows[:row_index]:
        if column_index < len(row) and row[column_index] is not None:
            fragments.append(str(row[column_index]).strip())
    row = sheet.rows[row_index]
    fragments.extend(str(value).strip() for value in row[:column_index] if value is not None)
    return [item for item in fragments if item]


def _find_context_value(fragments: list[str], choices: tuple[str, ...]) -> str | None:
    for fragment in reversed(fragments):
        for choice in choices:
            if choice in fragment:
                return choice
    return None


def _case_for_column(sheet: SheetData, row_index: int, column_index: int) -> str | None:
    for previous in range(row_index, -1, -1):
        row = sheet.rows[previous]
        value = str(row[column_index]).strip() if column_index < len(row) and row[column_index] is not None else ""
        for label, code in CASE_MAP.items():
            if value == label or label in value:
                return code
    return None


def parse_declension_sheets(sheets: list[SheetData]) -> DeclensionParseResult:
    rules: list[DeclensionRule] = []
    warnings: list[dict[str, Any]] = []
    issues: list[BuildIssue] = []
    candidates = [sheet for sheet in sheets if classify_sheet(sheet) == "DECLENSION"]
    if not candidates:
        issues.append(BuildIssue("INFO", "NO_DECLENSION_SHEET", "未提供可识别的变格规则表，未生成变格规则"))
        return DeclensionParseResult(rules, warnings, issues)
    for sheet in candidates:
        for row_index, row in enumerate(sheet.rows):
            for column_index, value in enumerate(row):
                raw = str(value).strip() if value is not None else ""
                if not raw or raw in CASE_MAP or raw in NUMBER_MAP or raw in GENDERS:
                    continue
                case_name = _case_for_column(sheet, row_index, column_index)
                if case_name is None:
                    continue
                fragments = _context(sheet, row_index, column_index)
                gender = _find_context_value(fragments, GENDERS)
                ending_type = _find_context_value(fragments, ENDING_MARKERS)
                number_label = _find_context_value(fragments, tuple(NUMBER_MAP))
                cell = f"{get_column_letter(column_index + 1)}{row_index + 1}"
                reference_match = REFERENCE_RE.match(raw)
                reference = reference_match.group(1).strip() if reference_match else None
                variants = [item.strip() for item in re.split(r"[/／]", raw) if item.strip()]
                interpretable = bool(gender or ending_type or number_label)
                if not interpretable:
                    warning = {
                        "file": sheet.source.name, "sheet": sheet.name, "sourceCell": cell,
                        "rawValue": raw, "message": "缺少足够的行列语义，已保留原值",
                    }
                    warnings.append(warning)
                rules.append(DeclensionRule(
                    id=_stable_rule_id(sheet, cell, raw), category="NOUN_DECLENSION",
                    gender=gender, ending_type=ending_type,
                    number=NUMBER_MAP.get(number_label) if number_label else None,
                    case_name=case_name, result_ending=raw,
                    description="；".join(fragments), source_file=sheet.source.name,
                    source_sheet=sheet.name, source_cell=cell, raw_value=raw,
                    reference_rule=reference, variants=variants if len(variants) > 1 else [],
                ))
    return DeclensionParseResult(rules, warnings, issues)
