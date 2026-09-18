#!/usr/bin/env python3
"""Merge OpenRussian dataset with existing course lexicon (Plan A - 10k Core Lexicon)."""

from __future__ import annotations

import csv
import hashlib
import json
import re
import sys
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

if __package__ in {None, ""}:
    sys.path.insert(0, str(Path(__file__).resolve().parents[2]))
    from tools.xlsx_builder.russian_normalizer import (
        generate_search_variants,
        normalize_russian_for_search,
    )
    from tools.xlsx_builder.validation import verify_assets
else:
    from .russian_normalizer import (
        generate_search_variants,
        normalize_russian_for_search,
    )
    from .validation import verify_assets

SPLIT_RE = re.compile(r"[,;/\\]+")


def clean_form_string(text: str) -> list[str]:
    """Split comma/slash separated inflected forms and normalize for search."""
    if not text or text.strip() in {"", "-", "none", "null"}:
        return []
    results = []
    for part in SPLIT_RE.split(text):
        part = part.strip()
        if not part or part == "-":
            continue
        results.extend(generate_search_variants(part))
    return results


def format_accented(val: str) -> str:
    """Replace single quote stress mark with combining acute accent."""
    if not val or val.strip() in {"", "-", "none", "null"}:
        return ""
    return val.replace("'", "\u0301").strip()


def stable_openrussian_id(pos: str, lemma: str) -> str:
    digest = hashlib.sha256(f"openrussian:{pos}:{lemma}".encode("utf-8")).hexdigest()[:16]
    return f"lex_or_{digest}"


