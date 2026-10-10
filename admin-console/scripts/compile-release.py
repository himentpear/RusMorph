#!/usr/bin/env python3
"""Build a frozen editorial overlay from verified online assets in an isolated directory.

This compiler never modifies the developer's Android assets and never activates R2 pointers.
Every untouched component comes from the recorded online version, not the dirty checkout.
"""
import argparse,datetime as dt,hashlib,importlib.util,json,re,subprocess,sys,urllib.request,zipfile
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]
API='https://api.namchieh.org'
def sha(data):return hashlib.sha256(data).hexdigest()
def raw(data):return (json.dumps(data,ensure_ascii=False,sort_keys=True,indent=2)+'\n').encode()
def load(url,max_bytes=16*1024*1024):
 if not url.startswith(API+'/api/v1/'):raise ValueError('Unexpected source URL')
 request=urllib.request.Request(url,headers={'User-Agent':'WeRus-admin-release/1.0','Accept':'application/json'})
 with urllib.request.urlopen(request,timeout=60) as r:
  data=r.read(max_bytes+1)
 if len(data)>max_bytes:raise ValueError('Source exceeds size limit')
 return data
def component(version,name):return load(API+'/api/v1/content/files/'+version+'/'+name+'.json')

def verify_private_sources(unchanged,output):
 # Verify private repository facts against the unedited online datasetHash before applying edits.
 for name,blob in unchanged.items():
  if name in ('textbook_texts','textbook_dialogues'):continue
  target=output/(name+'.json');target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes(blob)
 for dataset in (output/'textbook').glob('*'):
  canonical=ROOT/'data-source/admin-validation/textbook'/dataset.name
  if not canonical.is_dir():canonical=ROOT/'app/src/main/assets/database/textbook'/dataset.name
  for name in ('manual_review.json','exercise_answers.json'):
   source=canonical/name
   if not source.exists():raise ValueError('Private validation source missing')
   (dataset/name).write_bytes(source.read_bytes())
  result=subprocess.run([sys.executable,str(ROOT/'tools/textbook_dataset/validate_dataset.py'),str(dataset)],capture_output=True,text=True,encoding='utf8')
  if result.returncode:raise ValueError('Repository private facts do not reproduce the unedited online dataset: '+dataset.name)
  report=json.loads(result.stdout)
  if report['manifestHashMismatch'] or report['invalidTokenOffsets']:raise ValueError('Unedited online dataset baseline is invalid')
def apply(records,edits,key):
 by_id={r[key]:r for r in records};seen=set()
 forbidden={'id','pointId','questionId','sentenceId','paragraphId','charStart','charEnd','tokenIndex','sha256','assetHashes','dataVersion','catalogVersion','searchForms','normalizedLemma','lemma','homonym','partsOfSpeech'}
 for row in edits:
  identifier=row['id'];fields=row['fields']
  if identifier in seen or identifier not in by_id:raise ValueError('Duplicate or unknown stable ID')
  seen.add(identifier)
  if forbidden.intersection(fields) or set(fields)-set(by_id[identifier]):raise ValueError('Read-only or unknown field')
  by_id[identifier].update(fields)
 return records
def tokens(sentence_id,text):
 # Same token pattern and stable ID rule used by the existing textbook assembler.
 return [{'id':f'tok_{sentence_id}_{i:04d}','sentenceId':sentence_id,'tokenIndex':i,'surfaceForm':m.group(),'normalizedForm':m.group().replace('\u0301','').lower(),'charStart':m.start(),'charEnd':m.end()} for i,m in enumerate(re.finditer(r'[\w\u0301\-]+|[^\w\s\u0301]',text),1)]
