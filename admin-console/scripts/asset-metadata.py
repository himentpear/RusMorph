"""Regenerate metadata from verified assets, preserving every content record."""
import hashlib
import json
from collections import Counter


def raw(value):
    return (json.dumps(value, ensure_ascii=False, sort_keys=True, indent=2) + '\n').encode()


def refresh(assets, source_hash):
    path = assets / 'data_manifest.json'
    before = json.loads(path.read_bytes())
    after = json.loads(path.read_bytes())
    def read(name):
        return json.loads((assets / (name + '.json')).read_bytes())
    entries = read('lexicon')
    counts = {
        'entries': len(entries),
        'searchForms': sum(len(r.get('searchForms', [])) for r in entries),
        'partsOfSpeechRelations': sum(len(r.get('partsOfSpeech', [])) for r in entries),
        'annotationRecords': sum(len(r.get('annotations', [])) for r in entries),
        'provenanceRecords': sum(len(r.get('provenance', [])) for r in entries),
        'declensionRules': len(read('declension_rules')['rules']),
        'knowledgeChunks': len(read('knowledge_chunks')),
        'crossRefs': sum(len(r.get('relatedKnowledgeChunkIds', [])) for r in entries),
        'grammarPoints': len(read('grammar_points')),
        'questions': len(read('tem4_questions')),
        'grammarQuestionLinks': len(read('grammar_question_links')),
        'multiLinkQuestions': sum(n > 1 for n in Counter(r['questionId'] for r in read('grammar_question_links')).values()),
    }
    after['counts'] = {**after.get('counts', {}), **counts}
    after['morphologyCoverage'] = {k: sum(bool(r.get(k)) for r in entries) for k in (
        'partsOfSpeech', 'gender', 'declensionClass', 'endingType', 'pluralStressPattern',
        'aspect', 'conjugationClass', 'phoneticAlternation')}
    hashes = {f.name: hashlib.sha256(f.read_bytes()).hexdigest() for f in sorted(assets.glob('*.json')) if f != path}
    after['assetHashes'] = hashes
    after['grammarTem4AssetHashes'] = {name: hashes[name] for name in (
        'grammar_points.json', 'tem4_questions.json', 'grammar_question_links.json')}
    after['dataVersion'] = hashlib.sha256(raw(hashes)).hexdigest()
    after['metadataGenerator'] = 'werus-admin-assets-v1'
    # Source provenance is kept as evidence, not rewritten as newly verified source files.
    after.setdefault('sourceDataManifestSha256', source_hash)
    changes = {k: {'before': before.get(k), 'after': v} for k, v in after.items() if before.get(k) != v}
    if changes:
        path.write_bytes(raw(after))
    return {'componentId': 'data_manifest', 'sourceHash': source_hash, 'changes': changes,
            'contentRecordsChanged': False}