def build_openrussian_enhanced_lexicon(
    assets_dir: Path,
    openrussian_dir: Path,
    max_nouns: int = 3000,
    max_verbs: int = 2500,
    max_adjectives: int = 2000,
    max_others: int = 1500,
) -> dict[str, Any]:
    lexicon_path = assets_dir / "lexicon.json"
    manifest_path = assets_dir / "data_manifest.json"

    with open(lexicon_path, "r", encoding="utf-8") as f:
        existing_entries: list[dict[str, Any]] = json.load(f)

    # Filter out any previously added openrussian entries to start clean from course entries
    course_entries = [e for e in existing_entries if not e["id"].startswith("lex_or_")]
    print(f"Loaded {len(course_entries)} existing course entries from {lexicon_path.name}")

    course_lemma_map: dict[str, list[dict[str, Any]]] = {}
    for entry in course_entries:
        norm = entry["normalizedLemma"]
        course_lemma_map.setdefault(norm, []).append(entry)

    course_extra_search_forms: dict[str, set[str]] = {entry["id"]: set() for entry in course_entries}
    course_inflection_data: dict[str, str] = {}
    new_entries: list[dict[str, Any]] = []

    # 1. Parse nouns.csv
    nouns_csv = openrussian_dir / "nouns.csv"
    if nouns_csv.is_file():
        print(f"Processing nouns.csv (limit new={max_nouns})...")
        with open(nouns_csv, "r", encoding="utf-8") as f:
            reader = csv.DictReader(f, delimiter="\t")
            form_cols = ["sg_nom", "sg_gen", "sg_dat", "sg_acc", "sg_inst", "sg_prep",
                         "pl_nom", "pl_gen", "pl_dat", "pl_acc", "pl_inst", "pl_prep"]
            new_count = 0
            for row in reader:
                bare = (row.get("bare") or "").strip()
                if not bare:
                    continue
                norm_lemma = normalize_russian_for_search(bare)
                accented = (row.get("accented") or bare).strip()
                trans_en = (row.get("translations_en") or "").strip()
                gender_raw = (row.get("gender") or "").strip()
                gender = "阳性" if gender_raw == "m" else "阴性" if gender_raw == "f" else "中性" if gender_raw == "n" else None

                forms = set(generate_search_variants(bare))
                forms.update(generate_search_variants(accented))
                for col in form_cols:
                    val = row.get(col)
                    if val:
                        forms.update(clean_form_string(val))

                noun_infl = {
                    "type": "noun",
                    "partner": format_accented(row.get("partner") or ""),
                    "sg": {
                        "nom": format_accented(row.get("sg_nom") or accented),
                        "gen": format_accented(row.get("sg_gen") or ""),
                        "dat": format_accented(row.get("sg_dat") or ""),
                        "acc": format_accented(row.get("sg_acc") or ""),
                        "inst": format_accented(row.get("sg_inst") or ""),
                        "prep": format_accented(row.get("sg_prep") or ""),
                    },
                    "pl": {
                        "nom": format_accented(row.get("pl_nom") or ""),
                        "gen": format_accented(row.get("pl_gen") or ""),
                        "dat": format_accented(row.get("pl_dat") or ""),
                        "acc": format_accented(row.get("pl_acc") or ""),
                        "inst": format_accented(row.get("pl_inst") or ""),
                        "prep": format_accented(row.get("pl_prep") or ""),
                    },
                }
                has_noun_forms = any(noun_infl["sg"].values()) or any(noun_infl["pl"].values())
                infl_json = json.dumps(noun_infl, ensure_ascii=False) if has_noun_forms else None

                if norm_lemma in course_lemma_map:
                    for matched_entry in course_lemma_map[norm_lemma]:
                        course_extra_search_forms[matched_entry["id"]].update(forms)
                        if infl_json:
                            course_inflection_data.setdefault(matched_entry["id"], infl_json)
                elif new_count < max_nouns:
                    new_count += 1
                    annos = []
                    if infl_json:
                        annos.append({"fieldName": "inflection_data", "value": infl_json, "normalizedValue": "inflection_data"})
                    new_entries.append({
                        "id": stable_openrussian_id("noun", norm_lemma),
                        "lesson": None,
                        "sequence": None,
                        "displayForm": accented.replace("'", "\u0301"),
                        "lemma": bare,
                        "normalizedLemma": norm_lemma,
                        "searchForms": sorted(forms),
                        "chineseMeaning": f"[英] {trans_en}" if trans_en else None,
                        "partsOfSpeech": ["名词"],
                        "rawPartsOfSpeech": ["noun"],
                        "gender": gender,
                        "declensionClass": None,
                        "endingType": None,
                        "pluralStressPattern": None,
                        "aspect": None,
                        "conjugationClass": None,
                        "phoneticAlternation": None,
                        "annotations": annos,
                        "sourceWorkbook": "openrussian.org",
                        "sourceSheet": "nouns",
                        "sourceRow": new_count,
                        "provenance": [{"sourceWorkbook": "openrussian.org", "sourceSheet": "nouns", "sourceRow": new_count}],
                        "relatedKnowledgeChunkIds": [],
                    })

    # 2. Parse verbs.csv
    verbs_csv = openrussian_dir / "verbs.csv"
    if verbs_csv.is_file():
        print(f"Processing verbs.csv (limit new={max_verbs})...")
        with open(verbs_csv, "r", encoding="utf-8") as f:
            reader = csv.DictReader(f, delimiter="\t")
            form_cols = ["imperative_sg", "imperative_pl", "past_m", "past_f", "past_n", "past_pl",
                         "presfut_sg1", "presfut_sg2", "presfut_sg3", "presfut_pl1", "presfut_pl2", "presfut_pl3"]
            new_count = 0
            for row in reader:
                bare = (row.get("bare") or "").strip()
                if not bare:
                    continue
                norm_lemma = normalize_russian_for_search(bare)
                accented = (row.get("accented") or bare).strip()
                trans_en = (row.get("translations_en") or "").strip()
                aspect_raw = (row.get("aspect") or "").strip()
                aspect = "未完成体" if aspect_raw == "imperfective" else "完成体" if aspect_raw == "perfective" else None

                forms = set(generate_search_variants(bare))
                forms.update(generate_search_variants(accented))
                for col in form_cols:
                    val = row.get(col)
                    if val:
                        forms.update(clean_form_string(val))

                partner = (row.get("partner") or "").strip()
                if partner and partner != "-":
                    forms.update(clean_form_string(partner))

                verb_infl = {
                    "type": "verb",
                    "aspect": aspect or "",
                    "partner": format_accented(partner),
                    "presfut": {
                        "sg1": format_accented(row.get("presfut_sg1") or ""),
                        "sg2": format_accented(row.get("presfut_sg2") or ""),
                        "sg3": format_accented(row.get("presfut_sg3") or ""),
                        "pl1": format_accented(row.get("presfut_pl1") or ""),
                        "pl2": format_accented(row.get("presfut_pl2") or ""),
                        "pl3": format_accented(row.get("presfut_pl3") or ""),
                    },
                    "past": {
                        "m": format_accented(row.get("past_m") or ""),
                        "f": format_accented(row.get("past_f") or ""),
                        "n": format_accented(row.get("past_n") or ""),
                        "pl": format_accented(row.get("past_pl") or ""),
                    },
                    "imperative": {
                        "sg": format_accented(row.get("imperative_sg") or ""),
                        "pl": format_accented(row.get("imperative_pl") or ""),
                    },
                }
                has_verb_forms = (
                    any(verb_infl["presfut"].values())
                    or any(verb_infl["past"].values())
                    or any(verb_infl["imperative"].values())
                )
                infl_json = json.dumps(verb_infl, ensure_ascii=False) if has_verb_forms else None

                if norm_lemma in course_lemma_map:
                    for matched_entry in course_lemma_map[norm_lemma]:
                        course_extra_search_forms[matched_entry["id"]].update(forms)
                        if infl_json:
                            course_inflection_data.setdefault(matched_entry["id"], infl_json)
                elif new_count < max_verbs:
                    new_count += 1
                    annos = []
                    if infl_json:
                        annos.append({"fieldName": "inflection_data", "value": infl_json, "normalizedValue": "inflection_data"})
                    new_entries.append({
                        "id": stable_openrussian_id("verb", norm_lemma),
                        "lesson": None,
                        "sequence": None,
                        "displayForm": accented.replace("'", "\u0301"),
                        "lemma": bare,
                        "normalizedLemma": norm_lemma,
                        "searchForms": sorted(forms),
                        "chineseMeaning": f"[英] {trans_en}" if trans_en else None,
                        "partsOfSpeech": ["动词"],
                        "rawPartsOfSpeech": ["verb"],
                        "gender": None,
                        "declensionClass": None,
                        "endingType": None,
                        "pluralStressPattern": None,
                        "aspect": aspect,
                        "conjugationClass": None,
                        "phoneticAlternation": None,
                        "annotations": annos,
                        "sourceWorkbook": "openrussian.org",
                        "sourceSheet": "verbs",
                        "sourceRow": new_count,
                        "provenance": [{"sourceWorkbook": "openrussian.org", "sourceSheet": "verbs", "sourceRow": new_count}],
                        "relatedKnowledgeChunkIds": [],
                    })

    # 3. Parse adjectives.csv
    adj_csv = openrussian_dir / "adjectives.csv"
    if adj_csv.is_file():
        print(f"Processing adjectives.csv (limit new={max_adjectives})...")
        with open(adj_csv, "r", encoding="utf-8") as f:
            reader = csv.DictReader(f, delimiter="\t")
            new_count = 0
            for row in reader:
                bare = (row.get("bare") or "").strip()
                if not bare:
                    continue
                norm_lemma = normalize_russian_for_search(bare)
                accented = (row.get("accented") or bare).strip()
                trans_en = (row.get("translations_en") or "").strip()

                forms = set(generate_search_variants(bare))
                forms.update(generate_search_variants(accented))
                for key, val in row.items():
                    if key.startswith("decl_") or key.startswith("short_") or key in {"comparative", "superlative"}:
                        if val:
                            forms.update(clean_form_string(val))

                adj_infl = {
                    "type": "adjective",
                    "comparative": format_accented(row.get("comparative") or ""),
                    "superlative": format_accented(row.get("superlative") or ""),
                    "short": {
                        "m": format_accented(row.get("short_m") or ""),
                        "f": format_accented(row.get("short_f") or ""),
                        "n": format_accented(row.get("short_n") or ""),
                        "pl": format_accented(row.get("short_pl") or ""),
                    },
                    "cases": {
                        "m": {
                            "nom": format_accented(row.get("decl_m_nom") or accented),
                            "gen": format_accented(row.get("decl_m_gen") or ""),
                            "dat": format_accented(row.get("decl_m_dat") or ""),
                            "acc": format_accented(row.get("decl_m_acc") or ""),
                            "inst": format_accented(row.get("decl_m_inst") or ""),
                            "prep": format_accented(row.get("decl_m_prep") or ""),
                        },
                        "f": {
                            "nom": format_accented(row.get("decl_f_nom") or ""),
                            "gen": format_accented(row.get("decl_f_gen") or ""),
                            "dat": format_accented(row.get("decl_f_dat") or ""),
                            "acc": format_accented(row.get("decl_f_acc") or ""),
                            "inst": format_accented(row.get("decl_f_inst") or ""),
                            "prep": format_accented(row.get("decl_f_prep") or ""),
                        },
                        "n": {
                            "nom": format_accented(row.get("decl_n_nom") or ""),
                            "gen": format_accented(row.get("decl_n_gen") or ""),
                            "dat": format_accented(row.get("decl_n_dat") or ""),
                            "acc": format_accented(row.get("decl_n_acc") or ""),
                            "inst": format_accented(row.get("decl_n_inst") or ""),
                            "prep": format_accented(row.get("decl_n_prep") or ""),
                        },
                        "pl": {
                            "nom": format_accented(row.get("decl_pl_nom") or ""),
                            "gen": format_accented(row.get("decl_pl_gen") or ""),
                            "dat": format_accented(row.get("decl_pl_dat") or ""),
                            "acc": format_accented(row.get("decl_pl_acc") or ""),
                            "inst": format_accented(row.get("decl_pl_inst") or ""),
                            "prep": format_accented(row.get("decl_pl_prep") or ""),
                        },
                    },
                }
                has_adj_forms = (
                    any(adj_infl["short"].values())
                    or any(adj_infl["cases"]["m"].values())
                    or bool(adj_infl["comparative"])
                )
                infl_json = json.dumps(adj_infl, ensure_ascii=False) if has_adj_forms else None

                if norm_lemma in course_lemma_map:
                    for matched_entry in course_lemma_map[norm_lemma]:
                        course_extra_search_forms[matched_entry["id"]].update(forms)
                        if infl_json:
                            course_inflection_data.setdefault(matched_entry["id"], infl_json)
                elif new_count < max_adjectives:
                    new_count += 1
                    annos = []
                    if infl_json:
                        annos.append({"fieldName": "inflection_data", "value": infl_json, "normalizedValue": "inflection_data"})
                    new_entries.append({
                        "id": stable_openrussian_id("adjective", norm_lemma),
                        "lesson": None,
                        "sequence": None,
                        "displayForm": accented.replace("'", "\u0301"),
                        "lemma": bare,
                        "normalizedLemma": norm_lemma,
                        "searchForms": sorted(forms),
                        "chineseMeaning": f"[英] {trans_en}" if trans_en else None,
                        "partsOfSpeech": ["形容词"],
                        "rawPartsOfSpeech": ["adjective"],
                        "gender": None,
                        "declensionClass": None,
                        "endingType": None,
                        "pluralStressPattern": None,
                        "aspect": None,
                        "conjugationClass": None,
                        "phoneticAlternation": None,
                        "annotations": annos,
                        "sourceWorkbook": "openrussian.org",
                        "sourceSheet": "adjectives",
                        "sourceRow": new_count,
                        "provenance": [{"sourceWorkbook": "openrussian.org", "sourceSheet": "adjectives", "sourceRow": new_count}],
                        "relatedKnowledgeChunkIds": [],
                    })

    # 4. Parse others.csv
    others_csv = openrussian_dir / "others.csv"
    if others_csv.is_file():
        print(f"Processing others.csv (limit new={max_others})...")
        with open(others_csv, "r", encoding="utf-8") as f:
            reader = csv.DictReader(f, delimiter="\t")
            new_count = 0
            for row in reader:
                bare = (row.get("bare") or "").strip()
                if not bare:
                    continue
                norm_lemma = normalize_russian_for_search(bare)
                accented = (row.get("accented") or bare).strip()
                trans_en = (row.get("translations_en") or "").strip()

                forms = set(generate_search_variants(bare))
                forms.update(generate_search_variants(accented))

                if norm_lemma in course_lemma_map:
                    for matched_entry in course_lemma_map[norm_lemma]:
                        course_extra_search_forms[matched_entry["id"]].update(forms)
                elif new_count < max_others:
                    new_count += 1
                    new_entries.append({
                        "id": stable_openrussian_id("other", norm_lemma),
                        "lesson": None,
                        "sequence": None,
                        "displayForm": accented.replace("'", "\u0301"),
                        "lemma": bare,
                        "normalizedLemma": norm_lemma,
                        "searchForms": sorted(forms),
                        "chineseMeaning": f"[英] {trans_en}" if trans_en else None,
                        "partsOfSpeech": ["其他"],
                        "rawPartsOfSpeech": ["other"],
                        "gender": None,
                        "declensionClass": None,
                        "endingType": None,
                        "pluralStressPattern": None,
                        "aspect": None,
                        "conjugationClass": None,
                        "phoneticAlternation": None,
                        "annotations": [],
                        "sourceWorkbook": "openrussian.org",
                        "sourceSheet": "others",
                        "sourceRow": new_count,
                        "provenance": [{"sourceWorkbook": "openrussian.org", "sourceSheet": "others", "sourceRow": new_count}],
                        "relatedKnowledgeChunkIds": [],
                    })

    # Merge: update existing course entries with their extra forms and inflection data
    for entry in course_entries:
        merged = {normalize_russian_for_search(s) for s in entry.get("searchForms", []) if normalize_russian_for_search(s)}
        merged.update({normalize_russian_for_search(s) for s in course_extra_search_forms[entry["id"]] if normalize_russian_for_search(s)})
        entry["searchForms"] = sorted(merged)
        if entry["id"] in course_inflection_data:
            annos = [a for a in entry.get("annotations", []) if a.get("fieldName") != "inflection_data"]
            annos.append({
                "fieldName": "inflection_data",
                "value": course_inflection_data[entry["id"]],
                "normalizedValue": "inflection_data",
            })
            entry["annotations"] = annos

    for entry in new_entries:
        entry["searchForms"] = sorted({normalize_russian_for_search(s) for s in entry.get("searchForms", []) if normalize_russian_for_search(s)})

    # Ensure unique IDs across all entries
    seen_ids = set()
    final_entries: list[dict[str, Any]] = []
    for entry in course_entries:
        seen_ids.add(entry["id"])
        final_entries.append(entry)

    added_count = 0
    for entry in new_entries:
        if entry["id"] not in seen_ids:
            seen_ids.add(entry["id"])
            final_entries.append(entry)
            added_count += 1

    print(f"Final total entries: {len(final_entries)} ({len(course_entries)} course + {added_count} OpenRussian extended)")
    total_search_forms = sum(len(e["searchForms"]) for e in final_entries)
    print(f"Final total search forms: {total_search_forms}")

    # Write lexicon.json
    with open(lexicon_path, "w", encoding="utf-8") as f:
        json.dump(final_entries, f, ensure_ascii=False, indent=2)

    # Update data_manifest.json
    with open(manifest_path, "r", encoding="utf-8") as f:
        manifest = json.load(f)

    with open(assets_dir / "declension_rules.json", "r", encoding="utf-8") as f:
        declension = json.load(f)
    with open(assets_dir / "knowledge_chunks.json", "r", encoding="utf-8") as f:
        knowledge = json.load(f)

    # Compute digest for version
    hasher = hashlib.sha256()
    for name in sorted(["lexicon.json", "declension_rules.json", "knowledge_chunks.json"]):
        hasher.update(name.encode("utf-8"))
        hasher.update(b"\x00")
        hasher.update((assets_dir / name).read_bytes())
    new_data_version = hasher.hexdigest()

    manifest["dataVersion"] = new_data_version
    manifest["generatedAt"] = datetime.now(timezone.utc).isoformat()
    manifest["counts"] = {
        "entries": len(final_entries),
        "searchForms": total_search_forms,
        "partsOfSpeechRelations": sum(len(e.get("partsOfSpeech", [])) for e in final_entries),
        "annotationRecords": sum(len(e.get("annotations", [])) for e in final_entries),
        "provenanceRecords": sum(len(e.get("provenance", [])) for e in final_entries),
        "declensionRules": len(declension.get("rules", [])),
        "knowledgeChunks": len(knowledge),
        "crossRefs": sum(len(e.get("relatedKnowledgeChunkIds", [])) for e in final_entries),
    }

    with open(manifest_path, "w", encoding="utf-8") as f:
        json.dump(manifest, f, ensure_ascii=False, indent=2)

    print("Running verify_assets...")
    verify_result = verify_assets(assets_dir)
    print("Verification success:", verify_result)
    return verify_result


if __name__ == "__main__":
    root = Path(__file__).resolve().parents[2]
    assets_dir = root / "app/src/main/assets/database"
    openrussian_dir = root / "data-source/openrussian"
    build_openrussian_enhanced_lexicon(assets_dir, openrussian_dir)