def regenerate(data,component_id,old_rows):
 if '/sentences' not in component_id and '/paragraphs' not in component_id:return
 base=component_id.rsplit('/',1)[0];sentences=data[base+'/sentences'];paragraphs=data[base+'/paragraphs'];old={r['id']:r for r in old_rows}
 if component_id.endswith('/sentences'):
  paragraph_map={r['id']:r for r in paragraphs}
  for row in sentences:
   prev=old[row['id']]
   if row['textRu']!=prev['textRu']:
    p=paragraph_map[row['paragraphId']]
    if p['textRu'].count(prev['textRu'])!=1:raise ValueError('Sentence cannot be uniquely located in paragraph; teacher source review required')
    p['textRu']=p['textRu'].replace(prev['textRu'],row['textRu'],1)
 else:
  for p in paragraphs:
   if p['textRu']!=old[p['id']]['textRu']:
    children=sorted([s for s in sentences if s['paragraphId']==p['id']],key=lambda s:s['order'])
    pieces=[s.strip() for s in re.split(r'(?<=[.!?…])\s+(?=[А-ЯЁ«“])',p['textRu']) if s.strip()]
    if len(pieces)!=len(children):raise ValueError('Paragraph changes sentence structure; preserve existing sentence IDs via a reviewed source migration')
    for child,piece in zip(children,pieces):child['textRu']=piece
 previous=data[base+'/textbook_tokens'];fresh=[t for s in sentences for t in tokens(s['id'],s['textRu'])]
 # Rebind existing lexical links to the same surface occurrence, never silently to a different word.
 def occurrences(rows):
  count={};out={}
  for t in rows:
   k=(t['sentenceId'],t['normalizedForm']);count[k]=count.get(k,0)+1;out[t['id']]=(k,count[k])
  return out
 old_index=occurrences(previous);new_index={v:k for k,v in occurrences(fresh).items()};links=[]
 for link in data[base+'/textbook_lexicon_links']:
  occurrence=old_index.get(link['tokenId']);new=new_index.get(occurrence)
  if not new:raise ValueError('Edited word has a lexical relationship requiring explicit source review')
  links.append({**link,'tokenId':new})
 data[base+'/textbook_tokens']=fresh;data[base+'/textbook_lexicon_links']=links
 # Existing global course reader files are derived from UR1. Preserve their selection, update matching paragraphs/lines.
 if base.endswith('university_russian_1'):
  old_text={r['id']:r['textRu'] for r in old_rows}
  if component_id.endswith('/sentences'):
   replacements=[(old_text[s['id']],s['textRu']) for s in sentences if old_text[s['id']]!=s['textRu']]
  else:replacements=[(old_text[p['id']],p['textRu']) for p in paragraphs if old_text[p['id']]!=p['textRu']]
  for name,field in [('textbook_texts','paragraphs'),('textbook_dialogues','lines')]:
   for lesson in data[name]:
    for i,line in enumerate(lesson[field]):
     for before,after in replacements:
      if before in line:line=line.replace(before,after)
     lesson[field][i]=line
