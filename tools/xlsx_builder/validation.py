"""Validate generated RusMorph assets without Android or Room."""

from __future__ import annotations

import json
from pathlib import Path
from typing import Any

from . import SCHEMA_VERSION


class ValidationError(ValueError):
    """Raised when generated runtime assets are not internally consistent."""


def _read_json(path: Path) -> Any:
    try:
        return json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as exc:
        raise ValidationError(f"无法解析 {path}: {exc}") from exc


def verify_assets(asset_dir: Path, *, allow_empty: bool = False) -> dict[str, Any]:
    required = ["lexicon.json", "declension_rules.json", "knowledge_chunks.json", "data_manifest.json"]
    missing = [name for name in required if not (asset_dir / name).is_file()]
    if missing:
        raise ValidationError(f"缺少必要 assets：{', '.join(missing)}")
    lexicon = _read_json(asset_dir / "lexicon.json")
    declension = _read_json(asset_dir / "declension_rules.json")
    knowledge = _read_json(asset_dir / "knowledge_chunks.json")
    manifest = _read_json(asset_dir / "data_manifest.json")
    if manifest.get("schemaVersion") != SCHEMA_VERSION:
        raise ValidationError(f"data_manifest schemaVersion 必须为 {SCHEMA_VERSION}")
    if not isinstance(lexicon, list) or not isinstance(knowledge, list) or not isinstance(declension.get("rules"), list):
        raise ValidationError("asset 顶层 JSON 类型不正确")
    if not allow_empty and not lexicon:
        raise ValidationError("词条数量为 0")
    entry_ids = [item.get("id") for item in lexicon]
    chunk_ids = [item.get("id") for item in knowledge]
    if len(entry_ids) != len(set(entry_ids)):
        raise ValidationError("lexicon.json 存在重复主键")
    if len(chunk_ids) != len(set(chunk_ids)):
        raise ValidationError("knowledge_chunks.json 存在重复主键")
    known_chunks = set(chunk_ids)
    for item in lexicon:
        if not item.get("searchForms"):
            raise ValidationError(f"词条 {item.get('id')} 没有 searchForms")
        missing_refs = set(item.get("relatedKnowledgeChunkIds", [])) - known_chunks
        if missing_refs:
            raise ValidationError(f"词条 {item.get('id')} 引用了不存在的知识块 {sorted(missing_refs)}")
        for field in ("gender", "declensionClass", "endingType", "pluralStressPattern", "aspect", "conjugationClass", "phoneticAlternation"):
            value = item.get(field)
            if isinstance(value, str) and value.strip().casefold() in {"8", "null", "none", "-"}:
                raise ValidationError(f"词条 {item.get('id')} 的 {field} 仍为占位值")
    serialized = json.dumps([lexicon, declension, knowledge], ensure_ascii=False)
    if '"null"' in serialized or '"None"' in serialized:
        raise ValidationError("用户可见字段包含字符串 null/None")
    counts = manifest.get("counts", {})
    expected = {
        "entries": len(lexicon),
        "searchForms": sum(len(item.get("searchForms", [])) for item in lexicon),
        "partsOfSpeechRelations": sum(len(item.get("partsOfSpeech", [])) for item in lexicon),
        "annotationRecords": sum(len(item.get("annotations", [])) for item in lexicon),
        "provenanceRecords": sum(len(item.get("provenance", [])) for item in lexicon),
        "declensionRules": len(declension["rules"]),
        "knowledgeChunks": len(knowledge),
        "crossRefs": sum(len(item.get("relatedKnowledgeChunkIds", [])) for item in lexicon),
    }
    for key, actual in expected.items():
        if counts.get(key) != actual:
            raise ValidationError(f"manifest {key}={counts.get(key)}，实际为 {actual}")
    return {"valid": True, "counts": expected}
