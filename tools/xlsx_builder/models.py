"""Typed models shared by the RusMorph asset builder."""

from __future__ import annotations

from dataclasses import asdict, dataclass, field
from typing import Any


@dataclass(frozen=True)
class Provenance:
    source_file: str
    source_sheet: str
    source_row: int

    def to_asset(self) -> dict[str, Any]:
        return {
            "sourceWorkbook": self.source_file,
            "sourceSheet": self.source_sheet,
            "sourceRow": self.source_row,
        }


@dataclass(frozen=True)
class SearchForm:
    display: str
    normalized: str
    form_type: str


@dataclass(frozen=True)
class EntryAnnotation:
    field_name: str
    value: str
    normalized_value: str

    def to_asset(self) -> dict[str, str]:
        return {
            "fieldName": self.field_name,
            "value": self.value,
            "normalizedValue": self.normalized_value,
        }


@dataclass
class LexiconRecord:
    id: str
    lesson: int | None
    sequence: int | None
    raw_russian: str
    display_form: str
    primary_form: str
    normalized_primary_form: str
    chinese_meaning: str | None
    parts_of_speech: list[str]
    raw_parts_of_speech: list[str]
    gender: str | None
    declension_class: str | None
    ending_type: str | None
    plural_stress_pattern: str | None
    aspect: str | None
    conjugation_class: str | None
    phonetic_alternation: str | None
    search_forms: list[SearchForm]
    provenance: list[Provenance]
    annotations: list[EntryAnnotation] = field(default_factory=list)
    is_lemma_explicit: bool = True
    related_knowledge_chunk_ids: list[str] = field(default_factory=list)
    knowledge_associations: list[Association] = field(default_factory=list)

    def to_asset(self) -> dict[str, Any]:
        source = self.provenance[0]
        return {
            "id": self.id,
            "lesson": self.lesson,
            "sequence": self.sequence,
            "displayForm": self.display_form,
            "lemma": self.primary_form,
            "normalizedLemma": self.normalized_primary_form,
            "searchForms": list(dict.fromkeys(item.normalized for item in self.search_forms)),
            "chineseMeaning": self.chinese_meaning,
            "partsOfSpeech": self.parts_of_speech,
            "rawPartsOfSpeech": self.raw_parts_of_speech,
            "gender": self.gender,
            "declensionClass": self.declension_class,
            "endingType": self.ending_type,
            "pluralStressPattern": self.plural_stress_pattern,
            "aspect": self.aspect,
            "conjugationClass": self.conjugation_class,
            "phoneticAlternation": self.phonetic_alternation,
            "annotations": [item.to_asset() for item in self.annotations],
            **source.to_asset(),
            "provenance": [item.to_asset() for item in self.provenance],
            "relatedKnowledgeChunkIds": self.related_knowledge_chunk_ids,
        }


@dataclass
class DeclensionRule:
    id: str
    category: str
    gender: str | None
    ending_type: str | None
    number: str | None
    case_name: str
    result_ending: str
    description: str
    source_file: str | None
    source_sheet: str
    source_cell: str
    raw_value: str
    reference_rule: str | None = None
    variants: list[str] = field(default_factory=list)

    def to_asset(self) -> dict[str, Any]:
        return {
            "id": self.id,
            "category": self.category,
            "gender": self.gender,
            "endingType": self.ending_type,
            "number": self.number,
            "caseName": self.case_name,
            "resultEnding": self.result_ending,
            "description": self.description,
            "sourceWorkbook": self.source_file,
            "sourceSheet": self.source_sheet,
            "sourceCellRange": self.source_cell,
            "rawValue": self.raw_value,
            "referenceRule": self.reference_rule,
            "variants": self.variants,
        }


@dataclass
class KnowledgeChunk:
    id: str
    title: str
    category: str
    keywords: list[str]
    content: str
    examples: list[str]
    alternation_patterns: list[str]
    lemma_patterns: list[str]
    morphology_tags: list[str]
    source_document: str
    section_path: list[str]

    def to_asset(self) -> dict[str, Any]:
        return {
            "id": self.id,
            "title": self.title,
            "category": self.category,
            "keywords": self.keywords,
            "content": self.content,
            "examples": self.examples,
            "alternationPatterns": self.alternation_patterns,
            "lemmaPatterns": self.lemma_patterns,
            "morphologyTags": self.morphology_tags,
            "sourceDocument": self.source_document,
            "sectionPath": self.section_path,
        }


@dataclass(frozen=True)
class Association:
    entry_id: str
    knowledge_chunk_id: str
    score: int
    association_type: str
    reason: str
    category: str
    title: str

    def to_report(self) -> dict[str, Any]:
        return {
            "entryId": self.entry_id,
            "knowledgeChunkId": self.knowledge_chunk_id,
            "score": self.score,
            "associationType": self.association_type,
            "reason": self.reason,
            "category": self.category,
            "title": self.title,
        }


@dataclass
class BuildIssue:
    level: str
    code: str
    message: str
    source: str | None = None

    def to_dict(self) -> dict[str, Any]:
        return asdict(self)