def main():
 p=argparse.ArgumentParser();p.add_argument('--draft',type=Path,required=True);p.add_argument('--output',type=Path,required=True);p.add_argument('--source-commit',required=True);p.add_argument('--dictionary-source',type=Path);p.add_argument('--source-cache',type=Path);a=p.parse_args()
 d=json.loads(a.draft.read_text(encoding='utf8'));doc=d['document'];out=a.output.resolve();out.mkdir(parents=True,exist_ok=True)
 if not re.fullmatch('[a-f0-9]{40}',a.source_commit):raise ValueError('Traceable commit required')
 cm=json.loads(load(API+'/api/v1/content/manifest'));dm=json.loads(load(API+'/api/v1/dictionary/manifest'));expected=dm['catalogVersion'] if d['kind']=='dictionary' else cm['catalogVersion']
 if d['sourceVersion']!=expected:raise ValueError('Online source advanced; create a new draft')
 data={};unchanged={}
 for descriptor in cm['components']:
  cached=a.source_cache/(descriptor['id']+'.json') if a.source_cache else None
  blob=cached.read_bytes() if cached and cached.is_file() else component(cm['catalogVersion'],descriptor['id'])
  if len(blob)!=descriptor['size'] or sha(blob)!=descriptor['sha256']:raise ValueError('Online component failed hash verification')
  data[descriptor['id']]=json.loads(blob);unchanged[descriptor['id']]=blob
 assets=out/'assets';assets.mkdir(exist_ok=True);course=out/'course';course.mkdir(exist_ok=True)
 verify_private_sources(unchanged,out/'verified-baseline')
 if d['kind']=='dictionary':
  # An online dictionary is compiled from reviewed canonical facts, not inferred from its flattened index.
  if not a.dictionary_source:raise ValueError('Canonical dictionary source not configured')
  source=json.loads(a.dictionary_source.read_text(encoding='utf8'))
  # Prove the source recreates the online version before applying any editorial changes.
  sys.path.insert(0,str(ROOT/'tools/lexicon'));import manage
  baseline=manage.build(source,out/'dictionary-baseline',source.get('stage','pilot'),date=dm['catalogVersion'][:10])
  if baseline['catalogVersion']!=dm['catalogVersion'] or baseline['components']['index']['sha256']!=dm['components']['index']['sha256'] or baseline['components']['aliases']['sha256']!=dm['components']['aliases']['sha256']:raise ValueError('Canonical dictionary source does not reproduce online release')
  if doc['componentId']!='dictionary' or doc['sourceHash']!=dm['components']['index']['sha256']:raise ValueError('Frozen dictionary source hash changed')
  apply(source['entries'],doc['records'],'id');manage.validate(source,{'pilot':100,'foundation':1000,'complete':6000}[source.get('stage','pilot')]);manifest=manage.build(source,out/'dictionary',source.get('stage','pilot'));dictionary_version=manifest['catalogVersion']
 else:
  if sha(unchanged[doc['componentId']])!=doc['sourceHash']:raise ValueError('Frozen source hash changed')
  old=json.loads(unchanged[doc['componentId']]);key={'grammar':'pointId','question':'questionId'}.get(d['kind'],'id');apply(data[doc['componentId']],doc['records'],key);regenerate(data,doc['componentId'],old);dictionary_version=dm['catalogVersion']
 for name,value in data.items():
  target=(course if name in ('textbook_texts','textbook_dialogues') else assets)/(name+'.json');target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes(unchanged[name] if value==json.loads(unchanged[name]) else raw(value))
 # Private answer/review sources are never downloaded or exposed to the browser. Verify them using repository validators.
 for dataset in (assets/'textbook').glob('*'):
  canonical=ROOT/'data-source/admin-validation/textbook'/dataset.name
  if not canonical.is_dir():canonical=ROOT/'app/src/main/assets/database/textbook'/dataset.name
  for name in ('manual_review.json','exercise_answers.json'):
   source=canonical/name
   if not source.exists():raise ValueError('Private validation source missing: '+str(source))
   (dataset/name).write_bytes(source.read_bytes())
  validator=ROOT/'tools/textbook_dataset/validate_dataset.py'
  changed=any(value!=json.loads(unchanged[name]) for name,value in data.items() if name.startswith('textbook/'+dataset.name+'/'))
  result=subprocess.run([sys.executable,str(validator),str(dataset)]+(['--update-manifest'] if changed else []),capture_output=True,text=True,encoding='utf8')
  if result.returncode:raise ValueError('Textbook relationships invalid')
  report=json.loads(result.stdout)
  if report['invalidTokenOffsets']:raise ValueError('Invalid token offsets: source migration required')
 # Generated metadata is rebuilt only in this isolated output, after all online hashes and private facts pass.
 # The explicit report accompanies CI artifacts and the admin diff warns before freezing.
 spec=importlib.util.spec_from_file_location('asset_metadata',Path(__file__).with_name('asset-metadata.py'));metadata=importlib.util.module_from_spec(spec);spec.loader.exec_module(metadata)
 report=metadata.refresh(assets,sha(unchanged['data_manifest']));(out/'metadata-diff.json').write_bytes(raw(report))
 sys.path.insert(0,str(ROOT/'tools/cloud_content'));import build_cloud_content
 manifest=build_cloud_content.build(assets,out/'content',course_assets=course);content_version=manifest['catalogVersion']
 # Dictionary untouched: upload only new content; the complete existing dictionary stays referenced.
 bundle={'sourceCommit':a.source_commit,'contentVersion':content_version,'dictionaryVersion':dictionary_version,'files':[]}
 with zipfile.ZipFile(out/'bundle.zip','w',zipfile.ZIP_DEFLATED) as z:
  for prefix,version in [('content',content_version),('dictionary',dictionary_version)]:
   tree=out/prefix/version
   if not tree.exists():continue
   for file in sorted(tree.rglob('*.json')):
    key=prefix+'/'+version+'/'+file.relative_to(tree).as_posix();blob=file.read_bytes();bundle['files'].append({'key':key,'size':len(blob),'sha256':sha(blob)});z.writestr(key,blob)
  z.writestr('release.json',raw(bundle))
 (out/'release.json').write_bytes(raw(bundle));print(json.dumps({'contentVersion':content_version,'dictionaryVersion':dictionary_version,'files':len(bundle['files'])}))
if __name__=='__main__':main()
