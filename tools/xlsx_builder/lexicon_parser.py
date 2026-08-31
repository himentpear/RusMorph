"""Parse classified lexicon sources into deterministic Room-compatible records."""

from __future__ import annotations

import hashlib
import json
import re
import unicodedata
from collections import Counter
from dataclasses import dataclass, field
from typing import Any

from .models import BuildIssue, EntryAnnotation, LexiconRecord, Provenance, SearchForm
from .russian_normalizer import (
    generate_search_variants,
    normalize_russian_for_search,
    normalize_russian_for_storage,
    split_outer_forms,
)
from .workbook_auditor import SheetData, canonical_rows, classify_sheet, normalized_header

HEADER_FIELDS = {
    "课号": "lesson",
    "序号": "sequence",
    "俄语": "russian",
    "中文": "chinese",
    "词类": "pos1",
    "词类1": "pos1",
    "词类2": "pos2",
    "词类3": "pos3",
    "词类4": "pos4",
    "性": "gender",
    "变格法": "declension",
    "结尾字母": "ending",
    "复数重音转移": "plural_stress",
    "动词的体": "aspect",
    "变位法": "conjugation",
    "语音交替": "alternation",
}
KNOWN_POS = {"名词", "动词", "副词", "形容词", "数词", "代词", "前置词", "语气词", "连词", "感叹词", "插入语"}
EMPTY_TEXT = {"", "null", "none", "-"}
MORPHOLOGY_FIELDS = {"gender", "declension", "ending", "plural_stress", "aspect", "conjugation", "alternation", "pos1", "pos2", "pos3", "pos4", "chinese"}
CORE_IDENTITY_HEADERS = {"课号", "序号", "俄语", "中文"}
IGNORED_ANNOTATION_HEADER_RE = re.compile(r"^(?:column\d+|列\d+|unnamed(?::\s*\d+)?)$", re.IGNORECASE)


@dataclass
class LexiconParseResult:
    entries: list[LexiconRecord]
    issues: list[BuildIssue] = field(default_factory=list)
    selected_sheets: list[dict[str, str]] = field(default_factory=list)
    skipped_duplicate_sheets: list[dict[str, str]] = field(default_factory=list)
    pure_word_validation: dict[str, Any] = field(default_factory=dict)
    duplicate_records: int = 0
    legal_duplicate_words: int = 0
    multi_form_cells: int = 0
    raw_part_of_speech_values: list[str] = field(default_factory=list)
    normalized_part_of_speech_values: list[str] = field(default_factory=list)
    applied_aliases: dict[str, str] = field(default_factory=dict)
    unknown_part_of_speech_values: list[str] = field(default_factory=list)


def clean_value(field_name: str, value: Any) -> str | int | None:
    """Clean sentinels by field semantics; lesson/sequence value 8 is valid."""
    if value is None:
        return None
    if field_name in {"lesson", "sequence"}:
        text = str(value).strip()
        if not text:
            return None
        try:
            numeric = float(text)
            return int(numeric) if numeric.is_integer() else text
        except ValueError:
            return text
    text = normalize_russian_for_storage(str(value))
    if text.casefold() in EMPTY_TEXT or (field_name in MORPHOLOGY_FIELDS and text == "8"):
        return None
    return text


def stable_id(*parts: Any) -> str:
    canonical = json.dumps(parts, ensure_ascii=False, separators=(",", ":"), sort_keys=False)
    return "lex_" + hashlib.sha256(canonical.encode("utf-8")).hexdigest()[:20]


def _mapping(sheet: SheetData) -> dict[int, str]:
    header = sheet.effective_rows[0]
    return {
        index: HEADER_FIELDS[normalized_header(value)]
        for index, value in enumerate(header)
        if normalized_header(value) in HEADER_FIELDS
    }


def _completeness(sheet: SheetData) -> int:
    mapping = _mapping(sheet)
    return sum(
        clean_value(field, row[index] if index < len(row) else None) is not None
        for row in sheet.effective_rows[1:]
        for index, field in mapping.items()
    )


def _semantic_rows(sheet: SheetData) -> set[str]:
    """Compare known fields after placeholder cleaning, not raw cell layout."""
    mapping = _mapping(sheet)
    result: set[str] = set()
    for row in sheet.effective_rows[1:]:
        values = [
            str(clean_value(field, row[index] if index < len(row) else None) or "")
            for index, field in sorted(mapping.items())
        ]
        if any(values):
            result.add("\x1f".join(values))
    return result


