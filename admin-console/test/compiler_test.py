import importlib.util,json,shutil,subprocess,sys,tempfile,unittest
from pathlib import Path
spec=importlib.util.spec_from_file_location('compiler',Path(__file__).resolve().parents[1]/'scripts/compile-release.py');compiler=importlib.util.module_from_spec(spec);spec.loader.exec_module(compiler)
class CompilerTests(unittest.TestCase):
 def test_generated_metadata_repair_preserves_facts_and_is_repeatable(self):
  spec=importlib.util.spec_from_file_location('metadata',Path(__file__).resolve().parents[1]/'scripts/asset-metadata.py');metadata=importlib.util.module_from_spec(spec);spec.loader.exec_module(metadata)
  with tempfile.TemporaryDirectory() as folder:
   assets=Path(folder);facts={'lexicon':[{'id':'word','searchForms':['a','b'],'partsOfSpeech':['名词'],'annotations':[],'provenance':['source'],'relatedKnowledgeChunkIds':['k'],'gender':'阳性'}],'declension_rules':{'rules':[]},'knowledge_chunks':[{'id':'k'}],'grammar_points':[{'pointId':'g'}],'tem4_questions':[{'questionId':'q'}],'grammar_question_links':[{'pointId':'g','questionId':'q'}]}
   for name,value in facts.items():(assets/(name+'.json')).write_bytes(metadata.raw(value))
   (assets/'data_manifest.json').write_bytes(metadata.raw({'counts':{'entries':1929},'sourceFiles':[{'name':'original.xlsx'}]}))
   original={p.name:p.read_bytes() for p in assets.glob('*.json') if p.name!='data_manifest.json'}
   report=metadata.refresh(assets,'a'*64);manifest=json.loads((assets/'data_manifest.json').read_bytes())
   self.assertEqual(manifest['counts']['entries'],1);self.assertEqual(manifest['counts']['searchForms'],2);self.assertEqual(manifest['counts']['crossRefs'],1)
   self.assertEqual(manifest['sourceFiles'],[{'name':'original.xlsx'}]);self.assertFalse(report['contentRecordsChanged']);self.assertEqual(manifest['sourceDataManifestSha256'],'a'*64)
   self.assertEqual(original,{name:(assets/name).read_bytes() for name in original})
   self.assertEqual(metadata.refresh(assets,'b'*64)['changes'],{})
 def test_link_rebinding_preserves_occurrence_after_an_inserted_word(self):
  base='textbook/university_russian_2';original=[{'id':'s','paragraphId':'p','order':1,'textRu':'Я люблю чай.'}]
  data={base+'/sentences':[dict(original[0],textRu='Я очень люблю чай.')],base+'/paragraphs':[{'id':'p','textRu':original[0]['textRu']}],base+'/textbook_tokens':compiler.tokens('s',original[0]['textRu']),base+'/textbook_lexicon_links':[{'id':'l','tokenId':'tok_s_0003','entryId':'tea'}]}
  compiler.regenerate(data,base+'/sentences',original)
  self.assertEqual(data[base+'/textbook_lexicon_links'][0]['tokenId'],'tok_s_0004')
  self.assertEqual(data[base+'/paragraphs'][0]['textRu'],'Я очень люблю чай.')
 def test_linked_word_changes_require_relationship_review(self):
  base='textbook/university_russian_2';old=[{'id':'s','paragraphId':'p','order':1,'textRu':'Я люблю чай.'}]
  data={base+'/sentences':[dict(old[0],textRu='Я люблю кофе.')],base+'/paragraphs':[{'id':'p','textRu':old[0]['textRu']}],base+'/textbook_tokens':compiler.tokens('s',old[0]['textRu']),base+'/textbook_lexicon_links':[{'id':'l','tokenId':'tok_s_0003'}]}
  with self.assertRaises(ValueError):compiler.regenerate(data,base+'/sentences',old)
 def test_private_facts_must_reproduce_online_baseline_before_editing(self):
  real=compiler.ROOT
  with tempfile.TemporaryDirectory() as folder:
   root=Path(folder);canonical=root/'app/src/main/assets/database/textbook/university_russian_2';canonical.mkdir(parents=True)
   (root/'tools/textbook_dataset').mkdir(parents=True);validator=root/'tools/textbook_dataset/validate_dataset.py';shutil.copyfile(real/'tools/textbook_dataset/validate_dataset.py',validator)
   spec=importlib.util.spec_from_file_location('dataset_validator',validator);dataset_validator=importlib.util.module_from_spec(spec);spec.loader.exec_module(dataset_validator)
   rows={name:[] for name in dataset_validator.FILES.values()}
   rows.update({'textbooks.json':[{'id':'textbook_university_russian_2'}],'lessons.json':[{'id':'lesson','textbookId':'textbook_university_russian_2'}],'paragraphs.json':[{'id':'p','lessonId':'lesson','type':'TEXT','textRu':'Привет.'}],'sentences.json':[{'id':'s','paragraphId':'p','lessonId':'lesson','textRu':'Привет.'}],'manual_review.json':[{'id':'review','notes':'Synthetic review fixture'}]})
   for name,value in rows.items():(canonical/name).write_bytes(compiler.raw(value))
   subprocess.run([sys.executable,str(validator),str(canonical),'--update-manifest'],check=True,capture_output=True)
   unchanged={'textbook/'+canonical.name+'/'+p.stem:p.read_bytes() for p in canonical.glob('*.json') if p.name not in ('manual_review.json','exercise_answers.json')}
   compiler.ROOT=root
   try:
    compiler.verify_private_sources(unchanged,root/'baseline-valid')
    with (canonical/'manual_review.json').open('ab') as f:f.write(b'\n')
    with self.assertRaisesRegex(ValueError,'do not reproduce'):compiler.verify_private_sources(unchanged,root/'baseline-invalid')
   finally:compiler.ROOT=real
if __name__=='__main__':unittest.main()
