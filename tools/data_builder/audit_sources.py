#!/usr/bin/env python3
"""Audit RusMorph XLSX and DOCX inputs without modifying source files.

The implementation reads Office Open XML directly from ZIP members, keeping
the audit tool dependency-free and suitable for Windows and Linux build hosts.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import re
import sys
import unicodedata
import zipfile
from collections import Counter, defaultdict
from dataclasses import dataclass
from pathlib import Path, PurePosixPath
from typing import Any, Iterable
from xml.etree import ElementTree as ET


SPREADSHEET_NS = "http://schemas.openxmlformats.org/spreadsheetml/2006/main"
DOCUMENT_NS = "http://schemas.openxmlformats.org/wordprocessingml/2006/main"
REL_NS = "http://schemas.openxmlformats.org/officeDocument/2006/relationships"
PACKAGE_REL_NS = "http://schemas.openxmlformats.org/package/2006/relationships"
NS = {"s": SPREADSHEET_NS, "r": REL_NS, "p": PACKAGE_REL_NS}
CELL_REFERENCE = re.compile(r"^([A-Za-z]+)(\d+)$")
DUPLICATE_SUFFIX = re.compile(r"\s*\(\d+\)\s*$")


class AuditError(RuntimeError):
    """Raised when an Office Open XML source cannot be audited safely."""


@dataclass(frozen=True)
class SheetData:
    """Materialized values and metadata for one worksheet."""

    name: str
    rows: dict[int, dict[int, str]]


def column_index(reference: str) -> int:
    """Convert an Excel cell reference to a one-based column index."""
    match = CELL_REFERENCE.match(reference)
    if not match:
        raise AuditError(f"Invalid cell reference: {reference!r}")
    result = 0
    for character in match.group(1).upper():
        result = result * 26 + ord(character) - ord("A") + 1
    return result


def column_letter(index: int) -> str:
    """Convert a one-based column index to an Excel column label."""
    if index < 1:
        raise ValueError("Column index must be positive")
    letters: list[str] = []
    while index:
        index, remainder = divmod(index - 1, 26)
        letters.append(chr(ord("A") + remainder))
    return "".join(reversed(letters))


def text_content(element: ET.Element | None) -> str:
    """Join OOXML text runs without normalizing Unicode or accents."""
    if element is None:
        return ""
    return "".join(node.text or "" for node in element.iter() if node.tag.endswith("}t"))


def compare_key(value: str) -> str:
    """Return a conservative key used only for duplicate detection."""
    return " ".join(unicodedata.normalize("NFC", value).split()).casefold()


def is_placeholder_8(value: str) -> bool:
    """Return whether a cell contains the numeric/string placeholder 8."""
    return value.strip() == "8"


def read_xml(archive: zipfile.ZipFile, member: str, source: Path) -> ET.Element:
    """Read and parse one XML member with contextual error reporting."""
    try:
        with archive.open(member) as stream:
            return ET.parse(stream).getroot()
    except KeyError as exc:
        raise AuditError(f"{source.name}: missing OOXML member {member}") from exc
    except ET.ParseError as exc:
        raise AuditError(f"{source.name}: invalid XML in {member}: {exc}") from exc


def shared_strings(archive: zipfile.ZipFile, source: Path) -> list[str]:
    """Load the optional XLSX shared-string table."""
    if "xl/sharedStrings.xml" not in archive.namelist():
        return []
    root = read_xml(archive, "xl/sharedStrings.xml", source)
    return [text_content(item) for item in root.findall("s:si", NS)]


def worksheet_targets(archive: zipfile.ZipFile, source: Path) -> list[tuple[str, str]]:
    """Map workbook sheet names to their ZIP member targets."""
    workbook = read_xml(archive, "xl/workbook.xml", source)
    relations = read_xml(archive, "xl/_rels/workbook.xml.rels", source)
    targets = {
        relation.attrib["Id"]: relation.attrib.get("Target", "")
        for relation in relations.findall("p:Relationship", NS)
    }
    result: list[tuple[str, str]] = []
    sheets = workbook.find("s:sheets", NS)
    if sheets is None:
        return result
    for sheet in sheets.findall("s:sheet", NS):
        name = sheet.attrib.get("name", "")
        relation_id = sheet.attrib.get(f"{{{REL_NS}}}id", "")
        target = targets.get(relation_id)
        if not target:
            raise AuditError(f"{source.name}: no target for worksheet {name!r}")
        clean_target = target.replace("\\", "/").lstrip("/")
        path = (
            PurePosixPath(clean_target)
            if clean_target.startswith("xl/")
            else PurePosixPath("xl") / PurePosixPath(clean_target)
        )
        normalized_parts: list[str] = []
        for part in path.parts:
            if part == "..":
                if normalized_parts:
                    normalized_parts.pop()
            elif part not in ("", "."):
                normalized_parts.append(part)
        result.append((name, "/".join(normalized_parts)))
    return result


def cell_value(cell: ET.Element, strings: list[str], source: Path) -> str:
    """Extract the cached value of a worksheet cell as text."""
    value_type = cell.attrib.get("t")
    if value_type == "inlineStr":
        return text_content(cell.find("s:is", NS))
    value = cell.findtext("s:v", default="", namespaces=NS)
    if value_type == "s" and value:
        try:
            return strings[int(value)]
        except (ValueError, IndexError) as exc:
            reference = cell.attrib.get("r", "?")
            raise AuditError(
                f"{source.name}: invalid shared-string index {value!r} at {reference}"
            ) from exc
    if value_type == "b":
        return "TRUE" if value == "1" else "FALSE"
    return value


def read_sheet(
    archive: zipfile.ZipFile,
    source: Path,
    name: str,
    target: str,
    strings: list[str],
) -> SheetData:
    """Read non-empty cached cell values from one worksheet."""
    root = read_xml(archive, target, source)
    rows: dict[int, dict[int, str]] = {}
    sheet_data = root.find("s:sheetData", NS)
    if sheet_data is None:
        return SheetData(name=name, rows=rows)
    fallback_row = 0
    for row in sheet_data.findall("s:row", NS):
        fallback_row += 1
        try:
            row_index = int(row.attrib.get("r", fallback_row))
        except ValueError:
            row_index = fallback_row
        values: dict[int, str] = {}
        fallback_column = 0
        for cell in row.findall("s:c", NS):
            fallback_column += 1
            reference = cell.attrib.get("r")
            index = column_index(reference) if reference else fallback_column
            value = cell_value(cell, strings, source)
            if value != "":
                values[index] = value
        if values:
            rows[row_index] = values
    return SheetData(name=name, rows=rows)


def sheet_fingerprint(sheet: SheetData) -> str:
    """Hash normalized coordinates and values for exact duplicate detection."""
    digest = hashlib.sha256()
    for row_index, row in sorted(sheet.rows.items()):
        for col_index, value in sorted(row.items()):
            token = f"{row_index}:{col_index}:{compare_key(value)}\n"
            digest.update(token.encode("utf-8"))
    return digest.hexdigest()


def analyze_sheet(sheet: SheetData) -> tuple[dict[str, Any], dict[str, Any]]:
    """Build user-facing metrics and internal duplicate-detection metadata."""
    max_row = max(sheet.rows, default=0)
    max_column = max((max(row, default=0) for row in sheet.rows.values()), default=0)
    header_row = min(sheet.rows, default=None)
    header_values = sheet.rows.get(header_row, {}) if header_row is not None else {}
    headers = [header_values.get(index) for index in range(1, max_column + 1)]

    normalized_headers: defaultdict[str, list[int]] = defaultdict(list)
    for index, header in enumerate(headers, start=1):
        if header is not None and header.strip():
            normalized_headers[compare_key(header)].append(index)
    duplicate_headers = [
        {
            "header": headers[indices[0] - 1],
            "columns": [column_letter(index) for index in indices],
        }
        for indices in normalized_headers.values()
        if len(indices) > 1
    ]
    empty_header_columns = [
        column_letter(index)
        for index, header in enumerate(headers, start=1)
        if header is None or not header.strip()
    ]

    column_stats: list[dict[str, Any]] = []
    total_placeholders = 0
    data_rows = {
        row_index: values
        for row_index, values in sheet.rows.items()
        if row_index != header_row
    }
    for index in range(1, max_column + 1):
        values = [row[index] for row in data_rows.values() if index in row and row[index] != ""]
        placeholder_count = sum(is_placeholder_8(value) for value in values)
        total_placeholders += placeholder_count
        header = headers[index - 1] if headers else None
        column_stats.append(
            {
                "column": column_letter(index),
                "column_index": index,
                "header": header,
                "ignored_due_to_empty_header": header is None or not header.strip(),
                "non_empty_count": len(values),
                "unique_count": len({compare_key(value) for value in values}),
                "placeholder_8_count": placeholder_count,
            }
        )

    issues: list[dict[str, Any]] = []
    if empty_header_columns:
        issues.append(
            {
                "type": "empty_headers",
                "columns": empty_header_columns,
                "message": "Empty-header columns must be ignored by later imports.",
            }
        )
    if duplicate_headers:
        issues.append(
            {
                "type": "duplicate_headers",
                "items": duplicate_headers,
                "message": "Duplicate headers require explicit mapping before import.",
            }
        )
    if total_placeholders:
        issues.append(
            {
                "type": "placeholder_8",
                "count": total_placeholders,
                "message": "String or numeric 8 values must be converted to null during import.",
            }
        )

    public = {
        "name": sheet.name,
        "row_count": max_row,
        "non_empty_row_count": len(sheet.rows),
        "column_count": max_column,
        "header_row": header_row,
        "headers": headers,
        "empty_header_columns": empty_header_columns,
        "duplicate_headers": duplicate_headers,
        "column_stats": column_stats,
        "placeholder_8_count": total_placeholders,
        "issues": issues,
    }
    internal = {
        "fingerprint": sheet_fingerprint(sheet),
        "header_signature": tuple(compare_key(header or "") for header in headers),
        "base_name": DUPLICATE_SUFFIX.sub("", compare_key(sheet.name)),
        "cell_count": sum(len(row) for row in sheet.rows.values()),
    }
    return public, internal


def audit_xlsx(path: Path) -> tuple[dict[str, Any], list[dict[str, Any]]]:
    """Audit one XLSX file and return its report plus sheet metadata."""
    try:
        with zipfile.ZipFile(path) as archive:
            strings = shared_strings(archive, path)
            public_sheets: list[dict[str, Any]] = []
            duplicate_metadata: list[dict[str, Any]] = []
            for name, target in worksheet_targets(archive, path):
                sheet = read_sheet(archive, path, name, target, strings)
                public, internal = analyze_sheet(sheet)
                public_sheets.append(public)
                duplicate_metadata.append(
                    {"file": path.name, "sheet": name, **internal}
                )
            return (
                {
                    "file": path.name,
                    "size_bytes": path.stat().st_size,
                    "sheet_count": len(public_sheets),
                    "sheets": public_sheets,
                },
                duplicate_metadata,
            )
    except (OSError, zipfile.BadZipFile, AuditError) as exc:
        raise AuditError(f"Could not audit {path.name}: {exc}") from exc


def audit_docx(path: Path) -> dict[str, Any]:
    """Verify one DOCX container and count document paragraphs and tables."""
    try:
        with zipfile.ZipFile(path) as archive:
            document = read_xml(archive, "word/document.xml", path)
            return {
                "file": path.name,
                "size_bytes": path.stat().st_size,
                "paragraph_count": len(document.findall(f".//{{{DOCUMENT_NS}}}p")),
                "table_count": len(document.findall(f".//{{{DOCUMENT_NS}}}tbl")),
            }
    except (OSError, zipfile.BadZipFile, AuditError) as exc:
        raise AuditError(f"Could not audit {path.name}: {exc}") from exc


def find_duplicate_sheets(items: list[dict[str, Any]]) -> list[dict[str, Any]]:
    """Find exact-content and name/header-based suspected duplicate sheets."""
    pairs: list[dict[str, Any]] = []
    for left_index, left in enumerate(items):
        for right in items[left_index + 1 :]:
            reasons: list[str] = []
            if left["cell_count"] and left["fingerprint"] == right["fingerprint"]:
                reasons.append("identical_normalized_content")
            same_base = left["base_name"] == right["base_name"]
            same_headers = (
                bool(left["header_signature"])
                and left["header_signature"] == right["header_signature"]
            )
            if same_base and same_headers:
                reasons.append("same_base_name_and_headers")
            if reasons:
                pairs.append(
                    {
                        "left": {"file": left["file"], "sheet": left["sheet"]},
                        "right": {"file": right["file"], "sheet": right["sheet"]},
                        "reasons": reasons,
                    }
                )
    return pairs


def source_files(source_dir: Path, suffix: str) -> Iterable[Path]:
    """Yield non-temporary source files deterministically (non-recursive)."""
    return sorted(
        (
            path
            for path in source_dir.iterdir()
            if path.is_file()
            and path.suffix.casefold() == suffix.casefold()
            and not path.name.startswith("~$")
        ),
        key=lambda path: path.name.casefold(),
    )


def build_report(source_dir: Path) -> tuple[dict[str, Any], bool]:
    """Audit every supported source and return the report and error flag."""
    xlsx_reports: list[dict[str, Any]] = []
    docx_reports: list[dict[str, Any]] = []
    sheet_metadata: list[dict[str, Any]] = []
    errors: list[dict[str, str]] = []

    for path in source_files(source_dir, ".xlsx"):
        try:
            report, metadata = audit_xlsx(path)
            xlsx_reports.append(report)
            sheet_metadata.extend(metadata)
        except AuditError as exc:
            errors.append({"file": path.name, "error": str(exc)})

    for path in source_files(source_dir, ".docx"):
        try:
            docx_reports.append(audit_docx(path))
        except AuditError as exc:
            errors.append({"file": path.name, "error": str(exc)})

    duplicates = find_duplicate_sheets(sheet_metadata)
    issue_summary = Counter(
        issue["type"]
        for workbook in xlsx_reports
        for sheet in workbook["sheets"]
        for issue in sheet["issues"]
    )
    if duplicates:
        issue_summary["suspected_duplicate_sheets"] = len(duplicates)
    if errors:
        issue_summary["unreadable_sources"] = len(errors)

    notices: list[str] = []
    if not xlsx_reports and not docx_reports and not errors:
        notices.append("No XLSX or DOCX files were found in data-source.")

    report: dict[str, Any] = {
        "schema_version": 1,
        "source_directory": source_dir.name,
        "xlsx_file_count": len(xlsx_reports),
        "docx_file_count": len(docx_reports),
        "xlsx_files": xlsx_reports,
        "docx_files": docx_reports,
        "suspected_duplicate_sheets": duplicates,
        "issue_summary": dict(sorted(issue_summary.items())),
        "errors": errors,
        "notices": notices,
    }
    return report, bool(errors)


def parse_args(argv: list[str] | None = None) -> argparse.Namespace:
    """Parse command-line arguments."""
    project_root = Path(__file__).resolve().parents[2]
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--source-dir",
        type=Path,
        default=project_root / "data-source",
        help="Directory containing XLSX/DOCX sources.",
    )
    parser.add_argument(
        "--output",
        type=Path,
        default=project_root / "data_audit_report.json",
        help="JSON report path.",
    )
    return parser.parse_args(argv)


def main(argv: list[str] | None = None) -> int:
    """Run the source audit and write UTF-8 JSON."""
    args = parse_args(argv)
    source_dir: Path = args.source_dir
    output: Path = args.output
    try:
        source_dir.mkdir(parents=True, exist_ok=True)
        report, has_errors = build_report(source_dir)
        output.parent.mkdir(parents=True, exist_ok=True)
        output.write_text(
            json.dumps(report, ensure_ascii=False, indent=2) + "\n",
            encoding="utf-8",
        )
    except OSError as exc:
        print(f"Audit failed: {exc}", file=sys.stderr)
        return 2

    print(
        f"Audited {report['xlsx_file_count']} XLSX and "
        f"{report['docx_file_count']} DOCX file(s)."
    )
    print(f"Report: {output}")
    return 1 if has_errors else 0


if __name__ == "__main__":
    raise SystemExit(main())
