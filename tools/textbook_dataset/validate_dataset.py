#!/usr/bin/env python3
"""Validate and version a RusMorph textbook dataset asset directory."""
from __future__ import annotations

import argparse
import hashlib
import json
import re
import sys
from pathlib import Path

FILES = {
    "textbooks": "textbooks.json", "lessons": "lessons.json",
    "pages": "textbook_pages.json", "sections": "sections.json",
    "paragraphs": "paragraphs.json", "sentences": "sentences.json",
    "translations": "translations.json", "speakers": "speakers.json",
    "exercises": "exercises.json", "exercise_options": "exercise_options.json",
    "exercise_answers": "exercise_answers.json", "tokens": "textbook_tokens.json",
    "lexicon_links": "textbook_lexicon_links.json",
    "grammar_mentions": "grammar_mentions.json", "grammar_links": "textbook_grammar_links.json",
    "media": "textbook_media.json", "manual_review": "manual_review.json",
}
ID_FIELDS = {"textbooks":"id", "lessons":"id", "pages":"id", "sections":"id", "paragraphs":"id",
             "sentences":"id", "translations":"id", "speakers":"id", "exercises":"id",
             "exercise_options":"id", "exercise_answers":"id", "tokens":"id", "lexicon_links":"id",
             "grammar_mentions":"id", "grammar_links":"id", "media":"id", "manual_review":"id"}
TYPE_FIELDS = {
    "pages": ("pageType", {"COVER", "TOC", "LESSON", "CONTENT", "VOCABULARY", "REFERENCE", "UNKNOWN"}),
    "sections": ("type", {"DIALOGUE", "READING", "EXERCISE", "GRAMMAR", "VOCABULARY", "EXPLANATION", "CULTURE", "REFERENCE", "OTHER", "UNKNOWN"}),
    "paragraphs": ("type", {"TEXT", "QUOTE", "DIALOGUE", "TITLE", "NOTE", "CAPTION", "UNKNOWN"}),
    "exercises": ("type", {"MULTIPLE_CHOICE", "FILL_BLANK", "TRANSFORM", "TRANSLATE", "MATCH", "SHORT_ANSWER", "READING_COMPREHENSION", "WORD_FORM", "CONJUGATION", "DECLENSION", "WORD_ORDER", "LISTENING", "OTHER", "UNKNOWN"}),
}

def load(root: Path):
    data = {}
    for key, filename in FILES.items():
        path = root / filename
        if not path.is_file():
            raise ValueError(f"missing dataset file: {filename}")
        value = json.loads(path.read_text(encoding="utf-8-sig"))
        if not isinstance(value, list):
            raise ValueError(f"{filename} must contain a JSON array")
        data[key] = value
    return data

