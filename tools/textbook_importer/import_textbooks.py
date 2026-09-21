#!/usr/bin/env python3
"""Build structured textbook assets from Markdown and reviewed external knowledge JSON."""
from __future__ import annotations

import argparse
import json
import re
from pathlib import Path

LESSON_RE = re.compile(r"^#{2,3}\s+(?:Урок|УРОК)\s+(\d+)\b", re.IGNORECASE)
TITLE_RE = re.compile(r"^(?:#{3,4}\s+|\*\*)(.+?)(?:\*\*)?$")
SENTENCE_BOUNDARY_RE = re.compile(r"(?<=[.!?…])\s+")
KNOWLEDGE_TYPES = {"WORD", "PHRASE", "GRAMMAR", "PATTERN", "PRONUNCIATION", "AI_NOTE"}


def split_blocks(lines: list[str]) -> list[str]:
    blocks, current = [], []
    for line in lines:
        if line.strip():
            current.append(line.rstrip())
        elif current:
            blocks.append("\n".join(current).strip()); current = []
    if current:
        blocks.append("\n".join(current).strip())
    return [block for block in blocks if not block.startswith("---")]


def block_type(text: str, is_letter: bool) -> str:
    if is_letter:
        return "LETTER"
    return "DIALOGUE" if all(line.lstrip().startswith("—") for line in text.splitlines()) else "READING"


def split_sentences(text: str) -> list[str]:
    sentences = []
    for line in text.splitlines():
        sentences.extend(part.strip() for part in SENTENCE_BOUNDARY_RE.split(line.strip()) if part.strip())
    return sentences or [text]


def parse_book(path: Path, textbook_id: str, title: str, level: str):
    lines = path.read_text(encoding="utf-8-sig").splitlines()
    starts = [(i, int(m.group(1))) for i, line in enumerate(lines) if (m := LESSON_RE.match(line))]
    if not starts:
        raise ValueError(f"No lesson headers found in {path}")
    textbook = {"id": textbook_id, "title": title, "language": "ru", "level": level, "sourceVersion": 2}
    lessons, blocks, sentences = [], [], []
    for offset, (start, number) in enumerate(starts):
        end = starts[offset + 1][0] if offset + 1 < len(starts) else len(lines)
        raw, title_value, content_start = lines[start + 1:end], f"Урок {number}", 0
        for index, line in enumerate(raw):
            stripped = line.strip()
            if not stripped or stripped == "---":
                continue
            if match := TITLE_RE.match(stripped):
                candidate = match.group(1).strip()
                if candidate.lower() not in {"текст", "课文"}:
                    title_value = candidate
                content_start = index + 1
            break
        lesson_id = f"ur{level}-lesson-{number}"
        lessons.append({"id": lesson_id, "textbookId": textbook_id, "lessonNumber": number, "title": title_value})
        source_blocks = split_blocks(raw[content_start:])
        is_letter = bool(source_blocks and re.match(r"^(Дорога́я|Дорогой|Здра́вствуй|Здравствуй)", source_blocks[0]))
        for block_order, source_text in enumerate(source_blocks, start=1):
            block_id = f"{lesson_id}-b{block_order}"
            blocks.append({"id": block_id, "lessonId": lesson_id, "order": block_order, "type": block_type(source_text, is_letter), "title": title_value if block_order == 1 else None})
            for sentence_order, text in enumerate(split_sentences(source_text), start=1):
                sentences.append({"id": f"{block_id}-s{sentence_order}", "lessonId": lesson_id, "blockId": block_id, "order": sentence_order, "text": text, "sourceText": text})
    return textbook, lessons, blocks, sentences


def external_sentences(document: object):
    if isinstance(document, dict) and "lessons" in document:
        course_id = document.get("course", {}).get("id")
        records = []
        for lesson in document["lessons"]:
            for block in lesson.get("blocks", lesson.get("paragraphs", [])):
                for sentence in block.get("sentences", []):
                    records.append((course_id, lesson, block, sentence))
        return records
    rows = document if isinstance(document, list) else [document]
    return [(None, None, None, row) for row in rows if isinstance(row, dict)]


