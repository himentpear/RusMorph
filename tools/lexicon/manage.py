#!/usr/bin/env python3
"""Versioned Russian dictionary: candidate selection, editorial validation and immutable packaging."""
from __future__ import annotations
import argparse, datetime as dt, hashlib, json, re, sys, unicodedata
from pathlib import Path
ROOT = Path(__file__).resolve().parents[2]
SOURCE = ROOT / "data-source/common-russian"
OUTPUT = ROOT / "build/cloud-dictionary"
ID = re.compile(r"lex_[a-f0-9]{20}")
RELATIONS = {"synonym", "antonym", "derivation", "cognate", "aspect_pair", "confused_with", "related"}
POS = {"NOUN": "名词", "VERB": "动词", "INFN": "动词", "ADJF": "形容词", "ADJS": "形容词", "ADVB": "副词", "NPRO": "代词", "NUMR": "数词", "PREP": "前置词", "CONJ": "连词", "PRCL": "语气词", "INTJ": "感叹词"}
def read(path): return json.loads(Path(path).read_text(encoding="utf-8"))
def raw(value): return (json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(",", ":")) + "\n").encode()
def digest(data): return hashlib.sha256(data).hexdigest()
def write(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(raw(value))
def plain(s): return unicodedata.normalize("NFC", unicodedata.normalize("NFD", s).replace("\u0301", "")).lower().strip()
def identity(lemma, pos, homonym): return (plain(lemma), pos, str(homonym))
def stable_id(key): return "lex_" + digest(raw(key))[:20]
def require(ok, message):
    if not ok: raise ValueError(message)
def reviewed(value, label):
    review = value.get("review", {})
    require(review.get("status") == "reviewed" and bool(review.get("reviewer", "").strip()), f"{label}: missing real reviewer")
    require(bool(re.fullmatch(r"\d{4}-\d{2}-\d{2}", review.get("reviewedAt", ""))), f"{label}: missing review date")
    dt.date.fromisoformat(review["reviewedAt"])
def stress(text):
    # Multi-syllable Russian words must carry an accent or ё; exceptions must be explicitly reviewed.
    text = re.sub(r"за\u0301 руку|не\u0301 было", "х", text)  # verified stress transfer onto a proclitic
    for word in re.findall(r"[А-Яа-яЁё\u0301]+", text):
        if word.lower() in {"обо", "ото"}: continue  # unstressed preposition variants
        if sum(ch.lower() in "аеёиоуыэюя" for ch in word) > 1 and "\u0301" not in word and "ё" not in word.lower(): return False
    return True

def validate(document, expected=None):
    entries = document.get("entries", [])
    require(document.get("schemaVersion") == 2 and isinstance(entries, list), "invalid schema")
    require(expected is None or len(entries) == expected, f"expected {expected} entries, found {len(entries)}")
    require(0 < len(entries) <= 6000, "entry count must be 1..6000")
    sources = {s["id"]: s for s in document.get("sources", [])}
    require(len(sources) == len(document.get("sources", [])), "duplicate source IDs")
    ids = set(); keys = set(); senses_by_entry = {}
    for entry in entries:
        eid = entry.get("id", "")
        require(ID.fullmatch(eid) and eid not in ids, "invalid/duplicate entry ID")
        ids.add(eid)
        require(entry.get("lemma") and entry.get("displayForm") and entry.get("partsOfSpeech") and entry.get("gloss"), f"{eid}: missing headword, POS or Chinese gloss")
        key = identity(entry["lemma"], entry["partsOfSpeech"][0], entry.get("homonym", 1))
        require(key not in keys, f"{eid}: duplicate lexeme identity"); keys.add(key)
        require(entry.get("tier") in (1,2,3), f"{eid}: invalid tier")
        require(isinstance(entry.get("frequencyRank"), int) and entry["frequencyRank"] > 0 and entry.get("selectionReason"), f"{eid}: missing selection provenance")
        reviewed(entry, eid)
        def evidence(value, label):
            reviewed(value, label)
            refs = value.get("sourceRefs", [])
            require(isinstance(refs, list) and bool(refs), f"{label}: missing source references")
            for sid in refs:
                s = sources.get(sid)
                require(s and s.get("redistributionVerified") is True and s.get("license") and s.get("url") and s.get("version") and re.fullmatch(r"[a-f0-9]{64}", s.get("sha256", "")), f"{label}: unverified source {sid}")
            if value.get("origin") == "ai_generated":
                g = value.get("generation", {})
                require(g.get("model") and g.get("generatedAt") and g.get("promptVersion"), f"{label}: missing generation record")
        entry_senses = entry.get("senses", [])
        require(bool(entry_senses), f"{eid}: no senses")
        sense_ids = {s.get("id") for s in entry_senses}
        require(None not in sense_ids and len(sense_ids) == len(entry_senses), f"{eid}: duplicate/missing sense ID")
        senses_by_entry[eid] = sense_ids
        for sense in entry_senses:
            require(sense.get("definition"), f"{eid}: empty sense"); evidence(sense, f"{eid}/{sense['id']}")
        examples = entry.get("examples", [])
        require(len(examples) >= 2, f"{eid}: at least two examples required")
        require(len({plain(ex.get("ru", "")) for ex in examples}) == len(examples), f"{eid}: duplicate examples")
        require(sense_ids <= {ex.get("senseId") for ex in examples}, f"{eid}: sense lacks an example")
        for ex in examples:
            require(ex.get("senseId") in sense_ids and ex.get("zh") and ex.get("ru"), f"{eid}: invalid example")
            require(not re.search(r"[A-Za-z]", ex["ru"]), f"{eid}: Latin letters in Russian example")
            require(stress(ex["ru"]), f"{eid}: example missing Russian stress"); evidence(ex, f"{eid}/example")
            require(ex.get("origin") in ("source", "ai_generated", "editorial"), f"{eid}: example origin required")
        pronunciations = entry.get("pronunciations", [])
        require(bool(pronunciations), f"{eid}: IPA missing")
        require(stress(entry.get("displayForm", "")), f"{eid}: headword missing stress")
        for p in pronunciations:
            require(p.get("ipa") and p.get("notation") == "IPA", f"{eid}: invalid IPA notation")
            require(p.get("senseId") is None or p["senseId"] in sense_ids, f"{eid}: dangling pronunciation sense")
            evidence(p, f"{eid}/IPA")
        for group in ("relations", "pronunciations"):
            require(isinstance(entry.get(group), list), f"{eid}: {group} must be an array")
        status = entry.get("fieldStatus", {})
        require(status.get("relations") in ("available", "verified_absent", "missing"), f"{eid}: invalid relation status")
        require((status["relations"] == "available") == bool(entry["relations"]), f"{eid}: inconsistent relation status")
        usage = entry.get("usage", {})
        require(isinstance(usage, dict), f"{eid}: invalid usage")
        require(status.get("usage") in ("available", "verified_absent"), f"{eid}: usage not examined")
        require((status["usage"] == "available") == any(bool(values) for values in usage.values()), f"{eid}: inconsistent usage status")
        for group, values in usage.items():
            for value in values if isinstance(values, list) else [values]:
                require(isinstance(value, dict), f"{eid}: usage item must have provenance"); evidence(value, f"{eid}/{group}")
        forms = entry.get("inflections", {})
        require(forms.get("status") in ("available", "not_applicable", "verified_absent"), f"{eid}: inflections unexamined")
        evidence(forms, f"{eid}/inflections")
        paradigms = forms.get("paradigms", [])
        require(isinstance(paradigms, list) and (bool(paradigms) == (forms["status"] == "available")), f"{eid}: inconsistent inflection status")
        for paradigm in paradigms:
            require(paradigm.get("id") and paradigm.get("title") and paradigm.get("forms"), f"{eid}: empty paradigm")
            for f in paradigm["forms"]:
                require(f.get("text") and stress(f.get("stressedText") or f["text"]), f"{eid}: form missing stress")
                require(any(k in f for k in ("case", "person", "gender", "number", "tense", "mood", "nonfinite")), f"{eid}: form lacks grammatical labels")
                for v in f.get("variants", []): require(v.get("text") and stress(v.get("stressedText") or v["text"]) and v.get("condition"), f"{eid}: invalid form variant")
    for entry in entries:
        for rel in entry["relations"]:
            require(rel.get("type") in RELATIONS and rel.get("senseId") in senses_by_entry[entry["id"]] and rel.get("targetId") in ids, f"{entry['id']}: dangling/invalid relation")
            require(rel.get("targetSenseId") is None or rel["targetSenseId"] in senses_by_entry[rel["targetId"]], "dangling target sense")
            evidence(rel, f"{entry['id']}/relation")
    aliases = document.get("aliases", [])
    require(len({a.get("entryId") for a in aliases}) == len(aliases), "duplicate aliases")
    for alias in aliases:
        require(ID.fullmatch(alias.get("entryId", "")) and alias.get("canonicalId") in ids, "dangling alias")
        require(alias["entryId"] not in ids or alias["entryId"] == alias["canonicalId"], "alias shadows canonical ID")
        require(alias.get("senseId") is None or alias["senseId"] in senses_by_entry[alias["canonicalId"]], "dangling alias sense")
        reviewed(alias, "alias")
    return {"entries": len(entries), "aliases": len(aliases), "examples": sum(len(e["examples"]) for e in entries)}

def build(document, output=OUTPUT, stage="pilot", date=None):
    expected = {"pilot": 100, "foundation": 1000, "complete": 6000}[stage]
    counts = validate(document, expected)
    files = {}; index = []
    for entry in document["entries"]:
        eid = entry["id"]
        refs = set()
        def collect(value):
            if isinstance(value, dict):
                refs.update(value.get("sourceRefs", []))
                for v in value.values(): collect(v)
            elif isinstance(value, list):
                for v in value: collect(v)
        collect(entry)
        detail = {k:v for k,v in entry.items() if k in {"id", "lemma", "displayForm", "partsOfSpeech", "gloss", "homonym", "tier", "frequencyRank", "selectionReason", "morphology", "review", "senses", "examples", "relations", "pronunciations", "usage", "fieldStatus", "sourceRefs", "authorityReferences", "quality"}}
        detail.update(schemaVersion=2, sources=[s for s in document["sources"] if s["id"] in refs])
        forms = dict(entry["inflections"], entryId=eid)
        detail_name = f"entries/{eid}.json"; form_name = f"inflections/{eid}.json"
        files[detail_name] = raw(detail); files[form_name] = raw(forms)
        require(len(files[detail_name]) <= 256_000 and len(files[form_name]) <= 256_000, f"{eid}: detail/forms exceed client payload budget")
        row = {k:entry[k] for k in ("id", "lemma", "displayForm", "partsOfSpeech", "gloss", "tier")}
        row["normalizedLemma"] = plain(entry["lemma"]).replace("ё", "е")
        row["searchForms"] = sorted(set([plain(entry["lemma"])] + [plain(f.get("stressedText") or f["text"]) for p in forms["paradigms"] for f in p["forms"]] + [plain(v.get("stressedText") or v["text"]) for p in forms["paradigms"] for f in p["forms"] for v in f.get("variants", [])]))
        row["detail"] = {"key":detail_name,"size":len(files[detail_name]),"sha256":digest(files[detail_name])}
        row["inflections"] = {"key":form_name,"size":len(files[form_name]),"sha256":digest(files[form_name])}
        index.append(row)
    files["aliases.json"] = raw(document.get("aliases", []))
    # Include the index content in version identity before adding its derived version prefix.
    identity_bytes = raw(index) + raw(document.get("aliases", []))
    date = date or dt.datetime.now(dt.timezone.utc).strftime("%Y.%m.%d")
    require(bool(re.fullmatch(r"\d{4}\.\d{2}\.\d{2}", date)), "invalid version date")
    version = date + "." + digest(identity_bytes)[:12]
    prefix = f"dictionary/{version}/"
    for row in index:
        row["detail"]["key"] = prefix + row["detail"]["key"]
        row["inflections"]["key"] = prefix + row["inflections"]["key"]
    files["index.json"] = raw(index)
    require(len(files["index.json"]) <= 8_000_000 and len(files["aliases.json"]) <= 8_000_000, "index/aliases exceed service payload budget")
    manifest = {"schemaVersion":2,"catalogVersion":version,"entryCount":counts["entries"],"stage":stage,"counts":counts,"components":{name[:-5]:{"key":prefix+name,"size":len(files[name]),"sha256":digest(files[name])} for name in ("index.json","aliases.json")}}
    files["manifest.json"] = raw(manifest)
    for name, data in files.items():
        target=output/version/name
        require(not target.exists() or target.read_bytes()==data, f"immutable output differs: {target}")
        target.parent.mkdir(parents=True,exist_ok=True); target.write_bytes(data)
    # This is a proposal file; build never activates a local or remote current pointer.
    write(output/version/"activation.json", {"schemaVersion":2,"catalogVersion":version,"manifestKey":prefix+"manifest.json"})
    return manifest

def candidates(output=SOURCE):
    require(not (output/"candidates.json").exists() and not (output/"pilot-draft.json").exists(), "candidate/editorial files already exist; use a new --output directory")
    sys.path.insert(0,str(ROOT/"build/lexicon-runtime"))
    from wordfreq import top_n_list, zipf_frequency
    import pymorphy3
    morph=pymorphy3.MorphAnalyzer()
    lex=read(ROOT/"app/src/main/assets/database/lexicon.json")
    by_lemma={}
    for e in lex: by_lemma.setdefault(plain(e["lemma"]),[]).append(e)
    rows=[]; seen=set(); rejected=[]
    for rank,word in enumerate(top_n_list("ru",100000),1):
        if not re.fullmatch(r"[а-яё]+(?:-[а-яё]+)?",word): continue
        parsed=morph.parse(word)
        # Lemmatization is a candidate aid only: disambiguation and modern usage need teacher review.
        usable=[p for p in parsed if p.is_known and p.tag.POS in POS and not any(t in p.tag for t in ("Name","Surn","Patr","Geox","Orgn","Trad"))]
        if not usable: continue
        best=usable[0]; lemma=best.normal_form; pos=POS[best.tag.POS]
        if len(lemma) < 2 and lemma not in {"я", "в", "и", "а", "с", "о", "к", "у"}: continue
        key=identity(lemma,pos,1)
        if key in seen: continue
        seen.add(key)
        row={"id":stable_id(key),"lemma":lemma,"partsOfSpeech":[pos],"homonym":1,"frequencyRank":rank,"frequencyWord":word,"zipf":zipf_frequency(word,"ru"),"tier":1 if len(rows)<1000 else 2 if len(rows)<3000 else 3,"selectionReason":"modern_frequency_candidate","status":"candidate","sourceRefs":["wordfreq-3.1.1","pymorphy3-2.0.6"],"ambiguousAnalyses":[{"lemma":p.normal_form,"tag":str(p.tag),"score":p.score} for p in parsed[:3]],"textbookCandidates":[e["id"] for e in by_lemma.get(lemma,[]) if pos in e.get("partsOfSpeech",[])]}
        rows.append(row)
        if len(rows)==6000: break
    require(len(rows)==6000,"insufficient distinct frequency candidates")
    write(output/"candidates.json",{"schemaVersion":2,"status":"candidate_only","method":"wordfreq 3.1.1 ru; pymorphy3 2.0.6 first known non-proper analysis; teacher review required","entries":rows})
    # 100 editorial records: missing facts remain explicit, never fabricated.
    pilot=[]
    special={"замок","мука","идти","ехать","человек","ребёнок","писать","написать","лес","читать"}
    quotas={"名词":25,"动词":25,"形容词":15,"代词":10,"副词":10,"前置词":5,"连词":4,"数词":3,"语气词":2,"感叹词":1}
    selected=[row for row in rows if row["lemma"] in special][:10]
    for pos,limit in quotas.items():
        remaining=limit-sum(row["partsOfSpeech"][0]==pos for row in selected)
        selected.extend([row for row in rows if row["partsOfSpeech"][0]==pos and row not in selected][:max(0,remaining)])
    for row in rows:
        if len(selected)>=100:break
        if row not in selected:selected.append(row)
    selected=selected[:100]
    for row in selected:
        matches=[e for e in lex if e["id"] in row["textbookCandidates"]]
        e=matches[0] if matches else {}
        pilot.append({**row,"displayForm":e.get("displayForm",row["lemma"]),"gloss":e.get("chineseMeaning",""),"review":{"status":"draft","reviewer":None,"reviewedAt":None},"senses":[],"examples":[],"relations":[],"pronunciations":[],"usage":{},"fieldStatus":{"relations":"missing","usage":"missing"},"inflections":{"status":"missing","paradigms":[],"review":{"status":"draft"},"sourceRefs":[]}})
    write(output/"pilot-draft.json",{"schemaVersion":2,"stage":"pilot","entries":pilot,"sources":[],"aliases":[]})
    return {"candidates":6000,"pilotDrafts":len(pilot),"mappedCandidates":sum(bool(e["textbookCandidates"]) for e in rows)}

def refresh_manifest():
    assets=ROOT/"app/src/main/assets/database"
    lex=read(assets/"lexicon.json"); m=read(assets/"data_manifest.json")
    m.setdefault("counts",{})["entries"]=len(lex)
    m["counts"]["searchForms"]=sum(len(e.get("searchForms",[])) for e in lex)
    m["counts"]["partsOfSpeechRelations"]=sum(len(e.get("partsOfSpeech",[])) for e in lex)
    m["counts"]["annotationRecords"]=sum(len(e.get("annotations",[])) for e in lex)
    m["counts"]["provenanceRecords"]=sum(len(e.get("provenance",[])) for e in lex)
    m["morphologyCoverage"]={k:sum(bool(e.get(k)) for e in lex) for k in m.get("morphologyCoverage",{})}
    m["assetHashes"]={p.name:digest(p.read_bytes()) for p in sorted(assets.glob("*.json")) if p.name!="data_manifest.json"}
    m["dataVersion"]=digest(raw(m["assetHashes"]))
    m["countsRefreshedAt"]=dt.datetime.now(dt.timezone.utc).isoformat()
    write(assets/"data_manifest.json",m)
    return {"entries":len(lex)}

def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument("command",choices=["candidates","validate","build","refresh-manifest"])
    parser.add_argument("--input",type=Path,default=SOURCE/"pilot-draft.json")
    parser.add_argument("--output",type=Path)
    parser.add_argument("--stage",choices=["pilot","foundation","complete"],default="pilot")
    args=parser.parse_args()
    try:
        if args.command=="candidates": result=candidates(args.output or SOURCE)
        elif args.command=="refresh-manifest": result=refresh_manifest()
        elif args.command=="validate": result=validate(read(args.input),{"pilot":100,"foundation":1000,"complete":6000}[args.stage])
        else: result=build(read(args.input),args.output or OUTPUT,args.stage)
        print(json.dumps(result,ensure_ascii=False))
    except (ValueError,OSError,KeyError) as exc:
        print(f"Dictionary task failed: {exc}",file=sys.stderr); raise SystemExit(1)
if __name__=="__main__": main()