def select_main_sheets(sheets: list[SheetData]) -> tuple[list[SheetData], list[SheetData]]:
    candidates = [sheet for sheet in sheets if classify_sheet(sheet) == "LEXICON"]
    # When current XLSX workbooks are available they are authoritative. Legacy
    # CSV exports remain in data-source for audit/history, but must not create a
    # second fact source or alter the runtime database.
    xlsx_candidates = [sheet for sheet in candidates if sheet.source.suffix.casefold() == ".xlsx"]
    ignored_legacy = [sheet for sheet in candidates if sheet not in xlsx_candidates] if xlsx_candidates else []
    if xlsx_candidates:
        candidates = xlsx_candidates
    selected: list[SheetData] = []
    skipped: list[SheetData] = list(ignored_legacy)
    for candidate in sorted(candidates, key=lambda item: (-_completeness(item), item.name.casefold())):
        rows = _semantic_rows(candidate)
        duplicate = next((
            item for item in selected
            if rows and _semantic_rows(item)
            and len(rows & _semantic_rows(item)) / min(len(rows), len(_semantic_rows(item))) >= 0.90
        ), None)
        if duplicate is None:
            selected.append(candidate)
        else:
            skipped.append(candidate)
    return selected, skipped


def _annotations_from_row(
    sheet: SheetData,
    row: list[Any],
) -> list[EntryAnnotation]:
    headers = sheet.effective_rows[0]
    annotations: list[EntryAnnotation] = []
    seen: set[tuple[str, str]] = set()
    for index, header_value in enumerate(headers):
        header = normalized_header(header_value)
        if (
            not header
            or header in CORE_IDENTITY_HEADERS
            or IGNORED_ANNOTATION_HEADER_RE.match(header)
        ):
            continue
        value = clean_value(HEADER_FIELDS.get(header, "annotation"), row[index] if index < len(row) else None)
        if value is None:
            continue
        text = str(value)
        normalized = normalize_russian_for_search(text)
        key = (header, normalized)
        if normalized and key not in seen:
            annotations.append(EntryAnnotation(header, text, normalized))
            seen.add(key)
    return annotations


def _record_from_row(
    sheet: SheetData,
    source_row: int,
    row: list[Any],
    mapping: dict[int, str],
    issues: list[BuildIssue],
    aliases: dict[str, str],
    applied_aliases: dict[str, str],
) -> LexiconRecord | None:
    values = {field: clean_value(field, row[index] if index < len(row) else None) for index, field in mapping.items()}
    raw = values.get("russian")
    if raw is None:
        if any(value is not None for value in values.values()):
            issues.append(BuildIssue("WARNING", "ROW_WITHOUT_RUSSIAN", "忽略缺少俄语字段的数据行", f"{sheet.source.name}/{sheet.name}!{source_row}"))
        return None
    forms, explicit = split_outer_forms(str(raw))
    if not forms:
        issues.append(BuildIssue("WARNING", "NO_SEARCHABLE_FORM", "俄语单元格没有可用词形", f"{sheet.source.name}/{sheet.name}!{source_row}"))
        return None
    primary = forms[0]
    search_forms: list[SearchForm] = []
    seen: set[str] = set()
    for form_index, form in enumerate(forms):
        for normalized in generate_search_variants(form):
            if normalized not in seen:
                search_forms.append(SearchForm(form, normalized, "HEADWORD" if form_index == 0 else "INFLECTED"))
                seen.add(normalized)
    parts = []
    raw_parts: list[str] = []
    for field in ("pos1", "pos2", "pos3", "pos4"):
        part = values.get(field)
        if part is not None:
            raw_part = str(part)
            if raw_part not in raw_parts:
                raw_parts.append(raw_part)
            normalized_part = aliases.get(raw_part, raw_part)
            if raw_part != normalized_part:
                applied_aliases[raw_part] = normalized_part
            if normalized_part not in KNOWN_POS:
                issues.append(BuildIssue("WARNING", "UNKNOWN_PART_OF_SPEECH", f"未知词性：{raw_part}", f"{sheet.source.name}/{sheet.name}!{source_row}"))
            elif normalized_part not in parts:
                parts.append(normalized_part)
    lesson = values.get("lesson") if isinstance(values.get("lesson"), int) else None
    sequence = values.get("sequence") if isinstance(values.get("sequence"), int) else None
    normalized_primary = normalize_russian_for_search(primary)
    meaning = str(values["chinese"]) if values.get("chinese") is not None else None
    identity = ("rusmorph-lexicon-v1", lesson, sequence, normalized_primary, unicodedata.normalize("NFC", meaning or "").casefold(), parts[0] if parts else "")
    provenance = Provenance(sheet.source.name, sheet.name, source_row)
    return LexiconRecord(
        id=stable_id(*identity), lesson=lesson, sequence=sequence,
        raw_russian=str(raw), display_form=primary, primary_form=primary,
        normalized_primary_form=normalized_primary, chinese_meaning=meaning,
        parts_of_speech=parts, raw_parts_of_speech=raw_parts,
        gender=_text(values.get("gender")),
        declension_class=_text(values.get("declension")), ending_type=_text(values.get("ending")),
        plural_stress_pattern=_text(values.get("plural_stress")), aspect=_text(values.get("aspect")),
        conjugation_class=_text(values.get("conjugation")), phonetic_alternation=_text(values.get("alternation")),
        search_forms=search_forms, provenance=[provenance],
        annotations=_annotations_from_row(sheet, row),
        is_lemma_explicit=explicit,
    )


