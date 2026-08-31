"""Deterministic, evidence-only lexicon-to-knowledge association engine."""

from __future__ import annotations

import re
from collections import Counter
from dataclasses import dataclass, field
from typing import Any

from .models import Association, KnowledgeChunk, LexiconRecord
from .russian_normalizer import normalize_russian_for_search

RUSSIAN_TOKEN = re.compile(r"[А-Яа-яЁё]+(?:\u0301[А-Яа-яЁё]*)*")


def match_key(value: str) -> str:
    return normalize_russian_for_search(value).replace("ё", "е")


@dataclass
class AssociationResult:
    associations: list[Association]
    manual_review_candidates: list[dict[str, Any]]
    applied_overrides: list[dict[str, Any]]
    unused_overrides: list[dict[str, Any]]
    unmatched_knowledge_examples: list[dict[str, str]]
    warnings: list[str] = field(default_factory=list)


class KnowledgeAssociationEngine:
    def __init__(
        self,
        rules: dict[str, Any],
        prefixes: list[str],
        overrides: dict[str, Any] | None = None,
    ) -> None:
        self.rules = rules
        self.prefixes = sorted({match_key(item) for item in prefixes if item}, key=lambda item: (-len(item), item))
        self.overrides = overrides or {"entryMappings": [], "lemmaMappings": []}
        self.threshold = int(rules.get("automaticThreshold", 200))
        self.maximum = int(rules.get("maximumAutomaticAssociations", 3))

    def associate(self, entries: list[LexiconRecord], chunks: list[KnowledgeChunk]) -> AssociationResult:
        chunks_by_id = {chunk.id: chunk for chunk in chunks}
        chunks_by_category: dict[str, list[KnowledgeChunk]] = {}
        for chunk in chunks:
            chunks_by_category.setdefault(chunk.category, []).append(chunk)
        chunk_forms = {chunk.id: self._chunk_forms(chunk) for chunk in chunks}
        all_entry_forms = {match_key(form.normalized) for entry in entries for form in entry.search_forms}
        applied: list[dict[str, Any]] = []
        unused: list[dict[str, Any]] = []
        confirmed: list[Association] = []
        manual_candidates: list[dict[str, Any]] = []

        entry_overrides: dict[str, list[tuple[KnowledgeChunk, dict[str, Any]]]] = {}
        for mapping in self.overrides.get("entryMappings", []):
            used = False
            for chunk_id in mapping.get("knowledgeChunkIds", []):
                chunk = chunks_by_id.get(chunk_id)
                if chunk is not None:
                    entry_overrides.setdefault(str(mapping.get("entryId", "")), []).append((chunk, mapping))
                    used = True
            (applied if used else unused).append(mapping)

        lemma_overrides: list[tuple[str, str, dict[str, Any]]] = []
        for mapping in self.overrides.get("lemmaMappings", []):
            lemma = match_key(str(mapping.get("lemma", "")))
            category = str(mapping.get("category", ""))
            used = bool(lemma and category in chunks_by_category and any(entry.normalized_primary_form.replace("ё", "е") == lemma for entry in entries))
            if used:
                lemma_overrides.append((lemma, category, mapping))
                applied.append(mapping)
            else:
                unused.append(mapping)

        for entry in entries:
            candidates: dict[str, Association] = {}
            for chunk, mapping in entry_overrides.get(entry.id, []):
                self._offer(candidates, self._association(entry, chunk, 1000, "MANUAL_ENTRY_ID", str(mapping.get("reason") or "人工 entryId 映射")))
            entry_key = match_key(entry.normalized_primary_form)
            for lemma, category, mapping in lemma_overrides:
                if entry_key == lemma:
                    for chunk in chunks_by_category.get(category, []):
                        self._offer(candidates, self._association(entry, chunk, 900, "MANUAL_LEMMA", str(mapping.get("reason") or "人工 lemma 映射")))

            for chunk in chunks:
                if entry_key in chunk_forms[chunk.id]:
                    self._offer(candidates, self._association(entry, chunk, 500, "PRIMARY_FORM", f"主词形 {entry.primary_form} 与知识例词完整匹配"))
                    continue
                other_forms = {match_key(item.normalized) for item in entry.search_forms[1:]}
                matched = sorted(other_forms & chunk_forms[chunk.id])
                if matched:
                    self._offer(candidates, self._association(entry, chunk, 450, "SEARCH_FORM", f"检索词形 {matched[0]} 与知识例词完整匹配"))

            for rule in self.rules.get("fieldRules", []):
                source_value = self._entry_field(entry, str(rule.get("field", "")))
                if source_value is None:
                    continue
                normalized_value = self._label_key(source_value)
                matched_pattern = next((str(pattern) for pattern in rule.get("patterns", []) if self._label_key(str(pattern)) in normalized_value), None)
                if matched_pattern:
                    for chunk in chunks_by_category.get(str(rule.get("category", "")), []):
                        self._offer(candidates, self._association(entry, chunk, int(rule.get("score", 0)), str(rule.get("associationType", "STRUCTURED_FIELD")), f"词条字段 {rule.get('field')} 的 {matched_pattern} 匹配配置规则"))

            entry_labels = self._entry_labels(entry)
            for chunk in chunks:
                matched_tags = entry_labels & {self._label_key(item) for item in chunk.morphology_tags}
                if matched_tags:
                    tag = sorted(matched_tags)[0]
                    self._offer(candidates, self._association(entry, chunk, 250, "MORPHOLOGY_TAG", f"结构化形态标签 {tag} 完整匹配"))

            high_confidence = [item for item in candidates.values() if item.score >= self.threshold]
            if not high_confidence:
                for chunk in chunks:
                    matched_keywords = entry_labels & {self._label_key(item) for item in chunk.keywords}
                    if matched_keywords:
                        keyword = sorted(matched_keywords)[0]
                        self._offer(candidates, self._association(entry, chunk, 100, "CONTROLLED_KEYWORD", f"受控标签 {keyword} 完整匹配"))

            if "动词" in entry.parts_of_speech:
                for form in (entry_key,):
                    for prefix in self.prefixes:
                        if form.startswith(prefix) and len(form) - len(prefix) >= 3:
                            remainder = form[len(prefix):]
                            for chunk in chunks:
                                if remainder in chunk_forms[chunk.id]:
                                    association = self._association(entry, chunk, 180, "PREFIX_DERIVATION_CANDIDATE", f"去除一个受控前缀 {prefix} 后得到知识例词 {remainder}")
                                    self._offer(candidates, association)

            ranked = sorted(candidates.values(), key=lambda item: (-item.score, item.knowledge_chunk_id))
            manual = [item for item in ranked if item.score >= 900]
            automatic = [
                item for item in ranked
                if self.threshold <= item.score < 900
            ][: self.maximum]
            selected = [*manual, *automatic]
            entry.knowledge_associations = selected
            entry.related_knowledge_chunk_ids = [item.knowledge_chunk_id for item in selected]
            confirmed.extend(selected)
            for item in ranked:
                if item.score < self.threshold:
                    source = entry.provenance[0]
                    manual_candidates.append({
                        "entryId": entry.id, "displayForm": entry.display_form,
                        "meaning": entry.chinese_meaning,
                        "candidateKnowledgeChunk": {
                            "chunkId": item.knowledge_chunk_id, "category": item.category, "title": item.title,
                        },
                        "score": item.score, "associationType": item.association_type,
                        "reason": item.reason, "sourceRow": source.source_row,
                    })

        unmatched = []
        for chunk in chunks:
            for example in chunk.examples:
                tokens = {match_key(token) for token in RUSSIAN_TOKEN.findall(example) if len(match_key(token)) >= 2}
                if not (tokens & all_entry_forms):
                    unmatched.append({"knowledgeChunkId": chunk.id, "example": example})
        manual_candidates.sort(key=lambda item: (-int(item["score"]), str(item["entryId"]), str(item["candidateKnowledgeChunk"]["chunkId"])))
        return AssociationResult(confirmed, manual_candidates, applied, unused, unmatched)

    @staticmethod
    def _label_key(value: str) -> str:
        return re.sub(r"\s+", "", value).casefold().replace("–", "-").replace("—", "-")

    def _entry_labels(self, entry: LexiconRecord) -> set[str]:
        values = [
            entry.phonetic_alternation, entry.conjugation_class, entry.declension_class,
            entry.plural_stress_pattern, *entry.parts_of_speech,
        ]
        return {self._label_key(value) for value in values if value}

    @staticmethod
    def _entry_field(entry: LexiconRecord, field_name: str) -> str | None:
        return {
            "phoneticAlternation": entry.phonetic_alternation,
            "conjugationClass": entry.conjugation_class,
            "declensionClass": entry.declension_class,
            "pluralStressPattern": entry.plural_stress_pattern,
        }.get(field_name)

    @staticmethod
    def _chunk_forms(chunk: KnowledgeChunk) -> set[str]:
        values = list(chunk.lemma_patterns)
        values.extend(token for example in chunk.examples for token in RUSSIAN_TOKEN.findall(example))
        return {match_key(value) for value in values if len(match_key(value)) >= 2}

    @staticmethod
    def _association(entry: LexiconRecord, chunk: KnowledgeChunk, score: int, association_type: str, reason: str) -> Association:
        return Association(entry.id, chunk.id, score, association_type, reason, chunk.category, chunk.title)

    @staticmethod
    def _offer(candidates: dict[str, Association], association: Association) -> None:
        current = candidates.get(association.knowledge_chunk_id)
        if current is None or (association.score, association.association_type) > (current.score, current.association_type):
            candidates[association.knowledge_chunk_id] = association


def association_report(entries: list[LexiconRecord], chunks: list[KnowledgeChunk], result: AssociationResult) -> dict[str, Any]:
    by_category = Counter(item.category for item in result.associations)
    by_type = Counter(item.association_type for item in result.associations)
    score_distribution = Counter(str(item.score) for item in result.associations)
    associated_entries = {item.entry_id for item in result.associations}
    return {
        "knowledgeChunks": [{"id": chunk.id, "title": chunk.title, "category": chunk.category, "exampleCount": len(chunk.examples)} for chunk in chunks],
        "totalEntries": len(entries),
        "entriesWithAssociations": len(associated_entries),
        "entriesWithoutAssociations": len(entries) - len(associated_entries),
        "totalCrossRefs": len(result.associations),
        "associationCountsByCategory": dict(sorted(by_category.items())),
        "associationCountsByType": dict(sorted(by_type.items())),
        "scoreDistribution": dict(sorted(score_distribution.items())),
        "appliedOverrides": result.applied_overrides,
        "unusedOverrides": result.unused_overrides,
        "unmatchedKnowledgeExamples": result.unmatched_knowledge_examples,
        "warnings": result.warnings,
    }