def apply_external_knowledge(path: Path, lessons: list[dict], blocks: list[dict], sentences: list[dict], knowledge: list[dict]) -> None:
    document = json.loads(path.read_text(encoding="utf-8-sig"))
    sentence_by_id = {item["id"]: item for item in sentences}
    lesson_by_id = {item["id"]: item for item in lessons}
    for course_id, lesson, block, source in external_sentences(document):
        sentence_id = source["id"]
        if lesson is not None and block is not None:
            lesson_id, block_id = lesson["id"], block["id"]
            if lesson_id not in lesson_by_id:
                continue
            existing_block = next((item for item in blocks if item["id"] == block_id), None)
            if existing_block is None:
                blocks.append({"id": block_id, "lessonId": lesson_id, "order": block.get("order", len(blocks) + 1), "type": block_type(block["sourceText"], False), "title": None})
            sentence_by_id[sentence_id] = {
                "id": sentence_id, "lessonId": lesson_id, "blockId": block_id, "order": source.get("order", 1),
                "text": source.get("text", source["sourceText"]), "sourceText": source["sourceText"],
            }
        if sentence_id not in sentence_by_id:
            raise ValueError(f"Knowledge sentence {sentence_id} does not exist in Markdown or project structure")
        target = sentence_by_id[sentence_id]
        for index, annotation in enumerate(source.get("annotations", []), start=1):
            kind = annotation["type"].upper()
            if kind not in KNOWLEDGE_TYPES:
                raise ValueError(f"Unsupported knowledge type {kind}")
            start, end = int(annotation.get("start", 0)), int(annotation.get("end", 0))
            if start < 0 or end < start or end > len(target["sourceText"]):
                raise ValueError(f"Invalid offsets for {sentence_id}: {start}..{end}")
            knowledge.append({
                "id": annotation.get("id", f"{sentence_id}-k{index}"), "sentenceId": sentence_id, "type": kind,
                "text": annotation.get("text", target["sourceText"][start:end]), "label": annotation.get("label"),
                "explanation": annotation.get("explanation"), "example": annotation.get("example"),
                "start": start, "end": end, "status": annotation.get("status"),
                "knowledgeVersion": annotation.get("knowledgeVersion", annotation.get("version", 1)),
                "generatedBy": annotation.get("generatedBy", annotation.get("source")),
                "reviewStatus": annotation.get("reviewStatus", annotation.get("status")),
            })
    if isinstance(document, dict) and "lessons" in document:
        lesson_ids = {lesson["id"] for _, lesson, _, _ in external_sentences(document) if lesson}
        blocks[:] = [item for item in blocks if item["lessonId"] not in lesson_ids or any(item["id"] == block["id"] for _, lesson, block, _ in external_sentences(document) if lesson and lesson["id"] == item["lessonId"])]
        sentences[:] = [item for item in sentence_by_id.values() if item["lessonId"] not in lesson_ids or any(item["id"] == source["id"] for _, lesson, _, source in external_sentences(document) if lesson and lesson["id"] == item["lessonId"])]


def build_assets(book1: Path, book2: Path, knowledge_paths: list[Path]):
    specs = [(book1, "university-russian-1", "大学俄语 1", "1"), (book2, "university-russian-2", "大学俄语 2", "2")]
    textbooks, lessons, blocks, sentences, knowledge = [], [], [], [], []
    for spec in specs:
        book, book_lessons, book_blocks, book_sentences = parse_book(*spec)
        textbooks.append(book); lessons.extend(book_lessons); blocks.extend(book_blocks); sentences.extend(book_sentences)
    for path in knowledge_paths:
        apply_external_knowledge(path, lessons, blocks, sentences, knowledge)
    return textbooks, lessons, blocks, sentences, knowledge


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--book1", type=Path, required=True); parser.add_argument("--book2", type=Path, required=True)
    parser.add_argument("--knowledge", type=Path, action="append", default=[]); parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    assets = build_assets(args.book1, args.book2, args.knowledge)
    if [sum(1 for item in assets[1] if item["textbookId"] == book_id) for book_id in ("university-russian-1", "university-russian-2")] != [18, 12]:
        raise ValueError("Unexpected lesson counts; textbook source format may have changed")
    args.output.mkdir(parents=True, exist_ok=True)
    for name, data in zip(("textbooks.json", "lessons.json", "blocks.json", "sentences.json", "knowledge.json"), assets):
        (args.output / name).write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"Imported {len(assets[0])} textbooks, {len(assets[1])} lessons, {len(assets[2])} blocks, {len(assets[3])} sentences, {len(assets[4])} knowledge nodes.")


if __name__ == "__main__":
    main()
