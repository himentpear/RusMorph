import json
from pathlib import Path

from tools.xlsx_builder.build_database_assets import _agent_record
from tools.xlsx_builder.knowledge_association import KnowledgeAssociationEngine
from tools.xlsx_builder.models import Association, KnowledgeChunk, LexiconRecord, Provenance, SearchForm


CONFIG = Path(__file__).resolve().parents[1] / "config"
RULES = json.loads((CONFIG / "knowledge_association_rules.json").read_text(encoding="utf-8"))
PREFIXES = json.loads((CONFIG / "russian_prefixes.json").read_text(encoding="utf-8"))["prefixes"]


def entry(
    lemma: str,
    *,
    entry_id: str = "entry",
    search_forms: list[str] | None = None,
    phonetic: str | None = None,
    conjugation: str | None = None,
) -> LexiconRecord:
    forms = search_forms or [lemma]
    return LexiconRecord(
        id=entry_id, lesson=1, sequence=1, raw_russian=lemma,
        display_form=lemma, primary_form=lemma, normalized_primary_form=lemma,
        chinese_meaning="测试", parts_of_speech=["动词"], raw_parts_of_speech=["动词"],
        gender=None, declension_class=None, ending_type=None, plural_stress_pattern=None,
        aspect=None, conjugation_class=conjugation, phonetic_alternation=phonetic,
        search_forms=[SearchForm(value, value, "HEADWORD" if index == 0 else "INFLECTED") for index, value in enumerate(forms)],
        provenance=[Provenance("test.csv", "词表", 2)],
    )


def chunk(chunk_id: str, category: str, lemma_patterns: list[str] | None = None) -> KnowledgeChunk:
    return KnowledgeChunk(
        id=chunk_id, title=chunk_id, category=category, keywords=[], content="明确资料",
        examples=[], alternation_patterns=[], lemma_patterns=lemma_patterns or [],
        morphology_tags=[], source_document="test.docx", section_path=[chunk_id],
    )


def engine(overrides: dict | None = None) -> KnowledgeAssociationEngine:
    return KnowledgeAssociationEngine(RULES, PREFIXES, overrides)


def test_manual_entry_id_has_highest_priority() -> None:
    item = entry("делать")
    chunks = [chunk("manual", "OTHER"), chunk("direct", "OTHER", ["делать"])]
    result = engine({"entryMappings": [{"entryId": "entry", "knowledgeChunkIds": ["manual"], "reason": "人工核验"}], "lemmaMappings": []}).associate([item], chunks)
    assert item.knowledge_associations[0].score == 1000
    assert item.knowledge_associations[0].knowledge_chunk_id == "manual"
    assert result.applied_overrides


def test_manual_lemma_ignores_accent() -> None:
    item = entry("хотеть")
    result = engine({"entryMappings": [], "lemmaMappings": [{"lemma": "хоте́ть", "category": "MIXED_CONJUGATION", "reason": "文档列举"}]}).associate([item], [chunk("mixed", "MIXED_CONJUGATION")])
    assert result.associations[0].score == 900


def test_primary_form_exact_match_scores_500() -> None:
    item = entry("делать")
    engine().associate([item], [chunk("direct", "OTHER", ["делать"])])
    assert item.knowledge_associations[0].score == 500


def test_search_form_exact_match_scores_450() -> None:
    item = entry("делать", search_forms=["делать", "делаю"])
    engine().associate([item], [chunk("inflected", "OTHER", ["делаю"])])
    assert item.knowledge_associations[0].score == 450


def test_iotation_rule_comes_from_configuration() -> None:
    item = entry("писать", phonetic="с-ш")
    engine().associate([item], [chunk("iot", "IOTATION")])
    assert item.knowledge_associations[0].association_type == "PHONETIC_ALTERNATION"
    assert item.knowledge_associations[0].score == 350


def test_first_palatalization_rule_comes_from_configuration() -> None:
    item = entry("мочь", phonetic="г-ж")
    engine().associate([item], [chunk("pal", "FIRST_PALATALIZATION")])
    assert item.knowledge_associations[0].category == "FIRST_PALATALIZATION"


def test_prefix_derivation_is_manual_review_only() -> None:
    item = entry("написать")
    result = engine().associate([item], [chunk("write", "IOTATION", ["писать"])])
    assert item.knowledge_associations == []
    assert result.manual_review_candidates[0]["score"] == 180


def test_associations_are_deterministic() -> None:
    first = entry("писать", entry_id="one", phonetic="с-ш")
    second = entry("писать", entry_id="one", phonetic="с-ш")
    chunks = [chunk("iot", "IOTATION")]
    left = [item.to_report() for item in engine().associate([first], chunks).associations]
    right = [item.to_report() for item in engine().associate([second], chunks).associations]
    assert left == right


def test_empty_override_is_safe() -> None:
    item = entry("делать")
    result = engine({"entryMappings": [], "lemmaMappings": []}).associate([item], [])
    assert result.associations == [] and result.unused_overrides == []


def test_no_reliable_match_creates_no_association() -> None:
    item = entry("делать")
    result = engine().associate([item], [chunk("unrelated", "OTHER", ["город"])])
    assert result.associations == []
    assert item.related_knowledge_chunk_ids == []


def test_agent_record_references_chunk_without_copying_content() -> None:
    item = entry("писать", phonetic="с-ш")
    item.knowledge_associations = [Association(item.id, "iot", 350, "PHONETIC_ALTERNATION", "规则证据", "IOTATION", "j 音组")]
    item.related_knowledge_chunk_ids = ["iot"]
    record = _agent_record(item)
    assert record["knowledge"][0]["chunkId"] == "iot"
    assert "content" not in record["knowledge"][0]
    assert "相关知识类别：IOTATION" in record["retrievalText"]
