#!/usr/bin/env python3
"""Convert verified Russian textbook Markdown into normalized JSON reading assets.

The importer intentionally does not infer translations, vocabulary, grammar, audio, or speakers.
"""
from __future__ import annotations

import argparse
import json
import re
from pathlib import Path

LESSON_RE = re.compile(r"^#{2,3}\s+(?:Урок|УРОК)\s+(\d+)\b", re.IGNORECASE)
TITLE_RE = re.compile(r"^(?:#{3,4}\s+|\*\*)(.+?)(?:\*\*)?$")


def slug(value: str) -> str:
    return re.sub(r"[^a-z0-9]+", "-", value.lower()).strip("-")


def split_paragraphs(lines: list[str]) -> list[str]:
    blocks, current = [], []
    for line in lines:
        if line.strip():
            current.append(line.rstrip())
        elif current:
            blocks.append("\n".join(current).strip())
            current = []
    if current:
        blocks.append("\n".join(current).strip())
    return [block for block in blocks if not block.startswith("---")]


def parse_book(path: Path, textbook_id: str, title: str, level: str) -> tuple[dict, list[dict], list[dict], list[dict]]:
    lines = path.read_text(encoding="utf-8-sig").splitlines()
    starts = [(index, int(match.group(1))) for index, line in enumerate(lines) if (match := LESSON_RE.match(line))]
    if not starts:
        raise ValueError(f"No lesson headers found in {path}")
    textbook = {"id": textbook_id, "title": title, "language": "ru", "level": level, "sourceVersion": 1}
    lessons, sections, paragraphs = [], [], []
    for offset, (start, number) in enumerate(starts):
        end = starts[offset + 1][0] if offset + 1 < len(starts) else len(lines)
        raw = lines[start + 1:end]
        title_value = f"Урок {number}"
        content_start = 0
        for index, line in enumerate(raw):
            stripped = line.strip()
            if not stripped or stripped == "---":
                continue
            title_match = TITLE_RE.match(stripped)
            if title_match:
                candidate = title_match.group(1).strip()
                if candidate.lower() not in {"текст", "课文"}:
                    title_value = candidate
                content_start = index + 1
            break
        lesson_id = f"{textbook_id}-lesson-{number}"
        lessons.append({"id": lesson_id, "textbookId": textbook_id, "lessonNumber": number, "title": title_value})
        blocks = split_paragraphs(raw[content_start:])
        is_letter = bool(blocks and re.match(r"^(Дорога́я|Дорогой|Здра́вствуй|Здравствуй)", blocks[0]))
        runs: list[tuple[str, list[str]]] = []
        for block in blocks:
            block_type = "DIALOGUE" if all(line.lstrip().startswith("—") for line in block.splitlines()) else "READING"
            if is_letter:
                block_type = "LETTER"
            if runs and runs[-1][0] == block_type:
                runs[-1][1].append(block)
            else:
                runs.append((block_type, [block]))
        for section_order, (section_type, section_blocks) in enumerate(runs, start=1):
            section_id = f"{lesson_id}-section-{section_order}"
            sections.append({"id": section_id, "lessonId": lesson_id, "order": section_order, "type": section_type, "title": title_value if section_order == 1 else None})
            for paragraph_order, content in enumerate(section_blocks, start=1):
                paragraphs.append({"id": f"{section_id}-paragraph-{paragraph_order}", "sectionId": section_id, "order": paragraph_order, "content": content})
    return textbook, lessons, sections, paragraphs


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--book1", type=Path, required=True)
    parser.add_argument("--book2", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    book_specs = [
        (args.book1, "university-russian-1", "大学俄语 1", "1"),
        (args.book2, "university-russian-2", "大学俄语 2", "2"),
    ]
    textbooks, lessons, sections, paragraphs = [], [], [], []
    for spec in book_specs:
        book, book_lessons, book_sections, book_paragraphs = parse_book(*spec)
        textbooks.append(book); lessons.extend(book_lessons); sections.extend(book_sections); paragraphs.extend(book_paragraphs)
    if [len([lesson for lesson in lessons if lesson["textbookId"] == book_id]) for _, book_id, _, _ in book_specs] != [18, 12]:
        raise ValueError("Unexpected lesson counts; textbook source format may have changed")
    args.output.mkdir(parents=True, exist_ok=True)
    for name, data in (("textbooks.json", textbooks), ("lessons.json", lessons), ("sections.json", sections), ("paragraphs.json", paragraphs)):
        (args.output / name).write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"Imported {len(textbooks)} textbooks, {len(lessons)} lessons, {len(sections)} sections, {len(paragraphs)} paragraphs.")


if __name__ == "__main__":
    main()
