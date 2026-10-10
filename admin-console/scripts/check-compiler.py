#!/usr/bin/env python3
"""Compile an unchanged online draft in an isolated directory; never publish it."""
import importlib.util
import json
from pathlib import Path
import subprocess
import sys
import uuid
import argparse

ROOT = Path(__file__).resolve().parents[2]
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--source-cache', type=Path)
args = parser.parse_args()
spec = importlib.util.spec_from_file_location('release_compiler', Path(__file__).with_name('compile-release.py'))
compiler = importlib.util.module_from_spec(spec)
spec.loader.exec_module(compiler)
manifest = json.loads(compiler.load(compiler.API + '/api/v1/content/manifest'))
dictionary = json.loads(compiler.load(compiler.API + '/api/v1/dictionary/manifest'))
descriptor = next(item for item in manifest['components'] if item['id'] == 'grammar_points')
blob = compiler.component(manifest['catalogVersion'], descriptor['id'])
if compiler.sha(blob) != descriptor['sha256'] or len(blob) != descriptor['size']:
    raise ValueError('Online smoke source failed hash verification')
records = json.loads(blob)
if not records:
    raise ValueError('Online grammar source is empty')
task_id = str(uuid.uuid4())
folder = ROOT / 'admin-console/.wrangler' / ('compiler-smoke-' + task_id)
folder.mkdir(parents=True)
draft = {'schemaVersion': 1, 'taskId': task_id, 'kind': 'grammar',
         'sourceVersion': manifest['catalogVersion'], 'document': {
             'componentId': descriptor['id'], 'sourceHash': descriptor['sha256'],
             'records': [{'id': records[0]['pointId'], 'fields': {}}]}}
path = folder / 'draft.json'
path.write_text(json.dumps(draft, ensure_ascii=False), encoding='utf8')
commit = subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=ROOT, text=True).strip()
command = [sys.executable, str(Path(__file__).with_name('compile-release.py')),
                         '--draft', str(path), '--output', str(folder / 'build'),
                         '--source-commit', commit]
if args.source_cache:
    command.extend(['--source-cache', str(args.source_cache.resolve())])
result = subprocess.run(command, cwd=ROOT)
if result.returncode:
    raise SystemExit(result.returncode)
bundle = json.loads((folder / 'build/release.json').read_text(encoding='utf8'))
rebuilt = json.loads((folder / 'build/content' / bundle['contentVersion'] / 'manifest.json').read_text(encoding='utf8'))
expected = {item['id']: item['sha256'] for item in manifest['components']}
actual = {item['id']: item['sha256'] for item in rebuilt['components']}
fact_expected = {k: v for k, v in expected.items() if k != 'data_manifest'}
fact_actual = {k: v for k, v in actual.items() if k != 'data_manifest'}
if fact_expected != fact_actual or bundle['dictionaryVersion'] != dictionary['catalogVersion']:
    raise ValueError('Unchanged draft changed online content facts')
metadata = json.loads((folder / 'build/metadata-diff.json').read_bytes())
if metadata['sourceHash'] != expected['data_manifest'] or metadata['contentRecordsChanged']:
    raise ValueError('Metadata provenance is invalid')
print(json.dumps({'status': 'passed', 'sourceCommit': commit, 'sourceVersion': manifest['catalogVersion'],
                  'verifiedFactComponents': len(fact_expected), 'metadataFieldsRebuilt': list(metadata['changes']),
                  'output': str(folder), 'published': False}))