def validate(root: Path, update_manifest: bool):
    d = load(root)
    errors, warnings, duplicate_ids = [], [], []
    by_id = {}
    for key, rows in d.items():
        ids = [row.get(ID_FIELDS[key]) for row in rows]
        seen, repeated = set(), set()
        for value in ids:
            if not value:
                errors.append(f"{FILES[key]} contains a missing id")
            elif value in seen:
                repeated.add(value)
            seen.add(value)
        if repeated:
            duplicate_ids.extend(f"{key}:{value}" for value in sorted(repeated))
            errors.append(f"{FILES[key]} has {len(repeated)} duplicate IDs")
        by_id[key] = {row.get("id"): row for row in rows if row.get("id")}

    relations = [
        ("lessons","textbookId","textbooks"),("pages","textbookId","textbooks"),
        ("pages","lessonId","lessons"),("sections","textbookId","textbooks"),
        ("sections","lessonId","lessons"),("paragraphs","sectionId","sections"),
        ("paragraphs","lessonId","lessons"),("paragraphs","pageId","pages"),
        ("paragraphs","speakerId","speakers"),("sentences","paragraphId","paragraphs"),
        ("sentences","lessonId","lessons"),("exercises","lessonId","lessons"),
        ("exercises","sectionId","sections"),("exercises","pageId","pages"),
        ("exercise_options","exerciseId","exercises"),("exercise_answers","exerciseId","exercises"),
        ("tokens","sentenceId","sentences"),("lexicon_links","tokenId","tokens"),
        ("grammar_mentions","sectionId","sections"),("grammar_mentions","lessonId","lessons"),
        ("grammar_mentions","pageId","pages"),("grammar_links","grammarMentionId","grammar_mentions"),
        ("media","pageId","pages"),
    ]
    broken_fk = []
    for child, field, parent in relations:
        for row in d[child]:
            value = row.get(field)
            if value is not None and value not in by_id[parent]:
                broken_fk.append(f"{child}.{field}={value} ({row.get('id')})")
    if broken_fk: errors.append(f"{len(broken_fk)} broken internal foreign keys")
    for key, parent_field, order_field in [("sections","lessonId","order"),("paragraphs","sectionId","order"),
                                           ("sentences","paragraphId","order"),("exercises","lessonId","order")]:
        seen_order = set()
        for row in d[key]:
            order = row.get(order_field)
            group = (row.get(parent_field), order)
            if order is not None and group in seen_order:
                errors.append(f"duplicate ordering value in {key}: {group}")
                break
            seen_order.add(group)

    if not d["textbooks"]: errors.append("dataset has no textbook row")
    if not d["lessons"]: errors.append("dataset has no lessons")
    if not d["sentences"]: errors.append("dataset has no sentences")

    targets = {"LESSON":"lessons", "SECTION":"sections", "PARAGRAPH":"paragraphs", "SENTENCE":"sentences", "EXERCISE":"exercises"}
    invalid_targets = [r for r in d["translations"] if r.get("targetType") not in targets or r.get("targetId") not in by_id.get(targets.get(r.get("targetType"), ""), {})]
    if invalid_targets: errors.append(f"{len(invalid_targets)} invalid translation targets")

    invalid_offsets = []
    sentence_rows = by_id["sentences"]
    for token in d["tokens"]:
        sentence = sentence_rows.get(token.get("sentenceId"), {})
        text = sentence.get("textRu", "")
        start, end = token.get("charStart"), token.get("charEnd")
        if not isinstance(start, int) or not isinstance(end, int) or start < 0 or end < start or end > len(text) or text[start:end] != token.get("surfaceForm"):
            invalid_offsets.append(token.get("id"))
    if invalid_offsets: warnings.append(f"{len(invalid_offsets)} token offsets do not match sentence text")

    manifest_path = root / "dataset_manifest.json"
    old_manifest = json.loads(manifest_path.read_text(encoding="utf-8-sig")) if manifest_path.exists() else {}
    counts = {filename: len(d[key]) for key, filename in FILES.items()}
    actual_hash = hashlib.sha256()
    for filename in sorted([*FILES.values()]):
        actual_hash.update(filename.encode()); actual_hash.update(b"\0"); actual_hash.update((root / filename).read_bytes())
    dataset_hash = actual_hash.hexdigest()
    mismatches = {name: {"declared": old_manifest.get("counts", {}).get(name), "actual": count}
                  for name, count in counts.items() if old_manifest.get("counts", {}).get(name) != count}
    metadata_mismatches = {}
    expected_textbook = d["textbooks"][0].get("id") if d["textbooks"] else None
    for key, expected in (("datasetId", "university_russian_2"), ("textbookId", expected_textbook), ("schemaVersion", 1), ("datasetCount", len(FILES))):
        if old_manifest.get(key) != expected:
            metadata_mismatches[key] = {"declared": old_manifest.get(key), "actual": expected}
    if metadata_mismatches and not update_manifest:
        errors.append("manifest identity or schema metadata mismatch")
    hash_mismatch = old_manifest.get("datasetHash") != dataset_hash
    if hash_mismatch and not update_manifest:
        errors.append("manifest datasetHash does not match JSON contents")

    lexicon_path = root.parents[1] / "lexicon.json"
    lexicon_ids = set()
    if lexicon_path.is_file():
        lexicon_ids = {row.get("id") for row in json.loads(lexicon_path.read_text(encoding="utf-8-sig"))}
    external_lexicon = [r for r in d["lexicon_links"] if r.get("entryId") not in lexicon_ids]
    grammar_path = root.parents[1] / "knowledge_chunks.json"
    knowledge_ids = {r.get("id") for r in json.loads(grammar_path.read_text(encoding="utf-8-sig"))} if grammar_path.is_file() else set()
    declension_path = root.parents[1] / "declension_rules.json"
    declension_data = json.loads(declension_path.read_text(encoding="utf-8-sig")) if declension_path.is_file() else {}
    declension_ids = {r.get("id") for r in declension_data.get("rules", [])} if isinstance(declension_data, dict) else {r.get("id") for r in declension_data}
    external_grammar = [r for r in d["grammar_links"] if (r.get("knowledgeChunkId") and r.get("knowledgeChunkId") not in knowledge_ids) or (r.get("declensionRuleId") and r.get("declensionRuleId") not in declension_ids)]
    grammar_resolved = sum(bool(r.get("knowledgeChunkId") or r.get("declensionRuleId")) for r in d["grammar_links"])
    bound_speakers = sum(bool(r.get("speakerId")) for r in d["paragraphs"])
    answer_ids = {r.get("exerciseId") for r in d["exercise_answers"]}
    media_placeholders = sum(r.get("type") == "ILLUSTRATION" and (not r.get("bbox") or (len(r["bbox"]) >= 4 and r["bbox"][0] == 0 and r["bbox"][1] == 0)) for r in d["media"])
    ocr_suspects = sum(bool(re.search(r"[@*\[\]]|[A-Za-z]{2,}.*[А-Яа-яЁё]|[А-Яа-яЁё].*[A-Za-z]{2,}", r.get("textRu", ""))) for r in d["paragraphs"])
    translations = d["translations"]
    caps = {
        "reading": "ENABLED" if d["sentences"] and d["paragraphs"] else "DISABLED",
        "lessonNavigation": "ENABLED" if d["lessons"] else "DISABLED",
        "sectionNavigation": "ENABLED" if d["sections"] else "DISABLED",
        "tokenInteraction": "ENABLED" if d["tokens"] else "DISABLED",
        "lexiconLinks": "PARTIAL" if d["lexicon_links"] else "DISABLED",
        "exercises": "ENABLED" if d["exercises"] else "DISABLED",
        "exerciseAnswers": "PARTIAL" if answer_ids and len(answer_ids) < len(d["exercises"]) else ("ENABLED" if answer_ids else "DISABLED"),
        "lessonTranslations": "ENABLED" if any(r.get("targetType") == "LESSON" for r in translations) else "DISABLED",
        "sectionTranslations": "ENABLED" if any(r.get("targetType") == "SECTION" for r in translations) else "DISABLED",
        "sentenceTranslations": "ENABLED" if any(r.get("targetType") == "SENTENCE" for r in translations) else "DISABLED",
        "grammarMentions": "ENABLED" if d["grammar_mentions"] else "DISABLED",
        "resolvedGrammarLinks": "ENABLED" if grammar_resolved else ("UNRESOLVED" if d["grammar_links"] else "DISABLED"),
        "speakerData": "DATA_PRESENT" if d["speakers"] else "DISABLED",
        "speakerBindings": "ENABLED" if bound_speakers else ("UNRESOLVED" if d["speakers"] else "DISABLED"),
        "media": "DISABLED" if media_placeholders else ("ENABLED" if d["media"] else "DISABLED"),
    }
    if update_manifest:
        manifest = {"datasetId":"university_russian_2", "textbookId":expected_textbook,
                    "schemaVersion":1, "datasetCount":len(FILES), "counts":counts, "datasetHash":dataset_hash,
                    "capabilities":caps,
                    "validation":{"allJsonParseable":True,"uniqueIdsVerified":not duplicate_ids,"internalReferentialIntegrity":not broken_fk,
                                  "manifestCountsMatch":True,"invalidTokenOffsets":len(invalid_offsets),"unresolvedExternalLexiconReferences":len(external_lexicon),
                                  "unresolvedExternalGrammarReferences":len(external_grammar),"grammarLinksResolved":grammar_resolved,"speakerBindings":bound_speakers,
                                  "ocrSuspects":ocr_suspects,"mediaPlaceholders":media_placeholders,"manualReviewCount":len(d["manual_review"]),
                                  "provisionalExerciseTypes":{t:sum(r.get("type")==t for r in d["exercises"]) for t in sorted({r.get("type") for r in d["exercises"]})}}}
        manifest_path.write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
        mismatches = {}
        metadata_mismatches = {}
        hash_mismatch = False
    print(json.dumps({"datasetId":"university_russian_2", "assetRoot":str(root), "schemaVersion":1,
                      "datasetHash":dataset_hash,"counts":counts,"capabilities":caps,
                      "duplicateIds":duplicate_ids,"brokenInternalForeignKeys":broken_fk,
                      "manifestMismatches":mismatches,"invalidTokenOffsets":invalid_offsets,
                      "manifestMetadataMismatches":metadata_mismatches,
                      "unresolvedExternalLexiconReferences":len(external_lexicon),"unresolvedExternalGrammarReferences":len(external_grammar),"grammarLinksResolved":grammar_resolved,
                      "speakerBindings":bound_speakers,"exerciseTypes":{t:sum(r.get("type")==t for r in d["exercises"]) for t in sorted({r.get("type") for r in d["exercises"]})},
                      "ocrSuspects":ocr_suspects,"mediaPlaceholderCount":media_placeholders,"manualReviewCount":len(d["manual_review"]),
                      "manifestHashMismatch":hash_mismatch,"errors":errors,"warnings":warnings}, ensure_ascii=False, indent=2))
    return 1 if errors or mismatches else 0

if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("dataset", type=Path)
    parser.add_argument("--update-manifest", action="store_true")
    args = parser.parse_args()
    try: sys.exit(validate(args.dataset, args.update_manifest))
    except Exception as exc:
        print(f"validation failed: {exc}", file=sys.stderr); sys.exit(2)