def _text(value: Any) -> str | None:
    return str(value) if value is not None else None


def _merge_exact(existing: LexiconRecord, incoming: LexiconRecord) -> None:
    known_forms = {item.normalized for item in existing.search_forms}
    existing.search_forms.extend(item for item in incoming.search_forms if item.normalized not in known_forms)
    known_sources = {(item.source_file, item.source_sheet, item.source_row) for item in existing.provenance}
    existing.provenance.extend(item for item in incoming.provenance if (item.source_file, item.source_sheet, item.source_row) not in known_sources)
    known_annotations = {(item.field_name, item.normalized_value) for item in existing.annotations}
    existing.annotations.extend(
        item for item in incoming.annotations
        if (item.field_name, item.normalized_value) not in known_annotations
    )


def validate_pure_words(pure_sheets: list[SheetData], entries: list[LexiconRecord]) -> dict[str, Any]:
    if not pure_sheets:
        return {"available": False, "missingInMain": [], "russianMismatches": [], "extraInMain": []}
    main = {(item.lesson, item.sequence): item.raw_russian for item in entries}
    missing: list[dict[str, Any]] = []
    mismatches: list[dict[str, Any]] = []
    pure_keys: set[tuple[int | None, int | None]] = set()
    for sheet in pure_sheets:
        for source_row, row in sheet.effective_indexed_rows:
            if len(row) < 3:
                continue
            lesson = clean_value("lesson", row[0]); sequence = clean_value("sequence", row[1]); russian = clean_value("russian", row[2])
            key = (lesson if isinstance(lesson, int) else None, sequence if isinstance(sequence, int) else None)
            pure_keys.add(key)
            if key not in main:
                missing.append({"lesson": key[0], "sequence": key[1], "russian": russian, "row": source_row})
            elif normalize_russian_for_storage(main[key]) != normalize_russian_for_storage(str(russian or "")):
                mismatches.append({"lesson": key[0], "sequence": key[1], "main": main[key], "pure": russian, "row": source_row})
    extra = [{"lesson": key[0], "sequence": key[1], "russian": value} for key, value in main.items() if key not in pure_keys]
    return {"available": True, "missingInMain": missing, "russianMismatches": mismatches, "extraInMain": extra}


def parse_lexicon(
    sheets: list[SheetData],
    part_of_speech_aliases: dict[str, str] | None = None,
) -> LexiconParseResult:
    aliases = part_of_speech_aliases or {}
    selected, skipped = select_main_sheets(sheets)
    issues: list[BuildIssue] = []
    if not selected:
        issues.append(BuildIssue("ERROR", "NO_LEXICON_SHEET", "没有找到含俄语列的主词表"))
        return LexiconParseResult([], issues)
    result: dict[tuple[Any, ...], LexiconRecord] = {}
    multi_form_count = 0
    duplicate_records = 0
    applied_aliases: dict[str, str] = {}
    for sheet in selected:
        mapping = _mapping(sheet)
        for source_row, row in sheet.effective_indexed_rows[1:]:
            record = _record_from_row(sheet, source_row, row, mapping, issues, aliases, applied_aliases)
            if record is None:
                continue
            if len({item.display for item in record.search_forms}) > 1:
                multi_form_count += 1
            key = (record.lesson, record.sequence, record.normalized_primary_form, (record.chinese_meaning or "").casefold(), record.parts_of_speech[0] if record.parts_of_speech else "")
            if key in result:
                duplicate_records += 1
                _merge_exact(result[key], record)
            else:
                result[key] = record
    entries = sorted(result.values(), key=lambda item: (item.lesson is None, item.lesson or 0, item.sequence is None, item.sequence or 0, item.id))
    word_counts = Counter(item.normalized_primary_form for item in entries)
    legal_duplicates = sum(count - 1 for count in word_counts.values() if count > 1)
    pure = validate_pure_words([sheet for sheet in sheets if classify_sheet(sheet) == "PURE_WORDS"], entries)
    raw_pos = sorted({part for entry in entries for part in entry.raw_parts_of_speech})
    normalized_pos = sorted({part for entry in entries for part in entry.parts_of_speech})
    unknown_pos = sorted({part for part in normalized_pos if part not in KNOWN_POS})
    return LexiconParseResult(
        entries=entries, issues=issues,
        selected_sheets=[{"file": item.source.name, "sheet": item.name} for item in selected],
        skipped_duplicate_sheets=[{"file": item.source.name, "sheet": item.name} for item in skipped],
        pure_word_validation=pure, duplicate_records=duplicate_records,
        legal_duplicate_words=legal_duplicates, multi_form_cells=multi_form_count,
        raw_part_of_speech_values=raw_pos,
        normalized_part_of_speech_values=normalized_pos,
        applied_aliases=applied_aliases,
        unknown_part_of_speech_values=unknown_pos,
    )
