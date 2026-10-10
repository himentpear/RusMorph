#!/usr/bin/env python3
"""Package existing validated Android JSON assets as immutable cloud objects."""
from __future__ import annotations

import argparse
import datetime as dt
import hashlib
import json
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
ASSETS = ROOT / "app/src/main/assets/database"
OUTPUT = ROOT / "build/cloud-content"
COURSE_ASSETS = ROOT / "app/build/generated/course-assets/database"
API_ORIGIN = "https://api.namchieh.org"
PRIVATE_DATASET_FILES = {"manual_review.json", "exercise_answers.json"}


def sha256(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def write_immutable(path: Path, data: bytes) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    if path.exists():
        if path.read_bytes() != data:
            raise ValueError(f"immutable output differs: {path}")
        return
    path.write_bytes(data)


def validate_assets(assets: Path, parsed: dict[str, object]) -> None:
    for name in ("textbook_dialogues.json", "textbook_texts.json"):
        if not isinstance(parsed[name], list) or not parsed[name]:
            raise ValueError(f"{name} must be a nonempty generated array")
    for name, field in (("lexicon.json", "id"), ("grammar_points.json", "pointId"),
                        ("tem4_questions.json", "questionId")):
        rows = parsed[name]
        if not isinstance(rows, list) or not rows:
            raise ValueError(f"{name} must be a nonempty array")
        ids = [row[field] for row in rows]
        if len(ids) != len(set(ids)) or any(not value for value in ids):
            raise ValueError(f"{name} has missing or duplicate {field}")
    asset_manifest = parsed.get("data_manifest.json", {})
    if asset_manifest.get("counts", {}).get("entries") != len(parsed["lexicon.json"]):
        raise ValueError("data_manifest.json entry count differs from lexicon.json; refresh verified asset metadata")
    for name, expected in asset_manifest.get("assetHashes", {}).items():
        if sha256((assets / name).read_bytes()) != expected:
            raise ValueError(f"data_manifest.json hash differs for {name}")
    grammar_ids = {row["pointId"] for row in parsed["grammar_points.json"]}
    question_ids = {row["questionId"] for row in parsed["tem4_questions.json"]}
    for link in parsed["grammar_question_links.json"]:
        if link["pointId"] not in grammar_ids or link["questionId"] not in question_ids:
            raise ValueError("grammar question link references missing content")
    books = parsed["wordbooks.json"]["books"]
    book_ids = [item["book"]["id"] for item in books]
    if len(book_ids) != len(set(book_ids)):
        raise ValueError("duplicate wordbook ID")
    validator = ROOT / "tools/textbook_dataset/validate_dataset.py"
    textbook_dir = assets / "textbook"
    if textbook_dir.is_dir():
        for dataset in sorted(textbook_dir.iterdir()):
            if dataset.is_dir() and (dataset / "dataset_manifest.json").is_file():
                result = subprocess.run([sys.executable, str(validator), str(dataset)],
                                        capture_output=True, text=True, encoding="utf-8")
                if result.returncode:
                    raise ValueError(f"textbook validation failed for {dataset.name}: {result.stdout[-1200:]} {result.stderr[-500:]}")


def lesson_vocabulary(wordbooks: dict[str, object], parsed: dict[str, object]) -> list[dict[str, object]]:
    """Resolve each wordbook's stable entry links to the canonical textbook lesson IDs."""
    lexicon_ids = {row["id"] for row in parsed["lexicon.json"]}
    result: list[dict[str, object]] = []
    seen_lesson_ids: set[str] = set()
    for book in wordbooks.get("books", []):
        book_id = book["book"]["id"]
        volume_match = __import__("re").fullmatch(r"university-russian-(\d+)", book_id)
        if not volume_match:
            continue
        volume = int(volume_match.group(1))
        textbook_lessons = parsed.get(f"textbook/university_russian_{volume}/lessons.json", [])
        textbook_ids_by_number = {int(row.get("lessonNumber", row.get("number", 0))): row["id"]
                                  for row in textbook_lessons}
        for source_lesson in book.get("lessons", []):
            number = int(source_lesson["number"])
            lesson_id = textbook_ids_by_number.get(number, source_lesson["id"])
            if lesson_id in seen_lesson_ids:
                raise ValueError(f"duplicate lesson vocabulary mapping: {lesson_id}")
            words = []
            for word in source_lesson.get("words", []):
                entry_id = word.get("entryId")
                if entry_id not in lexicon_ids:
                    raise ValueError(f"lesson {lesson_id} references missing lexicon entry {entry_id}")
                words.append({
                    "entryId": entry_id,
                    "position": int(word.get("order", len(words) + 1)),
                    "isKey": bool(word.get("isKey", False)),
                })
            words.sort(key=lambda word: word["position"])
            result.append({
                "id": lesson_id,
                "lessonId": lesson_id,
                "sourceLessonId": source_lesson["id"],
                "bookId": book_id,
                "lessonNumber": number,
                "words": words,
            })
            seen_lesson_ids.add(lesson_id)
    return result


def build(assets: Path = ASSETS, output: Path = OUTPUT, date: str | None = None,
          course_assets: Path = COURSE_ASSETS) -> dict:
    date = date or dt.datetime.now(dt.timezone.utc).strftime("%Y.%m.%d")
    if not __import__("re").fullmatch(r"\d{4}\.\d{2}\.\d{2}", date):
        raise ValueError("date must be YYYY.MM.DD")
    files = sorted(assets.rglob("*.json"))
    generated = [course_assets / name for name in ("textbook_dialogues.json", "textbook_texts.json")]
    if any(not path.is_file() for path in generated):
        raise ValueError("run ./gradlew generateCourseAssets before packaging cloud content")
    if not files:
        raise ValueError("no normalized content assets found")
    data: dict[str, bytes] = {}
    parsed: dict[str, object] = {}
    for path in files:
        relative = path.relative_to(assets).as_posix()
        raw = path.read_bytes()
        parsed[relative] = json.loads(raw)
        if path.name not in PRIVATE_DATASET_FILES:
            data[relative] = raw
    for path in generated:
        raw = path.read_bytes()
        data[path.name] = raw
        parsed[path.name] = json.loads(raw)
    vocabulary = lesson_vocabulary(parsed["wordbooks.json"], parsed)
    vocabulary_raw = (json.dumps(vocabulary, ensure_ascii=False, sort_keys=True, indent=2) + "\n").encode("utf-8")
    data["lesson_vocabulary.json"] = vocabulary_raw
    parsed["lesson_vocabulary.json"] = vocabulary
    validate_assets(assets, parsed)
    identity = "\n".join(f"{name}\t{sha256(raw)}" for name, raw in data.items()).encode()
    version = f"{date}.{sha256(identity)[:12]}"
    version_dir = output / version
    components = []
    for name, raw in data.items():
        key = f"content/{version}/files/{name}"
        write_immutable(version_dir / "files" / name, raw)
        document = parsed[name]
        schema = document.get("schemaVersion", 1) if isinstance(document, dict) else 1
        components.append({
            "id": name.removesuffix(".json"),
            "version": version,
            "schemaVersion": schema,
            "sha256": sha256(raw),
            "size": len(raw),
            "url": f"{API_ORIGIN}/api/v1/content/files/{version}/{name}",
            "key": key,
            "required": name in {"lexicon.json", "wordbooks.json", "lesson_vocabulary.json", "grammar_points.json", "tem4_questions.json"},
        })
    manifest = {
        "schemaVersion": 1,
        "catalogVersion": version,
        "generatedAt": date.replace(".", "-") + "T00:00:00Z",
        "minimumAppVersion": "0.008",
        "minimumSchemaVersion": 7,
        "components": components,
    }
    manifest_raw = (json.dumps(manifest, ensure_ascii=False, sort_keys=True, indent=2) + "\n").encode("utf-8")
    write_immutable(version_dir / "manifest.json", manifest_raw)
    pointer = {"schemaVersion": 1, "catalogVersion": version,
               "manifestKey": f"content/{version}/manifest.json"}
    (output / "current.json").parent.mkdir(parents=True, exist_ok=True)
    (output / "current.json").write_text(json.dumps(pointer, sort_keys=True) + "\n", encoding="utf-8")
    return manifest


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--assets", type=Path, default=ASSETS)
    parser.add_argument("--output", type=Path, default=OUTPUT)
    parser.add_argument("--date")
    parser.add_argument("--course-assets", type=Path, default=COURSE_ASSETS)
    args = parser.parse_args()
    try:
        result = build(args.assets, args.output, args.date, args.course_assets)
        print(f"Built {result['catalogVersion']} with {len(result['components'])} verified components")
    except (ValueError, OSError, KeyError, json.JSONDecodeError) as exc:
        print(f"Cloud content build failed: {exc}", file=sys.stderr)
        raise SystemExit(1)
